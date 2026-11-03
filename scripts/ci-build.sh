#!/usr/bin/env bash
#
# Builds the release APK, checks it, and writes the release files to dist/.
#
# Called only by the release workflow (see docs/WORKFLOW.md), with CI=true. It needs these
# environment variables from the workflow: KEYSTORE_BASE64, KEYSTORE_PASS, KEY_ALIAS, KEY_PASS,
# and GH_TOKEN for the previous-release check. Nothing is committed or written to tracked files.
#
# Output in dist/:
#   GKUICalendar-<versionName>.apk
#   GKUICalendar-<versionName>.apk.sha256
#   build-info.json   (read by GKUI-Pack apps.yaml; keep the field names stable)
# and, outside dist/, build/release-notes.md for the release body: the top section of
# CHANGELOG.md (Chinese and English), the install notes and the build information.
# The tag v<versionName> is written to GITHUB_OUTPUT as "tag".

set -euo pipefail

readonly APP_NAME="GKUICalendar"
# The release applicationId. Must match app/build.gradle.kts and the LineageOS built-in package.
readonly EXPECTED_APPLICATION_ID="org.lineageos.etar"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly REPO_ROOT
readonly SIGNER_FILE="${REPO_ROOT}/release-signer.sha256"
readonly CHANGELOG_FILE="${REPO_ROOT}/CHANGELOG.md"
readonly APK_IN="${REPO_ROOT}/app/build/outputs/apk/release/app-release.apk"
readonly DIST_DIR="${REPO_ROOT}/dist"
readonly NOTES_FILE="${REPO_ROOT}/build/release-notes.md"

die() {
    echo "::error::$*" >&2
    exit 1
}

# Progress goes to stderr: some functions print values on stdout and are captured with $(...).
log() {
    echo "[ci-build] $*" >&2
}

require_env() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        die "environment variable ${name} is not set"
    fi
}

# Locates a build-tools binary. Prefers PATH, then the newest build-tools under the SDK.
find_build_tool() {
    local name="$1"
    if command -v "${name}" >/dev/null 2>&1; then
        command -v "${name}"
        return
    fi
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "${sdk}" ]]; then
        die "ANDROID_HOME is not set; cannot find ${name}"
    fi
    local found
    found="$(find "${sdk}/build-tools" -mindepth 2 -maxdepth 2 -name "${name}" 2>/dev/null | sort -V | tail -n 1)"
    if [[ -z "${found}" ]]; then
        die "${name} not found under ${sdk}/build-tools; install a build-tools package"
    fi
    echo "${found}"
}

# Prints the body of the first "## " section of CHANGELOG.md: no heading, no leading or trailing
# blank lines. Prints nothing when the file or the section is missing. Only the top section is
# published; older versions stay below it.
changelog_body() {
    [[ -f "${CHANGELOG_FILE}" ]] || return 0
    awk '
        /^## / { section++; next }
        section > 1 { exit }
        section == 1 { lines[++n] = $0 }
        END {
            first = 1; last = n
            while (first <= last && lines[first] ~ /^[[:space:]]*$/) first++
            while (last >= first && lines[last] ~ /^[[:space:]]*$/) last--
            for (i = first; i <= last; i++) print lines[i]
        }
    ' "${CHANGELOG_FILE}"
}

# The release body is written for people who install the app, so it must exist before the build
# spends minutes producing an APK that would then be published with empty notes.
require_changelog() {
    if [[ ! -f "${CHANGELOG_FILE}" ]]; then
        die "CHANGELOG.md is missing; the release body comes from its first \"## \" section"
    fi
    if [[ -z "$(changelog_body)" ]]; then
        die "the first \"## \" section of CHANGELOG.md is empty; write the update notes in Chinese and English before publishing"
    fi
}

# Checks the signing environment before anything is built. A release build costs minutes, so a
# missing secret must fail here with a readable message, not after the APK has been packaged.
require_signing_env() {
    local name missing=()
    for name in KEYSTORE_BASE64 KEYSTORE_PASS KEY_ALIAS KEY_PASS; do
        if [[ -z "${!name:-}" ]]; then
            missing+=("${name}")
        fi
    done
    if (( ${#missing[@]} > 0 )); then
        die "release signing environment incomplete; missing: ${missing[*]}. \
Set the repository secrets KEYSTORE_BASE64, KEYSTORE_PASS, KEY_ALIAS and KEY_PASS \
(see docs/WORKFLOW.md). Release builds in CI never fall back to the debug key."
    fi
}

# Opens the decoded keystore with the JDK before Gradle runs. A wrong store password or an alias
# that is not in the keystore would otherwise surface only inside the build. The password is
# passed by environment reference, so it never appears in a process list.
check_keystore() {
    local keytool out
    keytool="$(command -v keytool || true)"
    if [[ -z "${keytool}" ]]; then
        die "keytool not found on PATH; a JDK is required to check the release keystore"
    fi
    if ! out="$("${keytool}" -list -keystore "${KEYSTORE_FILE}" \
            -storepass:env KEYSTORE_PASS -alias "${KEY_ALIAS}" 2>&1)"; then
        printf '%s\n' "${out}" >&2
        die "cannot open the release keystore with KEYSTORE_PASS and KEY_ALIAS; check the secrets"
    fi
    log "release keystore and key alias verified before the build"
}

# Decodes the keystore into a private temporary file. The file is removed on exit.
# Global, so the EXIT trap can still see it after the function returns.
KEYSTORE_TMP=""

decode_keystore() {
    require_env KEYSTORE_BASE64
    umask 077
    KEYSTORE_TMP="$(mktemp "${RUNNER_TEMP:-/tmp}/release-keystore.XXXXXX")"
    trap 'rm -f "${KEYSTORE_TMP}"' EXIT
    if ! printf '%s' "${KEYSTORE_BASE64}" | base64 --decode > "${KEYSTORE_TMP}"; then
        die "KEYSTORE_BASE64 is not valid base64"
    fi
    if [[ ! -s "${KEYSTORE_TMP}" ]]; then
        die "decoded keystore is empty"
    fi
    export KEYSTORE_FILE="${KEYSTORE_TMP}"
}

# Reads "name" from aapt2 badging output, first match only.
badging_field() {
    local badging="$1"
    local regex="$2"
    sed -n "${regex}" <<<"${badging}" | head -n 1
}

# Lower-case hex SHA-256 with spaces, colons and line breaks removed.
normalise_fingerprint() {
    tr -d ' :\r\n' | tr 'A-F' 'a-f'
}

# Prints the versionCode of the most recent release that has a build-info.json.
# Prints nothing when there is no earlier release, or when it has no build-info.json.
previous_version_code() {
    local tag
    if ! tag="$(gh release list --exclude-drafts --limit 1 --json tagName --jq '.[0].tagName // empty')"; then
        die "cannot list releases with gh (check GH_TOKEN)"
    fi
    if [[ -z "${tag}" ]]; then
        return
    fi
    local assets
    if ! assets="$(gh release view "${tag}" --json assets --jq '.assets[].name')"; then
        die "cannot read assets of release ${tag}"
    fi
    if ! grep -qx 'build-info.json' <<<"${assets}"; then
        log "previous release ${tag} has no build-info.json; versionCode check skipped"
        return
    fi
    local tmp_dir
    tmp_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/prev-release.XXXXXX")"
    if ! gh release download "${tag}" --pattern build-info.json --dir "${tmp_dir}" --clobber >/dev/null; then
        die "cannot download build-info.json from release ${tag}"
    fi
    jq -r '.versionCode' "${tmp_dir}/build-info.json"
    rm -rf "${tmp_dir}"
}

main() {
    if [[ "${CI:-}" != "true" ]]; then
        die "ci-build.sh is for CI only (CI=true); local release builds use Gradle directly"
    fi
    require_signing_env
    require_changelog
    local aapt2 apksigner
    aapt2="$(find_build_tool aapt2)"
    apksigner="$(find_build_tool apksigner)"

    decode_keystore
    check_keystore

    log "building release APK"
    cd "${REPO_ROOT}"
    ./gradlew --no-daemon assembleRelease

    # The build must not have changed any tracked file. Build output is git-ignored.
    if [[ -n "$(git status --porcelain)" ]]; then
        git status --short
        die "the build changed tracked files; release refused"
    fi

    if [[ ! -f "${APK_IN}" ]]; then
        die "release APK not found at ${APK_IN}"
    fi

    # --- Gate 1: package name and debuggable flag -------------------------------------------
    local badging app_id version_code version_name
    badging="$("${aapt2}" dump badging "${APK_IN}")"
    app_id="$(badging_field "${badging}" "s/^package: name='\([^']*\)'.*/\1/p")"
    version_code="$(badging_field "${badging}" "s/^package: .* versionCode='\([^']*\)'.*/\1/p")"
    version_name="$(badging_field "${badging}" "s/^package: .* versionName='\([^']*\)'.*/\1/p")"

    if [[ "${app_id}" =~ \.(dev|debug)$ ]]; then
        die "applicationId ${app_id} carries a dev/debug suffix"
    fi
    if [[ "${app_id}" != "${EXPECTED_APPLICATION_ID}" ]]; then
        die "applicationId is ${app_id}, expected ${EXPECTED_APPLICATION_ID}"
    fi
    if [[ ! "${version_code}" =~ ^[0-9]+$ ]]; then
        die "versionCode is not a number: '${version_code}'"
    fi
    if [[ -z "${version_name}" ]]; then
        die "versionName is empty"
    fi

    local manifest
    manifest="$("${aapt2}" dump xmltree --file AndroidManifest.xml "${APK_IN}")"
    if grep -Eq 'android:debuggable\(0x[0-9a-f]+\)=\(type 0x[0-9a-f]+\)0xffffffff' <<<"${manifest}"; then
        die "release APK is debuggable"
    fi
    log "applicationId=${app_id} versionCode=${version_code} versionName=${version_name}"

    # --- Gate 2: signing certificate -------------------------------------------------------
    # Both streams are captured and printed: apksigner writes certificates to stdout and problems
    # to stderr, and the job log is the only record of what this gate actually saw.
    local signer_out signer_count fingerprint expected
    local -a fingerprints=()
    if ! signer_out="$("${apksigner}" verify --verbose --print-certs "${APK_IN}" 2>&1)"; then
        printf '%s\n' "${signer_out}" >&2
        die "apksigner verify failed: the APK is not correctly signed"
    fi
    printf '%s\n' "${signer_out}" >&2

    # apksigner does not use one stable label for the certificate. Seen in the wild:
    #   Signer #1 certificate SHA-256 digest: …                                  (older tools)
    #   Signer (minSdkVersion=…, maxSdkVersion=…) certificate SHA-256 digest: …  (v3.1 rotation)
    #   V2 Signer: certificate SHA-256 digest: …                                 (build-tools 36)
    # Matching one spelling reads a correctly signed APK as unsigned. Select the certificate
    # lines by what they are, not by how the signer is named: any line that names a Signer and
    # carries a certificate SHA-256 digest, excluding the source stamp, which is not an APK
    # signer. The same certificate is printed once per scheme, so count distinct digests.
    # "public key SHA-256 digest" lines are not certificates and never match.
    mapfile -t fingerprints < <(
        grep -i 'certificate SHA-256 digest:' <<<"${signer_out}" \
            | grep -i 'signer' \
            | grep -vi 'stamp' \
            | sed -n 's/.*certificate SHA-256 digest: *\([0-9a-fA-F]*\).*/\1/p' \
            | tr 'A-F' 'a-f' \
            | grep -E '^[0-9a-f]{64}$' \
            | sort -u
    )
    signer_count="${#fingerprints[@]}"
    if [[ "${signer_count}" != "1" ]]; then
        die "expected exactly one signer, found ${signer_count} distinct signing certificates in apksigner output"
    fi
    fingerprint="${fingerprints[0]}"
    if [[ ! "${fingerprint}" =~ ^[0-9a-f]{64}$ ]]; then
        die "could not read the signer SHA-256 from apksigner output"
    fi
    if [[ ! -s "${SIGNER_FILE}" ]]; then
        echo "release-signer.sha256 is missing. The owner must create it with this content:" >&2
        echo "${fingerprint}" >&2
        die "release-signer.sha256 not found in the repository"
    fi
    expected="$(normalise_fingerprint < "${SIGNER_FILE}")"
    if [[ "${fingerprint}" != "${expected}" ]]; then
        die "signer SHA-256 ${fingerprint} does not match release-signer.sha256 (${expected})"
    fi
    log "signer SHA-256 ${fingerprint} matches release-signer.sha256"

    # --- Gate 3: versionCode must increase over the previous release -----------------------
    local prev_code
    prev_code="$(previous_version_code)"
    if [[ -n "${prev_code}" ]]; then
        if [[ ! "${prev_code}" =~ ^[0-9]+$ ]]; then
            die "previous build-info.json has no numeric versionCode: '${prev_code}'"
        fi
        if (( version_code <= prev_code )); then
            die "versionCode ${version_code} is not greater than the previous release (${prev_code})"
        fi
        log "versionCode ${version_code} > previous ${prev_code}"
    fi

    local tag="v${version_name}"
    if gh release view "${tag}" >/dev/null 2>&1; then
        die "release ${tag} already exists"
    fi

    # --- Outputs ---------------------------------------------------------------------------
    local base="${APP_NAME}-${version_name}"
    local commit="${GITHUB_SHA:-$(git rev-parse HEAD)}"
    local repo="${GITHUB_REPOSITORY:-gx-bangsong/GKUICalendar}"
    local source_url="https://github.com/${repo}/tree/${commit}"

    rm -rf "${DIST_DIR}"
    mkdir -p "${DIST_DIR}"
    cp "${APK_IN}" "${DIST_DIR}/${base}.apk"
    (cd "${DIST_DIR}" && sha256sum "${base}.apk" > "${base}.apk.sha256")
    local apk_sha
    apk_sha="$(cut -d' ' -f1 "${DIST_DIR}/${base}.apk.sha256")"

    jq -n \
        --arg applicationId "${app_id}" \
        --argjson versionCode "${version_code}" \
        --arg versionName "${version_name}" \
        --arg signer_sha256 "${fingerprint}" \
        --arg apk_sha256 "${apk_sha}" \
        --arg apk "${base}.apk" \
        --arg commit "${commit}" \
        --arg source_url "${source_url}" \
        '{applicationId: $applicationId, versionCode: $versionCode, versionName: $versionName,
          signer_sha256: $signer_sha256, apk_sha256: $apk_sha256, apk: $apk,
          commit: $commit, source_url: $source_url}' \
        > "${DIST_DIR}/build-info.json"

    local changelog
    changelog="$(changelog_body)"
    mkdir -p "$(dirname "${NOTES_FILE}")"
    cat > "${NOTES_FILE}" <<NOTES
# ${APP_NAME} ${version_name}

${changelog}

---

## 安装说明

- 本安装包使用**正式发布密钥**签名。
- 它与 debug 包（\`${app_id}.debug\`）的签名不同，**无法覆盖安装**。请先卸载 debug 包，再安装本包。
- 卸载会清除应用数据，请先导出需要保留的事件。

## Install notes

- This package is signed with the **release key**.
- Its signature differs from the debug builds (\`${app_id}.debug\`), so it **cannot update them in place**. Uninstall the debug build first, then install this one.
- Uninstalling removes the app's data. Export anything you want to keep beforehand.

---

<details>
<summary>构建信息 / Build info</summary>

| 项目 / Field | 值 / Value |
|---|---|
| applicationId | \`${app_id}\` |
| versionCode | ${version_code} |
| versionName | ${version_name} |
| 签名证书 / Signer SHA-256 | \`${fingerprint}\` |
| APK SHA-256 | \`${apk_sha}\` |
| 源码 / Source code | ${source_url} |

本版本的完整源代码见上方链接（GPL）。
The complete source code for this build is at the link above (GPL).

</details>
NOTES

    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        printf 'tag=%s\n' "${tag}" >> "${GITHUB_OUTPUT}"
    fi
    log "wrote ${DIST_DIR}/${base}.apk, tag ${tag}"
}

main "$@"

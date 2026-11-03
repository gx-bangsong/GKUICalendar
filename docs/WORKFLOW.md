# Release workflow

> **State (2026-11-03).** `.github/workflows/release.yml` is already in the repository (Variant A,
> commit `21af15e`), and `release-signer.sha256` is filled in. The two failures of the first run
> are fixed on this branch; see [What the first release run got wrong](#what-the-first-release-run-got-wrong).
> Automation still cannot write files under `.github/`, so everything below the workflow itself
> lives in `scripts/ci-build.sh`, `app/build.gradle.kts` and this document.

Automation cannot write files under `.github/workflows/`. The owner pastes one of the two
variants below into the repository. Both do the same thing; pick one.

- **Variant A** is a single file, `.github/workflows/release.yml`. Easiest to read.
- **Variant B** is a reusable workflow plus a 10-line caller. Use it if other workflows should
  call the same release job.

Both run `scripts/ci-build.sh`. All logic (gates, file names, `build-info.json`) lives in the
script, so the workflow itself stays short.

## Owner checklist (manual)

1. **Create the keystore once** (keep the file and its passwords; losing them means no upgrade
   path for users):
   ```sh
   keytool -genkeypair -v -keystore release.jks -alias gkui-release \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 release.jks > release.jks.b64     # value for the KEYSTORE_BASE64 secret
   ```
2. **Add four repository secrets** (Settings → Secrets and variables → Actions):
   `KEYSTORE_BASE64` (contents of `release.jks.b64`), `KEYSTORE_PASS`, `KEY_ALIAS`
   (`gkui-release` in the command above), `KEY_PASS`.
3. **Paste one variant** into `.github/workflows/release.yml` (A), or the two files (B).
   *Done: Variant A is in the repository.*
4. **Run the workflow once** (push to `lineage-23.0`, or Actions → release → Run workflow).
   With no `release-signer.sha256` the run stops at the signer gate and prints the signer SHA-256.
5. **Create `release-signer.sha256`** at the repository root with that 64-character hex value,
   commit it, and run again. After this the gate blocks any other signing key.
   *Done: the file is committed. It is never rewritten by the build — if a run reports a mismatch,
   the keystore in the secrets is not the pinned one, and the owner has to decide which is right.*
6. **Remove the stale `sed` lines** from `.github/workflows/build/action.yml`. They rewrite
   `package="..."` in `AndroidManifest.xml`, which the manifest no longer has, so they do nothing.
   The file is not changed by this change because automation cannot edit `.github/`.

## What the first release run got wrong

Run `41580870244` on `lineage-23.0` (`21af15e`) failed two ways.

**1. `lintVitalRelease` reported 46 errors and the build still said `BUILD SUCCESSFUL`.**
45 of them were `ExtraTranslation` for `cal_export_event_sdcard_title`: the SD-card export was
replaced by the SAF export, the default string was deleted, and the 45 translations were left
behind. No Java or Kotlin code references the name. They are removed here rather than given a new
default, because the feature is gone. The 46th was `MissingDefaultResource` for
`agenda_card_text_max_width`, declared only in `values-sw600dp`; `values/dimens.xml` now carries
the base value. `abortOnError` was `false`, which is why lint could not fail the build; it is now
`true` in CI (see [Lint gate](#lint-gate)).

**2. `assembleRelease` succeeded, then the signer gate said `expected exactly one signer, found 0`.**
The APK was **not** unsigned. The run shows `validateSigningRelease` executing, the Gradle guard
not firing (it refuses a CI release build when a signing input is missing), the output named
`app-release.apk` (AGP names an unsigned release `app-release-unsigned.apk`), and `apksigner verify`
exiting 0 — an unsigned APK makes it exit 1 with `DOES NOT VERIFY`, which the script reports as
"apksigner verify failed". The failure was in how the gate **read** apksigner's output:
it matched only `^Signer #N certificate SHA-256 digest:`. When an APK also verifies with the v3.1
scheme, apksigner (build-tools 33+) prints the same certificate as
`Signer (minSdkVersion=…, maxSdkVersion=…) certificate SHA-256 digest: …`, once per scheme, so the
old pattern counted zero. The gate now matches any `Signer…` certificate line, counts **distinct**
SHA-256 digests, and still requires exactly one. The workflow installs `build-tools;36.0.0`.

The old script never logged apksigner's output, so the exact label of that run cannot be read back
from the log. The mechanism is reproduced: feeding the old script a verified APK whose certificate
lines use the v3.1 labels produces `expected exactly one signer, found 0`; the new script accepts
the same input. The gate now prints the whole `apksigner verify --verbose --print-certs` output to
the job log, so the next run shows the real labels either way.

## Signing chain

| Step | Where | Behaviour |
|---|---|---|
| Secrets → env | workflow `env:` | `KEYSTORE_BASE64`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`, never as arguments |
| Presence check | `ci-build.sh` `require_signing_env` | before Gradle; names every missing variable and refuses |
| Decode | `ci-build.sh` `decode_keystore` | private temp file, `umask 077`, removed by the EXIT trap, exports `KEYSTORE_FILE` |
| Keystore check | `ci-build.sh` `check_keystore` | before Gradle; `keytool -list` with `-storepass:env` proves the store opens and the alias exists |
| Second line of defence | `app/build.gradle.kts` task graph | a CI release build with a missing input throws before packaging and lists what is missing |
| Local builds | `app/build.gradle.kts` | debug key fallback stays, with a `WARNING … Do not distribute it.` |
| Certificate gate | `ci-build.sh` Gate 2 | one distinct signer, compared with `release-signer.sha256` |

`ci-build.sh` writes only to `dist/`, `build/release-notes.md` and `$GITHUB_OUTPUT`. It never
writes a tracked file, and it refuses to continue if the build changed one.

## Lint gate

`.github/workflows/build.yml` runs `assembleDebug` on every push and pull request, and the release
workflow only runs on `lineage-23.0`. Release-only lint errors would therefore first appear
*after* a merge. Because `.github/` cannot be edited here, `app/build.gradle.kts` adds the hook:
when `CI` is set, `assembleDebug` also depends on `lintVitalRelease`, and `lint.abortOnError` is
`true`. A release-only lint error now fails the branch build. `lintVitalRelease` analyzes sources
and resources; it does not package or sign, so it needs no keystore. Locally nothing changes:
`abortOnError` stays `false` and `assembleDebug` does not run release lint.

## Variant A: `.github/workflows/release.yml`

```yaml
# Builds the release APK and publishes it as a GitHub Release.
name: release

on:
  push:
    # Publish from the branch that carries the release code. Docs-only and README-only pushes do
    # not publish: a documentation change must not create a new release.
    branches: [ "lineage-23.0" ]
    paths-ignore:
      - "docs/**"
      - "**.md"
  # Lets the owner publish by hand from the Actions tab, for example after a fix to the script.
  workflow_dispatch:

# The only permission needed is writing the release and its tag. Everything else stays read-only.
permissions:
  contents: write

# Two publishes must not run at once: they would race on the same tag. A running publish is
# never cancelled, because stopping it half way could leave a tag without assets.
concurrency:
  group: release
  cancel-in-progress: false

jobs:
  release:
    runs-on: ubuntu-latest
    # A release build with the Gradle cache should finish well within this. The limit stops a
    # hung job from holding the concurrency group.
    timeout-minutes: 60
    steps:
      # Full history, not the default depth 1: versionCode is the commit count, and a shallow
      # clone would give a smaller number. A shallow clone is rejected by the Gradle script.
      - name: Check out repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # The Gradle build compiles with a JDK 21 toolchain (kotlin jvmToolchain(21)).
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: gradle

      # aapt2 and apksigner come from build-tools. The gate in ci-build.sh needs them to check
      # the package name, debuggable flag and signing certificate. Installing a pinned version
      # keeps the checks the same from run to run.
      - name: Install build-tools (aapt2, apksigner)
        run: |
          # Accept the SDK licences if they are not accepted yet. Failing here is not fatal:
          # the install below reports any real problem.
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
          "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "build-tools;36.0.0"

      # All release logic is in the script: decode the keystore, build, gate, write dist/. The
      # four secrets are passed as environment variables, never as command-line arguments, so
      # they do not show in process lists. GH_TOKEN lets the script read the previous release.
      - name: Build, check and prepare release files
        id: build
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASS: ${{ secrets.KEYSTORE_PASS }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASS: ${{ secrets.KEY_PASS }}
          GH_TOKEN: ${{ github.token }}
        run: |
          # Checkouts can lose the executable bit; set it again before running.
          chmod +x gradlew scripts/ci-build.sh
          scripts/ci-build.sh

      # Creates the tag at the built commit and uploads dist/: the APK, its .sha256 and
      # build-info.json (GKUI-Pack reads the fields from build-info.json). The body comes from
      # build/release-notes.md, which carries the package name, versionCode, signer fingerprint,
      # the source link (GPL), and the note that this key differs from the debug builds.
      - name: Create GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          TAG: ${{ steps.build.outputs.tag }}
        run: |
          gh release create "$TAG" dist/* \
            --target "$GITHUB_SHA" \
            --title "$TAG" \
            --notes-file build/release-notes.md
```

## Variant B: reusable workflow and a 10-line caller

Two files. First `.github/workflows/release-reusable.yml`:

```yaml
# Reusable release job. Called by .github/workflows/release.yml; never runs on its own.
name: release-reusable

on:
  workflow_call:

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      # Full history: versionCode is the commit count (see Variant A).
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      # JDK 21 is required by the Gradle toolchain.
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: gradle
      # Installs the aapt2 and apksigner used by the gate.
      - name: Install build-tools (aapt2, apksigner)
        run: |
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
          "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "build-tools;36.0.0"
      # Secrets arrive from the caller with `secrets: inherit`. They are passed as environment
      # variables only, never as arguments.
      - name: Build, check and prepare release files
        id: build
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASS: ${{ secrets.KEYSTORE_PASS }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASS: ${{ secrets.KEY_PASS }}
          GH_TOKEN: ${{ github.token }}
        run: |
          chmod +x gradlew scripts/ci-build.sh
          scripts/ci-build.sh
      # Publishes the tag and the files in dist/. See Variant A for why each file is uploaded.
      - name: Create GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          TAG: ${{ steps.build.outputs.tag }}
        run: |
          gh release create "$TAG" dist/* \
            --target "$GITHUB_SHA" \
            --title "$TAG" \
            --notes-file build/release-notes.md
```

Then `.github/workflows/release.yml` (the caller, about 10 lines):

```yaml
# Caller: publishes a release from lineage-23.0 through the reusable release job.
name: release
on:
  push: { branches: [ "lineage-23.0" ], paths-ignore: [ "docs/**", "**.md" ] }
  workflow_dispatch:
permissions: { contents: write }
jobs:
  release:
    uses: ./.github/workflows/release-reusable.yml
    secrets: inherit
```

## Why the design looks like this

- **Only `lineage-23.0` publishes.** It is the PR base. Feature branches build with the debug
  workflow and do not create releases.
- **`paths-ignore`** keeps documentation edits from creating releases.
- **`contents: write` only**: the default token is otherwise read-only, and this is the one
  permission a release needs.
- **The script never commits.** A push from the build would start this workflow again.
  `ci-build.sh` checks that the build left no tracked file changed.
- **`--target $GITHUB_SHA`** makes the tag point at the exact commit that was built.
- **Release notes** name the source commit so the GPL source obligation can be met from the
  release page.

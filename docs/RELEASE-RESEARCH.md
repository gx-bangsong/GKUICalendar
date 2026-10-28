# Release variant: research and decisions

Branch: `arena/01a05b5a-gkuicalendar`, PR base `lineage-23.0`.
Scope: automated release builds, signing from environment variables, and the applicationId change to
the LineageOS built-in package.

## Facts (verified)

### Package name
- The LineageOS built-in package is `org.lineageos.etar`. Evidence:
  - Upstream `LineageOS/android_packages_apps_Etar`, branch `lineage-23.0`:
    `app/src/main/AndroidManifest.xml` has `package="org.lineageos.etar"`.
    Branch `lineage-24.0` has the same.
  - This tree: `app/org.lineageos.etar.allowlist.xml`, `initial-package-stopped-states-org.lineageos.etar.xml`
    and `preinstalled-packages-org.lineageos.etar.xml`, and the `required:` list in `app/Android.bp`.
  - This tree: `app/src_aosp/main/java/ws/xsoh/etar/BuildConfig.java` has `APPLICATION_ID = "org.lineageos.etar"`.
- Before this change, the Gradle `applicationId` was `ws.xsoh.etar`, which differs from the built-in package.
- `namespace` stays `ws.xsoh.etar`. R and BuildConfig keep their packages. `--custom-package ws.xsoh.etar` in
  `Android.bp` only affects the R class, and upstream uses the same flag.

### Build and signing before this change
- `build.gradle.kts` read signing from `~/.gradle/gradle.properties`, not from environment variables.
  Without them the release was unsigned.
- `versionCode = 51` and `versionName = "1.0.51"` were hard-coded.
- The debug variant had the suffix `.debug` only. There is no `.dev` suffix.
- Release had no `applicationIdSuffix` and no `versionNameSuffix`.

### Release 1.0 on GitHub
- Tag `1.0`, asset `GKUICalendar-1.0.apk`, no `build-info.json`.
- The owner confirmed on two devices, through the GKUI-Pack diagnostic, that `ws.xsoh.etar.debug` is installed
  under `/system/app/GKUICalendar/` and comes from this repository's Release 1.0. The APK was not downloaded.

### Hard-coded package strings (before this change)
| Location | Value | Handling |
|---|---|---|
| `app/build.gradle.kts` `applicationId` | `ws.xsoh.etar` | changed to `org.lineageos.etar` |
| `res/xml-v25/shortcuts.xml` `targetPackage` | `ws.xsoh.etar` | now `@string/shortcut_target_package`, set per build type |
| `search/CalendarAppSearchIndexer.kt` `DATABASE_NAME`, `EVENTS_NAMESPACE` | `ws.xsoh.etar.events` | changed to `org.lineageos.etar.events` |
| `icalendar/VCalendar.java` `PRODUCT_IDENTIFIER` | `-//Calendar//ws.xsoh.etar` | unchanged (ICS identity) |
| `icalendar/VEvent.java` UID suffix | `@ws.xsoh.etar` | unchanged (ICS identity) |
| `AndroidManifest.xml` `android:authorities` | `${applicationId}.provider`, `.androidx-startup`, `.CalendarRecentSuggestionsProvider` | already follow `applicationId`, no change |
| `build.gradle.kts` `search_authority` resValue | `applicationId` based | already follows `applicationId`, no change |
| `README*.md` store badges | `ws.xsoh.etar` | one-line note added |
| `.github/workflows/build/action.yml` `sed` | rewrites `package="..."` in the manifest | no-op, the manifest has no `package`; not changed (automation cannot edit `.github/`) |
| `AndroidManifest.xml` `android:host="com.android.calendar"` | deep-link host | not an applicationId; unchanged |
| `import ws.xsoh.etar.R` and `package ws.xsoh.etar` | namespace | unchanged |

### Permissions and hidden APIs
- No `sharedUserId`. No `signature` or `privileged` permission is requested. `BIND_REMOTEVIEWS` is on a
  component, so it is a requirement on callers.
- No `com.android.internal` or `android.internal` reference. The only `Class.forName` loads an extension class
  named in configuration (`ExtensionsFactory`).

### Sandbox
- No JDK and no Android SDK. Gradle distributions, Google Maven and Maven Central are unreachable. A Gradle build
  cannot run here, so every Gradle change is verified only by the GitHub Actions run.

## Decisions (confirmed by the owner)
1. applicationId is `org.lineageos.etar`, a separate commit. Namespace stays `ws.xsoh.etar`.
   Debug becomes `org.lineageos.etar.debug`.
2. PR base is `lineage-23.0`. No `main` branch is created. The session branch was merged with
   `origin/lineage-23.0` (a merge, not a rebase, so no history is rewritten). The merge brings in `handleref.webp`,
   which already exists on `lineage-23.0`.
3. Release signing comes only from `KEYSTORE_FILE`, `KEYSTORE_PASS`, `KEY_ALIAS` and `KEY_PASS`. CI refuses a
   release build without them. Locally the debug key is used, with a warning.
4. `scripts/ci-build.sh` is the only place with logic. It never writes to tracked files.
5. The owner does the manual steps: four secrets, keystore, pasting the workflow, and writing
   `release-signer.sha256` after the first run.

## Verification done in the sandbox
- `shellcheck` 0.11.0 on `scripts/ci-build.sh`: clean.
- `ci-build.sh` run against stub `gradlew`, `aapt2`, `apksigner` and `gh`:
  - happy path: APK, `.sha256`, `build-info.json`, release notes and the tag output are written, and the
    keystore temp file is removed;
  - failures refused: wrong or `.debug` applicationId, debuggable APK, signer mismatch, missing
    `release-signer.sha256`, invalid base64, empty `KEY_PASS`, Gradle failure, a build that changed a tracked
    file, a non-increasing versionCode, a previous release with non-numeric versionCode;
  - previous release without `build-info.json` is skipped with a message.
- `PyYAML` parses every YAML block in `docs/WORKFLOW.md` (3 blocks).

## Not verified
- Gradle builds and `assembleRelease`: cannot run in the sandbox. The Gradle change is checked only by the CI
  `assembleDebug` run, which exercises the applicationId and string changes but not the release signing path.
- The release workflow itself: not present in the repository until the owner pastes it, so it has not run.
- `sdkmanager` licence handling and the `build-tools;36.0.0` package on the runner.
- `aapt2 dump badging` and `xmltree` output format on a real APK (the stubs follow the documented format).
- `resValue` string reference in `xml-v25/shortcuts.xml` on device (shortcut launch).
- AppSearch index: switching the namespace starts an empty index that needs a reindex.
- AOSP tree: the fork's manifest has no `package` attribute, so the AOSP build's package name is not defined by
  this repository. Not changed here.

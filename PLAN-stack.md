# PLAN — Moderate stack update (stay on AGP 8)

Read `CLAUDE.md` first. Goal: newer build tools and libraries with **no behaviour change**.
Do the steps in order. Do not change app behaviour or relax tests. If a step can't be done as
written, stop and report `PLAN PROBLEM` with the exact error.

PowerShell: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`. Never cut Gradle output with
`Select-Object -First` (use `-Last`). If "Could not close incremental caches":
`.\gradlew.bat :app:compileDebugKotlin --rerun-tasks`.

## S1. SDK platform 36 (the user approved this download)
`& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36"`
(accept the licence if asked via piping `y`). Install nothing else.

## S2. Gradle 8.14.7 and AGP 8.13.2
- `gradle/wrapper/gradle-wrapper.properties`: `distributionUrl` → `gradle-8.14.7-bin.zip`
  (edit the line; don't regenerate other wrapper files).
- Root `build.gradle.kts`: AGP `8.13.2`; Kotlin plugins (`org.jetbrains.kotlin.android`,
  `org.jetbrains.kotlin.plugin.compose`) `2.4.21`.
- Keep `android.enableJetifier=true` (the Onyx SDK needs it).

## S3. `app/build.gradle.kts`
- `compileSdk = 36`. **Keep `targetSdk = 35`** (with 36, Android 16 ignores portrait-only on
  large screens). Add a comment saying so next to `targetSdk`.
- Replace `kotlinOptions { jvmTarget = "17" }` with the current DSL, outside `android {}`:
  `kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }`.
- Keep the `androidComponents` APK-naming block working (output names must stay
  `PuzzleNook-<version>-<abi>-debug.apk` / `-unsigned.apk`). Update its comment to AGP 8.13.2.
- `versionCode = 26`, `versionName = "0.12.1"`.
- Dependencies: raise to the newest stable versions that build with compileSdk 36 and AGP 8.13.2:
  - `compose-bom`: start at `2026.09.00`
  - `core-ktx`: start at `1.19.1`
  - `activity-compose`: start at `1.13.0`
  - `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`: start at `2.11.0`
  If `checkDebugAarMetadata` fails (a library needs compileSdk 37 or a newer AGP), step that
  library back release by release (versions from `https://dl.google.com/android/maven2/<group path>/<artifact>/maven-metadata.xml`)
  until it passes. Do not raise compileSdk above 36 and do not change AGP.
  **Do not change** `digital-ink-recognition`, the Onyx SDKs, `hiddenapibypass`, `junit`,
  `org.json`.

## S4. Compile fixes
`.\gradlew.bat :app:assembleDebug`. Fix compile errors from API changes with the smallest
equivalent change (e.g. Material3 `menuAnchor(MenuAnchorType…)` → the new anchor type if the old
one is an error). Also fix new **deprecation warnings in our code** when the replacement is a
one-line equivalent; list any you leave. No visual or behaviour changes.

## S5. Checks (all must pass)
1. Full `.\gradlew.bat :app:testDebugUnitTest` in the background (about 8 min): 112 tests,
   0 failures.
2. `.\gradlew.bat :app:assembleDebug` and `.\gradlew.bat :app:assembleRelease` build (R8 must not
   strip anything the app needs: release build must succeed without new keep-rule warnings that
   fail the build). Output names as in S3.
3. Emulator `emulator-5554` (start it if not running; adb at
   `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`): install
   `app\build\outputs\apk\debug\PuzzleNook-0.12.1-x86_64-debug.apk` (`adb install -r`, keeps data).
   Open: Home, one digit game (Sudoku Easy), Nonogram, Takuzu, Slitherlink, Hidato, Settings
   (Help section), Statistics (both dropdowns open and close). Tap Hint once in Sudoku. Screenshots
   to `build\stack\`. Check `adb logcat -d *:E` for crashes from
   `com.puzzlenook`. Install the **release** x86_64 APK too? No — release is unsigned; only check
   it builds.
4. Report: final version of every changed dependency/plugin, any library stepped back and why,
   deprecations left, test count, screenshots taken, any logcat errors.

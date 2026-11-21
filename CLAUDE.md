# Puzzle Nook

Android app (Kotlin, Jetpack Compose), 31 logic puzzles for phone (S22 Ultra, S Pen) and colour
e-ink tablet (Boox Note Air 5C). Package `com.puzzlenook`, single `app` module. Public on GitHub
(`OBELIKS/PuzzleNook`, GPL-3.0, `README.md`); repo-local git identity uses the GitHub noreply email.

## Commands (PowerShell)
- First: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`
- Debug: `.\gradlew.bat :app:assembleDebug` → emulator APK `PuzzleNook-<version>-x86_64-debug.apk`
- Release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, then
  `.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease` → `PuzzleNook-<version>-<abi>-unsigned.apk`
  (arm64, x86_64). Sign arm64 with build-tools 35.0.0 `apksigner`, `~/.android/debug.keystore`
  (pass `android`, alias `androiddebugkey`) as `PuzzleNook-<version>-arm64.apk`; send it in chat.
- GitHub: commit + `git push`; release = `gh release create v<version> <signed arm64 APK> --notes ...`.
- Tests: `.\gradlew.bat :app:testDebugUnitTest` (~50 s; `--tests "pkg.Class"`, `--rerun` forces).
  UI-only changes: build + emulator check. Logic/generator changes: affected classes while
  working, full suite once at the end. Gradle skips unchanged tests: no `--rerun` to check executor work.
- Stop check (`.claude/check.json`) runs `compileDebugKotlin` at every stop; unit tests stay manual.
- Emulator `emulator-5554`; adb `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. `input tap`
  uses 1080×2340; screenshots are scaled by 1,17.
- Tablet check: `adb shell wm size 1860x2480` + `wm density 320`, then `wm size reset` / `wm density reset`.

## Code map (`app/src/main/java/com/puzzlenook/`; per-game rules, encodings, levels: KDoc atop each file)
- `MainActivity.kt` — screens (HOME, MENU, GAME, SETTINGS, STATS), one view model per game; `attachBaseContext` applies language, then `UiZoom`
- `sudoku/` — digit games, Fillomino: `SudokuGenerator.kt` (`Variant` enum of all games), `GameState`, `SudokuViewModel`,
  `DigitLogic`, `Latin*.kt`, `KillerSolver`, `Regions`/`JigsawGenerator`/`Suguru`, `Fillomino.kt`, `GameStore.kt` (saves, stats)
- `nonogram/` — Nonogram; its `Mark` enum is shared by other games
- `shade/` — Star Battle, Hitori, Nurikabe, Takuzu, Mosaic, Light Up, Tents, Kurodoko, Norinori, Battleships, Aquarium,
  Heyawake, LITS: logic + generator per game, `ShadeState` (also `toJson`/`fromJson`), `ShadeViewModel`
- `slither/` — Slitherlink, Masyu, Yajilin, Galaxies; `Loop.kt` shared loop reasoning; `ui/slither/EdgeGestures.kt` edge modifier
- `region/` Shikaku, `bridges/` Bridges, `hidato/` Hidato, `ink/` handwriting (ML Kit), `settings/` (`AppSettings`, `HelpRules.kt`)
- `ui/` — screens, `GameCommon`, `GameLayout`, `HowToPlay`, `UiText.kt`, `PauseUi.kt`; boards in `ui/<package>`; `ui/ink`, `ui/theme`
- Tests in `app/src/test/java/com/puzzlenook/` mirror the packages

## Conventions
- Text only in `res/values/strings.xml` + `values-sl` (Slovene, tu-form, reuse existing terms);
  counts via `<plurals>`; non-composable code passes `UiText`/`@StringRes`.
- Every puzzle must be solvable by step-by-step logic: generate against the game's logic solver, test all difficulties with it, plus brute-force
  uniqueness on small grids. Easier levels may use simpler logic (then generate, test and hint with that level's logic) and keep a minimum
  of clues; harder levels should need their top technique.
- Levels (new games): 0 basic, 1 next technique, 2 = bounded what-if (`TRIAL_SWEEPS` sweeps of level 0), 3 = what-if over level 1 + branch
  intersection; Hard must need 2, Expert should need 3 (report-only where it can't: Shikaku, Norinori). Add a `logicIsSound` test (random partial solutions).
- Hints point at the cell or edge the solver settles soonest (`round` in its result); if the level settles nothing (old saves), use the next level up.
- E-ink has no red: wrong lines are dashed (`ui/slither/WrongDash.kt`), wrong cells get a white mark.
- Scoring: a mistake or hint step = ½ point; each point adds `penaltyPercent`% of playing time. Help limits go through `HelpRules` in every
  state's `hint()`; a game's `help` is fixed at its start.
- "Errors off": boards use `shownWrong(i)`, never `isWrong(i)`; auto helpers must not depend on a cell being right, or they leak the answer.
- Never rename a `Variant` store name (saved games and stats use it).
- E-ink theme (`LocalEink`): flat greys, no translucent tints, no animation.
- Every view model has `pause()`/`resume()`; game screens show `PausedPane` and call `PauseOnStop`.
- Game screens use `GameLayout`: rule line, status, board, number pad, then buttons. Top bar title: `GameTitle` (`GameCommon`: one line, shrinks, "…").
- Portrait only. Tablets: `UiZoom` enlarges the whole UI; wide screens use two home columns and `readableWidth()`.
- Adding a game: `Variant`, generator + logic + tests, view model, board, screen, `MainActivity` (view model, `saved`, stats, detail, newGame, GAME),
  `HowToPlay`.

## Gotchas
- "Could not close incremental caches": run `compileDebugKotlin --rerun-tasks`.
- `shade_saves.txt` (`ShadeSaveFixtureTest`) was recorded before `counts`: do not regenerate.
- `slither_fixture.txt` (`SlitherRegressionTest`): re-record only with `SLITHER_FIXTURE_WRITE=1`.
- Generators have a wall-clock budget; slower devices get a few more clues.
- Emulator has no stylus: test handwriting on real devices.
- Stack: AGP 8.13.2, Gradle 8.14.7, Kotlin 2.4.21, compileSdk 36. Keep `targetSdk = 35` (36 makes
  Android 16 ignore portrait on large screens). Compose BOM > 2026.06.01, core-ktx 1.19+ and
  lifecycle 2.11+ need AGP 9.1 + compileSdk 37. R8 "parsing kotlin metadata" warnings are harmless.

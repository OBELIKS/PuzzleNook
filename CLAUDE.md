# Puzzle Nook

Android app (Kotlin, Jetpack Compose), 31 logic puzzles for phone (S22 Ultra, S Pen) and colour
e-ink tablet (Boox Note Air 5C). Package `com.puzzlenook`, single `app` module. Not a git repo.

## Commands (PowerShell)
- First: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`
- Debug: `.\gradlew.bat :app:assembleDebug` → emulator APK `PuzzleNook-<version>-x86_64-debug.apk`
- Release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, then
  `.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease` → `PuzzleNook-<version>-<abi>-unsigned.apk`
  (arm64, x86_64). Sign arm64 with build-tools 35.0.0 `apksigner`, `~/.android/debug.keystore`
  (pass `android`, alias `androiddebugkey`) as `PuzzleNook-<version>-arm64.apk`; send it in chat.
- Tests: `.\gradlew.bat :app:testDebugUnitTest` (~50 s; `--tests "pkg.Class"`, `--rerun` forces).
  UI-only changes: build + emulator check (no unit tests cover layout). Logic/generator changes:
  affected classes while working, full suite once at the end. Gradle skips unchanged tests, so
  don't add `--rerun` to check executor work.
- Stop check (`.claude/check.json`) runs `compileDebugKotlin` at every stop; unit tests stay manual.
- Emulator `emulator-5554`; adb `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. `input tap`
  uses 1080×2340; screenshots are scaled by 1,17.
- Tablet check: `adb shell wm size 1860x2480` + `wm density 320`, then `wm size reset` / `wm density reset`.

## Code map (`app/src/main/java/com/puzzlenook/`)
- `MainActivity.kt` — screens (HOME, MENU, GAME, SETTINGS, STATS), one view model per game;
  `attachBaseContext` applies the language, then `UiZoom`
- `sudoku/` — digit games (Sudoku, Killer, Kakuro, Futoshiki, Calcudoku, Skyscrapers, Kropki, Jigsaw, Suguru)
  - `SudokuGenerator.kt` — `Variant` enum (all games), `Difficulty`, `Puzzle`
  - `GameState.kt`, `SudokuViewModel.kt` — shared state, hints, handwriting input
  - `DigitLogic.kt` (1–9), `Latin*.kt` (n×n; Kropki dots in `LatinRules.white/black/kropkiAll`, `KropkiGenerator`), Killer/Kakuro generators, `KillerSolver.kt` (`GridSolver`)
  - `Regions.kt`, `JigsawGenerator.kt`, `Suguru.kt` — irregular regions (`Variant.hasRegions`)
  - `Fillomino.kt` — Fillomino (home section "Regions"): `FillominoLogic` (digit candidates, groups of touching
    equal digits; levels 0-3, `TRIAL_SWEEPS` = 2), `FillominoGenerator` (random partition, merge equal touching
    areas, thin givens); no units/peers/regions in `GameState`; hints via `fillominoLogic` (falls back to higher
    levels); `SudokuBoard` draws thick lines between touching cells with different shown digits (player data only)
  - `GameStore.kt` — saved game, stats, win history per variant (SharedPreferences)
- `nonogram/` — Nonogram; its `Mark` enum is shared by other games
- `shade/` — Star Battle, Hitori, Nurikabe, Takuzu, Mosaic, Light Up (`LightUp.kt`), Tents (`Tents.kt`),
  Kurodoko (`Kurodoko.kt`), Norinori (`Norinori.kt`), Battleships (`Battleships.kt`), Aquarium (`Aquarium.kt`): logic + generator per game, `ShadeState` (also owns `toJson`/`fromJson`),
  `ShadeViewModel`. Light Up: `numbers` -1 white, `WALL` 9 plain wall, 0..4 numbered
  wall; walls are locked and `ShadeState.isBlocked` (strokes skip them); bulb = FILLED, no bulb = CROSSED.
  Tents: `numbers` `TREE` = 1 (blocked), tent = FILLED; outside counts = `ShadePuzzle.counts`/`ShadeState.counts`
  (rows, then columns, -1 hidden; optional JSON `"counts"`): `ShadeBoard` adds a one-cell margin top/left
  (board stays square), taps there do nothing, a count fades when marks meet it (`countDone`). A tree may
  touch several tents, so only matching-sound rules. Kurodoko: `numbers` > 0 clue (blocked white cell), shaded
  = FILLED; clue fades via `kurodokoMet`. Norinori: `regions` (thick borders, region ids), shaded = FILLED,
  `NorinoriGenerator` builds dominoes then regions around them, `givens` = revealed cells (`minGivens` floor),
  `ShadeState.norinoriClash` (red from own marks, errors on only). Regression of saved shade games: `ShadeSaveFixtureTest` +
  `src/test/resources/shade_saves.txt` (recorded before `counts`; do not regenerate).
  Heyawake (`Heyawake.kt`): rectangular `regions` (rooms), `numbers` = shaded count on a room's first cell (-1 none),
  `HeyawakeLogic` windows rule (three rooms in a line need a shade) + `anchorCuts` (Shade.kt) connectivity.
  LITS (`Lits.kt`): `regions` only, `Tetromino` (shape ids, placements), `LitsLogic` = alive placements per region
  (level 1 = arc consistency + connectivity; its level 2 trial uses level-1 steps), `ShadeState.litsClash`.
  Battleships: `numbers` = cells shown from the start (-1 none, 0 water, 1 single, 2 top, 3 bottom, 4 left, 5 right,
  6 middle; locked, not blocked), counts as Tents, fleet `BattleshipsGenerator.fleet(n)`; ship = FILLED drawn as capsules
  joined to own FILLED neighbours, a correct ship dots its diagonals, `ShadeState.fleetStatus` (status row ticks ships
  closed by own water marks), `shipClash`. Aquarium: `regions` = tanks (each tank row one run), water = FILLED, state per
  tank = possible water levels (bit mask), counts as Tents, `aquariumClash`. Both: level 2 = bounded what-if
  (`TRIAL_SWEEPS` = 2), level 3 = branch intersection; independent brute force in `ShadeRules` (Aquarium: levels per tank)
- `slither/` — Slitherlink (edge grid) and Masyu (loop through cell centres: `MasyuGrid` = `SlitherGrid(n - 1)`, `MasyuLogic` levels 0-3, `MasyuGenerator` hill-climbs the blob loop until the full pearl set solves, then removes pearls; `MasyuState`/`MasyuViewModel`). `Loop.kt` = shared loop reasoning (`closeLoops`, `connectable`, `isSingleLoop`); `ui/slither/EdgeGestures.kt` = shared tap/drag edge modifier. `YajilinState`/`YajilinViewModel` + `Yajilin.kt` (Yajilin: shading + loop through cell centres on `MasyuGrid`; elements = edges then cells, one `YajilinLogic` over both, clue `dir*100+count`, `YajilinGenerator` hill-climbs the blob loop then turns clue cells into shaded ones; Shade mode `ui/slither/CellGestures.kt`, Line mode `EdgeGestures`; board `YajilinBoard`). Slitherlink regression fixture: `SlitherRegressionTest` + `test/resources/slither_fixture.txt` (re-record only with `SLITHER_FIXTURE_WRITE=1`); Galaxies (home section "Regions"; `GalaxyGrid` = dots in half-grid coordinates `y*(2n+1)+x` on `SlitherGrid(n)` walls, `GalaxiesLogic` = per-cell candidate dots: 0 mirror/forced, 1 reachability, 2 bounded what-if (`TRIAL_SWEEPS`), 3 branch intersection; `GalaxiesGenerator` splits the board into symmetric galaxies, peels cells off until the level solves, then merges neighbouring galaxies while it still does; `GalaxiesState` tints finished areas from player walls only; board `ui/slither/GalaxiesBoard.kt` on `edgeGestures`; tests `GalaxiesTest`, `GalaxiesStateTest`); `hidato/` — Hidato (path logic, two-digit entry)
- `region/` — Shikaku (home section "Regions"): `Rect` (`code` = one int), `ShikakuLogic` (candidate rectangles per
  clue, levels 0-3), `ShikakuGenerator` (random partition, then moves clues until the level's logic solves),
  `ShikakuState` (player `rects`, hint-revealed `fixed`, `hintClue`), `ShikakuViewModel`; board in `ui/region`
  (drag = rectangle with preview, tap = remove, tap a lone 1 = single cell)
- `bridges/` — Bridges / Hashi (home section "Lines"): `BridgeLayout` (islands, slots between facing islands,
  crossings), `BridgesLogic` (slot counts 0..2: capacity; 1 adds crossing + isolation; 2/3 what-if),
  `BridgesGenerator` (grows islands without crossings, retries until the level's logic solves; Hard/Expert need
  their top level), `BridgesState` (`bridges` per slot, hint-revealed `fixed`; wrong = more bridges than the
  solution, so 0→1→2 never costs extra), `BridgesViewModel`; board in `ui/bridges` (drag island→island or tap bridge cycles)
- `ink/` — handwriting (ML Kit, template recogniser, two-digit split)
- `settings/` — `AppSettings`; `HelpRules.kt` (hint limit, rising cost, cooldown)
- `ui/` — screens, `GameCommon` (`HintButton`, `RuleLine`), `GameLayout`, `HowToPlay`, `UiText.kt`,
  `PauseUi.kt`; boards in `ui/<package>`; `ui/ink` (Onyx pen), `ui/theme` (e-ink theme, `UiZoom`)
- Tests in `app/src/test/java/com/puzzlenook/` mirror the packages

## Conventions
- Text only in `res/values/strings.xml` + `values-sl` (Slovene, tu-form, reuse existing terms);
  counts via `<plurals>`; non-composable code passes `UiText`/`@StringRes`.
- Every puzzle must be solvable by step-by-step logic: generate against the game's logic solver,
  test all difficulties with it, plus brute-force uniqueness on small grids. Easier levels may use
  simpler logic (then generate, test and hint with that level's logic) and keep a minimum of clues;
  harder levels should need their top technique.
- Levels (new games): 0 basic, 1 next technique, 2 = bounded what-if (`TRIAL_SWEEPS` sweeps of level 0),
  3 = what-if over level 1 + branch intersection; Hard must need 2, Expert should need 3 (report-only
  where it can't: Shikaku, Norinori). Add a `logicIsSound` test (random partial solutions).
- Hints point at the cell or edge the solver settles soonest (`round` in its result); if the level
  settles nothing (old saves), use the next level up.
- E-ink has no red: wrong lines are dashed (`ui/slither/WrongDash.kt`), wrong cells get a white mark.
- Scoring: a mistake or hint step = ½ point; each point adds `penaltyPercent`% of playing time.
  Help limits go through `HelpRules` in every state's `hint()`; a game's `help` is fixed at its start.
- "Errors off": boards use `shownWrong(i)`, never `isWrong(i)`; auto helpers must not depend on a
  cell being right, or they leak the answer.
- Never rename a `Variant` store name (saved games and stats use it).
- E-ink theme (`LocalEink`): flat greys, no translucent tints, no animation.
- Every view model has `pause()`/`resume()`; game screens show `PausedPane` and call `PauseOnStop`.
- Game screens use `GameLayout`: rule line, status, board, number pad, then buttons. The top bar
  title is `GameTitle` (`GameCommon`: one line, shrinks, then "…").
- Portrait only. Tablets: `UiZoom` enlarges the whole UI; wide screens use two home columns
  and `readableWidth()`.
- Adding a game: `Variant`, generator + logic + tests, view model, board, screen, `MainActivity`
  (view model, `saved`, stats, detail, newGame, GAME), `HowToPlay`.

## Gotchas
- "Could not close incremental caches": run `compileDebugKotlin --rerun-tasks`.
- Generators have a wall-clock budget; slower devices get a few more clues.
- Emulator has no stylus: test handwriting on real devices.
- Stack: AGP 8.13.2, Gradle 8.14.7, Kotlin 2.4.21, compileSdk 36. Keep `targetSdk = 35` (36 makes
  Android 16 ignore portrait on large screens). Compose BOM > 2026.06.01, core-ktx 1.19+ and
  lifecycle 2.11+ need AGP 9.1 + compileSdk 37. R8 "parsing kotlin metadata" warnings are harmless.

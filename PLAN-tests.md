# PLAN — Faster unit tests (same coverage)

Read `CLAUDE.md` first. Goal: the full `:app:testDebugUnitTest` run goes from about 7½ min to
**under 90 s** (warm Gradle daemon, sources already compiled), with **the same checks**: same
puzzles, same seeds, same assertions. Don't change app code, don't weaken or drop a test. If a
target can't be met as written, stop and report `PLAN PROBLEM` with the timings.

PowerShell: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`. Never cut Gradle output with
`Select-Object -First` (use `-Last`). Per-test times: `time` attributes in
`app\build\test-results\testDebugUnitTest\*.xml`.

Today (serial, one JVM, total 475 s): `LatinBruteForceTest.futoshikiBig` 298 s; `ShadeTest` 75 s
(starBattle 24, nurikabe 20, nurikabeSmallUnique 18, mosaic 11); SuguruTest 19; DigitLogicTest 18
(kakuro 17); StarBattleUniqueTest 17; HidatoTest 16; KakuroTest 15; everything else < 6 s per class.
Machine: 16 hardware threads.

## T0. Baseline
Run the full suite once before changing anything; note wall time and the per-class times.

## T1. `futoshikiBig` under 20 s
`app/src/test/java/com/puzzlenook/sudoku/LatinBruteForceTest.kt`, class `Naive`: a deliberately
independent brute-force solver (shares no code with `LatinSolver`/`LatinLogic` — keep it so).
It fills cells in reading order and checks each rule only when its last cell is filled, so Hard
Futoshiki explores a huge tree. Make it faster while staying simple and independent, e.g.:
- keep per-row/per-column "used digit" bitmasks instead of scanning;
- choose the next empty cell with the fewest allowed digits (rows/columns/givens), not reading order;
- check a `<` rule as soon as both of its cells are filled (index the rules per cell, not per last
  cell in reading order); check a cage once all its cells are filled (Calcudoku stays correct).
**Round 2 (planner, after round 1 gave 298 → 280 s):** the `<` rules must narrow the allowed
digits *before* a cell is chosen, otherwise the fewest-options choice and the "0 options → back
up" check ignore them. In `allowed(i)` also apply:
- static chain bounds, computed once in `init`: `low[i]` = 1 + length of the longest chain of
  `<` rules ending at `i` (cells that must be smaller), `high[i]` = n − length of the longest
  chain starting at `i`; mask out digits outside `low[i]..high[i]`;
- filled neighbours: for each rule `a < b`, if `i == a` and `b` is filled with `w`, only digits
  `< w`; if `i == b` and `a` is filled with `w`, only digits `> w`.
Keep the post-placement checks as a safety net. Still no code shared with `LatinSolver`/`LatinLogic`.

The test methods, puzzles (`LatinGenerator.generate` with the same `Random` seeds), difficulties,
seed counts and the assertion (exactly 1 solution, stop at 2) stay unchanged. All 5 tests in the
class must still pass. Target: `futoshikiBig` < 20 s, the whole class < 30 s.
Sanity check (keep it as a new test): the naive solver finds ≥ 2 solutions for a Futoshiki puzzle
with one given removed if that makes it non-unique — or simpler: for an empty 4×4 Futoshiki
(no givens, no rules) it reports 2 (stops at 2). It must not be able to "pass" by finding nothing.

## T2. Split `ShadeTest` by game
`app/src/test/java/com/puzzlenook/shade/ShadeTest.kt` holds 6 tests (75 s). Test classes are the
unit that parallel forks share out, so split it into one class per game (e.g. `StarBattleGenTest`,
`NurikabeGenTest` with both nurikabe tests, `MosaicGenTest`, `TakuzuGenTest`, `HitoriGenTest`;
check existing names in `shade/` first to avoid clashes). Move the methods and their helpers as they
are; shared helpers go to an `internal` object in the same package. No assertion changes. Delete
`ShadeTest.kt` after the move. Test count stays 115 (+1 from T1's sanity test = 116).

## T3. Parallel test JVMs and build cache
- `app/build.gradle.kts`: in `android { testOptions { unitTests.all { … } } }` (or
  `tasks.withType<Test>().configureEach { … }`) set
  `maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)` (8 on this
  16-thread machine: leaves headroom so the generators' wall-clock budgets aren't starved) and a
  comment saying why half.
- `gradle.properties`: add `org.gradle.caching=true`. Keep the existing lines.
- **Flakiness check (required):** the generators have wall-clock budgets and some tests assert that
  harder levels need their top technique; CPU contention could change generated puzzles. Run the
  full suite **3 times in a row** with `--rerun-tasks` scoped to tests only:
  `.\gradlew.bat :app:testDebugUnitTest --rerun` (Gradle 8.14 supports `--rerun` for one task).
  All 3 must be green. If any run fails, set forks to 6, then 4, and repeat; report which value
  was stable and the failures seen.

## T4. Timing report
Final full run (`--rerun`, warm daemon): wall time of the Gradle command and of the test task,
per-class times of the 8 slowest classes, test count. Target: command < 90 s, 116 tests, 0 failures.

## T5. Docs
Project `CLAUDE.md`, Commands, the Tests line: replace "full run takes about 7 min" with the new
measured time and "(parallel JVMs; `--rerun` forces a run)". Don't edit other lines.

## Report
Files changed; T0 vs T4 timings (table); the forks value used; the 3 flakiness runs' results;
anything not done.

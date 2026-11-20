# PLAN — Code-review fixes (help limits, statistics, formatting)

Read `CLAUDE.md` first. Paths are under `app/src/main/java/com/puzzlenook/` unless they start
with `app/` or `app/src/test`. Do the steps in order, then run the checks at the end.
Do not change the design or relax a test; if something can't be done as written, stop and
report `PLAN PROBLEM` with the details (file, error, numbers).

The five state classes: `sudoku/GameState`, `shade/ShadeState`, `nonogram/Nonogram.kt`
(`NonogramState`), `slither/SlitherState`, `hidato/HidatoState`. The five view models:
`SudokuViewModel`, `ShadeViewModel`, `NonogramViewModel`, `SlitherViewModel`, `HidatoViewModel`.

## R1. One half-point formatter with ½ (fixes "0.5" in dialogs and statistics)
- `sudoku/GameState.kt` `formatPoints(halfPoints)`: return `"0"` for 0, `"½"` for 1,
  `"${h/2}"` for even, `"${h/2}½"` for odd ≥ 3 (e.g. 5 → "2½"). Update its KDoc.
- `ui/GameCommon.kt`: delete `formatHalf`; `hintButtonLabel` uses `formatPoints`.
- Update `SudokuTest` line asserting `"2.5"` → `"2½"`. Add a test (new or in SudokuTest):
  0→"0", 1→"½", 2→"1", 3→"1½", 5→"2½".
- `ui/StatsScreen.kt` `Overview` "Mistakes / game": format with a decimal comma
  (`String.format(java.util.Locale.forLanguageTag("sl"), "%.1f", …)`), so 1.5 shows "1,5".

## R2. Small cleanups in `ui/GameCommon.kt`
- The KDoc `/** Label of the hint button … */` currently sits above `formatHalf`; after R1 make
  sure it sits directly above `hintButtonLabel`, and no function has two KDocs.
- Cooldown label: use `"Hint ${formatTime(cooldownLeft)}"` (`formatTime` from
  `sudoku/GameStore.kt`) instead of the hand-made m:ss.

## R3. Shared hint bookkeeping in `settings/HelpRules.kt`
Add (pure, no Android):
```kotlin
/** Counters after framing a new hint, and the half points it costs. */
data class HintFrame(val hintsUsed: Int, val lastHintAt: Long, val costHalfPoints: Int)

/** Frames a new hint, or null when the limit or the wait blocks it. */
fun frame(hintsUsed: Int, elapsed: Long, lastHintAt: Long, s: HelpSettings): HintFrame?
/** Half points for revealing the framed hint (the hintsUsed-th hint). */
fun revealCost(hintsUsed: Int, s: HelpSettings): Int = stepCost(hintsUsed, s.risingCost)
```
`frame` returns null if `!canStartHint(...)`, else `HintFrame(hintsUsed + 1, elapsed,
stepCost(hintsUsed + 1, s.risingCost))`.
In all five `hint()` functions replace the copied logic with:
`val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this`
(placed where `canStartHint` is checked today, before `pickHintCell`), then
`hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt, hintHalfPoints = hintHalfPoints + f.costHalfPoints`;
reveal branches use `HelpRules.revealCost(hintsUsed, help)`. Import `com.puzzlenook.settings.HelpRules`
instead of writing it fully qualified. Behaviour must not change (existing tests stay green).
`HelpRulesTest`: tests for `frame` (allowed → counters/cost; blocked by limit → null; blocked by
cooldown → null) and `revealCost` (rising on/off).

## R4. Help settings are fixed when a game starts
Today every view model has `settings.help.collect { h -> _state.value = _state.value?.copy(help = h) }`,
so switching to Unlimited mid-game lifts the limit for that game. Change:
- Remove that collector from all five view models (keep the `penaltyPercent` collector).
- `newGame` keeps setting `help = settings.help.value` (already done).
- Save the game's help in every saved-game JSON: keys `"hintLimit"`, `"risingCost"`,
  `"hintCooldown"` (digit games: `sudoku/GameStore.save/load`; others: each view model's
  `persist()`/`load()`). On load, a missing key → the current setting's value
  (`settings.help.value` field by field), so pre-0.12 saves follow the current settings once.
  `GameStore.load` has no settings access: give it a parameter
  `defaultHelp: HelpSettings = HelpSettings()` and pass `settings.help.value` from
  `SudokuViewModel`.
- `ui/SettingsScreen.kt` Help section: add one line under the section (same small text style
  as other explanations there): "Changes apply from the next new game."
- `ui/HowToPlay.kt` "Hints and time": append to the limits line that changes apply to the next
  game.

## R5. Old saves: estimate `hintsUsed`
In all five loaders, when `"hintsUsed"` is missing use `(hintHalfPoints + 1) / 2` (a full hint
was 2 half points before 0.12) instead of 0. Put this in `HelpRules` as
`fun hintsUsedFromOldSave(hintHalfPoints: Int): Int = (hintHalfPoints + 1) / 2` and call it
(`json.optInt("hintsUsed", HelpRules.hintsUsedFromOldSave(hintHalfPoints))`, reading
hintHalfPoints first). Test it in `HelpRulesTest` (0→0, 1→1, 2→1, 3→2, 6→3).

## R6. Old win records without hint data are not clean
`sudoku/GameStore.kt` `WinRecord`: add `val hintsRecorded: Boolean = true` and a property
`val clean: Boolean get() = hintsRecorded && hintHalfPoints == 0`. `fromJson` sets
`hintsRecorded = o.has("hints")`. `toJson` unchanged (always writes "hints").
`ui/StatsScreen.kt`: `Stats.summarize` uses `it.clean` for clean times; the Recent wins marker
uses `w.clean` for "· clean". Test in `StatsTest`: a JSON record without `"hints"` → `clean`
false and not counted in `summarize(...).clean`; with `"hints": 0` → counted.

## R7. Clean / unaided counts survive the 500-win history trim
- `GameStore.recordWin`: also increment `clean_<D>` (when `hintHalfPoints == 0`) and
  `unaided_<D>` (when `unaided`) Int prefs. `resetStats()` removes them too.
- `DifficultyStats` gets `clean: Int = 0, unaided: Int = 0` read from those keys in `stats(d)`.
- `Stats.summarize` gets parameters `cleanWins: Int = 0, unaidedWins: Int = 0`; the counts become
  `maxOf(cleanWins, cleanTimes.size)` and `maxOf(unaidedWins, unaidedTimes.size)` (bests still
  come from the history). Pass them from `StatsScreen`.
- `StatsTest`: counter larger than history → counter wins; history larger (old wins, counter
  0) → history count wins.

## R8. APK naming: document the internal API
`app/build.gradle.kts`, `androidComponents` block: add a comment line that
`VariantOutputImpl` is internal AGP API (checked with AGP 8.7.3, root `build.gradle.kts`); if an
AGP upgrade breaks it, rename the APKs in a task after assemble instead. No code change.

## Checks
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`
- Full `.\gradlew.bat :app:testDebugUnitTest` (about 8 min; run in background and wait; never cut
  its output with `Select-Object -First`). Target: all green, test count ≥ 105 + the new tests.
- `.\gradlew.bat :app:assembleDebug` builds.
- Grep: no `formatHalf` left; no `settings.help.collect` left; no fully qualified
  `com.puzzlenook.settings.HelpRules.` left in the five state files.
- No emulator run needed. Report: files changed, test count, any deviation.

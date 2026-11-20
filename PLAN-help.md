# PLAN — Limit help (hint limit, rising cost, cooldown, clean solves)

(Parts H1–H9 below are DONE. Part U at the end is the current task.)

Read `CLAUDE.md` first. Paths are under `app/src/main/java/com/puzzlenook/` unless they start
with `app/src/test`. Do the steps in order; run the checks at the end.

Today a hint has two steps: first press frames a cell (`hintCell`/`hintEdge` set, +1 half
point), second press on the same hint reveals it (+1 half point). Buttons read "Hint ½" /
"Reveal ½". Five state classes have their own `hint()`: `sudoku/GameState`, `shade/ShadeState`,
`nonogram/Nonogram.kt` (`NonogramState`), `slither/SlitherState`, `hidato/HidatoState`. Their
view models already push `settings.penaltyPercent` into the state; do the same for the new
settings.

Definitions
- A "hint" = a first step (framing). Its reveal belongs to the same hint.
- `hintsUsed` = number of first steps taken in this game.
- Hint limit L (-1 = unlimited): a new hint is allowed only while `hintsUsed < L`. Reveal of
  the already framed hint is always allowed.
- Rising cost (on/off): the k-th hint (k = hintsUsed after its first step, 1-based) costs k
  half points for its first step and k for its reveal (½+½, 1+1, 1½+1½ …). Off: 1 each, as now.
- Cooldown C seconds (0 = off): a new hint is allowed only when
  `elapsedSeconds - lastHintAt >= C` (playing time, so pausing doesn't count down;
  `lastHintAt` = elapsedSeconds at the last first step, -1 = none). Reveal is not blocked.
- Clean solve: a win with `hintHalfPoints == 0` (mistakes allowed).

H1. `settings/HelpRules.kt` (new, pure Kotlin, no Android):
```kotlin
data class HelpSettings(val hintLimit: Int = -1, val risingCost: Boolean = false, val cooldownSeconds: Int = 0)
object HelpRules {
    fun stepCost(hintsUsedAfterFirstStep: Int, rising: Boolean): Int  // half points
    fun cooldownLeft(elapsed: Long, lastHintAt: Long, cooldown: Int): Long  // 0 when free
    fun canStartHint(hintsUsed: Int, elapsed: Long, lastHintAt: Long, s: HelpSettings): Boolean
    fun hintsLeft(hintsUsed: Int, s: HelpSettings): Int?  // null = unlimited
}
```
H2. `settings/AppSettings.kt`: `hintLimit` (Int, default -1, choices `HINT_LIMIT_CHOICES =
listOf(-1, 5, 3, 1, 0)`), `risingHintCost` (Boolean, default false), `hintCooldown` (Int
seconds, default 0, choices `COOLDOWN_CHOICES = listOf(0, 30, 60, 120)`), each a StateFlow +
setter + prefs key, like `penaltyPercent`. Plus `val help: StateFlow<HelpSettings>` combining
them (or let view models combine).

H3. Each of the five states: add `hintsUsed: Int = 0`, `lastHintAt: Long = -1`,
`help: HelpSettings = HelpSettings()`. `hint()`:
- if a hint is framed → reveal as today but add `HelpRules.stepCost(hintsUsed, help.risingCost)`
  instead of 1;
- else if `!HelpRules.canStartHint(...)` → return `this` unchanged;
- else frame as today, `hintsUsed + 1`, `lastHintAt = elapsedSeconds`, cost
  `stepCost(hintsUsed + 1, rising)`.
- Keep every other branch of today's `hint()` (e.g. the immediate-fill branch in
  `GameState.hint`) and charge it the same way (it counts as a first step).
Save/load `hintsUsed` and `lastHintAt` in every saved-game JSON (`GameStore.save/load` for
digit games; the view models' own JSON for shade/nonogram/slither/hidato); missing keys
→ 0 / -1 so old saves load.

H4. View models (5): push `help` into the state like `penaltyPercent` (on new game, on load,
and when the settings flow changes).

H5. Hint button in the five game screens (`SudokuGameScreen`, `ShadeGameScreen`,
`NonogramGameScreen`, `SlitherGameScreen`, `HidatoGameScreen`). Move the label logic into one
shared function in `ui/GameCommon.kt`, e.g. `hintButtonLabel(revealing, cost, hintsLeft,
cooldownLeft): String` and `hintEnabled(...)`:
- revealing → "Reveal <cost>"; otherwise "Hint <cost>" where cost is `formatPoints(stepCost)`
  for the next step (½, 1, 1½ …).
- no hints left (and not revealing) → "No hints", button disabled.
- cooldown running (and not revealing) → "Hint m:ss" counting down, disabled. It must update
  each second while playing (the screens already tick `elapsedSeconds`).
- hints limited → append the count left, e.g. "Hint ½ · 2 left" (keep it short; if it wraps
  badly, put the count on a second line in smaller text).
E-ink: disabled = normal disabled look, no tints, no animation.

H6. `ui/SettingsScreen` (find it via `SettingsScreen(` in `MainActivity`): new "Help" section
near the time-penalty setting: "Hints per game" (Unlimited / 5 / 3 / 1 / None), "Rising hint
cost" switch with the line "Each further hint in a game costs more: ½, 1, 1½ …", "Wait between
hints" (Off / 30 s / 1 min / 2 min). Same controls style as the existing settings.

H7. Statistics (`ui/StatsScreen.kt`, `Stats.summarize` and the per-difficulty rows): add
"Clean" (count of clean wins) and "Best clean" time (with the selected penalty %, clean wins
have no hint points but may have mistakes). In the win history list, mark clean wins (e.g.
"· clean"). `WinRecord` already has `hintHalfPoints`; no storage change.

H8. `ui/HowToPlay.kt` "Hints and time": one line each for the limit, rising cost, wait, and
clean solves (mention they're in Settings).

H9. Tests:
- `app/src/test/java/com/puzzlenook/settings/HelpRulesTest.kt`: stepCost, cooldownLeft,
  canStartHint, hintsLeft (unlimited, limit reached, cooldown, reveal not affected).
- In existing state tests (one per engine where a test class exists, at least Sudoku
  `GameState` and `ShadeState`): limit 1 → second new hint does nothing; reveal still works;
  rising → hintHalfPoints after two full hints = 1+1+2+2 = 6; cooldown blocks a new hint until
  elapsed advances.
- `Stats.summarize` clean count / best clean if it is testable without Android (it's an
  object in StatsScreen.kt; test it if it has no Compose dependency).

Checks: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`; the full
`.\gradlew.bat :app:testDebugUnitTest` (about 7–8 min, run in background and wait) green;
`.\gradlew.bat :app:assembleDebug` builds. Emulator: in Settings set Hints 1, Rising on,
Wait 30 s; start Takuzu Easy: Hint → "Reveal ½" → reveal → button "No hints" disabled. Set
Hints Unlimited: next hint shows "Hint m:ss" countdown while playing, then "Hint 1". Screenshots
to `build\help\`. Set all three back to defaults afterwards.

---

## Part U — Unaided solves (no hints AND "Errors off" the whole game)

Definitions
- `errorsSeen` (per game): true once the player could have used red error marks:
  - toggling "Errors" ON during the game sets it;
  - any player move (placing/clearing a mark, digit, note, edge…) while `showWrong` is true sets it.
  So a game that starts with errors on but is switched off before the first move can still be
  unaided. Undo does not clear it. Hints/reveals don't set it (hints are counted separately).
- Unaided win: `hintHalfPoints == 0 && !errorsSeen`. (Unaided ⊂ clean.)

U1. Five states (`GameState`, `ShadeState`, `NonogramState`, `SlitherState`, `HidatoState`): add
`errorsSeen: Boolean = false`; set it as defined above (in the shared `withShowWrong()` path
when turning on, and in each move function while `showWrong`). Save/load it in every
saved-game JSON; a missing key in an old save → `true` (unknown, so not unaided).
U2. `WinRecord` (`sudoku/GameStore.kt`): add `unaided: Boolean`, JSON key `"unaided"`, missing
→ false (old wins). `recordWin` takes it; all five view models pass
`s.hintHalfPoints == 0 && !s.errorsSeen`.
U3. Statistics (`ui/StatsScreen.kt`): `DifficultySummary` gets `unaided` count and
`bestUnaided`; show "Unaided" and "Best unaided" next to "Clean" / "Best clean". Check phone
width (1080 px): if the table gets too wide or wraps badly, put Clean/Unaided as a second
line under each difficulty row instead. History marker: "· unaided" for unaided wins, else
"· clean" for clean wins.
U4. `ui/HowToPlay.kt` "Hints and time": one line: "Unaided: solved with no hints and with
Errors off for the whole game — shown separately in Statistics."
U5. Tests: in the existing state tests (at least `SudokuTest` and `ShadeStateTest`, plus one of
Nonogram/Slither/Hidato): move with errors on → errorsSeen; errors on at start but toggled off
before the first move → still false after moves; toggling on mid-game → true; undo keeps it.
`StatsTest`: unaided count and best unaided; `WinRecord` JSON round trip without `"unaided"`
→ false.

Checks U: full `.\gradlew.bat :app:testDebugUnitTest` (background, ~8 min) green;
`:app:assembleDebug` builds. Emulator: open Statistics (phone width) and screenshot the table
to `build\unaided\`; if any game can be won quickly (e.g. Takuzu or
Suguru Easy via repeated Hint/Reveal is NOT unaided — instead just check the table layout and
the history markers with existing data).

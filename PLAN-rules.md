# PLAN — Home scroll memory, a rule line in every game, Hidato "Still to place" wrapping

Read `CLAUDE.md` first. Paths are under `app/src/main/java/com/puzzlenook/` unless they start
with `app/src/test`. Do the steps in order, then the checks. Don't change other behaviour or
relax tests; if something can't be done as written, stop and report `PLAN PROBLEM` with details.

## P1. Home screen keeps its scroll position
`ui/HomeScreen.kt` uses `rememberScrollState()` inside `HomeScreen`, which leaves composition when
`MainActivity` switches `screen`, so coming back from a game starts at the top.
- `HomeScreen` gets a parameter `scroll: ScrollState` and uses it instead of `rememberScrollState()`.
- In `MainActivity`'s screen composable, create `val homeScroll = rememberScrollState()` **before**
  the `when (screen)` (so it stays in composition) and pass it to `HomeScreen`.
  (`rememberScrollState` is saveable, so it also survives the activity being recreated.)

## P2. One rule line in every game
Today only Star Battle, Takuzu and Mosaic show a one-line rule under the board
(`ruleText` + `RuleLine` in `ui/shade/ShadeGameScreen.kt`). Every game gets one.
- Move the text to a shared pure function in `ui/HowToPlay.kt`, next to `gameSummary`:
  `fun ruleText(variant: Variant, size: Int, stars: Int = 1): String` (non-null for every
  `Variant`; `when` without `else`, so a new game can't be forgotten). Texts (n = `size`):
  - CLASSIC: "Every row, column and 3×3 box holds 1 to 9 once"
  - KILLER: "Every row, column and 3×3 box holds 1 to 9 once · a cage's digits add up to its sum, none repeated"
  - JIGSAW: "Every row, column and coloured region holds 1 to 9 once"
  - SUGURU: "A region of n cells holds 1 to n · equal digits never touch, not even diagonally" (literal "n", it varies per region)
  - KAKURO: "Every run adds up to its clue · no digit twice in a run"
  - FUTOSHIKI: "Every row and column holds 1 to $n once · < and > point to the smaller number"
  - CALCUDOKU: "Every row and column holds 1 to $n once · a cage's digits give its target with its + − × ÷"
  - SKYSCRAPERS: "Every row and column holds heights 1 to $n once · a clue counts the buildings seen from its side"
  - NONOGRAM: "Clues are the runs of filled cells in each row and column, in order, with gaps between"
  - STAR_BATTLE, TAKUZU, MOSAIC: today's texts unchanged (Star Battle uses `stars`)
  - HITORI: "Shade until no number repeats in a row or column · shaded cells never touch · white cells stay connected"
  - NURIKABE: "Each number is an island of that many cells · one connected sea, no 2×2 pools"
  - SLITHERLINK: "Draw one closed loop along the dots · each number counts its sides on the loop"
  - HIDATO: "A path from 1 to the end · each number touches the next, sideways or diagonally"
  Before writing them, check each against that game's `rules(...)` section in `HowToPlay.kt` and
  its logic (e.g. Hitori "never touch" = sideways only? Hidato diagonal moves allowed? Kakuro digits
  1–9?). If a text would be wrong for this app, correct the fact and say so in the report.
- Move `RuleLine` to `ui/GameCommon.kt` as `@Composable fun RuleLine(text: String, scale: Float)`
  (same style as today: bodyMedium scaled, onSurfaceVariant, centred, full width). Delete the
  private copies from `ShadeGameScreen.kt`.
- Show it in every game screen, placed and sized like `ShadeGameScreen.Layout` does today:
  portrait → directly under the board, the board's height budget reduced by the rule space
  (`52.dp * scale`, as in Shade); landscape → in the side column under `StatusRow`.
  Screens: `ui/shade/ShadeGameScreen.kt` (now for all 5 shade games), `ui/sudoku/SudokuGameScreen.kt`
  (all 8 digit games; its portrait board size is computed from `statusHeight`, `controlsHeight`,
  `gap` — subtract the rule space there), `ui/nonogram/NonogramGameScreen.kt`,
  `ui/slither/SlitherGameScreen.kt`, `ui/hidato/HidatoGameScreen.kt`.
  The board must never overlap the controls; the rule text may take 2 lines on a phone.
- `HowToPlay` dialogs and menus keep `gameSummary` unchanged.

## P3. Hidato "Still to place" shows every number, on several lines
`ui/hidato/HidatoGameScreen.kt` `Controls`: today it shows only the first 12 numbers on one line.
- New pure function (in `hidato/` or `ui/hidato/`, not a composable):
  `fun formatMissing(missing: List<Int>): String` — sorted, consecutive runs of 3 or more as
  ranges with an en dash, others comma-separated: `[1,2,3,4,7,9,10,12,13,14]` → `"1–4, 7, 9, 10, 12–14"`.
- Text: `"Still to place: " + formatMissing(missing)`, no `take(12)`, `maxLines = 3`,
  `overflow = TextOverflow.Ellipsis`, so it wraps onto more lines.
- Portrait board budget: reserve room for the extra lines (two more lines of bodySmall, about
  `32.dp * scale`) in `boardSize` so a 3-line list never pushes the controls off screen.

## P4. Tests (pure JVM, `app/src/test/...`)
- `formatMissing`: empty → ""; `[5]` → "5"; `[1,2]` → "1, 2"; `[1,2,3]` → "1–3";
  the example above; unsorted input gets sorted.
- `ruleText`: non-blank for every `Variant.entries`, with size 9 and 5; Futoshiki with size 5
  contains "1 to 5"; Star Battle with stars 2 contains "2 stars".
  (Put it where `HowToPlay.kt`'s package tests live, e.g. `app/src/test/java/com/puzzlenook/ui/`.)

## Checks
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`; full `.\gradlew.bat :app:testDebugUnitTest`
  in the background (about 8 min; never cut output with `Select-Object -First`): all green,
  112 + new tests. `.\gradlew.bat :app:assembleDebug` builds.
- Emulator `emulator-5554`, `adb install -r` the x86_64 debug APK (keeps data). Screenshots to
  `build\rules\`:
  - Phone size: Killer (longest digit text), Skyscrapers, Hitori, Nonogram, Slitherlink, Hidato
    (start an Expert or Hard game so "Still to place" has many numbers). Board, rule line,
    controls and number pad all fully visible and not overlapping.
  - Tablet size: `adb shell wm size 1860x2480` + `adb shell wm density 320`; Killer and Hidato;
    then `adb shell wm size reset` and `adb shell wm density reset` (must be reset).
  - Home: scroll to the bottom (Hidato), open it, go back: the list is still scrolled down.
    Screenshot before and after.
- Report: files changed, any rule text you corrected and why, test count, screenshots, anything
  that didn't fit.

# PLAN — One shared game layout; unified "not filled" labels and icons

Read `CLAUDE.md` first. Paths are under `app/src/main/java/com/puzzlenook/` unless they start with
`app/src/test`. Do the steps in order, then the checks. Don't change game logic, scoring or saved
data; don't relax tests. If something can't be done as written, or a target number is missed,
stop and report `PLAN PROBLEM` with the numbers.

Game screens: `ui/sudoku/SudokuGameScreen.kt` (`GameLayout`, 8 digit games), `ui/hidato/HidatoGameScreen.kt`,
`ui/shade/ShadeGameScreen.kt` (Star Battle, Hitori, Nurikabe, Takuzu, Mosaic),
`ui/slither/SlitherGameScreen.kt`, `ui/nonogram/NonogramGameScreen.kt` (each has a private `Layout`).

## U0. Measure "before" (tablet)
Before changing code: install the current debug APK on `emulator-5554`, set
`adb shell wm size 1860x2480` + `adb shell wm density 320`, and note the board width in px
(uiautomator bounds or screenshot) for Killer, Hidato, Hitori, Nonogram, Slitherlink. Then
`wm size reset` / `wm density reset`. Also note the same on the phone size. These are the "before"
numbers for the checks.

## U1. Shared layout `GameLayout` (new file `ui/GameLayout.kt`)
One composable used by all 5 screens, replacing their private layouts:
```kotlin
@Composable
fun GameLayout(
    rule: String,
    status: @Composable (scale: Float) -> Unit,
    controls: @Composable (scale: Float) -> Unit,
    boardAspect: Float = 1f,          // width / height (Nonogram uses nonogramAspect(state))
    maxScale: Float = 1.6f,           // Hidato passes 1.2f (portrait), as today
    board: @Composable (Modifier) -> Unit,
)
```
- Outer: `BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp))`.
- **Portrait** (`maxWidth <= maxHeight`): `scale = (maxWidth / 400.dp).coerceIn(1f, maxScale)`,
  `gap = 12.dp * scale`, `contentWidth = minOf(maxWidth, 720.dp)`.
  `Column(fillMaxSize, CenterHorizontally)`:
  1. `RuleLine(rule, scale)` in `Box(width(contentWidth))` — directly under the title bar, no free
     space above it.
  2. gap, then `status(scale)` in `Box(width(contentWidth))`.
  3. gap, then a `Column(Modifier.weight(1f).fillMaxWidth(), CenterHorizontally,
     verticalArrangement = Arrangement.Center)` holding the **block** board + controls, so free
     space is split equally above the board and below the controls (user's choice):
     - board: `BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Center)`
       → `board(Modifier.size(w, w / boardAspect))` with `w = minOf(maxWidth, maxHeight * boardAspect)`
       (the box wraps the board, so nothing sits between board and controls),
     - `Spacer(height = gap)`,
     - `controls(scale)` in `Box(width(contentWidth))`.
  The controls (number pad first, if any) are therefore directly under the board in every game.
- **Landscape** (`maxWidth > maxHeight`, only in split screen / freeform; the app is portrait-only):
  keep today's behaviour: `Row(fillMaxSize, spacedBy(16.dp), CenterVertically)`, board
  `size(maxHeight * boardAspect, maxHeight)`, scale `((maxWidth - boardWidth) / 320.dp).coerceIn(1f, maxScale)`,
  right `Column(weight(1f), spacedBy(gap))` with rule, status, controls.
- Use it in all 5 screens; delete their private `Layout`/`GameLayout` (Sudoku's measured-height
  version with `onSizeChanged` goes away; the Skyscrapers clue-margin `Board` wrapper stays as the
  `board` lambda). Each screen passes `ruleText(...)` as today and its own `StatusRow(...)` (keep the
  `message` arguments as today) and `Controls(...)`.
- Slitherlink: drop its extra `padding(horizontal = 4.dp)` on the status row (now 12.dp like all).

## U2. Controls: same spacing everywhere
- Sudoku and Hidato `Controls`: `Column(spacedBy(12.dp * scale))`; number-pad keys
  `spacedBy(6.dp * scale)` in both (Sudoku is 4.dp today, Hidato 6.dp). Number pad stays first,
  action row second, Hidato "Still to place" last.
- Hidato "Still to place" must not resize the board while a game is played (its text gets shorter
  as numbers are placed): keep its box at the largest height measured in this game, e.g.
  `var reserved by remember(<puzzle identity>) { mutableStateOf(0.dp) }` +
  `Modifier.heightIn(min = reserved).onSizeChanged { reserved = maxOf(reserved, it.height.toDp()) }`.
  Resets on a new game.

## U3. "Not filled" mode buttons
- Hitori's cross-mode label "Keep white" → **"Mark white"** (same as Mosaic). Grep for "Keep white"
  (e.g. `HowToPlay.kt`) and update matching wording.
- For every game with a mode toggle (Shade `modes(variant)` for the 5 shade games, Slitherlink,
  Nonogram), read how its board draws the cross-mode mark (× / dot / circle / white …) and make the
  button icon the same shape (Material icon closest to the drawn mark, e.g. `Icons.Outlined.Close`
  for ×, a dot icon for a dot). Likewise the fill-mode icon should match the drawn fill (star for
  Star Battle, filled square for shading, line for Slitherlink). Keep the other labels (Star,
  Mark empty, Black/White, Sea/Island, Line/Cross out, Fill/Mark empty).
  Report a table: game → fill label/icon, cross label/icon, what the board draws.

## U4. Docs
- Project `CLAUDE.md`, Conventions: replace the "Portrait game layouts measure status, rule and
  controls…" lines with: all game screens use `GameLayout` (`ui/GameLayout.kt`): rule, status, then
  board + controls as one centred block (number pad first, buttons below); no fixed dp budgets.
  Add `GameLayout.kt` to the code map under `ui/`.

## Checks (all must pass)
1. `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'`; `.\gradlew.bat :app:assembleDebug` builds;
   full `.\gradlew.bat :app:testDebugUnitTest` in the background (about 8 min; never cut Gradle
   output with `Select-Object -First`): 115 tests, 0 failures.
2. Emulator `emulator-5554`, `adb install -r` the x86_64 debug APK (keeps data). Screenshots to
   `build\ui\`, phone size: Killer, Skyscrapers, Hidato (Expert),
   Hitori, Takuzu, Star Battle, Nonogram, Slitherlink. Tablet size (as in U0): Killer, Hidato,
   Hitori, Nonogram, Slitherlink; then **reset** `wm size` and `wm density`.
3. Numbers, from uiautomator bounds, for every screenshot:
   - rule line top ≤ 12 dp below the title bar bottom (phone: ≤ 32 px; tablet: ≤ 24 px plus UiZoom);
   - gap between the board view's bottom and the first control view's top ≤ 60 px (buttons have
     their own padding); Slitherlink ≤ 100 px (its board draws its own white margin);
   - free space above the board and below the controls differ by ≤ 8 px (centred block);
   - board width ≥ the U0 "before" width − 20 px for the same game and size. Exceptions (decided by
     the planner): tablet Killer/Sudoku family — "before" had the pad cut off and the buttons off
     screen, so the target is only that the whole pad and button row are on screen; tablet
     Hidato ≥ 1100 px;
   - nothing overlaps; the bottom control is fully on screen.
   - Hidato: place one number in an Expert game; the board size does not change.
4. Report: files changed, the U0 and after numbers in a table, the U3 icon table, test count,
   screenshots, anything not done.

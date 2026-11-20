# PLAN — 15 new puzzles for Puzzle Nook

Read the project `CLAUDE.md` first (commands, conventions, code map). This file is the full brief:
the executor has no other context. One **step** = one game (plus any shared code it needs first).
Work is handed out in **batches** (one executor run of about 2 hours, see "Batches"). Steps in a
batch run one at a time; each step ends with its gates passing; each batch ends with a report.

## Rules for every step

- Follow "Adding a game" in CLAUDE.md. Exhaustive `when (variant)` blocks that must get a branch:
  `MainActivity` (view model, map `shadeGames`/`digitGames`, `GameEntry`, MENU `difficultyDetail`,
  and the GAME/saved/stats/newGame branches when the game has its own family), `ui/HowToPlay.kt`
  (summary, `ruleText`, `rules_*` array, controls), plus the family's own switches listed below.
  `Variant.isShading` / `isLatin` / `hasRegions` are explicit `==` chains: extend them by hand.
- New `Variant` entries go at the end of the enum, with the store name given here; never rename one.
- Texts: `values/strings.xml` and `values-sl/strings.xml` (Slovene, tu-form, reuse terms already in
  `values-sl`): `title_`, `blurb_`, `summary_`, `rule_` (one line), `rules_` array, controls strings.
- Logic levels follow the Mosaic pattern: `XLogic.forLevel(d, …)` with level = `d.ordinal`.
  Level 0 = the basic local rules, 1 = adds the game's next technique, 2 = what-if (one-cell trial)
  over level-0 propagation, 3 = what-if over level-1. Generate, test and hint with the level's logic.
- Generators: `generate(d, random = Random.Default, budgetMs = 4000, n = size(d))`, wall-clock
  budget with `deadline`/`past` (`shade/Shade.kt`); past the budget the puzzle stays valid with
  more clues. Easy/Medium keep a clue floor (`minClues(d)`, like `MosaicGenerator.minClues`).
  Hard/Expert try to need their top level (`needsTop`, like `SuguruGenerator`).
- Errors off: boards use `shownWrong(i)`. Rule violations that follow from the player's own marks
  (two bulbs seeing each other, a count exceeded) may be shown only while `showWrong` is on; fading
  a satisfied clue is always allowed. Nothing may depend on the hidden solution.
- E-ink (`LocalEink`): flat greys, black ink, no alpha tints, no animation. Check both themes.
- Hints: the cell/edge/rectangle/bridge the level's logic settles soonest (`round`).
- Fix stale doc comments you touch (e.g. `ShadeState`/`ShadeViewModel` still list only 3 games).
- Update CLAUDE.md (code map line for any new package, game count) in the same step.

## Gates (every step; all are commands)

1. `.\gradlew.bat :app:compileDebugKotlin` clean.
2. The game's test class(es) with `--tests`, then the full `:app:testDebugUnitTest` once at the end.
   - Every difficulty × 5 seeds: solution satisfies an independent rule checker; the level's logic
     solves the puzzle from its givens to exactly the stored solution.
   - Independent brute-force uniqueness (shares no code with the logic) = 1 solution on Easy
     (and Medium where the grid is ≤ 7×7), at least 5 seeds.
   - Hard: needs its top level in ≥ 3 of 5 seeds. Easy/Medium: clue count ≥ `minClues`.
   - Generation on the dev PC: median ≤ 1,5 s and max ≤ budget + 1 s per difficulty.
   - Each new test class ≤ 120 s; `@Test(timeout = 300_000)`.
3. `.\gradlew.bat :app:assembleDebug`, install `PuzzleNook-<version>-x86_64-debug.apk` on
   `emulator-5554`, open the new game on every difficulty, play a few moves (tap and drag), hint,
   undo, pause, errors on/off; screenshot phone size and tablet size (`wm size 1860x2480`,
   `wm density 320`, then reset both). No clipped board or controls, rule line visible.
4. Existing tests keep passing unchanged. Shared-code refactors (steps 2 and 4) need an
   identical-result regression first (see those steps).

If a gate cannot be met, stop and report `PLAN PROBLEM` with the numbers (sizes, ms, seed counts).
Do not lower a gate, shrink a test, or change the game's rules to pass.

## Batches (about 2 hours of executor time each; run in this order)

| Batch | Steps | Estimate | Notes |
|---|---|---|---|
| 1 | 1 Light Up, 3 Kropki | 2 h | adds blocked shade cells |
| 2 | 2 Tents, 7 Kurodoko | 2¼ h | adds outside counts (regression first) |
| 3 | 4 Masyu | 2 h | adds loop-through-centres code (regression first) |
| 4 | 5 Shikaku, 8 Norinori | 2¼ h | adds `region/` family and Regions section |
| 5 | 6 Bridges + release | 1¾ h | all best-fit games done → release (0.14.0, done) |
| 5b | Expert tuning: Light Up, Shikaku, Norinori | 1½ h | see "Batch 5b" below |
| 6 | 9 Battleships, 10 Aquarium | 2¼ h | both use outside counts |
| 7 | 11 Heyawake, 12 LITS | 2¼ h | region shading games |
| 8 | 13 Fillomino | 1½ h | digit family |
| 9 | 14 Galaxies | 1½ h | edge walls on the step-4 gesture code |
| 10 | 15 Yajilin + release | 2¼ h | shading + loop → release |

A batch ends with the full unit-test suite once. If a step in a batch hits `PLAN PROBLEM`, finish
nothing else in that batch: report what is done and stop. Release = bump the version,
`.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease`, sign the arm64 APK (CLAUDE.md).

## Batch 5b — Expert tuning (Light Up, Shikaku, Norinori)

Problem (measured, 5 seeds): Expert puzzles needed their top level in Light Up 0/5, Shikaku 0/5,
Norinori 1/5, so Expert is Hard on a bigger grid. Cause: level 3 (what-if over level-1
propagation, contradictions only) is barely stronger than level 2. Tents, Kurodoko, Masyu and
Bridges reach 3–5/5 and stay as they are.

Change, in each of the three logic classes: **level 3 = branch intersection** over level-1
propagation. Take a choice point, propagate every option with the level-1 steps to a fixpoint,
drop options that hit a contradiction, and settle everything that comes out the same in all
surviving options (not only contradictions). Choice points:
- Light Up: an undecided cell (bulb / no bulb) and, stronger, a numbered wall with open
  neighbours (every way to place its missing bulbs).
- Shikaku: each clue with ≥ 2 candidates (every candidate rectangle).
- Norinori: an undecided cell, and each unfinished region (every domino placement still possible).
Levels 0–2 stay exactly as they are. Hints at Expert use the new level 3 (`round` as before).

Order: (1) regression first: record levels 0–2 results (state + round) on 5 stored puzzles per
game and difficulty into a test resource, assert identical after the change; (2) implement;
(3) generators unchanged except that Expert's `needsTop` now means "level 2 fails".

Gates (in addition to the usual ones):
- Expert needs level 3 (level 2 fails) in ≥ 3 of 5 seeds for each of the three games.
- Expert generation median ≤ 1,5 s and max ≤ budget + 1 s; level-3 solve of one Expert puzzle
  ≤ 300 ms median.
- Hard results unchanged (regression fixture), Hard still ≥ 3/5; all existing tests pass unchanged.
- Emulator: open one Expert puzzle per game, take one hint, check it points at a sensible cell.
If a game can't reach 3/5 within the time gates, report `PLAN PROBLEM` with the numbers for it
and still deliver the others.

### Round 2 (after PLAN PROBLEM in round 1)

Round 1 result: branch intersection is in (keep it), but Expert still needs level 3 in 0/5
(0/40 extra seeds). Cause: level 2 (what-if over level-0 propagation **run to a fixpoint**) already
solves nearly every unique puzzle, so clue removal stops at uniqueness, not at the logic. The fix
is a weaker level 2, not a stronger level 3: Hard = short look-ahead, Expert = full look-ahead.

Change, in the same three logic classes:
- **Level 2 = bounded what-if**: the trial runs at most `K` sweeps of the level-0 steps (one sweep =
  one pass of `basic`/`step` over the board), then stops; a contradiction found within those
  sweeps counts, anything later doesn't. `K` is a per-game constant, tuned (try 1, 2, 3, 4).
- **Level 3 = unbounded what-if over level-1 propagation + branch intersection** (round 1's
  level 3, unchanged).
- Levels 0–1 stay identical: the round-1 fixture keeps asserting them; replace only its level-2
  expectations (record them again after the change, and say so in the report).
- Hints for games saved before this change: if the level's logic settles nothing new from the
  player's marks, the hint uses the next level up (then level 3). Add a state test: an old-style
  Hard puzzle that new level 2 can't finish still gets a hint on the solution path.

Tuning gates (per game, 5 seeds, plus a check over 20 extra seeds reported but not asserted):
- Expert: level 2 fails in ≥ 3/5. Hard: level 1 fails in ≥ 3/5 (as before), and new level 2
  solves every Hard puzzle it generates (it's generated with it).
- Hard clue/given count may rise; report before → after. Generation and solve time gates as above.
- Pick the smallest `K` that meets both; if no `K` in 1..4 does, `PLAN PROBLEM` for that game with
  a table of K → Hard x/5, Expert x/5, ms.
Round limit: one pass of K tuning per game.

**Outcome (done):** Light Up K = 1 meets every gate (Expert needs level 3 in 4/5, 20/20 extra).
Shikaku and Norinori: no K in 1..4 makes Expert need level 3 (best 2/5 Norinori, 0/5 Shikaku);
by the user's choice they use K = 2 (Hard 5/5, fast generation) and their Expert check is
report-only: Expert there is a bigger grid at Hard's technique level. Hints fall back to the next
level up when a saved game's level settles nothing (`HintFallback5bTest`).

---

## Steps (game specs; numbers are references, the batch table sets the order)

### Step 1 · Light Up (shade family)
- Variant `LIGHT_UP`, store `"lightup"`, title "Light Up" / "Žarnice", section shading,
  icon `Lightbulb`. Sizes 7/8/10/12.
- Rules: black walls (some numbered 0–4) and white cells. Place bulbs so every white cell is lit
  (a bulb lights its row and column until a wall), no bulb lights another bulb, a numbered wall
  has exactly that many bulbs orthogonally next to it.
- Data: `ShadePuzzle.numbers`: `-1` white cell, `WALL = 9` plain wall, `0..4` numbered wall.
  Walls are fixed (locked like Nurikabe numbered cells in `ShadeState.fromPuzzle`), solution false.
  Bulb = FILLED, "no bulb" = CROSSED (drawn as a dot).
- Shared change: add a per-variant "blocked cell" check in `ShadeState` (walls, later trees and
  clue cells) that strokes skip and win checks ignore; Nurikabe keeps its current behaviour.
- Logic levels: 0 = numbered-wall saturation (full → rest crossed; open = needed → bulbs), lit
  cells can't hold a bulb, an unlit cell with one possible lighter gets that bulb; 1 = adds
  wall-corner pairs (a 3 or a 1 in a corner of two walls / diagonal reasoning) and "a crossed cell
  must be lit by someone in its row or column"; 2/3 = what-if.
- Generator: random walls (~18–22 %, symmetric optional), place bulbs greedily at random until all
  white cells are lit, number every wall, then remove numbers (and plain-wall numbers) while the
  level's logic still solves; floor 60 %/40 % of walls numbered on Easy/Medium.
- Board: walls black squares with the number in white; lit cells get a light flat tint computed
  from the player's FILLED cells (allowed: not the solution); bulb drawn as a filled circle.
- Tests: `LightUpGenTest` via `ShadeCheck.check(Variant.LIGHT_UP, 5, setOf(EASY))`; add the
  variant to `ShadeRules.kt` (`valid`, `countSolutions` with light-based pruning).

### Step 2 · Tents (shade family + outside counts)
- Variant `TENTS`, store `"tents"`, "Tents" / "Šotori", shading, icon `Park`. Sizes 6/8/10/12.
- Rules: each tree has exactly one tent orthogonally next to it (one-to-one), tents never touch
  (also diagonally), numbers outside give the tents in each row and column.
- Shared change "outside counts": `ShadePuzzle.counts: IntArray?` (rows 0..n-1, then columns,
  `-1` = hidden), in `ShadeState`, saved as optional JSON `"counts"` (old saves load without it).
  `ShadeBoard` reserves a margin of one cell above (column counts) and left (row counts) when
  `counts != null`; pass `boardAspect` to `GameLayout`. A count fades when the player's FILLED
  cells in that line equal it. Taps in the margin do nothing.
  - Regression: before this change, record board drawing inputs and ShadeState JSON for 3 saved
    games per existing shade variant (fixture in test resources); after it, they load and
    round-trip identically.
- Data: `numbers[i] = TREE = 1` on tree cells (fixed, blocked); tent = FILLED.
- Logic levels: 0 = counts (line full → rest crossed; open = needed → tents), cells not next to a
  tree are crossed, cells next to a tent are crossed; 1 = tree-tent matching (a tree with one
  possible tent cell gets it; a tent cell that is the only option of two trees…), line-pair counts;
  2/3 = what-if.
- Generator: place tents randomly (non-touching), give each a tree next to it with a valid perfect
  matching, compute counts; Easy/Medium may give some tents as givens until solvable.
  Uniqueness is on tent positions.
- Tests: `TentsGenTest` (ShadeCheck), ShadeRules branch (matching check in `valid`).

### Step 3 · Kropki (Latin family)
- Variant `KROPKI`, store `"kropki"`, "Kropki" / "Kropki", numbers, icon `MoreHoriz`.
  Sizes 5/6/7/8 (`LatinGenerator.size`).
- Rules: Latin square; a white dot between two cells means consecutive digits, a black dot means
  one is double the other; **all** dots are shown, so no dot means neither (1–2 shows either; use
  white for it, as usual).
- Data: `LatinRules.white`, `LatinRules.black` (`List<IntArray>` of `[a, b]` orthogonal pairs) and
  `kropkiAll: Boolean`; JSON fields `"white"`, `"black"`, `"kropkiAll"` (optional when loading).
- Logic: in `LatinRules.candidates`/`LatinLogic.run`, prune each pair by the dot (or by "no dot"
  when `kropkiAll`); keep LatinLogic's existing behaviour for other variants (all `LatinTest` and
  `LatinBruteForceTest` results unchanged).
- Generator: random Latin square, all dots from the solution, remove givens while `LatinLogic`
  solves; `extraClues(d)` like Futoshiki.
- Board: small circles (white = outline, black = filled; e-ink: black outline / black fill) on the
  cell border, like `drawSigns`.
- Tests: add `kropki` to `LatinTest` and `LatinBruteForceTest` (Easy/Medium brute force, 15 seeds).

### Step 4 · Masyu (loop family)
- Variant `MASYU`, store `"masyu"`, "Masyu" / "Masyu", lines, icon `RadioButtonUnchecked`.
  Sizes 6/8/10/12 cells.
- Rules: draw one closed loop through cell centres, through every pearl. Black pearl: the loop
  turns there and goes straight through the next cell on both sides. White pearl: straight
  through, turning in at least one of the two neighbouring cells.
- Shared change "loop through cell centres": an n×n cell board is `SlitherGrid(n - 1)` with dots =
  cell centres. Move `closeLoops`, `connectable`, `isSingleLoop` into `slither/Loop.kt` taking a
  "must be on the loop" predicate instead of Slitherlink clues. Extract the edge/drag gesture code
  of `SlitherBoard` into a shared modifier; `SlitherBoard` keeps identical behaviour.
  - **Done (batch 3).** As built: `Loop.isSingleLoop(grid, line)`, `Loop.closeLoops(grid, s, accepts)`
    (`accepts` = the game's check on the closed loop), `Loop.connectable(grid, s, mustBeOnLoop)`;
    gestures in `ui/slither/EdgeGestures.kt` (`Modifier.edgeGestures`). Loops from a random blob
    border are almost never unique for Masyu, so `MasyuGenerator` hill-climbs the blob (flip a cell,
    keep it if the logic leaves no more open edges) — reuse that for later loop games.
  - Regression first: record `SlitherLogic.run` results (state + round) for 10 fixed puzzles
    (fixture in test resources) before the refactor; after it they must be identical.
- Data: own `MasyuState` in `slither/` (like `SlitherState`: marks per edge, `fixed`, mistakes,
  hints, help, showWrong, chargedWrong, JSON like `SlitherViewModel`), `pearls: IntArray`
  (0 none, 1 white, 2 black). `MasyuViewModel`, `MasyuBoard`, `MasyuGameScreen`
  (Line / Cross modes like Slitherlink).
- Logic levels: 0 = dot degree rules + local pearl rules; 1 = adds premature-loop prevention and
  pearl chains (adjacent whites, black next to edge); 2/3 = what-if.
- Generator: random loop = border of a random blob on `SlitherGrid(n - 1)` (reuse the blob code of
  `SlitherGenerator`), mark every loop cell that qualifies as black/white, remove pearls while the
  level's logic solves; floor on Easy/Medium.
- Tests: `MasyuTest` like `SlitherTest` (single loop, pearl rules, logic → solution) plus an
  independent brute-force loop counter on 6×6.

### Step 5 · Shikaku (new region family)
- Variant `SHIKAKU`, store `"shikaku"`, "Shikaku" / "Pravokotniki", new home section `regions`
  (`section_regions`, "Regions" / "Območja"), icon `CropSquare`. Sizes 6/8/10/12.
- Rules: divide the grid into rectangles; each holds exactly one number, equal to its area.
- New package `region/` + `ui/region/`: `ShikakuPuzzle` (clue cells + numbers, solution as a
  rectangle id per cell), `ShikakuState` (player rectangles, fixed = revealed hints, mistakes,
  hints, help, showWrong, chargedWrong, pause, JSON), `ShikakuViewModel`, `ShikakuBoard`,
  `ShikakuGameScreen`; `MainActivity` gets the new family branches (saved, stats, newGame, GAME).
- Input: drag from one cell to another draws a rectangle (preview while dragging, placed on
  release, replacing rectangles it overlaps); tap inside a rectangle removes it. Undo per action.
  A placed rectangle that is not in the solution is a mistake (only when errors are on).
- Logic levels: 0 = candidate rectangles per clue (right area, no other clue); a clue with one
  candidate is placed; a cell covered by only one clue's candidates fixes that clue to the
  candidates covering it; 1 = removes candidates overlapping another clue's forced cells;
  2/3 = what-if. Hint = a rectangle settled soonest.
- Generator: random partition into rectangles (area 2..maxArea(d), few area-1 cells), clue in a
  random cell of each, retry until the level's logic solves it.
- Tests: `ShikakuTest` (valid partition, logic → solution, independent exact-cover brute force on
  Easy and Medium).

### Step 6 · Bridges (Hashi; new family)
- Variant `BRIDGES`, store `"bridges"`, "Bridges" / "Mostovi", lines, icon `Hub`.
  Grid 7/9/11/13, islands ≈ n²/5.
- Rules: connect islands with horizontal/vertical bridges, at most two between a pair, bridges
  never cross, each island's number = its bridges, all islands connected.
- Package `bridges/` + `ui/bridges/`: puzzle (island cells + numbers), slots (pairs of facing
  islands) with counts 0..2, state/view model/board/screen with the usual hint/help/errors/pause.
- Input: drag from an island to a facing island adds a bridge (0→1→2→0); tap on a bridge cycles
  it. A move that would cross a bridge does nothing. Wrong slot count = mistake (errors on).
  Islands fade when their count is met.
- Logic levels: 0 = capacity rules (number vs. neighbours, 2×neighbours, one-neighbour islands);
  1 = adds crossing and isolation (no closed sub-group before the end); 2/3 = what-if.
- Generator: grow islands from a random start (extend a random direction a random length, place
  island, bridge 1 or 2, no crossings), compute numbers, check logic + retry.
- Tests: `BridgesTest` (rules, logic → solution, independent brute-force over slot values on
  Easy/Medium).

### Step 7 · Kurodoko (shade)
- `KURODOKO`, `"kurodoko"`, "Kurodoko" / "Kurodoko", shading, icon `Visibility`. Sizes 6/7/9/11.
- Rules: shade cells so numbers (never shaded) = white cells seen in the four directions including
  itself; shaded cells don't touch orthogonally; white cells form one area.
- Data: `numbers[i] > 0` clue (fixed white), 0 none. Logic: 0 = visibility bounds (min/max
  reach), adjacency; 1 = connectivity (cut points, reuse `connected`); 2/3 = what-if.
- Generator: random non-touching shaded set keeping white connected, clues on random white cells,
  remove clues while solvable. Tests via ShadeCheck + ShadeRules branch.

### Step 8 · Norinori (shade, regions)
- `NORINORI`, `"norinori"`, "Norinori" / "Norinori", shading, icon `ViewAgenda`. Sizes 6/8/10/12.
- Rules: shade exactly two cells in each region; every shaded cell touches exactly one other
  shaded cell (dominoes), dominoes may cross region borders.
- Data: `regions` (random regions like `SuguruGenerator.randomRegions`, sizes 2..6). Logic: 0 =
  region counts, domino neighbours; 1 = region pair/overlap reasoning; 2/3 = what-if.
- Generator: random domino tiling subset meeting region counts (or regions grown around dominoes),
  retry until solvable. Tests via ShadeCheck.

### Step 9 · Battleships (shade + outside counts)
- `BATTLESHIPS`, `"battleships"`, "Battleships" / "Ladjice", shading, icon `DirectionsBoat`.
  Sizes 6/8/10/10; fleet from `BattleshipsGenerator.fleet(n)` (10×10: 1×4, 2×3, 3×2, 4×1).
- Rules: place the fleet; ships don't touch, not even diagonally; outside counts = ship cells
  per line; some cells are given (water or ship part).
- Data: `counts`; `numbers` for givens: -1 none, 0 water, 1 single, 2 top end, 3 bottom end,
  4 left end, 5 right end, 6 middle (fixed). Player ship cells drawn as segments shaped by their
  own FILLED neighbours; status shows the fleet with complete ships ticked (from player marks).
- Logic: 0 = counts, diagonal water, given-part neighbours; 1 = fleet reasoning (largest ship
  placements); 2/3 = what-if. Tests via ShadeCheck (Rules must check fleet).

### Step 10 · Aquarium (shade, regions + outside counts)
- `AQUARIUM`, `"aquarium"`, "Aquarium" / "Akvarij", shading, icon `WaterDrop`. Sizes 6/8/10/12.
- Rules: fill tanks (regions) with water; inside a tank water lies flat: a filled cell means every
  cell of that tank in the same row and below is filled; outside counts = filled cells per line.
- Logic: per tank a water level; 0 = counts + level propagation; 1 = combined tank/line limits;
  2/3 = what-if. Generator: random tanks, random levels, counts, retry. Tests via ShadeCheck.

### Step 11 · Heyawake (shade, regions)
- `HEYAWAKE`, `"heyawake"`, "Heyawake" / "Heyawake", shading, icon `MeetingRoom`. Sizes 6/7/9/10.
- Rules: rooms (rectangular regions), a number in a room = its shaded cells; shaded cells don't
  touch orthogonally; white cells are connected; no straight run of white cells crosses two room
  borders (spans three rooms).
- Data: rectangular `regions`; `numbers` on the room's first cell in reading order, -1 none.
- Logic: 0 = counts, adjacency, three-room rule; 1 = connectivity; 2/3 = what-if. Tests via ShadeCheck.

### Step 12 · LITS (shade, regions)
- `LITS`, `"lits"`, "LITS" / "LITS", shading, icon `Extension`. Sizes 6/8/10/10.
- Rules: shade one tetromino in each region; all shaded cells connected; no 2×2 shaded; two equal
  tetrominoes (rotations/reflections count as equal) never touch across a region border.
- Logic: candidate placements per region; 0 = intersection/none-of-them cells, 2×2; 1 = shape
  rule + connectivity; 2/3 = what-if. Generator: random regions, choose placements, retry.
  Tests via ShadeCheck.

### Step 13 · Fillomino (digit family)
- `FILLOMINO`, `"fillomino"`, "Fillomino" / "Fillomino", section regions, icon `Category`.
  Sizes 6/7/8/10; digits 1..9.
- Rules: write a number in every cell so that each area of equal numbers that touch has exactly
  that many cells; two areas of the same size never touch.
- Uses `SudokuViewModel`/`GameState` (pad, notes, handwriting, hints): `FILLOMINO` branches with no
  units/peers (so no auto note clearing), `FillominoLogic` for hints. Board draws thick borders
  between cells with different non-empty player digits (player data only).
- Logic: 0 = complete areas closed, cells with one possible size; 1 = reachability (an area can't
  grow to its size → neighbours differ); 2/3 = what-if. Generator: random polyomino partition
  (sizes 1..9, equal sizes not touching), remove givens while solvable.
- Tests: `FillominoTest` + independent brute force on 6×6.

### Step 14 · Galaxies (loop/edge family)
- `GALAXIES`, `"galaxies"`, "Galaxies" / "Galaksije", section regions, icon `AutoAwesome`.
  Sizes 5/7/9/10.
- Rules: draw walls on grid lines to split the grid into areas; each area holds one dot and is
  point-symmetric around it.
- Data: dots in half-grid coordinates (cell centre, edge midpoint or corner); walls are
  `SlitherGrid(n)` edges (outer border fixed). Own `GalaxiesState`/view model/board on the
  shared edge gesture modifier from step 4. Areas closed by the player's walls get a flat tint.
- Logic: cell → dot assignment with mirror cells; 0 = forced cells, mirror propagation;
  1 = reachability; 2/3 = what-if. Hint = an edge settled soonest.
- Tests: `GalaxiesTest` (symmetry, logic → solution, independent brute force on 5×5).

### Step 15 · Yajilin (shade + loop)
- `YAJILIN`, `"yajilin"`, "Yajilin" / "Yajilin", lines, icon `Route`. Sizes 6/7/8/10.
- Rules: shade some cells (not touching orthogonally); arrow clues give the number of shaded
  cells in their direction; one loop passes through every other cell (not clue cells).
- Data: `YajilinState` in `slither/`: cell marks + loop edges on `SlitherGrid(n - 1)`; clues
  `dir * 100 + count`, -1 none. Modes: Shade (tap/drag cells) and Line (drag through centres).
- Logic: 0 = arrow counts, adjacency, loop degree rules (from `Loop.kt`); 1 = premature loops +
  connectivity; 2/3 = what-if. Generator: blob-border loop, off-loop cells become shaded or clue
  cells (adjacent off-loop cells → one becomes a clue), compute arrows, add clues until solvable.
  Blob loops are rarely unique (Masyu, batch 3): hill-climb the blob like `MasyuGenerator.climb`.
- Tests: `YajilinTest` + brute force on 6×6.
  - **Done (batch 10).** One logic over elements (edges, then cells); clue cells and their edges start fixed; Shade-mode tap cycles empty, shaded, dot; hints fall back to higher levels. `TRIAL_SWEEPS` = 2 meets the gates (Hard needs level 2 in 5/5 and 20/20 extra seeds, Expert level 3 in 5/5 and 20/20).

---

## Report after each batch (per step inside it)

- Files added/changed (one line each).
- Gate results: per difficulty — median/max generation ms, clue counts, top-level seeds (x/5),
  test class times, full-suite time; emulator screenshots checked (phone + tablet, light + e-ink).
- Anything not done, and any `PLAN PROBLEM` with numbers.

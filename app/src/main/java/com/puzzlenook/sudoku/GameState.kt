package com.puzzlenook.sudoku

import com.puzzlenook.settings.HelpRules

data class Cell(
    val value: Int = 0,
    val given: Boolean = false,
    /** Pencil notes as a bitmask: bit d set means candidate d is noted. */
    val notes: Int = 0,
    /** A wall (kakuro clue square) that never takes a digit. */
    val blocked: Boolean = false,
) {
    fun hasNote(d: Int) = (notes shr d) and 1 == 1
}

/**
 * A digit puzzle on a [size]×[size] grid: classic and killer sudoku, and kakuro.
 * Digits must differ within each unit (sudoku houses, cages, kakuro runs); cages also
 * have to add up to their sum.
 */
data class GameState(
    val difficulty: Difficulty,
    val cells: List<Cell>,
    val solution: List<Int>,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    val mistakes: Int = 0,
    /** Hint cost in half points: highlighting a cell costs 1, revealing its number 1 more. */
    val hintHalfPoints: Int = 0,
    /** Cell highlighted by the first hint press; the next press reveals its number. */
    val hintCell: Int? = null,
    /** Hints started (first steps) in this game. */
    val hintsUsed: Int = 0,
    /** Playing time at the last first step; -1 = none. */
    val lastHintAt: Long = -1,
    val help: com.puzzlenook.settings.HelpSettings = com.puzzlenook.settings.HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    /** Final-time penalty per point, in percent; follows the app setting. */
    val penaltyPercent: Int = 10,
    /** Killer cages or kakuro runs; empty for classic sudoku. */
    val cages: List<Cage> = emptyList(),
    val variant: Variant = Variant.CLASSIC,
    val size: Int = 9,
    /** Futoshiki, Calcudoku and Skyscrapers rules; null for the sudoku family and kakuro. */
    val rules: LatinRules? = null,
    /** False in "hide errors" mode: wrong digits look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    /** True once red error marks could have been used (errors on during a move, or switched on). */
    val errorsSeen: Boolean = false,
    /** Wrong cells (cell → digit) at the moment errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
    /** Jigsaw sudoku: region id of every cell; null for the other games. */
    val regions: List<Int>? = null,
) {
    /** Highest digit: n on an n×n Latin square, the largest region in Suguru, else 9. */
    val digits: Int by lazy {
        rules?.n ?: if (variant == Variant.SUGURU && regions != null) Regions.cells(regions.toIntArray()).maxOf { it.size } else 9
    }

    /** Groups of cells whose digits must all differ. */
    val units: List<IntArray> by lazy {
        rules?.units ?: (houseUnits() + cages.map { it.cells.toIntArray() })
    }

    private fun houseUnits(): List<IntArray> = when {
        regions != null && variant == Variant.SUGURU -> Regions.cells(regions.toIntArray())
        regions != null -> Regions.rows(size) + Regions.cols(size) + Regions.cells(regions.toIntArray())
        variant.hasHouses -> GridSolver.SUDOKU_HOUSES
        else -> emptyList()
    }

    /** Indices into [cages] for every cell (kakuro cells sit in two runs). */
    val cagesOf: Array<IntArray> by lazy {
        val lists = Array(cells.size) { mutableListOf<Int>() }
        cages.forEachIndexed { c, cage -> cage.cells.forEach { lists[it] += c } }
        Array(cells.size) { lists[it].toIntArray() }
    }

    /** First cage of each cell, or -1 (killer cages don't overlap). */
    val cageOf: IntArray by lazy { IntArray(cells.size) { cagesOf[it].firstOrNull() ?: -1 } }

    private val peerMatrix: Array<BooleanArray> by lazy {
        Array(cells.size) { BooleanArray(cells.size) }.also { m ->
            for (u in units) for (a in u) for (b in u) if (a != b) m[a][b] = true
            // Suguru: equal digits may not touch, even diagonally.
            if (variant == Variant.SUGURU) for (a in cells.indices) for (b in cells.indices) {
                if (a != b && kotlin.math.abs(a / size - b / size) <= 1 && kotlin.math.abs(a % size - b % size) <= 1) m[a][b] = true
            }
        }
    }

    /** Digits 1..k allowed in a cell of a Suguru region of k cells, as bits. */
    private fun regionMask(i: Int): Int {
        val r = regions ?: return 0x3FE
        val k = r.count { it == r[i] }
        return ((1 shl (k + 1)) - 1) and 1.inv()
    }

    /** True when [a] and [b] share a unit, so they can't hold the same digit. */
    fun isPeer(a: Int, b: Int) = peerMatrix[a][b]

    /** Digits that can still finish every cage of cell [i], as bits 1..9. */
    private fun cageCandidates(i: Int): Int {
        var mask = 0x3FE
        for (c in cagesOf[i]) {
            val cage = cages[c]
            var used = 0
            var missing = cage.sum
            var empty = 0
            for (j in cage.cells) {
                val v = cells[j].value
                if (v == 0) empty++ else {
                    used = used or (1 shl v)
                    missing -= v
                }
            }
            mask = mask and GridSolver.comboMask(0x3FE and used.inv(), empty, missing)
        }
        return mask
    }

    fun isWrong(i: Int) = cells[i].value != 0 && cells[i].value != solution[i]

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(i: Int) = showWrong && isWrong(i)

    /** How many more correct copies of [d] still need to be placed (any copies when errors are hidden). */
    fun remaining(d: Int) = solution.count { it == d } - cells.indices.count { cells[it].value == d && !shownWrong(it) }

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): GameState {
        if (show == showWrong || completed) return this
        val wrong = cells.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { cells[it].value }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { cells[it].value })
        }
    }

    val isSolved: Boolean get() = cells.indices.all { cells[it].value == solution[it] }

    /** The highlighted hint cell, if it still needs solving. */
    val activeHintCell: Int? get() = hintCell?.takeIf { !completed && cells[it].value != solution[it] }

    /** Penalty points in halves: each mistake is ½ point, a full hint 1 point. */
    val penaltyHalfPoints: Int get() = mistakes * MISTAKE_HALF_POINTS + hintHalfPoints

    /** Penalty as a share of the playing time, in percent. */
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2

    /** Each penalty point adds [penaltyPercent]% of the playing time. */
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200

    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    /** Enters [d] in the selected cell (or toggles it as a note in notes mode). */
    fun enter(d: Int): GameState {
        val i = selected ?: return this
        val cell = cells[i]
        if (cell.given || cell.blocked || completed || d > digits) return this
        if (notesMode) {
            if (cell.value != 0) return this
            return copy(cells = cells.replaced(i, cell.copy(notes = cell.notes xor (1 shl d))), errorsSeen = errorsSeen || showWrong)
        }
        if (cell.value == d) return copy(cells = cells.replaced(i, Cell()), errorsSeen = errorsSeen || showWrong)
        val wrong = d != solution[i]
        var next: List<Cell> = cells.replaced(i, Cell(value = d))
        // While errors are shown only right digits clear peers' notes; while hidden every digit
        // does, so the notes can't tell right from wrong.
        if (!wrong || !showWrong) next = withoutPeerNote(next, i, d)
        return copy(
            cells = next,
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
            // Solving the highlighted cell yourself uses up the hint.
            hintCell = if (!wrong && i == hintCell) null else hintCell,
        ).checkSolved()
    }

    /**
     * Adds [d] as a note in the selected empty cell (no toggle: writing a note twice keeps it).
     * Returns the state unchanged if the cell already holds a number.
     */
    fun addNote(d: Int): GameState {
        val i = selected ?: return this
        val cell = cells[i]
        if (cell.given || cell.blocked || completed || cell.value != 0 || cell.hasNote(d) || d > digits) return this
        return copy(cells = cells.replaced(i, cell.copy(notes = cell.notes or (1 shl d))), errorsSeen = errorsSeen || showWrong)
    }

    /** Removes note [d] from the selected cell. */
    fun removeNote(d: Int): GameState {
        val i = selected ?: return this
        val cell = cells[i]
        if (completed || !cell.hasNote(d)) return this
        return copy(cells = cells.replaced(i, cell.copy(notes = cell.notes and (1 shl d).inv())), errorsSeen = errorsSeen || showWrong)
    }

    fun erase(): GameState {
        val i = selected ?: return this
        val cell = cells[i]
        if (cell.given || cell.blocked || completed || cell == Cell()) return this
        return copy(cells = cells.replaced(i, Cell()), errorsSeen = errorsSeen || showWrong)
    }

    /**
     * Two-step hint. First press highlights a cell worth looking at (½ point);
     * pressing again reveals that cell's number (another ½ point).
     */
    fun hint(): GameState {
        if (completed) return this
        val highlighted = activeHintCell
        if (highlighted != null) {
            val v = solution[highlighted]
            val next = withoutPeerNote(cells.replaced(highlighted, Cell(value = v, given = true)), highlighted, v)
            return copy(cells = next, hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help), hintCell = null, selected = highlighted)
                .checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintCell() ?: return this
        return copy(
            hintCell = target, selected = target,
            hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /**
     * The most useful cell to point at: a wrong entry first; otherwise a cell that can be
     * solved right now (only one candidate, or the only place for a digit in a full
     * 9-cell unit); otherwise the empty cell with the fewest candidates.
     */
    fun pickHintCell(): Int? {
        cells.indices.firstOrNull { isWrong(it) }?.let { return it }
        // The cell the step-by-step logic pins down soonest from here, nearest the selection.
        val board = IntArray(cells.size) { cells[it].value }
        val logic = rules?.let { LatinLogic.run(it, board) } ?: if (variant == Variant.SUGURU) {
            regions?.let { SuguruLogic.forLevel(difficulty, size, it.toIntArray()).run(board) }
        } else if (variant == Variant.FILLOMINO) {
            fillominoLogic(board)
        } else when (variant) {
            Variant.CLASSIC -> DigitLogic.sudoku()
            Variant.KILLER -> DigitLogic.killer(cages)
            Variant.JIGSAW -> regions?.let { DigitLogic.jigsaw(it.toIntArray()) }
            Variant.KAKURO -> DigitLogic.kakuro(size, BooleanArray(cells.size) { cells[it].blocked }, cages)
            else -> null
        }?.run(board)
        logic?.let { l ->
            cells.indices.filter { !cells[it].blocked && cells[it].value == 0 && l.round[it] > 0 }
                .minWithOrNull(compareBy({ l.round[it] }, { selected?.let { s -> distance(s, it) } ?: 0 }))
                ?.let { return it }
        }
        val empty = cells.indices.filter { !cells[it].blocked && cells[it].value == 0 }
        if (empty.isEmpty()) return null
        val candidates = empty.associateWith { i ->
            val allowed = rules?.candidates(board, i) ?: if (variant == Variant.SUGURU) regionMask(i) else cageCandidates(i)
            (1..digits).filter { d -> (allowed shr d) and 1 == 1 && cells.indices.none { j -> isPeer(i, j) && cells[j].value == d } }
        }
        // Prefer cells near the current selection, so the hint fits where you're looking.
        val order = empty.sortedBy { i -> selected?.let { s -> distance(s, i) } ?: 0 }
        order.firstOrNull { candidates.getValue(it).size == 1 }?.let { return it }
        // Hidden singles: only full units (every digit once) must hold every digit.
        val fullUnits = units.filter { it.size == digits || variant == Variant.SUGURU }
        for (i in order) {
            val hiddenSingle = candidates.getValue(i).any { d ->
                fullUnits.any { u -> i in u && u.none { j -> j != i && cells[j].value == 0 && d in candidates.getValue(j) } }
            }
            if (hiddenSingle) return i
        }
        return order.minBy { candidates.getValue(it).size }
    }

    /**
     * Fillomino: the logic of this level; when it settles nothing new from the player's digits (a game
     * saved under other rules, or a position the level can't finish), the next levels up.
     */
    private fun fillominoLogic(board: IntArray): LatinLogic.Result? {
        for (level in difficulty.ordinal..3) {
            val r = FillominoLogic.forLevel(level, size).run(board)
            if (cells.indices.any { cells[it].value == 0 && r.round[it] > 0 }) return r
        }
        return null
    }

    /**
     * Goes back to an earlier set of cells (undo). Cells revealed by a hint since then stay:
     * their penalty has been charged, so undo must not take them away.
     */
    fun restored(previous: List<Cell>): GameState =
        copy(cells = previous.mapIndexed { i, c -> if (cells[i].given) cells[i] else c })

    private fun distance(a: Int, b: Int) = kotlin.math.abs(a / size - b / size) + kotlin.math.abs(a % size - b % size)

    private fun withoutPeerNote(list: List<Cell>, i: Int, d: Int): List<Cell> = list.mapIndexed { j, c ->
        if (isPeer(i, j) && c.hasNote(d)) c.copy(notes = c.notes and (1 shl d).inv()) else c
    }

    private fun checkSolved() = if (isSolved) copy(completed = true, notesMode = false, hintCell = null) else this

    companion object {
        const val MISTAKE_HALF_POINTS = 1

        /** Same row, column or box of a 9×9 sudoku. */
        fun peers(a: Int, b: Int) =
            a / 9 == b / 9 || a % 9 == b % 9 || SudokuSolver.box(a) == SudokuSolver.box(b)

        /**
         * The note in [cell] closest to a point given as fractions (0..1) of the cell.
         * Notes are drawn in a 3×3 grid: 1 2 3 on top, 7 8 9 at the bottom.
         */
        fun nearestNote(cell: Cell, fx: Float, fy: Float): Int? = (1..9)
            .filter { cell.hasNote(it) }
            .minByOrNull { d ->
                val nx = ((d - 1) % 3 + 0.5f) / 3f
                val ny = ((d - 1) / 3 + 0.5f) / 3f
                (nx - fx) * (nx - fx) + (ny - fy) * (ny - fy)
            }

        fun fromPuzzle(p: Puzzle, variant: Variant = if (p.cages.isEmpty()) Variant.CLASSIC else Variant.KILLER) = GameState(
            difficulty = p.difficulty,
            cells = p.givens.indices.map { i ->
                if (p.blocked?.get(i) == true) Cell(blocked = true, given = true)
                else Cell(value = p.givens[i], given = p.givens[i] != 0)
            },
            solution = p.solution.toList(),
            cages = p.cages,
            variant = variant,
            size = p.size,
            rules = p.rules,
            regions = p.regions?.toList(),
        )
    }
}

/**
 * "Hide errors" mode: while answers are hidden, wrong entries aren't shown or counted. When
 * they are shown again, every wrong cell that wasn't already charged costs a mistake. A cell
 * counts as already charged if it was wrong with the same value when the answers were hidden.
 */
object AnswerCheck {
    /** Wrong cells now that still owe a mistake. [charged] maps cell → value at hide time. */
    fun owed(wrong: List<Int>, value: (Int) -> Int, charged: Map<Int, Int>): Int =
        wrong.count { charged[it] != value(it) }

    fun snapshot(wrong: List<Int>, value: (Int) -> Int): Map<Int, Int> = wrong.associateWith(value)

    fun encode(m: Map<Int, Int>): String = m.entries.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(s: String): Map<Int, Int> =
        if (s.isEmpty()) emptyMap() else s.split(",").associate { p -> p.substringBefore(':').toInt() to p.substringAfter(':').toInt() }
}

/** Formats half points as "0", "½", "1", "1½", "2½". */
fun formatPoints(halfPoints: Int): String = when {
    halfPoints == 0 -> "0"
    halfPoints == 1 -> "½"
    halfPoints % 2 == 0 -> "${halfPoints / 2}"
    else -> "${halfPoints / 2}½"
}

private fun List<Cell>.replaced(i: Int, cell: Cell) = toMutableList().also { it[i] = cell }

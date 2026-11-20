package com.puzzlenook.nonogram

import com.puzzlenook.settings.HelpRules
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState
import kotlin.random.Random

/** What the player has put in a nonogram cell. */
enum class Mark { NONE, FILLED, CROSSED }

class NonogramPuzzle(
    val size: Int,
    val difficulty: Difficulty,
    val solution: BooleanArray,
    /** Cells shown from the start because line logic alone couldn't settle them. */
    val givens: BooleanArray,
)

/**
 * A nonogram in progress. Filling a cell that should stay empty, or crossing one that
 * should be filled, is a mistake (½ point) and stays visible until you clear it.
 */
data class NonogramState(
    val difficulty: Difficulty,
    val size: Int,
    val solution: List<Boolean>,
    val marks: List<Mark>,
    /** Givens and revealed hints: shown correctly and locked. */
    val fixed: List<Boolean>,
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    val hintCell: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: com.puzzlenook.settings.HelpSettings = com.puzzlenook.settings.HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    /** Taps and drags put crosses instead of filling. */
    val crossMode: Boolean = false,
    /** False in "hide errors" mode: wrong marks look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong cells (cell → mark) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    val rowClues: List<List<Int>> by lazy { List(size) { r -> LineSolver.clue(List(size) { solution[r * size + it] }) } }
    val colClues: List<List<Int>> by lazy { List(size) { c -> LineSolver.clue(List(size) { solution[it * size + c] }) } }

    fun isWrong(i: Int) = when (marks[i]) {
        Mark.FILLED -> !solution[i]
        Mark.CROSSED -> solution[i]
        Mark.NONE -> false
    }

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(i: Int) = showWrong && isWrong(i)

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): NonogramState {
        if (show == showWrong || completed) return this
        val wrong = marks.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { marks[it].ordinal }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { marks[it].ordinal })
        }
    }

    /** A row's filled cells already match its clue. */
    fun rowDone(r: Int) = LineSolver.clue(List(size) { marks[r * size + it] == Mark.FILLED }) == rowClues[r]
    fun colDone(c: Int) = LineSolver.clue(List(size) { marks[it * size + c] == Mark.FILLED }) == colClues[c]

    val isSolved: Boolean get() = marks.indices.all { (marks[it] == Mark.FILLED) == solution[it] }

    val activeHintCell: Int? get() = hintCell?.takeIf { !completed && !fixed[it] && marks[it] != correctMark(it) }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    fun correctMark(i: Int) = if (solution[i]) Mark.FILLED else Mark.CROSSED

    /** Sets cell [i] to [mark] (NONE clears it). Wrong marks count as a mistake. */
    fun set(i: Int, mark: Mark): NonogramState {
        if (completed || fixed[i] || marks[i] == mark) return this
        val wrong = when (mark) {
            Mark.FILLED -> !solution[i]
            Mark.CROSSED -> solution[i]
            Mark.NONE -> false
        }
        return copy(
            marks = marks.toMutableList().also { it[i] = mark },
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
            hintCell = if (!wrong && mark != Mark.NONE && i == hintCell) null else hintCell,
        ).checkSolved()
    }

    /** Two-step hint: highlight a cell that can be worked out (½ point), then reveal it (½ point). */
    fun hint(): NonogramState {
        if (completed) return this
        activeHintCell?.let { h ->
            return copy(
                marks = marks.toMutableList().also { it[h] = correctMark(h) },
                fixed = fixed.toMutableList().also { it[h] = true },
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintCell = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintCell() ?: return this
        return copy(
            hintCell = target, hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /** A wrong mark first; else a cell line logic can settle from what is correctly marked. */
    fun pickHintCell(): Int? {
        marks.indices.firstOrNull { isWrong(it) }?.let { return it }
        val known = IntArray(size * size) { i ->
            when {
                marks[i] == Mark.FILLED -> 1
                marks[i] == Mark.CROSSED -> 0
                else -> LineSolver.UNKNOWN
            }
        }
        // One line at a time, so the hint points at something you can see from one clue.
        for (r in 0 until size) {
            val line = IntArray(size) { known[r * size + it] }
            val solved = LineSolver.solveLine(rowClues[r], line) ?: continue
            (0 until size).firstOrNull { line[it] == LineSolver.UNKNOWN && solved[it] != LineSolver.UNKNOWN }
                ?.let { return r * size + it }
        }
        for (c in 0 until size) {
            val line = IntArray(size) { known[it * size + c] }
            val solved = LineSolver.solveLine(colClues[c], line) ?: continue
            (0 until size).firstOrNull { line[it] == LineSolver.UNKNOWN && solved[it] != LineSolver.UNKNOWN }
                ?.let { return it * size + c }
        }
        return marks.indices.firstOrNull { marks[it] != correctMark(it) && !(marks[it] == Mark.NONE && !solution[it]) }
            ?: marks.indices.firstOrNull { marks[it] == Mark.NONE }
    }

    private fun checkSolved(): NonogramState {
        if (!isSolved) return this
        // Finished: cross out the rest so the picture stands out.
        return copy(
            completed = true,
            hintCell = null,
            marks = marks.mapIndexed { i, m -> if (solution[i]) Mark.FILLED else if (m == Mark.NONE) Mark.CROSSED else m },
        )
    }

    companion object {
        fun fromPuzzle(p: NonogramPuzzle) = NonogramState(
            difficulty = p.difficulty,
            size = p.size,
            solution = p.solution.toList(),
            marks = List(p.size * p.size) { i -> if (p.givens[i]) (if (p.solution[i]) Mark.FILLED else Mark.CROSSED) else Mark.NONE },
            fixed = p.givens.toList(),
        )
    }
}

object NonogramGenerator {
    fun size(d: Difficulty) = when (d) {
        Difficulty.EASY -> 8
        Difficulty.MEDIUM -> 10
        Difficulty.HARD -> 12
        Difficulty.EXPERT -> 15
    }

    private fun density(d: Difficulty) = when (d) {
        Difficulty.EASY -> 0.55f
        Difficulty.MEDIUM -> 0.51f
        Difficulty.HARD -> 0.48f
        Difficulty.EXPERT -> 0.46f
    }

    /**
     * Random blobby picture (noise smoothed once so shapes clump together), solved with
     * line logic. Where logic gets stuck a cell is given, so every puzzle has one solution
     * that can be reached without guessing. Keeps the attempt with the fewest givens.
     */
    fun generate(difficulty: Difficulty, random: Random = Random.Default, budgetMs: Long = 1_500): NonogramPuzzle {
        val deadline = System.currentTimeMillis() + budgetMs
        var best: NonogramPuzzle? = null
        var bestScore = Int.MAX_VALUE
        while (best == null || System.currentTimeMillis() < deadline) {
            val p = attempt(difficulty, random)
            // Fewest givens first; very full or very empty pictures are dull.
            val fill = p.solution.count { it }.toFloat() / p.solution.size
            val score = p.givens.count { it } * 10 + if (fill in 0.4f..0.66f) 0 else 5
            if (score < bestScore) {
                best = p
                bestScore = score
            }
            if (bestScore == 0) break
        }
        return best!!
    }
    private fun attempt(difficulty: Difficulty, random: Random): NonogramPuzzle {
        val n = size(difficulty)
        val noise = BooleanArray(n * n) { random.nextFloat() < density(difficulty) }
        val picture = BooleanArray(n * n) { i ->
            val r = i / n
            val c = i % n
            var filled = 0
            var total = 0
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr
                val cc = c + dc
                if (rr !in 0 until n || cc !in 0 until n) continue
                val weight = if (dr == 0 && dc == 0) 2 else 1
                total += weight
                if (noise[rr * n + cc]) filled += weight
            }
            filled * 2 > total || (filled * 2 == total && noise[i])
        }
        val rows = List(n) { r -> LineSolver.clue(List(n) { picture[r * n + it] }) }
        val cols = List(n) { c -> LineSolver.clue(List(n) { picture[it * n + c] }) }
        val grid = IntArray(n * n) { LineSolver.UNKNOWN }
        val givens = BooleanArray(n * n)
        while (true) {
            check(LineSolver.solveGrid(n, n, rows, cols, grid))
            val open = grid.indices.filter { grid[it] == LineSolver.UNKNOWN }
            if (open.isEmpty()) break
            // Stuck: give a filled cell if possible (more telling than an empty one).
            val pick = open.filter { picture[it] }.ifEmpty { open }.random(random)
            givens[pick] = true
            grid[pick] = if (picture[pick]) 1 else 0
        }
        return NonogramPuzzle(n, difficulty, picture, givens)
    }
}

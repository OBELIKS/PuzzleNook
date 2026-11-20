package com.puzzlenook.slither

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpRules
import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState

/**
 * A Slitherlink game in progress. Marks are per edge: [Mark.FILLED] is a line, [Mark.CROSSED]
 * an ✕ (optional). A line where the loop doesn't go, or an ✕ where it does, is a mistake.
 */
data class SlitherState(
    val difficulty: Difficulty,
    val n: Int,
    val clues: List<Int>,
    val solution: List<Boolean>,
    val marks: List<Mark>,
    /** Revealed hints: shown correctly and locked. */
    val fixed: List<Boolean>,
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    val hintEdge: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: com.puzzlenook.settings.HelpSettings = com.puzzlenook.settings.HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    val crossMode: Boolean = false,
    /** False in "hide errors" mode: wrong marks look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong edges (edge → mark) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    val grid: SlitherGrid get() = grids.getOrPut(n) { SlitherGrid(n) }

    fun isWrong(e: Int) = when (marks[e]) {
        Mark.FILLED -> !solution[e]
        Mark.CROSSED -> solution[e]
        Mark.NONE -> false
    }

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(e: Int) = showWrong && isWrong(e)

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): SlitherState {
        if (show == showWrong || completed) return this
        val wrong = marks.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { marks[it].ordinal }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { marks[it].ordinal })
        }
    }

    /** A number with exactly that many lines around it (a 0 once all four sides are crossed out). */
    fun clueDone(i: Int): Boolean {
        val es = grid.cellEdges[i]
        return when {
            clues[i] < 0 -> false
            clues[i] == 0 -> es.all { marks[it] == Mark.CROSSED }
            else -> es.count { marks[it] == Mark.FILLED } == clues[i]
        }
    }

    val isSolved: Boolean get() = marks.indices.all { (marks[it] == Mark.FILLED) == solution[it] }

    val activeHintEdge: Int? get() = hintEdge?.takeIf { !completed && !fixed[it] && marks[it] != correctMark(it) }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    fun correctMark(e: Int) = if (solution[e]) Mark.FILLED else Mark.CROSSED

    fun set(e: Int, mark: Mark): SlitherState {
        if (completed || fixed[e] || marks[e] == mark) return this
        val wrong = when (mark) {
            Mark.FILLED -> !solution[e]
            Mark.CROSSED -> solution[e]
            Mark.NONE -> false
        }
        return copy(
            marks = marks.toMutableList().also { it[e] = mark },
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
            hintEdge = if (!wrong && mark != Mark.NONE && e == hintEdge) null else hintEdge,
        ).checkSolved()
    }

    fun hint(): SlitherState {
        if (completed) return this
        activeHintEdge?.let { h ->
            return copy(
                marks = marks.toMutableList().also { it[h] = correctMark(h) },
                fixed = fixed.toMutableList().also { it[h] = true },
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintEdge = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintEdge() ?: return this
        return copy(
            hintEdge = target, hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /** A wrong mark first; else the open edge the logic settles soonest, preferring lines. */
    fun pickHintEdge(): Int? {
        marks.indices.firstOrNull { isWrong(it) }?.let { return it }
        val start = IntArray(marks.size) { e ->
            when (marks[e]) {
                Mark.FILLED -> SHADED
                Mark.CROSSED -> WHITE
                Mark.NONE -> UNKNOWN
            }
        }
        val result = SlitherLogic(grid, clues.toIntArray()).run(start)
        val open = marks.indices.filter { marks[it] == Mark.NONE && result.round[it] > 0 }
        val soonest = open.minOfOrNull { result.round[it] } ?: return marks.indices.firstOrNull { marks[it] == Mark.NONE && solution[it] }
        val now = open.filter { result.round[it] == soonest }
        return now.firstOrNull { solution[it] } ?: now.first()
    }

    private fun checkSolved(): SlitherState =
        if (isSolved) copy(completed = true, hintEdge = null, crossMode = false) else this

    companion object {
        private val grids = HashMap<Int, SlitherGrid>()

        fun fromPuzzle(p: SlitherPuzzle) = SlitherState(
            difficulty = p.difficulty,
            n = p.n,
            clues = p.clues.toList(),
            solution = p.solution.toList(),
            marks = List(p.solution.size) { Mark.NONE },
            fixed = List(p.solution.size) { false },
        )
    }
}

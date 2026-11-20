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
 * A Yajilin game in progress. One list of marks: the first `edges` entries are the loop's edges (on
 * `SlitherGrid(n - 1)`, dots = cell centres), then one per cell. Edges: [Mark.FILLED] is a line, [Mark.CROSSED]
 * an ✕ (only from hints). Cells: [Mark.FILLED] is shaded, [Mark.CROSSED] is a dot (not shaded). Clue cells and
 * the edges around them are fixed from the start. A wrong mark is a mistake.
 */
data class YajilinState(
    val difficulty: Difficulty,
    /** Cells per side. */
    val n: Int,
    /** Per cell: [NO_CLUE] or `dir * 100 + count`. */
    val clues: List<Int>,
    /** Per element (edges, then cells): is it a line / a shaded cell in the solution. */
    val solution: List<Boolean>,
    val marks: List<Mark>,
    /** Revealed hints and clue cells: shown correctly and locked. */
    val fixed: List<Boolean>,
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    /** Element a hint points at (an edge, or `edges + cell`). */
    val hintAt: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: com.puzzlenook.settings.HelpSettings = com.puzzlenook.settings.HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    /** Input mode: false shades cells, true draws the loop. */
    val lineMode: Boolean = false,
    /** False in "hide errors" mode: wrong marks look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong elements (element → mark) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    private val geometry: MasyuGrid get() = yajilinGrids.getOrPut(n) { MasyuGrid(n) }
    val grid: SlitherGrid get() = geometry.grid

    /** Number of edges: cell `i` is element `edges + i`. */
    val edges: Int get() = geometry.grid.edges

    fun isWrong(x: Int) = when (marks[x]) {
        Mark.FILLED -> !solution[x]
        Mark.CROSSED -> solution[x]
        Mark.NONE -> false
    }

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(x: Int) = showWrong && isWrong(x)

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): YajilinState {
        if (show == showWrong || completed) return this
        val wrong = marks.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { marks[it].ordinal }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { marks[it].ordinal })
        }
    }

    private fun shadedAt(i: Int) = marks[edges + i] == Mark.FILLED

    /** Shaded cells of your own that lie in the direction of clue cell [i]. */
    fun clueShaded(i: Int): Int = yajilinRay(geometry, i, yajilinDir(clues[i])).count { shadedAt(it) }

    /** The clue's count is met by your shaded cells (the clue fades; no solution involved). */
    fun clueDone(i: Int) = clues[i] >= 0 && clueShaded(i) == yajilinCount(clues[i])

    /** Your shaded cells exceed the clue's count: shown only while errors are on. */
    fun clueOver(i: Int) = clues[i] >= 0 && clueShaded(i) > yajilinCount(clues[i])

    /** A shaded cell of yours touches another one: shown only while errors are on. */
    fun shadeClash(i: Int): Boolean = shadedAt(i) && (0 until 4).any { d -> geometry.nb(i, d).let { it >= 0 && shadedAt(it) } }

    val isSolved: Boolean get() = marks.indices.all { (marks[it] == Mark.FILLED) == solution[it] }

    val activeHint: Int? get() = hintAt?.takeIf { !completed && !fixed[it] && marks[it] != correctMark(it) }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    fun correctMark(x: Int) = if (solution[x]) Mark.FILLED else Mark.CROSSED

    fun set(x: Int, mark: Mark): YajilinState {
        if (completed || fixed[x] || marks[x] == mark) return this
        val wrong = when (mark) {
            Mark.FILLED -> !solution[x]
            Mark.CROSSED -> solution[x]
            Mark.NONE -> false
        }
        return copy(
            marks = marks.toMutableList().also { it[x] = mark },
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
            hintAt = if (!wrong && mark != Mark.NONE && x == hintAt) null else hintAt,
        ).checkSolved()
    }

    fun hint(): YajilinState {
        if (completed) return this
        activeHint?.let { h ->
            return copy(
                marks = marks.toMutableList().also { it[h] = correctMark(h) },
                fixed = fixed.toMutableList().also { it[h] = true },
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintAt = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHint() ?: return this
        return copy(
            hintAt = target, hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /**
     * A wrong mark first; else the open element the difficulty's logic settles soonest (preferring lines
     * and shaded cells). If that logic settles nothing from your marks, the next level up is tried.
     */
    fun pickHint(): Int? {
        marks.indices.firstOrNull { isWrong(it) }?.let { return it }
        val start = IntArray(marks.size) { x ->
            when (marks[x]) {
                Mark.FILLED -> SHADED
                Mark.CROSSED -> WHITE
                Mark.NONE -> UNKNOWN
            }
        }
        val clueArray = clues.toIntArray()
        for (level in difficulty.ordinal..3) {
            val result = YajilinLogic(geometry, clueArray, level).run(start)
            val open = marks.indices.filter { marks[it] == Mark.NONE && !fixed[it] && result.round[it] > 0 }
            val soonest = open.minOfOrNull { result.round[it] } ?: continue
            val now = open.filter { result.round[it] == soonest }
            return now.firstOrNull { solution[it] } ?: now.first()
        }
        return marks.indices.firstOrNull { marks[it] == Mark.NONE && !fixed[it] && solution[it] }
    }

    private fun checkSolved(): YajilinState =
        if (isSolved) copy(completed = true, hintAt = null) else this

    companion object {
        private val yajilinGrids = HashMap<Int, MasyuGrid>()

        fun fromPuzzle(p: YajilinPuzzle): YajilinState {
            val g = MasyuGrid(p.n)
            val edges = g.grid.edges
            val solution = List(edges + p.n * p.n) { if (it < edges) p.loop[it] else p.shaded[it - edges] }
            val fixed = BooleanArray(edges + p.n * p.n)
            for (i in 0 until p.n * p.n) if (p.clues[i] >= 0) {
                fixed[edges + i] = true
                for (e in g.edgesOf(i)) fixed[e] = true
            }
            return YajilinState(
                difficulty = p.difficulty,
                n = p.n,
                clues = p.clues.toList(),
                solution = solution,
                marks = List(solution.size) { Mark.NONE },
                fixed = fixed.toList(),
            )
        }
    }
}

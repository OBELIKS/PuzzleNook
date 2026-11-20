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
 * A Galaxies game in progress. Marks are per edge of `SlitherGrid(n)`: [Mark.FILLED] is a wall, [Mark.CROSSED]
 * an ✕ ("no wall", optional). The outer border is a fixed wall from the start. A wall where the solution has
 * none, or an ✕ where it has one, is a mistake.
 */
data class GalaxiesState(
    val difficulty: Difficulty,
    /** Cells per side. */
    val n: Int,
    /** Half-grid points of the dots (see [GalaxyGrid]). */
    val dots: List<Int>,
    val solution: List<Boolean>,
    val marks: List<Mark>,
    /** Revealed hints and the outer border: shown correctly and locked. */
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
    val geometry: GalaxyGrid get() = geometryOf(n, dots)
    val grid: SlitherGrid get() = geometry.grid

    fun isWrong(e: Int) = when (marks[e]) {
        Mark.FILLED -> !solution[e]
        Mark.CROSSED -> solution[e]
        Mark.NONE -> false
    }

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(e: Int) = showWrong && isWrong(e)

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): GalaxiesState {
        if (show == showWrong || completed) return this
        val wrong = marks.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { marks[it].ordinal }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { marks[it].ordinal })
        }
    }

    val isSolved: Boolean get() = marks.indices.all { (marks[it] == Mark.FILLED) == solution[it] }

    val activeHintEdge: Int? get() = hintEdge?.takeIf { !completed && !fixed[it] && marks[it] != correctMark(it) }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    fun correctMark(e: Int) = if (solution[e]) Mark.FILLED else Mark.CROSSED

    /**
     * Cells of areas your own walls have closed into a finished galaxy: the area holds exactly one dot,
     * lies around it symmetrically and has no wall inside. Computed from your marks only. -1 = not finished.
     */
    fun finishedAreas(): IntArray {
        val g = geometry
        val cells = n * n
        val area = IntArray(cells) { -1 }
        var count = 0
        val stack = ArrayDeque<Int>()
        for (s in 0 until cells) {
            if (area[s] >= 0) continue
            area[s] = count
            stack.addLast(s)
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                val es = g.neighbourEdges[c]
                for (k in es.indices) {
                    val nb = g.neighbours[c][k]
                    if (area[nb] < 0 && marks[es[k]] != Mark.FILLED) { area[nb] = count; stack.addLast(nb) }
                }
            }
            count++
        }
        val dotsIn = IntArray(count)
        val mirrored = BooleanArray(count) { true }
        for (d in 0 until g.count) {
            val a = area[g.own[d][0]]
            if (g.own[d].any { area[it] != a }) continue
            dotsIn[a]++
            for (c in 0 until cells) if (area[c] == a) {
                val m = g.mirror(c, d)
                if (m < 0 || area[m] != a) mirrored[a] = false
            }
        }
        // A dot that touches two areas spoils both: count it for every area it touches.
        val touched = IntArray(count)
        for (d in 0 until g.count) for (a in g.own[d].map { area[it] }.toSet()) touched[a]++
        // A wall between two cells of one area (a wall that doesn't close anything off) means it isn't finished.
        val inner = BooleanArray(count)
        for (e in 0 until grid.edges) if (marks[e] == Mark.FILLED) {
            val (a, b) = grid.edgeCells[e].let { it[0] to it[1] }
            if (a >= 0 && b >= 0 && area[a] == area[b]) inner[area[a]] = true
        }
        return IntArray(cells) { c ->
            val a = area[c]
            if (dotsIn[a] == 1 && touched[a] == 1 && mirrored[a] && !inner[a]) a else -1
        }
    }

    fun set(e: Int, mark: Mark): GalaxiesState {
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

    fun hint(): GalaxiesState {
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

    /**
     * A wrong mark first; else the open edge the logic settles soonest, preferring walls. The difficulty's
     * level first; a game whose marks leave that level nothing to settle falls back to the next level up.
     */
    fun pickHintEdge(): Int? {
        marks.indices.firstOrNull { isWrong(it) }?.let { return it }
        val g = geometry
        val start = IntArray(marks.size) { e ->
            when {
                marks[e] == Mark.FILLED -> SHADED
                marks[e] == Mark.CROSSED -> WHITE
                else -> UNKNOWN
            }
        }
        for (level in difficulty.ordinal..3) {
            val result = GalaxiesLogic(g, level).run(start)
            val open = marks.indices.filter { marks[it] == Mark.NONE && result.round[it] > 0 }
            val soonest = open.minOfOrNull { result.round[it] } ?: continue
            val now = open.filter { result.round[it] == soonest }
            return now.firstOrNull { solution[it] } ?: now.first()
        }
        return marks.indices.firstOrNull { marks[it] == Mark.NONE && solution[it] }
            ?: marks.indices.firstOrNull { marks[it] == Mark.NONE }
    }

    private fun checkSolved(): GalaxiesState =
        if (isSolved) copy(completed = true, hintEdge = null, crossMode = false) else this

    companion object {
        @Volatile private var cached: GalaxyGrid? = null

        /** The geometry of a board; the last one is kept, as the board asks for it on every frame. */
        private fun geometryOf(n: Int, dots: List<Int>): GalaxyGrid {
            cached?.let { if (it.n == n && it.dots.asList() == dots) return it }
            return GalaxyGrid(n, dots.toIntArray()).also { cached = it }
        }

        fun fromPuzzle(p: GalaxiesPuzzle): GalaxiesState {
            val g = GalaxyGrid(p.n, p.dots)
            val border = List(p.solution.size) { e -> g.grid.edgeCells[e].any { it < 0 } }
            return GalaxiesState(
                difficulty = p.difficulty,
                n = p.n,
                dots = p.dots.toList(),
                solution = p.solution.toList(),
                marks = border.map { if (it) Mark.FILLED else Mark.NONE },
                fixed = border,
            )
        }
    }
}

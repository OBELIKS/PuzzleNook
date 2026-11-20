package com.puzzlenook.slither

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GalaxiesStateTest {
    private val puzzle = GalaxiesGenerator.generate(Difficulty.EASY, Random(3), budgetMs = 2000)

    @Test
    fun borderIsFixedAndDrawingTheWallsSolvesIt() {
        var s = GalaxiesState.fromPuzzle(puzzle)
        for (e in s.solution.indices) if (s.grid.edgeCells[e].any { it < 0 }) {
            assertTrue(s.fixed[e] && s.marks[e] == Mark.FILLED)
        }
        // A fixed edge can't be erased.
        val border = s.solution.indices.first { s.fixed[it] }
        assertEquals(s, s.set(border, Mark.NONE))
        for (e in s.solution.indices) if (s.solution[e]) s = s.set(e, Mark.FILLED)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun wrongMarksAreMistakes() {
        var s = GalaxiesState.fromPuzzle(puzzle)
        val off = s.solution.indices.first { !s.solution[it] }
        val on = s.solution.indices.first { s.solution[it] && !s.fixed[it] }
        s = s.set(off, Mark.FILLED).set(on, Mark.CROSSED)
        assertEquals(2, s.mistakes)
        assertTrue(s.isWrong(off) && s.isWrong(on))
        assertFalse(s.completed)
    }

    @Test
    fun hintsAloneFinishThePuzzleOnEveryLevel() {
        for (d in Difficulty.entries) {
            var s = GalaxiesState.fromPuzzle(GalaxiesGenerator.generate(d, Random(8)))
            var presses = 0
            while (!s.completed && presses++ < 2000) s = s.hint()
            assertTrue("$d", s.completed)
            assertEquals("$d", 0, s.mistakes)
        }
    }

    /** A game whose level settles nothing from the player's marks still gets a hint on the solution path (next level up). */
    @Test
    fun hintFallsBackToAHigherLevel() {
        val expert = GalaxiesGenerator.generate(Difficulty.EXPERT, Random(4))
        var s = GalaxiesState.fromPuzzle(expert).copy(difficulty = Difficulty.EASY)
        var presses = 0
        while (!s.completed && presses++ < 2000) {
            val e = s.pickHintEdge()
            assertTrue("a hint exists", e != null)
            assertEquals("hint is on the solution path", s.solution[e!!], s.correctMark(e) == Mark.FILLED)
            s = s.hint()
        }
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = GalaxiesState.fromPuzzle(puzzle).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
    }

    @Test
    fun errorsOffCostNothingUntilShown() {
        val s0 = GalaxiesState.fromPuzzle(puzzle).withShowWrong(false)
        val off = s0.solution.indices.first { !s0.solution[it] }
        val s1 = s0.set(off, Mark.FILLED)
        assertEquals(0, s1.mistakes)
        assertFalse(s1.shownWrong(off))
        assertEquals(1, s1.withShowWrong(true).mistakes)
    }

    /** The tint comes from the player's walls alone: the same marks give the same areas whatever the solution is. */
    @Test
    fun finishedAreasFollowThePlayersWalls() {
        val start = GalaxiesState.fromPuzzle(puzzle)
        assertTrue(start.finishedAreas().all { it < 0 })
        var s = start
        for (e in s.solution.indices) if (s.solution[e]) s = s.set(e, Mark.FILLED)
        // Everything is tinted once the board is solved (checked before the win resets the marks).
        val tinted = s.copy(completed = false).finishedAreas()
        assertTrue(tinted.all { it >= 0 })
        val blind = s.copy(solution = List(s.solution.size) { false }, completed = false).finishedAreas()
        assertEquals(tinted.toList(), blind.toList())
        // A wall inside a galaxy makes it unfinished.
        val inner = s.solution.indices.first { e ->
            !s.solution[e] && s.grid.edgeCells[e].all { it >= 0 }
        }
        val broken = s.copy(completed = false, marks = s.marks.toMutableList().also { it[inner] = Mark.FILLED }).finishedAreas()
        val cell = s.grid.edgeCells[inner][0]
        assertTrue(broken[cell] < 0)
        // One closed galaxy next to unfinished space is tinted on its own.
        val g = start.geometry
        val one = (0 until g.count).first { g.own[it].size == 1 }
        val c = g.own[one][0]
        var t = start
        for (e in g.grid.cellEdges[c]) t = t.set(e, Mark.FILLED)
        val areas = t.finishedAreas()
        assertTrue(areas[c] >= 0)
        assertEquals(1, areas.count { it >= 0 })
    }
}

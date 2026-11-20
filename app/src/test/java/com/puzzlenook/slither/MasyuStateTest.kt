package com.puzzlenook.slither

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MasyuStateTest {
    private val puzzle = MasyuGenerator.generate(Difficulty.EASY, Random(3), budgetMs = 2000)

    @Test
    fun drawingTheLoopSolvesIt() {
        var s = MasyuState.fromPuzzle(puzzle)
        for (e in s.solution.indices) if (s.solution[e]) s = s.set(e, Mark.FILLED)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
        assertTrue((0 until s.n * s.n).all { s.pearls[it] == NO_PEARL || s.pearlDone(it) || s.completed })
    }

    @Test
    fun wrongMarksAreMistakes() {
        var s = MasyuState.fromPuzzle(puzzle)
        val off = s.solution.indices.first { !s.solution[it] }
        val on = s.solution.indices.first { s.solution[it] }
        s = s.set(off, Mark.FILLED).set(on, Mark.CROSSED)
        assertEquals(2, s.mistakes)
        assertTrue(s.isWrong(off) && s.isWrong(on))
        assertFalse(s.completed)
    }

    @Test
    fun hintsAloneFinishThePuzzle() {
        var s = MasyuState.fromPuzzle(puzzle)
        var presses = 0
        while (!s.completed && presses++ < 1000) s = s.hint()
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = MasyuState.fromPuzzle(puzzle).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
    }

    @Test
    fun errorsOffCostNothingUntilShown() {
        val s0 = MasyuState.fromPuzzle(puzzle).withShowWrong(false)
        val off = s0.solution.indices.first { !s0.solution[it] }
        val s1 = s0.set(off, Mark.FILLED)
        assertEquals(0, s1.mistakes)
        assertFalse(s1.shownWrong(off))
        assertEquals(1, s1.withShowWrong(true).mistakes)
    }

    @Test
    fun pearlRuleViolationsFollowThePlayersLines() {
        val g = MasyuGrid(puzzle.n)
        val black = puzzle.pearls.indices.firstOrNull { puzzle.pearls[it] == BLACK_PEARL } ?: return
        // Two vertical lines through a black pearl go straight: broken, whatever the solution is.
        val up = g.e(black, 0)
        val down = g.e(black, 1)
        if (up < 0 || down < 0) return
        val s = MasyuState.fromPuzzle(puzzle).set(up, Mark.FILLED).set(down, Mark.FILLED)
        assertTrue(s.pearlBroken(black))
    }
}

package com.puzzlenook.slither

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlitherStateTest {
    private val puzzle = SlitherGenerator.generate(Difficulty.EASY, Random(3), budgetMs = 2000)

    @Test
    fun drawingTheLoopSolvesIt() {
        var s = SlitherState.fromPuzzle(puzzle)
        for (e in s.solution.indices) if (s.solution[e]) s = s.set(e, Mark.FILLED)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun wrongMarksAreMistakes() {
        var s = SlitherState.fromPuzzle(puzzle)
        val off = s.solution.indices.first { !s.solution[it] }
        val on = s.solution.indices.first { s.solution[it] }
        s = s.set(off, Mark.FILLED).set(on, Mark.CROSSED)
        assertEquals(2, s.mistakes)
        assertTrue(s.isWrong(off) && s.isWrong(on))
        assertFalse(s.completed)
    }

    @Test
    fun hintsAloneFinishThePuzzle() {
        var s = SlitherState.fromPuzzle(puzzle)
        var presses = 0
        while (!s.completed && presses++ < 1000) s = s.hint()
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = SlitherState.fromPuzzle(puzzle).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
        var r = SlitherState.fromPuzzle(puzzle).copy(help = HelpSettings(risingCost = true))
        repeat(4) { r = r.hint() }
        assertEquals(6, r.hintHalfPoints)
    }

    @Test
    fun errorsSeenFollowsMovesWhileErrorsAreOn() {
        val e = puzzle.let { SlitherState.fromPuzzle(it) }.let { s -> s.solution.indices.first { s.solution[it] } }
        val s0 = SlitherState.fromPuzzle(puzzle)
        assertFalse(s0.errorsSeen)
        assertTrue(s0.set(e, Mark.FILLED).errorsSeen)
        val off = s0.withShowWrong(false)
        assertFalse(off.set(e, Mark.FILLED).errorsSeen)
        assertTrue(off.set(e, Mark.FILLED).withShowWrong(true).errorsSeen)
    }}

package com.puzzlenook.region

import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShikakuStateTest {
    private val puzzle = ShikakuGenerator.generate(Difficulty.EASY, Random(3), budgetMs = 2000)

    /** A rectangle that isn't in the solution (the first solution rectangle with one row cut off, or one cell). */
    private fun wrongRect(s: ShikakuState): Rect {
        val n = s.n
        for (a in 0 until n * n) for (b in a until n * n) {
            val r = Rect.between(a, b, n)
            if (r.area in 2..4 && r !in s.solution) return r
        }
        error("none")
    }

    @Test
    fun drawingTheSolutionSolvesIt() {
        var s = ShikakuState.fromPuzzle(puzzle)
        for (r in s.solution) s = s.place(r)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun wrongRectanglesAreMistakesAndReplaceOverlaps() {
        var s = ShikakuState.fromPuzzle(puzzle)
        val w = wrongRect(s)
        s = s.place(w)
        assertEquals(1, s.mistakes)
        assertTrue(s.isWrong(w) && s.shownWrong(w))
        // Drawing over it replaces it.
        val right = s.solution.first { it.overlaps(w) }
        s = s.place(right)
        assertFalse(s.rects.any { it == w })
        assertTrue(s.rects.any { it == right })
        assertFalse(s.completed)
        // A tap on it removes it again.
        s = s.remove(right.r0 * s.n + right.c0)
        assertTrue(s.rects.isEmpty())
    }

    @Test
    fun hintsAloneFinishThePuzzle() {
        var s = ShikakuState.fromPuzzle(puzzle)
        var presses = 0
        while (!s.completed && presses++ < 1000) s = s.hint()
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun hintRevealsAreLockedAndFirstHintPointsAtAClue() {
        var s = ShikakuState.fromPuzzle(puzzle).hint()
        val clue = s.activeHintClue
        assertNotNull(clue)
        assertTrue(s.numbers[clue!!] > 0)
        s = s.hint()
        val r = s.solutionOf(clue)
        assertTrue(s.isFixed(r))
        // A fixed rectangle can't be removed or drawn over.
        assertTrue(s.remove(clue) === s)
        assertTrue(s.place(Rect(r.r0, r.c0, r.r0, r.c0)) === s)
        // Undo keeps it.
        assertTrue(s.restore(emptyList()).rects.any { it == r })
    }

    @Test
    fun aWrongRectangleIsHintedFirst() {
        var s = ShikakuState.fromPuzzle(puzzle)
        val w = wrongRect(s)
        s = s.place(w)
        val clue = s.pickHintClue()!!
        assertTrue(s.solutionOf(clue).overlaps(w))
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = ShikakuState.fromPuzzle(puzzle).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
    }

    @Test
    fun errorsOffCostNothingUntilShown() {
        val s0 = ShikakuState.fromPuzzle(puzzle).withShowWrong(false)
        val w = wrongRect(s0)
        val s1 = s0.place(w)
        assertEquals(0, s1.mistakes)
        assertFalse(s1.shownWrong(w))
        assertEquals(1, s1.withShowWrong(true).mistakes)
    }

    @Test
    fun cluesFadeFromYourOwnRectangles() {
        var s = ShikakuState.fromPuzzle(puzzle)
        val r = s.solution.first()
        val clue = r.cells(s.n).first { s.numbers[it] > 0 }
        assertFalse(s.clueDone(clue))
        s = s.place(r)
        assertTrue(s.clueDone(clue))
    }

    @Test
    fun savedGameRoundTrips() {
        var s = ShikakuState.fromPuzzle(puzzle).hint().hint()
        s = s.place(wrongRect(s))
        val back = ShikakuState.fromJson(s.toJson(), HelpSettings(), 10)!!
        assertEquals(s.rects, back.rects)
        assertEquals(s.fixed, back.fixed)
        assertEquals(s.solution, back.solution)
        assertEquals(s.numbers, back.numbers)
        assertEquals(s.mistakes, back.mistakes)
    }
}

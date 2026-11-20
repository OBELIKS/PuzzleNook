package com.puzzlenook.hidato

import com.puzzlenook.ink.InkPoint
import com.puzzlenook.ink.InkStroke
import com.puzzlenook.ink.splitDigits
import com.puzzlenook.ink.toNumber
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HidatoStateTest {
    private val puzzle = HidatoGenerator.generate(Difficulty.MEDIUM, Random(9), budgetMs = 2000)

    @Test
    fun fillingTheSolutionWins() {
        var s = HidatoState.fromPuzzle(puzzle)
        for (i in s.values.indices) if (s.values[i] == 0) s = s.enter(i, s.solution[i])
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun wrongNumbersAreMistakes() {
        var s = HidatoState.fromPuzzle(puzzle)
        val i = s.values.indexOfFirst { it == 0 }
        val wrong = if (s.solution[i] == 2) 3 else 2
        s = s.enter(i, wrong)
        assertEquals(1, s.mistakes)
        assertTrue(s.isWrong(i))
        assertTrue(wrong !in s.missing || s.solution.indexOf(wrong) != i)
    }

    @Test
    fun hintsAloneFinishThePuzzle() {
        var s = HidatoState.fromPuzzle(puzzle)
        var presses = 0
        while (!s.completed && presses++ < 200) s = s.hint()
        assertTrue(s.completed)
    }

    private fun stroke(x0: Float, x1: Float) = InkStroke(listOf(InkPoint(x0, 10f), InkPoint(x1, 90f)))

    @Test
    fun handwritingSplitsIntoDigitsLeftToRight() {
        // "1" then "4" written as two strokes (the 4's strokes overlap each other).
        val one = stroke(10f, 12f)
        val fourA = stroke(40f, 55f)
        val fourB = stroke(60f, 62f)
        val fourC = InkStroke(listOf(InkPoint(38f, 60f), InkPoint(70f, 60f)))
        val groups = splitDigits(listOf(fourB, one, fourA, fourC))
        assertEquals(2, groups.size)
        assertEquals(listOf(one), groups[0])
        assertEquals(3, groups[1].size)
    }

    @Test
    fun recognisedTextReadsAsNumbers() {
        assertEquals(42, toNumber("42"))
        assertEquals(10, toNumber("1O"))
        assertEquals(7, toNumber(" 7 "))
        assertNull(toNumber("123"))
        assertNull(toNumber("ab"))
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = HidatoState.fromPuzzle(puzzle).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
        var r = HidatoState.fromPuzzle(puzzle).copy(help = HelpSettings(risingCost = true))
        repeat(4) { r = r.hint() }
        assertEquals(6, r.hintHalfPoints)
    }
}

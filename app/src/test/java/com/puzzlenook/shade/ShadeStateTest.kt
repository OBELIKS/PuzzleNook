package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShadeStateTest {
    private val start = ShadeState.fromPuzzle(StarBattleGenerator.generate(Difficulty.EASY, Random(3)))
    private val n = start.size

    @Test
    fun correctStarDotsItsNeighbours() {
        val star = start.solution.indices.first { start.solution[it] && !start.fixed[it] }
        val s = start.set(star, Mark.FILLED)
        assertEquals(0, s.mistakes)
        for (j in king(star, n)) assertTrue("cell $j", s.marks[j] == Mark.CROSSED || s.fixed[j])
        // Dots are never wrong.
        assertTrue(s.marks.indices.none { s.isWrong(it) })
    }

    @Test
    fun givenStarsStartWithDots() {
        for (i in start.marks.indices) if (start.fixed[i] && start.solution[i]) {
            for (j in king(i, n)) assertEquals(Mark.CROSSED, start.marks[j])
        }
    }

    @Test
    fun wrongStarIsAMistakeAndDotsNothing() {
        val wrong = start.solution.indices.first { !start.solution[it] && start.marks[it] == Mark.NONE }
        val s = start.set(wrong, Mark.FILLED)
        assertEquals(1, s.mistakes)
        for (j in king(wrong, n)) assertEquals(start.marks[j], s.marks[j])
    }

    @Test
    fun takuzuNeedsBothColours() {
        var s = ShadeState.fromPuzzle(TakuzuGenerator.generate(Difficulty.EASY, Random(3)))
        for (i in s.solution.indices) if (s.solution[i] && !s.fixed[i]) s = s.set(i, Mark.FILLED)
        assertTrue("only black placed", !s.completed)
        val whites = s.solution.indices.filter { !s.solution[it] && !s.fixed[it] }
        for (i in whites.dropLast(1)) s = s.set(i, Mark.CROSSED)
        assertTrue("one white missing", !s.completed)
        s = s.set(whites.last(), Mark.CROSSED)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun mosaicClueDoneUsesMarksOnly() {
        val p = MosaicGenerator.generate(Difficulty.EASY, Random(3))
        var s = ShadeState.fromPuzzle(p)
        val clue = s.numbers.indices.first { s.numbers[it] >= 0 }
        assertTrue("nothing is finished at the start", !s.clueDone(clue) || (king(clue, s.size) + clue).all { s.marks[it] != Mark.NONE })
        s = s.copy(marks = s.marks.map { Mark.NONE })
        assertTrue(!s.clueDone(clue))
        // Marking the whole block from the solution finishes the clue; a no-clue cell never is.
        for (j in king(clue, s.size) + clue) s = s.set(j, if (s.solution[j]) Mark.FILLED else Mark.CROSSED)
        assertTrue(s.clueDone(clue))
        s.numbers.indices.firstOrNull { s.numbers[it] < 0 }?.let { assertTrue(!s.clueDone(it)) }
    }

    @Test
    fun placingEveryStarSolvesThePuzzle() {
        var s = start
        for (i in s.solution.indices) if (s.solution[i]) s = s.set(i, Mark.FILLED)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun hintLimitRisingCostAndCooldown() {
        val limited = start.copy(help = HelpSettings(hintLimit = 1)).hint()
        assertEquals(1, limited.hintsUsed)
        val revealed = limited.hint() // reveal still works
        assertEquals(null, revealed.hintCell)
        assertEquals(2, revealed.hintHalfPoints)
        assertEquals(revealed, revealed.hint()) // no second new hint

        var rising = start.copy(help = HelpSettings(risingCost = true))
        repeat(4) { rising = rising.hint() }
        assertEquals(6, rising.hintHalfPoints)

        val cool = start.copy(help = HelpSettings(cooldownSeconds = 30), elapsedSeconds = 5).hint().hint()
        assertEquals(cool.copy(elapsedSeconds = 15), cool.copy(elapsedSeconds = 15).hint())
        assertEquals(2, cool.copy(elapsedSeconds = 40).hint().hintsUsed)
    }

    @Test
    fun errorsSeenFollowsMovesWhileErrorsAreOn() {
        val i = start.solution.indices.first { start.solution[it] && !start.fixed[it] }
        assertFalse(start.errorsSeen)
        assertTrue(start.set(i, Mark.FILLED).errorsSeen)
        var s = start.withShowWrong(false)
        assertFalse(s.errorsSeen)
        s = s.set(i, Mark.FILLED).set(i, Mark.NONE)
        assertFalse(s.errorsSeen)
        assertTrue(s.withShowWrong(true).errorsSeen)
    }}

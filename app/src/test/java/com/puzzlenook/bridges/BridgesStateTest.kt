package com.puzzlenook.bridges

import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BridgesStateTest {
    private val puzzle = BridgesGenerator.generate(Difficulty.MEDIUM, Random(5), budgetMs = 2000)
    private fun fresh() = BridgesState.fromPuzzle(puzzle)

    /** A slot without a bridge in the solution. */
    private fun emptySlot(s: BridgesState) = s.solution.indexOfFirst { it == 0 }

    @Test
    fun cyclingToTheSolutionSolvesIt() {
        var s = fresh()
        for (i in s.solution.indices) repeat(s.solution[i]) { s = s.cycle(i) }
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun cycleGoesOneTwoNone() {
        var s = fresh()
        val i = s.solution.indexOfFirst { it == 2 }.takeIf { it >= 0 } ?: 0
        s = s.cycle(i).cycle(i)
        assertEquals(2, s.bridges[i])
        s = s.cycle(i)
        assertEquals(0, s.bridges[i])
    }

    @Test
    fun aBridgeTheSolutionLacksIsOneMistakeAndShowsWrong() {
        var s = fresh()
        val i = emptySlot(s)
        assertTrue("puzzle has an empty slot", i >= 0)
        s = s.cycle(i)
        assertEquals(1, s.mistakes)
        assertTrue(s.shownWrong(i))
        s = s.cycle(i)
        assertEquals(1, s.mistakes)
        s = s.cycle(i)
        assertEquals(1, s.mistakes)
        assertFalse(s.isWrong(i))
    }

    @Test
    fun aMoveThatWouldCrossDoesNothing() {
        val s0 = fresh()
        val layout = s0.layout
        val a = layout.crossing.indices.first { layout.crossing[it].isNotEmpty() }
        val b = layout.crossing[a][0]
        val s1 = s0.cycle(a)
        assertEquals(1, s1.bridges[a])
        assertTrue(s1.cycle(b) === s1)
        // Taking the first one away frees the second.
        val s2 = s1.cycle(a).cycle(a)
        assertEquals(0, s2.bridges[a])
        assertEquals(1, s2.cycle(b).bridges[b])
    }

    @Test
    fun hintsAloneFinishThePuzzle() {
        var s = fresh()
        var presses = 0
        while (!s.completed && presses++ < 1000) s = s.hint()
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun hintRevealsAreLocked() {
        var s = fresh().hint()
        val slot = s.activeHintSlot
        assertNotNull(slot)
        s = s.hint()
        assertTrue(slot!! in s.fixed)
        assertEquals(s.solution[slot], s.bridges[slot])
        assertTrue(s.cycle(slot) === s)
        // Undo keeps it.
        assertEquals(s.solution[slot], s.restore(List(s.solution.size) { 0 }).bridges[slot])
    }

    @Test
    fun aWrongBridgeIsHintedFirst() {
        var s = fresh()
        val i = emptySlot(s)
        s = s.cycle(i)
        assertEquals(i, s.pickHintSlot())
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = fresh().copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
    }

    @Test
    fun errorsOffCostNothingUntilShown() {
        val s0 = fresh().withShowWrong(false)
        val i = emptySlot(s0)
        val s1 = s0.cycle(i)
        assertEquals(0, s1.mistakes)
        assertFalse(s1.shownWrong(i))
        assertEquals(1, s1.withShowWrong(true).mistakes)
    }

    @Test
    fun islandsFadeFromYourOwnBridges() {
        var s = fresh()
        val slot = s.layout.slots[0]
        assertFalse(s.islandDone(slot.ia))
        // Fill island ia with its solution bridges.
        for (t in s.layout.slotsAt[slot.ia]) repeat(s.solution[t]) { s = s.cycle(t) }
        assertTrue(s.islandDone(slot.ia))
        assertFalse(s.islandOver(slot.ia))
    }

    @Test
    fun savedGameRoundTrips() {
        var s = fresh().hint().hint()
        s = s.cycle(emptySlot(s))
        val back = BridgesState.fromJson(s.toJson(), HelpSettings(), 10)!!
        assertEquals(s.bridges, back.bridges)
        assertEquals(s.fixed, back.fixed)
        assertEquals(s.solution, back.solution)
        assertEquals(s.numbers, back.numbers)
        assertEquals(s.mistakes, back.mistakes)
    }
}

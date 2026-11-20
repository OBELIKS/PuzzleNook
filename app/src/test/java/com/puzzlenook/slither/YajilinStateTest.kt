package com.puzzlenook.slither

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YajilinStateTest {
    private val puzzle = YajilinGenerator.generate(Difficulty.EASY, Random(3), budgetMs = 2000)

    @Test
    fun drawingTheLoopAndShadingSolvesIt() {
        var s = YajilinState.fromPuzzle(puzzle)
        for (x in s.solution.indices) if (s.solution[x]) s = s.set(x, Mark.FILLED)
        assertTrue(s.completed)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun clueCellsAndTheirEdgesAreLocked() {
        val s = YajilinState.fromPuzzle(puzzle)
        val clue = puzzle.clues.indices.first { puzzle.clues[it] >= 0 }
        assertTrue(s.fixed[s.edges + clue])
        val g = MasyuGrid(puzzle.n)
        for (e in g.edgesOf(clue)) {
            assertTrue(s.fixed[e])
            assertEquals(s, s.set(e, Mark.FILLED))
        }
        assertEquals(s, s.set(s.edges + clue, Mark.FILLED))
    }

    @Test
    fun wrongMarksAreMistakes() {
        var s = YajilinState.fromPuzzle(puzzle)
        val free = s.solution.indices.filter { !s.fixed[it] }
        val off = free.first { !s.solution[it] }
        val on = free.first { s.solution[it] }
        s = s.set(off, Mark.FILLED).set(on, Mark.CROSSED)
        assertEquals(2, s.mistakes)
        assertTrue(s.isWrong(off) && s.isWrong(on))
        assertFalse(s.completed)
    }

    @Test
    fun hintsAloneFinishThePuzzle() {
        for (d in Difficulty.entries) {
            val p = YajilinGenerator.generate(d, Random(4), budgetMs = 2000)
            var s = YajilinState.fromPuzzle(p)
            var presses = 0
            while (!s.completed && presses++ < 2000) s = s.hint()
            assertTrue("$d", s.completed)
            assertEquals(0, s.mistakes)
        }
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = YajilinState.fromPuzzle(puzzle).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
    }

    @Test
    fun errorsOffCostNothingUntilShown() {
        val s0 = YajilinState.fromPuzzle(puzzle).withShowWrong(false)
        val off = s0.solution.indices.first { !s0.fixed[it] && !s0.solution[it] }
        val s1 = s0.set(off, Mark.FILLED)
        assertEquals(0, s1.mistakes)
        assertFalse(s1.shownWrong(off))
        assertEquals(1, s1.withShowWrong(true).mistakes)
    }

    @Test
    fun ruleViolationsFollowThePlayersMarks() {
        var s = YajilinState.fromPuzzle(puzzle)
        val n = puzzle.n
        // Two shaded cells side by side clash, whatever the solution is.
        val a = (0 until n * n - 1).first { puzzle.clues[it] < 0 && puzzle.clues[it + 1] < 0 && it % n < n - 1 }
        s = s.set(s.edges + a, Mark.FILLED).set(s.edges + a + 1, Mark.FILLED)
        assertTrue(s.shadeClash(a) && s.shadeClash(a + 1))
        // Overfilling an arrow's ray shows as too many; filling it exactly makes the clue done.
        val fresh = YajilinState.fromPuzzle(puzzle)
        val clue = puzzle.clues.indices.first { puzzle.clues[it] >= 0 && yajilinCount(puzzle.clues[it]) == 0 }
        assertTrue(fresh.clueDone(clue))
        val ray = yajilinRay(MasyuGrid(n), clue, yajilinDir(puzzle.clues[clue]))
        val cell = ray.first { puzzle.clues[it] < 0 }
        val over = fresh.set(fresh.edges + cell, Mark.FILLED)
        assertTrue(over.clueOver(clue) && !over.clueDone(clue))
    }

    /** When the game's own level settles nothing from the player's marks, the hint comes from a higher level and is still right. */
    @Test
    fun hintFallsBackToTheNextLevel() {
        val p = YajilinGenerator.generate(Difficulty.EXPERT, Random(9), budgetMs = 2000)
        val g = MasyuGrid(p.n)
        val size = g.grid.edges + p.n * p.n
        val l0 = YajilinLogic(g, p.clues, 0).run(IntArray(size) { UNKNOWN })
        var s = YajilinState.fromPuzzle(p).copy(difficulty = Difficulty.EASY)
        // The player has everything level 0 can find: it has nothing left to say.
        for (x in 0 until size) if (!s.fixed[x] && l0.state[x] != UNKNOWN) s = s.set(x, if (l0.state[x] == SHADED) Mark.FILLED else Mark.CROSSED)
        assertEquals(0, s.mistakes)
        assertFalse(s.completed)
        val target = s.pickHint()
        assertNotNull(target)
        assertEquals(Mark.NONE, s.marks[target!!])
        val start = IntArray(size) { x -> when (s.marks[x]) { Mark.FILLED -> SHADED; Mark.CROSSED -> WHITE; Mark.NONE -> UNKNOWN } }
        val decided = (1..3).map { YajilinLogic(g, p.clues, it).run(start).state[target] }.first { it != UNKNOWN }
        assertEquals(s.solution[target], decided == SHADED)
    }
}
package com.puzzlenook.nonogram

import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NonogramTest {
    private val U = LineSolver.UNKNOWN

    @Test
    fun cluesCountBlocks() {
        assertEquals(listOf(2, 3), LineSolver.clue(listOf(true, true, false, true, true, true)))
        assertEquals(emptyList<Int>(), LineSolver.clue(listOf(false, false)))
    }

    @Test
    fun lineLogicFindsOverlaps() {
        // A block of 4 in 5 cells must cover the middle three.
        assertArrayEquals(intArrayOf(U, 1, 1, 1, U), LineSolver.solveLine(listOf(4), IntArray(5) { U }))
        // 2 1 in 4 cells has only one placement.
        assertArrayEquals(intArrayOf(1, 1, 0, 1), LineSolver.solveLine(listOf(2, 1), IntArray(4) { U }))
        // Empty clue empties the line; a contradiction gives null.
        assertArrayEquals(intArrayOf(0, 0, 0), LineSolver.solveLine(emptyList(), IntArray(3) { U }))
        assertNull(LineSolver.solveLine(listOf(3), intArrayOf(U, 0, U)))
        // Knowing a filled cell pins a block next to it.
        assertArrayEquals(intArrayOf(1, 1, 0, 0, 0), LineSolver.solveLine(listOf(2), intArrayOf(1, U, U, U, U)))
    }

    @Test
    fun generatedPuzzlesSolveByLogicToTheirPicture() {
        for (d in Difficulty.entries) {
            repeat(3) { seed ->
                val start = System.currentTimeMillis()
                val p = NonogramGenerator.generate(d, Random(seed * 7 + d.ordinal))
                val ms = System.currentTimeMillis() - start
                val n = p.size
                println("$d seed $seed: ${n}x$n, ${p.solution.count { it }} filled, ${p.givens.count { it }} givens, $ms ms")
                val s = NonogramState.fromPuzzle(p)
                val grid = IntArray(n * n) { i -> if (p.givens[i]) (if (p.solution[i]) 1 else 0) else U }
                assertTrue(LineSolver.solveGrid(n, n, s.rowClues, s.colClues, grid))
                for (i in 0 until n * n) assertEquals(if (p.solution[i]) 1 else 0, grid[i])
            }
        }
    }

    @Test
    fun mistakesHintsAndSolving() {
        val p = NonogramGenerator.generate(Difficulty.EASY, Random(3))
        var s = NonogramState.fromPuzzle(p)
        val empty = (0 until p.size * p.size).first { !p.solution[it] && !p.givens[it] }
        s = s.set(empty, Mark.FILLED)
        assertEquals(1, s.mistakes)
        assertTrue(s.isWrong(empty))
        // The hint points at the wrong cell first.
        s = s.hint()
        assertEquals(empty, s.hintCell)
        s = s.hint()
        assertEquals(Mark.CROSSED, s.marks[empty])
        while (!s.completed) s = s.hint()
        assertTrue(s.isSolved)
        assertTrue((0 until s.size).all { s.rowDone(it) && s.colDone(it) })
    }

    @Test
    fun hintLimitAndRisingCost() {
        val a = NonogramState.fromPuzzle(NonogramGenerator.generate(Difficulty.EASY, Random(3))).copy(help = HelpSettings(hintLimit = 1, risingCost = true)).hint()
        assertEquals(1, a.hintsUsed)
        val b = a.hint()
        assertEquals(2, b.hintHalfPoints)
        assertEquals(b, b.hint())
        var r = NonogramState.fromPuzzle(NonogramGenerator.generate(Difficulty.EASY, Random(3))).copy(help = HelpSettings(risingCost = true))
        repeat(4) { r = r.hint() }
        assertEquals(6, r.hintHalfPoints)
    }
}

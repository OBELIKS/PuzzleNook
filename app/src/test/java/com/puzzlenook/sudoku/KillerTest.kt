package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KillerTest {

    @Test
    fun comboMaskListsDigitsOfMatchingSums() {
        val all = 0x3FE
        // Two cells adding to 3 can only be 1 + 2.
        assertEquals((1 shl 1) or (1 shl 2), KillerSolver.comboMask(all, 2, 3))
        // 17 in two cells is 8 + 9.
        assertEquals((1 shl 8) or (1 shl 9), KillerSolver.comboMask(all, 2, 17))
        // Without the 9 there is no way to make 17 in two cells.
        assertEquals(0, KillerSolver.comboMask(all and (1 shl 9).inv(), 2, 17))
        assertEquals(all, KillerSolver.comboMask(all, 9, 45))
    }

    @Test
    fun cagesCoverTheGridWithoutRepeatedDigits() {
        repeat(10) { seed ->
            val solution = SudokuSolver.randomSolved(Random(seed))
            val cages = KillerGenerator.makeCages(solution, 5, Random(seed))
            assertEquals((0 until 81).toList(), cages.flatMap { it.cells }.sorted())
            for (cage in cages) {
                assertTrue(cage.cells.size <= 6)
                assertEquals(cage.cells.size, cage.cells.map { solution[it] }.toSet().size)
                assertEquals(cage.sum, cage.cells.sumOf { solution[it] })
            }
            assertEquals(405, cages.sumOf { it.sum })
        }
    }

    @Test
    fun generatedKillerPuzzlesHaveOneSolution() {
        for (d in Difficulty.entries) {
            repeat(3) { seed ->
                val start = System.currentTimeMillis()
                val p = KillerGenerator.generate(d, Random(seed * 17 + d.ordinal))
                val ms = System.currentTimeMillis() - start
                val singles = p.cages.count { it.cells.size == 1 }
                println("$d seed $seed: ${p.cages.size} cages, $singles single, ${p.clueCount} givens, ${ms} ms")
                assertEquals(1, KillerSolver.countSolutions(p.givens, p.cages, nodeLimit = Int.MAX_VALUE))
                assertTrue(KillerSolver.solve(p.givens, p.cages)!!.contentEquals(p.solution))
                assertTrue("$d had ${p.clueCount} givens", p.clueCount <= KillerGenerator.targetGivens(d) + 12)
            }
        }
    }

    @Test
    fun killerHintsSolveThePuzzle() {
        val p = KillerGenerator.generate(Difficulty.HARD, Random(5))
        var s = GameState.fromPuzzle(p)
        // First hint on an empty-ish board should still point at a cell, not give up.
        assertTrue(s.pickHintCell() != null)
        while (!s.completed) s = s.hint()
        assertTrue(s.isSolved)
    }
}

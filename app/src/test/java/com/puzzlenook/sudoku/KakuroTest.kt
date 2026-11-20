package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KakuroTest {

    @Test
    fun generatedKakuroIsValidAndUnique() {
        for (d in Difficulty.entries) {
            repeat(3) { seed ->
                val start = System.currentTimeMillis()
                val p = KakuroGenerator.generate(d, Random(seed * 13 + d.ordinal))
                val ms = System.currentTimeMillis() - start
                val n = p.size
                val blocked = p.blocked!!
                val whites = (0 until n * n).count { !blocked[it] }
                println("$d seed $seed: ${n}x$n, $whites white, ${p.cages.size} runs, ${p.clueCount} givens, $ms ms")
                // Every white cell is in exactly one across and one down run.
                val covered = IntArray(n * n)
                for (cage in p.cages) {
                    assertTrue(cage.cells.size in 2..KakuroGenerator.maxRun(d))
                    assertEquals(cage.cells.size, cage.cells.map { p.solution[it] }.toSet().size)
                    assertEquals(cage.sum, cage.cells.sumOf { p.solution[it] })
                    cage.cells.forEach { covered[it]++ }
                }
                for (i in 0 until n * n) assertEquals(if (blocked[i]) 0 else 2, covered[i])
                val active = (0 until n * n).filter { !blocked[it] }.toIntArray()
                val solver = GridSolver(n * n, active, emptyList(), p.cages)
                assertEquals(1, solver.countSolutions(p.givens, nodeLimit = Int.MAX_VALUE))
            }
        }
    }

    @Test
    fun kakuroGameSolvesWithHintsAndIgnoresClueSquares() {
        val p = KakuroGenerator.generate(Difficulty.MEDIUM, Random(4))
        var s = GameState.fromPuzzle(p, Variant.KAKURO)
        val clue = p.blocked!!.indexOfFirst { it }
        assertEquals(s, s.copy(selected = clue).enter(5).copy(selected = s.selected))
        // Cells in one run are peers; cells far apart are not.
        val run = p.cages.first().cells
        assertTrue(s.isPeer(run[0], run[1]))
        while (!s.completed) s = s.hint()
        assertTrue(s.isSolved)
        assertEquals(0, s.remaining(p.solution.first { it != 0 }))
    }
}

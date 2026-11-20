package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlitherTest {
    @Test(timeout = 600_000)
    fun everyPuzzleSolvesByLogicToItsLoop() {
        for (d in Difficulty.entries) {
            val report = (0 until 5).map { seed ->
                val start = System.currentTimeMillis()
                val p = SlitherGenerator.generate(d, Random(seed + 11))
                val ms = System.currentTimeMillis() - start
                val grid = SlitherGrid(p.n)
                assertTrue(grid.isSingleLoop(p.solution))
                for (i in 0 until p.n * p.n) {
                    if (p.clues[i] >= 0) assertEquals(p.clues[i], grid.cellEdges[i].count { p.solution[it] })
                }
                val result = SlitherLogic(grid, p.clues).run(IntArray(grid.edges) { UNKNOWN })
                assertTrue("$d seed $seed solvable by logic", result.solved)
                for (e in 0 until grid.edges) assertEquals("$d edge $e", p.solution[e], result.state[e] == SHADED)
                "${ms}ms/${p.clues.count { it >= 0 }} clues"
            }
            println("SLITHER ${d.name} ${SlitherGenerator.size(d)}: $report")
        }
    }

    /** A plain search, sharing no code with the logic, finds no second loop on small grids. */
    @Test(timeout = 600_000)
    fun smallPuzzlesAreUnique() {
        for (seed in 0 until 14) {
            val n = when {
                seed < 5 -> 5
                seed < 9 -> 6
                else -> 8
            }
            val p = SlitherGenerator.generate(if (n == 8) Difficulty.EXPERT else Difficulty.HARD, Random(seed), n = n)
            assertEquals("seed $seed", 1, countLoops(p, 2))
        }
    }

    /**
     * Every loop is the border of the squares it encloses, so try every set of inside squares
     * (row by row, checking each number as soon as its four neighbours are known) and count
     * the sets whose border is one loop.
     */
    private fun countLoops(p: SlitherPuzzle, limit: Int): Int {
        val n = p.n
        val g = SlitherGrid(n)
        val inside = BooleanArray(n * n)
        var found = 0
        fun inAt(r: Int, c: Int) = r in 0 until n && c in 0 until n && inside[r * n + c]
        fun clueOk(j: Int): Boolean {
            val clue = p.clues[j]
            if (clue < 0) return true
            val r = j / n
            val c = j % n
            val me = inside[j]
            val sides = listOf(inAt(r - 1, c), inAt(r + 1, c), inAt(r, c - 1), inAt(r, c + 1)).count { it != me }
            return sides == clue
        }
        fun rec(i: Int) {
            if (found >= limit) return
            if (i == n * n) {
                if ((n * (n - 1) until n * n).all(::clueOk) && g.isSingleLoop(g.loopOf(inside))) found++
                return
            }
            for (v in booleanArrayOf(false, true)) {
                inside[i] = v
                // The square above now has all four neighbours decided.
                if (i >= n && !clueOk(i - n)) continue
                // The previous square in the last row too, once its right neighbour is known.
                if (i / n == n - 1 && i % n > 0 && !clueOk(i - 1)) continue
                rec(i + 1)
            }
            inside[i] = false
        }
        rec(0)
        return found
    }
}

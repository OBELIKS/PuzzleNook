package com.puzzlenook.hidato

import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HidatoTest {
    @Test(timeout = 600_000)
    fun everyPuzzleSolvesByLogicToItsPath() {
        for (d in Difficulty.entries) {
            val report = (0 until 6).map { seed ->
                val start = System.currentTimeMillis()
                val p = HidatoGenerator.generate(d, Random(seed + 5))
                val ms = System.currentTimeMillis() - start
                val n = p.n
                // The stored path is a real path.
                val pos = IntArray(n * n + 1)
                p.solution.forEachIndexed { i, v -> pos[v] = i }
                for (k in 1 until n * n) {
                    val a = pos[k]
                    val b = pos[k + 1]
                    assertTrue(kotlin.math.abs(a / n - b / n) <= 1 && kotlin.math.abs(a % n - b % n) <= 1)
                }
                val result = HidatoLogic(n).run(p.givens)
                assertTrue("$d seed $seed solvable by logic", result.solved)
                assertArrayEquals(p.solution, result.board)
                "${ms}ms/${p.givens.count { it != 0 }} of ${n * n}"
            }
            println("HIDATO ${d.name}: $report")
        }
    }

    /** A plain path search, sharing no code with the logic, finds no second solution. */
    @Test(timeout = 600_000)
    fun puzzlesAreUnique() {
        for (seed in 0 until 12) {
            val d = Difficulty.entries[seed % 4]
            val p = HidatoGenerator.generate(d, Random(seed + 100))
            assertEquals("$d seed $seed", 1, countPaths(p, 2))
        }
    }

    private fun countPaths(p: HidatoPuzzle, limit: Int): Int {
        val n = p.n
        val total = n * n
        val givenAt = IntArray(total + 1) { -1 }
        p.givens.forEachIndexed { i, v -> if (v != 0) givenAt[v] = i }
        val nextGiven = IntArray(total + 2) { -1 }
        var next = -1
        for (k in total downTo 1) {
            nextGiven[k] = next
            if (givenAt[k] >= 0) next = k
        }
        val board = p.givens.copyOf()
        var found = 0
        fun adjacent(a: Int, b: Int) = a != b && kotlin.math.abs(a / n - b / n) <= 1 && kotlin.math.abs(a % n - b % n) <= 1
        fun far(a: Int, b: Int) = maxOf(kotlin.math.abs(a / n - b / n), kotlin.math.abs(a % n - b % n))
        fun rec(k: Int, at: Int) {
            if (found >= limit) return
            if (k == total) { found++; return }
            val g = nextGiven[k]
            if (g >= 0 && far(at, givenAt[g]) > g - k) return
            if (givenAt[k + 1] >= 0) {
                if (adjacent(at, givenAt[k + 1])) rec(k + 1, givenAt[k + 1])
                return
            }
            for (c in 0 until total) {
                if (board[c] != 0 || !adjacent(at, c)) continue
                board[c] = k + 1
                rec(k + 1, c)
                board[c] = 0
            }
        }
        rec(1, givenAt[1])
        return found
    }
}

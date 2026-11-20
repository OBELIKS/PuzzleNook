package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NorinoriGenTest {
    /** Brute-force uniqueness on Easy (6x6) and Medium (8x8). */
    @Test(timeout = 300_000) fun norinori() = ShadeCheck.check(Variant.NORINORI, 5, setOf(Difficulty.EASY, Difficulty.MEDIUM))

    /** Regions are connected, every region holds two shaded cells, the solution wins and the save round-trips. */
    @Test(timeout = 300_000) fun stateAndRegions() {
        val p = ShadeCheck.generate(Variant.NORINORI, Difficulty.EASY, 3)
        val n = p.size
        val g = p.regions!!
        for (r in 0..g.max()) {
            val cells = g.indices.filter { g[it] == r }
            assertTrue("region $r has two shaded", cells.count { p.solution[it] } == 2)
            assertTrue("region $r connected", connectedCells(n, cells))
        }
        var s = ShadeState.fromPuzzle(p)
        assertTrue(s.regions != null)
        val hint = s.pickHintCell()!!
        assertFalse(s.fixed[hint])
        // A shaded cell with two shaded neighbours shows a clash from the marks alone.
        val mid = (0 until n * n).first { i -> orth(i, n).size == 4 && !s.fixed[i] && orth(i, n).all { !s.fixed[it] } }
        val three = s.copy(marks = s.marks.toMutableList().also { m -> m[mid] = Mark.FILLED; orth(mid, n).take(2).forEach { m[it] = Mark.FILLED } })
        assertTrue(three.norinoriClash(mid))
        assertFalse(three.copy(showWrong = false).showWrong)
        for (i in 0 until n * n) if (p.solution[i] && !s.fixed[i]) s = s.set(i, Mark.FILLED)
        assertTrue(s.completed)
        assertEquals(s.regions, ShadeState.fromJson(Variant.NORINORI, s.toJson(), HelpSettings(), 10)!!.regions)
    }

    private fun connectedCells(n: Int, cells: List<Int>): Boolean {
        val set = cells.toSet()
        val seen = mutableSetOf(cells.first())
        val stack = ArrayDeque(listOf(cells.first()))
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            for (j in listOfNotNull((c - n).takeIf { c >= n }, (c + n).takeIf { c + n < n * n }, (c - 1).takeIf { c % n > 0 }, (c + 1).takeIf { c % n < n - 1 })) {
                if (j in set && seen.add(j)) stack.addLast(j)
            }
        }
        return seen.size == set.size
    }

    /** Given floors, top-level need on Hard and generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val givens = ArrayList<Int>()
            val sizes = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.NORINORI, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val c = p.givens.count { it }
                givens += c
                sizes += p.regions!!.let { g -> g.max() + 1 }
                assertTrue("$d seed $seed givens $c", c >= NorinoriGenerator.minGivens(d, n * n))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !NorinoriLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, p.regions!!).run(given).solved) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", NorinoriLogic.forLevel(d, n, p.regions!!).run(given).solved)
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("NORINORI $d median=${times[2]}ms max=${times.last()}ms givens=$givens regions=$sizes top=$top/5 solve3=$solveMs")
            if (d == Difficulty.EXPERT) assertTrue("level 3 solve median ${solveMs[2]}", solveMs[2] <= 300)
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            // Expert only prefers its top level (reported above): it is rarely needed (batch 5b).
            if (d == Difficulty.HARD) assertTrue("$d needs top level in $top/5", top >= 3)
        }
    }
}

package com.puzzlenook.region

import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShikakuTest {
    /** Independent rule check: the rectangles tile the grid and each holds one clue equal to its area. */
    private fun validPartition(p: ShikakuPuzzle): Boolean {
        val n = p.n
        val seen = IntArray(n * n)
        for (rect in p.solution) {
            var clues = 0
            var sum = 0
            for (r in rect.r0..rect.r1) for (c in rect.c0..rect.c1) {
                seen[r * n + c]++
                if (p.numbers[r * n + c] > 0) { clues++; sum = p.numbers[r * n + c] }
            }
            if (clues != 1 || sum != (rect.r1 - rect.r0 + 1) * (rect.c1 - rect.c0 + 1)) return false
        }
        return seen.all { it == 1 } && p.numbers.count { it > 0 } == p.solution.size
    }

    /**
     * Exact-cover count without any logic code: the first free cell (reading order) is the top-left of
     * its rectangle; try every size whose area is the one clue inside it.
     */
    private fun countPartitions(n: Int, numbers: IntArray, limit: Int): Int {
        val taken = BooleanArray(n * n)
        var found = 0
        fun rec() {
            if (found >= limit) return
            val cell = taken.indexOfFirst { !it }
            if (cell < 0) { found++; return }
            val r = cell / n
            val c = cell % n
            for (h in 1..n - r) for (w in 1..n - c) {
                var free = true
                var clues = 0
                var area = 0
                for (rr in r until r + h) for (cc in c until c + w) {
                    if (taken[rr * n + cc]) free = false
                    if (numbers[rr * n + cc] > 0) { clues++; area = numbers[rr * n + cc] }
                }
                if (!free || clues != 1 || area != h * w) continue
                for (rr in r until r + h) for (cc in c until c + w) taken[rr * n + cc] = true
                rec()
                for (rr in r until r + h) for (cc in c until c + w) taken[rr * n + cc] = false
            }
        }
        rec()
        return found
    }

    @Test(timeout = 300_000)
    fun everyPuzzleSolvesByLogicToItsPartition() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            val clues = ArrayList<Int>()
            var top = 0
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShikakuGenerator.generate(d, Random(seed + 7))
                times += System.currentTimeMillis() - t
                assertEquals(ShikakuGenerator.size(d), p.n)
                assertTrue("$d seed $seed valid partition", validPartition(p))
                clues += p.solution.size
                val logic = ShikakuLogic.forLevel(d, p.n, p.numbers)
                val t3 = System.currentTimeMillis()
                val result = logic.run()
                if (d == Difficulty.EXPERT) solveMs += System.currentTimeMillis() - t3
                assertTrue("$d seed $seed solvable by logic", result.solved)
                for (rect in p.solution) {
                    val clue = rect.cells(p.n).first { p.numbers[it] > 0 }
                    assertEquals("$d seed $seed clue $clue", rect, result.settled[clue])
                }
                if (d.ordinal >= 2 && !ShikakuLogic.forLevel(Difficulty.entries[d.ordinal - 1], p.n, p.numbers).run().solved) top++
            }
            times.sort()
            solveMs.sort()
            println("SHIKAKU $d ${ShikakuGenerator.size(d)}: median=${times[2]}ms max=${times.last()}ms clues=$clues top=$top/5 solve3=$solveMs")
            if (d == Difficulty.EXPERT) assertTrue("level 3 solve median ${solveMs[2]}", solveMs[2] <= 300)
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 4000 + 1000)
            // Expert only prefers its top level (reported above): random partitions almost never need it (batch 5b).
            if (d == Difficulty.HARD) assertTrue("$d needs its top level in $top/5", top >= 3)
        }
    }

    /** Easy and Medium: a plain exact-cover search finds exactly one partition. */
    @Test(timeout = 300_000)
    fun easyAndMediumAreUnique() {
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
            for (seed in 0 until 5) {
                val p = ShikakuGenerator.generate(d, Random(seed + 40))
                assertEquals("$d seed $seed", 1, countPartitions(p.n, p.numbers, 2))
            }
        }
    }

    @Test fun rectCodesRoundTrip() {
        val r = Rect(3, 0, 11, 7)
        assertEquals(r, Rect.fromCode(r.code))
        assertEquals(Rect(1, 2, 3, 4), Rect.between(3 * 6 + 2, 1 * 6 + 4, 6))
    }
}

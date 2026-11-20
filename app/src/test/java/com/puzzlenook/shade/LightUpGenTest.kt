package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertTrue
import org.junit.Test

class LightUpGenTest {
    @Test(timeout = 300_000) fun lightUp() = ShadeCheck.check(Variant.LIGHT_UP, 5, setOf(Difficulty.EASY))

    /** Walls are locked and blocked; placing exactly the solution's bulbs wins; hints use the logic. */
    @Test(timeout = 300_000) fun stateBlocksWalls() {
        val p = ShadeCheck.generate(Variant.LIGHT_UP, Difficulty.EASY, 3)
        var s = ShadeState.fromPuzzle(p)
        val n = p.size
        for (i in 0 until n * n) if (p.numbers[i] >= 0) {
            assertTrue(s.isBlocked(i) && s.fixed[i])
            assertTrue(s.set(i, com.puzzlenook.nonogram.Mark.FILLED) === s)
        }
        val hint = s.pickHintCell()!!
        assertTrue(p.numbers[hint] < 0)
        for (i in 0 until n * n) if (p.solution[i]) s = s.set(i, com.puzzlenook.nonogram.Mark.FILLED)
        assertTrue(s.completed)
    }

    /** Clue floors, top-level need on Hard and generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val numbered = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.LIGHT_UP, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val walls = p.numbers.count { it >= 0 }
                val clues = p.numbers.count { it in 0..4 }
                numbered += clues
                assertTrue("$d seed $seed clues $clues of $walls", clues >= LightUpGenerator.minClues(d, walls))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !LightUpLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, p.numbers).run(given).solved) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", LightUpLogic.forLevel(d, n, p.numbers).run(given).solved)
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("LIGHTUP $d median=${times[2]}ms max=${times.last()}ms numbered=$numbered top=$top/5 solve3=$solveMs")
            if (d == Difficulty.EXPERT) assertTrue("level 3 solve median ${solveMs[2]}", solveMs[2] <= 300)
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            if (d >= Difficulty.HARD) assertTrue("$d needs top level in $top/5", top >= 3)
        }
    }
}

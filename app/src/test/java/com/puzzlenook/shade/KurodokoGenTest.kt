package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KurodokoGenTest {
    /** Brute-force uniqueness on Easy (6x6) and Medium (7x7). */
    @Test(timeout = 300_000) fun kurodoko() = ShadeCheck.check(Variant.KURODOKO, 5, setOf(Difficulty.EASY, Difficulty.MEDIUM))

    /** Clue cells are locked and blocked, clues fade from the player's marks, the solution wins. */
    @Test(timeout = 300_000) fun stateBlocksClues() {
        val p = ShadeCheck.generate(Variant.KURODOKO, Difficulty.EASY, 3)
        var s = ShadeState.fromPuzzle(p)
        val n = p.size
        for (i in 0 until n * n) if (p.numbers[i] > 0) {
            assertTrue(s.isBlocked(i) && s.fixed[i])
            assertTrue(s.set(i, Mark.FILLED) === s)
        }
        assertFalse((0 until n * n).all { s.kurodokoMet(it) })
        val hint = s.pickHintCell()!!
        assertTrue(p.numbers[hint] <= 0)
        for (i in 0 until n * n) if (p.solution[i] && !s.fixed[i]) s = s.set(i, Mark.FILLED)
        assertTrue(s.completed)
        // Fully shaded the right way, every clue sees its number.
        assertTrue((0 until n * n).all { p.numbers[it] <= 0 || s.kurodokoMet(it) })
        assertEquals(s.numbers, ShadeState.fromJson(Variant.KURODOKO, s.toJson(), HelpSettings(), 10)!!.numbers)
    }

    /** Clue floors, top-level need on Hard and generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val clues = ArrayList<Int>()
            val givens = ArrayList<Int>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.KURODOKO, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val whites = n * n - p.solution.count { it }
                val c = p.numbers.count { it > 0 }
                clues += c
                givens += p.givens.count { it }
                assertTrue("$d seed $seed clues $c of $whites", c >= KurodokoGenerator.minClues(d, whites))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !KurodokoLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, p.numbers).run(given).solved) top++
            }
            times.sort()
            println("KURODOKO $d median=${times[2]}ms max=${times.last()}ms clues=$clues givens=$givens top=$top/5")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            if (d == Difficulty.HARD) assertTrue("Hard needs top level in $top/5", top >= 3)
        }
    }
}

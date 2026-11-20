package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TentsGenTest {
    /** Brute-force uniqueness on Easy, Medium and (cheap enough thanks to the count prunes) Hard. */
    @Test(timeout = 300_000) fun tents() = ShadeCheck.check(Variant.TENTS, 5, setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD))

    /** Trees are locked and blocked; counts fade from the player's marks; the solution wins; counts survive saving. */
    @Test(timeout = 300_000) fun stateWithCounts() {
        val p = ShadeCheck.generate(Variant.TENTS, Difficulty.EASY, 3)
        var s = ShadeState.fromPuzzle(p)
        val n = p.size
        for (i in 0 until n * n) if (p.numbers[i] == TREE) {
            assertTrue(s.isBlocked(i) && s.fixed[i])
            assertTrue(s.set(i, Mark.FILLED) === s)
        }
        assertEquals(p.counts!!.toList(), s.counts)
        val restored = ShadeState.fromJson(Variant.TENTS, s.toJson(), HelpSettings(), 10)!!
        assertEquals(s.counts, restored.counts)
        val hint = s.pickHintCell()!!
        assertTrue(p.numbers[hint] != TREE)
        assertFalse((0 until 2 * n).any { s.countDone(it) && s.lineMarked(it) != s.counts!![it] })
        for (i in 0 until n * n) if (p.solution[i] && !s.fixed[i]) s = s.set(i, Mark.FILLED)
        assertTrue(s.completed)
        // After a win every visible count is met by the marks alone.
        assertTrue((0 until 2 * n).all { s.counts!![it] < 0 || s.countDone(it) })
    }

    /** Clue floors, top-level need on Hard and generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val shown = ArrayList<Int>()
            val givens = ArrayList<Int>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.TENTS, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val clues = p.counts!!.count { it >= 0 }
                shown += clues
                givens += p.givens.count { it }
                assertTrue("$d seed $seed counts $clues", clues >= TentsGenerator.minClues(d, n))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !TentsLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, p.numbers, p.counts).run(given).solved) top++
            }
            times.sort()
            println("TENTS $d median=${times[2]}ms max=${times.last()}ms counts=$shown givens=$givens top=$top/5")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            if (d == Difficulty.HARD) assertTrue("Hard needs top level in $top/5", top >= 3)
        }
    }
}

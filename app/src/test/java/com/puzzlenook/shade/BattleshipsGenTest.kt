package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleshipsGenTest {
    /** Brute-force uniqueness on Easy (6x6). */
    @Test(timeout = 300_000) fun battleships() = ShadeCheck.check(Variant.BATTLESHIPS, 5, setOf(Difficulty.EASY))

    /** Shown cells are locked, the fleet ticks from the player's own marks, the solution wins, the save round-trips. */
    @Test(timeout = 300_000) fun stateAndFleet() {
        val p = ShadeCheck.generate(Variant.BATTLESHIPS, Difficulty.EASY, 3)
        val n = p.size
        var s = ShadeState.fromPuzzle(p)
        assertEquals(p.counts!!.toList(), s.counts)
        for (i in 0 until n * n) assertEquals("shown cell $i locked", p.numbers[i] >= 0, s.fixed[i])
        assertEquals(BattleshipsGenerator.fleet(n), s.fleetStatus().map { it.first })
        // A lone single-cell ship ringed by marked water (or edge) ticks off a length-1 ship; nothing else does.
        val single = (0 until n * n).first { i -> p.solution[i] && !s.fixed[i] && orth(i, n).none { p.solution[it] } }
        var t = s.copy(marks = s.marks.toMutableList().also { m -> m[single] = Mark.FILLED })
        assertEquals(s.fleetStatus().count { it.second }, t.fleetStatus().count { it.second })
        t = t.copy(marks = t.marks.toMutableList().also { m -> for (j in orth(single, n)) m[j] = Mark.CROSSED })
        assertEquals(s.fleetStatus().count { it.second } + 1, t.fleetStatus().count { it.second })
        // A correct ship cell dots its diagonals; a wrong one shows a clash from marks alone.
        val dotted = s.set(single, Mark.FILLED)
        for (j in king(single, n)) if (j / n != single / n && j % n != single % n && !s.fixed[j]) assertEquals(Mark.CROSSED, dotted.marks[j])
        val a = (0 until n * n).first { i -> !s.fixed[i] && i % n < n - 1 && i / n < n - 1 && !s.fixed[i + n + 1] }
        val clash = s.copy(marks = s.marks.toMutableList().also { m -> m[a] = Mark.FILLED; m[a + n + 1] = Mark.FILLED })
        assertTrue(clash.shipClash(a))
        val hint = s.pickHintCell()!!
        assertFalse(s.fixed[hint])
        assertEquals(s.numbers, ShadeState.fromJson(Variant.BATTLESHIPS, s.toJson(), HelpSettings(), 10)!!.numbers)
        for (i in 0 until n * n) if (p.solution[i] && !s.fixed[i]) s = s.set(i, Mark.FILLED)
        assertTrue(s.completed)
        assertTrue(s.fleetStatus().all { it.second })
        assertTrue((0 until 2 * n).all { s.countDone(it) })
    }

    /** No level ever decides a cell against the solution, whatever part of the solution is already marked. */
    @Test(timeout = 300_000) fun logicIsSound() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.BATTLESHIPS, d, 11)
            val n = p.size
            val random = Random(5)
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = IntArray(n * n) { if (random.nextInt(100) < 25) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                val r = BattleshipsLogic.forLevel(level, n, p.numbers, p.counts!!).run(start)
                for (i in 0 until n * n) if (r.state[i] != UNKNOWN) assertEquals("$d level $level cell $i", if (p.solution[i]) SHADED else WHITE, r.state[i])
            }
        }
    }

    /** Given floors, top-level need on Hard and Expert, generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val shown = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.BATTLESHIPS, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val c = p.numbers.count { it >= 0 }
                shown += c
                assertTrue("$d seed $seed shown $c", c >= BattleshipsGenerator.minGivens(d, n * n))
                val given = IntArray(n * n) { UNKNOWN }
                if (d.ordinal >= 2 && !BattleshipsLogic.forLevel(d.ordinal - 1, n, p.numbers, p.counts!!).run(given).solved) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", BattleshipsLogic.forLevel(d, n, p.numbers, p.counts!!).run(given).solved)
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("BATTLESHIPS $d median=${times[2]}ms max=${times.last()}ms shown=$shown top=$top/5 solve3=$solveMs")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            // Hard needs level 2 (level 1 fails), Expert needs level 3 (level 2 fails).
            if (d.ordinal >= 2) assertTrue("$d needs top level in $top/5", top >= 3)
        }
    }
}

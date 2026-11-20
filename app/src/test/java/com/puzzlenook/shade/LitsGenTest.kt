package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LitsGenTest {
    /** Brute-force uniqueness on Easy (6x6) and Medium (8x8). */
    @Test(timeout = 300_000) fun lits() = ShadeCheck.check(Variant.LITS, 5, setOf(Difficulty.EASY, Difficulty.MEDIUM))

    @Test fun shapesIgnoreTurnsAndFlips() {
        val n = 6
        val l = intArrayOf(0, n, 2 * n, 2 * n + 1)
        val turned = intArrayOf(2, n + 2, n + 1, n)          // an L lying down
        val flipped = intArrayOf(1, n + 1, 2 * n + 1, 2 * n) // a J
        val t = intArrayOf(0, 1, 2, n + 1)
        val s = intArrayOf(1, 2, n, n + 1)
        assertEquals(Tetromino.shape(l, n), Tetromino.shape(turned.sortedArray(), n))
        assertEquals(Tetromino.shape(l, n), Tetromino.shape(flipped.sortedArray(), n))
        assertNotEquals(Tetromino.shape(l, n), Tetromino.shape(t, n))
        assertNotEquals(Tetromino.shape(t, n), Tetromino.shape(s, n))
        // Over a whole grid the non-square tetrominoes come in exactly four shapes: L, I, T and S.
        assertEquals(4, Tetromino.placements(n, IntArray(n * n) { it }).map { Tetromino.shape(it, n) }.toSet().size)
    }

    /** Regions are connected and hold a tetromino; clash, saved game and win from the marks. */
    @Test(timeout = 300_000) fun stateAndRegions() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.LITS, d, 3)
            val n = p.size
            val g = p.regions!!
            for (r in 0..g.max()) {
                val cells = g.indices.filter { g[it] == r }
                assertTrue("region $r has at least four cells", cells.size >= 4)
                assertEquals("region $r holds four shaded cells", 4, cells.count { p.solution[it] })
            }
        }
        val p = ShadeCheck.generate(Variant.LITS, Difficulty.EASY, 3)
        val n = p.size
        val g = p.regions!!
        var s = ShadeState.fromPuzzle(p)
        assertEquals(g.toList(), s.regions)
        assertFalse(s.fixed[s.pickHintCell()!!])
        val blank = s.copy(marks = List(n * n) { Mark.NONE })
        // A 2x2 shaded square shows a clash from the marks alone.
        val square = blank.copy(marks = blank.marks.toMutableList().also { m -> for (c in intArrayOf(0, 1, n, n + 1)) m[c] = Mark.FILLED })
        assertTrue(square.litsClash(0) && square.litsClash(n + 1))
        // A region with five shaded cells too.
        val big = (0..g.max()).first { r -> g.count { it == r } >= 5 }
        val five = g.indices.filter { g[it] == big }.take(5)
        val over = blank.copy(marks = blank.marks.toMutableList().also { m -> for (c in five) m[c] = Mark.FILLED })
        assertTrue(five.all { over.litsClash(it) })
        assertFalse(blank.litsClash(0))
        // The solution has no clash anywhere.
        val solved = blank.copy(marks = List(n * n) { if (p.solution[it]) Mark.FILLED else Mark.NONE })
        assertTrue((0 until n * n).none { solved.litsClash(it) })
        val back = ShadeState.fromJson(Variant.LITS, s.toJson(), HelpSettings(), 10)!!
        assertEquals(s.regions, back.regions)
        for (j in 0 until n * n) if (p.solution[j] && !s.fixed[j]) s = s.set(j, Mark.FILLED)
        assertTrue(s.completed)
    }

    /** No level ever decides a cell against the solution, whatever part of the solution is already marked. */
    @Test(timeout = 300_000) fun logicIsSound() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.LITS, d, 11)
            val n = p.size
            val random = Random(5)
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = IntArray(n * n) { if (random.nextInt(100) < 20) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                val r = LitsLogic.forLevel(level, n, p.regions!!).run(start)
                for (i in 0 until n * n) if (r.state[i] != UNKNOWN) assertEquals("$d level $level cell $i", if (p.solution[i]) SHADED else WHITE, r.state[i])
            }
        }
    }

    /** Given floors, top-level need on Hard and Expert, generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val givens = ArrayList<Int>()
            val regionCounts = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.LITS, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val count = p.givens.count { it }
                givens += count
                regionCounts += p.regions!!.max() + 1
                assertTrue("$d seed $seed givens $count", count >= LitsGenerator.minGivens(d, n * n))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !LitsLogic.forLevel(d.ordinal - 1, n, p.regions!!).run(given).solved) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", LitsLogic.forLevel(d, n, p.regions!!).run(given).solved)
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("LITS $d median=${times[2]}ms max=${times.last()}ms givens=$givens regions=$regionCounts top=$top/5 solve3=$solveMs")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            // Hard needs level 2 (level 1 fails), Expert level 3 (level 2 fails).
            if (d.ordinal >= 2) assertTrue("$d needs top level in $top/5", top >= 3)
        }
    }
}

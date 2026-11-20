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

class AquariumGenTest {
    /** Brute-force uniqueness on Easy (6x6); Medium is 8x8, too many tanks for the naive search. */
    @Test(timeout = 300_000) fun aquarium() = ShadeCheck.check(Variant.AQUARIUM, 5, setOf(Difficulty.EASY))

    /** Tanks are connected, each row of a tank is one run, water is flat; counts fade and the solution wins. */
    @Test(timeout = 300_000) fun stateAndTanks() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.AQUARIUM, d, 3)
            val n = p.size
            val g = p.regions!!
            for (t in 0..g.max()) {
                val cells = g.indices.filter { g[it] == t }
                assertTrue("tank $t connected", connectedCells(n, cells))
                for (r in cells.map { it / n }.toSet()) {
                    val cols = cells.filter { it / n == r }.map { it % n }
                    assertEquals("tank $t row $r is one run", cols.max() - cols.min() + 1, cols.size)
                }
                // Water level: once a row holds water, every later row does too.
                val wet = cells.map { it / n }.toSortedSet().map { r -> cells.filter { it / n == r }.all { p.solution[it] } }
                assertTrue("tank $t flat", wet == wet.sorted())
            }
        }
        val p = ShadeCheck.generate(Variant.AQUARIUM, Difficulty.EASY, 3)
        val n = p.size
        var s = ShadeState.fromPuzzle(p)
        assertEquals(p.counts!!.toList(), s.counts)
        assertEquals(p.regions!!.toList(), s.regions)
        val hint = s.pickHintCell()!!
        assertFalse(s.fixed[hint])
        // Water above air in the same tank shows a clash from the marks alone.
        val i = (0 until n * n).first { j -> !s.fixed[j] && j / n < n - 1 && p.regions!![j] == p.regions!![j + n] && !s.fixed[j + n] }
        val clash = s.copy(marks = s.marks.toMutableList().also { m -> m[i] = Mark.FILLED; m[i + n] = Mark.CROSSED })
        assertTrue(clash.aquariumClash(i) && clash.aquariumClash(i + n))
        assertEquals(s.regions, ShadeState.fromJson(Variant.AQUARIUM, s.toJson(), HelpSettings(), 10)!!.regions)
        for (j in 0 until n * n) if (p.solution[j] && !s.fixed[j]) s = s.set(j, Mark.FILLED)
        assertTrue(s.completed)
        assertTrue((0 until 2 * n).all { s.counts!![it] < 0 || s.countDone(it) })
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

    /** No level ever decides a cell against the solution, whatever part of the solution is already marked. */
    @Test(timeout = 300_000) fun logicIsSound() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.AQUARIUM, d, 11)
            val n = p.size
            val random = Random(5)
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = IntArray(n * n) { if (random.nextInt(100) < 20) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                val r = AquariumLogic.forLevel(level, n, p.regions!!, p.counts!!).run(start)
                for (i in 0 until n * n) if (r.state[i] != UNKNOWN) assertEquals("$d level $level cell $i", if (p.solution[i]) SHADED else WHITE, r.state[i])
            }
        }
    }

    /** Count floors, top-level need on Hard and Expert, generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val shown = ArrayList<Int>()
            val givens = ArrayList<Int>()
            val tanks = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.AQUARIUM, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val clues = p.counts!!.count { it >= 0 }
                shown += clues
                givens += p.givens.count { it }
                tanks += p.regions!!.max() + 1
                assertTrue("$d seed $seed counts $clues", clues >= AquariumGenerator.minClues(d, n))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !AquariumLogic.forLevel(d.ordinal - 1, n, p.regions!!, p.counts!!).run(given).solved) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", AquariumLogic.forLevel(d, n, p.regions!!, p.counts!!).run(given).solved)
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("AQUARIUM $d median=${times[2]}ms max=${times.last()}ms counts=$shown givens=$givens tanks=$tanks top=$top/5 solve3=$solveMs")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            // Hard needs level 2 (level 1 fails), Expert needs level 3 (level 2 fails).
            if (d.ordinal >= 2) assertTrue("$d needs top level in $top/5", top >= 3)
        }
    }
}

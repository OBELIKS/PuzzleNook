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

class HeyawakeGenTest {
    /** Brute-force uniqueness on Easy (6x6) and Medium (7x7). */
    @Test(timeout = 300_000) fun heyawake() = ShadeCheck.check(Variant.HEYAWAKE, 5, setOf(Difficulty.EASY, Difficulty.MEDIUM))

    /** Rooms are rectangles with their number in the first cell; clash, fading, saved game and win from the marks. */
    @Test(timeout = 300_000) fun stateAndRooms() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.HEYAWAKE, d, 3)
            val n = p.size
            val g = p.regions!!
            for (room in 0..g.max()) {
                val cells = g.indices.filter { g[it] == room }
                val rows = cells.map { it / n }
                val cols = cells.map { it % n }
                assertEquals("room $room is a rectangle", (rows.max() - rows.min() + 1) * (cols.max() - cols.min() + 1), cells.size)
                assertTrue("room $room has at least two cells", cells.size >= 2)
                assertTrue("only the first cell carries a number", cells.drop(1).all { p.numbers[it] < 0 })
                if (p.numbers[cells.first()] >= 0) assertEquals(cells.count { p.solution[it] }, p.numbers[cells.first()])
            }
        }
        val p = ShadeCheck.generate(Variant.HEYAWAKE, Difficulty.EASY, 3)
        val n = p.size
        var s = ShadeState.fromPuzzle(p)
        assertEquals(p.regions!!.toList(), s.regions)
        assertFalse(s.fixed[s.pickHintCell()!!])
        // Two shaded cells side by side show a clash from the marks alone.
        val i = (0 until n * n).first { j -> j % n < n - 1 && !s.fixed[j] && !s.fixed[j + 1] }
        val clash = s.copy(marks = s.marks.toMutableList().also { m -> m[i] = Mark.FILLED; m[i + 1] = Mark.FILLED })
        assertTrue(clash.heyawakeClash(i) && clash.heyawakeClash(i + 1))
        // A numbered room fades when your shading meets its number, and shows over when it passes it.
        val first = (0 until n * n).first { p.numbers[it] >= 0 }
        val room = (0 until n * n).filter { p.regions!![it] == p.regions!![first] }
        val met = s.copy(marks = s.marks.toMutableList().also { m -> for (c in room) m[c] = if (c in room.filter { p.solution[it] }) Mark.FILLED else Mark.CROSSED })
        assertTrue(met.roomMet(first) && !met.roomOver(first))
        val over = s.copy(marks = s.marks.toMutableList().also { m -> for (c in room.take(p.numbers[first] + 1)) m[c] = Mark.FILLED })
        assertTrue(over.roomOver(first))
        val back = ShadeState.fromJson(Variant.HEYAWAKE, s.toJson(), HelpSettings(), 10)!!
        assertEquals(s.regions, back.regions)
        assertEquals(s.numbers, back.numbers)
        for (j in 0 until n * n) if (p.solution[j] && !s.fixed[j]) s = s.set(j, Mark.FILLED)
        assertTrue(s.completed)
    }

    /** No level ever decides a cell against the solution, whatever part of the solution is already marked. */
    @Test(timeout = 300_000) fun logicIsSound() {
        for (d in Difficulty.entries) {
            val p = ShadeCheck.generate(Variant.HEYAWAKE, d, 11)
            val n = p.size
            val random = Random(5)
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = IntArray(n * n) { if (random.nextInt(100) < 20) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                val r = HeyawakeLogic.forLevel(level, n, p.regions!!, p.numbers).run(start)
                for (i in 0 until n * n) if (r.state[i] != UNKNOWN) assertEquals("$d level $level cell $i", if (p.solution[i]) SHADED else WHITE, r.state[i])
            }
        }
    }

    /** Number floors, top-level need on Hard and Expert, generation times (median <= 1,5 s, max <= budget + 1 s). */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            var top = 0
            val numbers = ArrayList<Int>()
            val rooms = ArrayList<Int>()
            val givens = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = ShadeCheck.generate(Variant.HEYAWAKE, d, seed + 7)
                times += System.currentTimeMillis() - t
                val n = p.size
                val clues = p.numbers.count { it >= 0 }
                numbers += clues
                rooms += p.regions!!.max() + 1
                givens += p.givens.count { it }
                assertTrue("$d seed $seed numbers $clues", clues >= HeyawakeGenerator.minNumbers(d, p.regions!!.max() + 1))
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                if (d.ordinal >= 2 && !HeyawakeLogic.forLevel(d.ordinal - 1, n, p.regions!!, p.numbers).run(given).solved) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", HeyawakeLogic.forLevel(d, n, p.regions!!, p.numbers).run(given).solved)
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("HEYAWAKE $d median=${times[2]}ms max=${times.last()}ms numbers=$numbers rooms=$rooms givens=$givens top=$top/5 solve3=$solveMs")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            // Hard needs level 2 (level 1 fails), Expert level 3 (level 2 fails).
            if (d.ordinal >= 2) assertTrue("$d needs top level in $top/5", top >= 3)
        }
    }
}

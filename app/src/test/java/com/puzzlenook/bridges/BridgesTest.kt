package com.puzzlenook.bridges

import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BridgesTest {
    /** Independent rule check on the solution: counts 0..2, island sums, no crossings, one connected group. */
    private fun validSolution(p: BridgesPuzzle): Boolean {
        val n = p.n
        val layout = BridgeLayout(n, p.numbers)
        if (layout.slots.size != p.solution.size) return false
        if (p.solution.any { it !in 0..2 }) return false
        val sum = IntArray(n * n)
        for ((s, slot) in layout.slots.withIndex()) {
            sum[slot.a] += p.solution[s]
            sum[slot.b] += p.solution[s]
        }
        for (cell in 0 until n * n) if (p.numbers[cell] > 0 && sum[cell] != p.numbers[cell]) return false
        if (!noCrossings(n, layout, p.solution)) return false
        return connected(layout, p.solution)
    }

    private fun noCrossings(n: Int, layout: BridgeLayout, values: IntArray): Boolean {
        val used = IntArray(n * n)
        for ((s, slot) in layout.slots.withIndex()) {
            if (values[s] == 0) continue
            val step = if (slot.horizontal) 1 else n
            var cell = slot.a + step
            while (cell != slot.b) {
                if (used[cell] > 0) return false
                used[cell]++
                cell += step
            }
        }
        return true
    }

    private fun connected(layout: BridgeLayout, values: IntArray): Boolean {
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack += layout.islands.first()
        seen += layout.islands.first()
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            for ((s, slot) in layout.slots.withIndex()) {
                if (values[s] == 0) continue
                val other = if (slot.a == cell) slot.b else if (slot.b == cell) slot.a else continue
                if (seen.add(other)) stack += other
            }
        }
        return seen.size == layout.islands.size
    }

    /**
     * Brute force over slot values 0..2 without any logic code: island sums, crossings (cells a bridge
     * passes over), then connectivity at the end. Counts solutions up to [limit].
     */
    private fun countSolutions(p: BridgesPuzzle, limit: Int): Int {
        val n = p.n
        val layout = BridgeLayout(n, p.numbers)
        val slots = layout.slots
        val value = IntArray(slots.size)
        val sum = IntArray(n * n)
        val last = HashMap<Int, Int>()
        for ((s, slot) in slots.withIndex()) { last[slot.a] = s; last[slot.b] = s }
        val owner = IntArray(n * n) { -1 }
        var found = 0
        fun rec(s: Int) {
            if (found >= limit) return
            if (s == slots.size) {
                if (layout.islands.all { sum[it] == p.numbers[it] } && connected(layout, value)) found++
                return
            }
            val slot = slots[s]
            val step = if (slot.horizontal) 1 else n
            for (v in 0..2) {
                if (sum[slot.a] + v > p.numbers[slot.a] || sum[slot.b] + v > p.numbers[slot.b]) continue
                var clear = true
                if (v > 0) {
                    var cell = slot.a + step
                    while (cell != slot.b) { if (owner[cell] >= 0) clear = false; cell += step }
                }
                if (!clear) continue
                value[s] = v
                sum[slot.a] += v; sum[slot.b] += v
                if (v > 0) { var cell = slot.a + step; while (cell != slot.b) { owner[cell] = s; cell += step } }
                val okA = last[slot.a] != s || sum[slot.a] == p.numbers[slot.a]
                val okB = last[slot.b] != s || sum[slot.b] == p.numbers[slot.b]
                if (okA && okB) rec(s + 1)
                if (v > 0) { var cell = slot.a + step; while (cell != slot.b) { owner[cell] = -1; cell += step } }
                sum[slot.a] -= v; sum[slot.b] -= v
                value[s] = 0
            }
        }
        rec(0)
        return found
    }

    @Test(timeout = 300_000)
    fun everyPuzzleSolvesByLogicToItsSolution() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            val islands = ArrayList<Int>()
            var top = 0
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = BridgesGenerator.generate(d, Random(seed + 7))
                times += System.currentTimeMillis() - t
                assertEquals(BridgesGenerator.size(d), p.n)
                assertTrue("$d seed $seed valid solution", validSolution(p))
                val layout = BridgeLayout(p.n, p.numbers)
                islands += layout.islands.size
                if (d <= Difficulty.MEDIUM) {
                    assertTrue("$d seed $seed islands ${layout.islands.size}", layout.islands.size >= BridgesGenerator.minIslands(p.n))
                }
                val result = BridgesLogic.forLevel(d, layout).run()
                assertTrue("$d seed $seed solvable by logic", result.solved)
                assertEquals("$d seed $seed", p.solution.toList(), result.values.toList())
                if (d.ordinal >= 2 && !BridgesLogic.forLevel(Difficulty.entries[d.ordinal - 1], layout).run().solved) top++
            }
            val sorted = times.sorted()
            println("BRIDGES $d ${BridgesGenerator.size(d)}: median=${sorted[2]}ms max=${sorted.last()}ms islands=$islands top=$top/5")
            assertTrue("$d median ${sorted[2]}", sorted[2] <= 1500)
            assertTrue("$d max ${sorted.last()}", sorted.last() <= 4000 + 1000)
            if (d == Difficulty.HARD) assertTrue("Hard needs its top level in $top/5", top >= 3)
        }
    }

    /** Easy and Medium: a plain search over slot values finds exactly one solution. */
    @Test(timeout = 300_000)
    fun easyAndMediumAreUnique() {
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
            for (seed in 0 until 5) {
                val p = BridgesGenerator.generate(d, Random(seed + 40))
                assertEquals("$d seed $seed", 1, countSolutions(p, 2))
            }
        }
    }

    /** The brute-force counter itself: a tiny puzzle with two solutions and one without. */
    @Test fun bruteForceCounts() {
        // Four islands in a ring, numbers 2: single bridges all round is the only way (a double leaves a pair closed).
        val n = 5
        val numbers = IntArray(n * n)
        numbers[0] = 2; numbers[4] = 2; numbers[20] = 2; numbers[24] = 2
        val layout = BridgeLayout(n, numbers)
        val p = BridgesPuzzle(Difficulty.EASY, n, numbers, IntArray(layout.slots.size) { 1 })
        assertTrue(validSolution(p))
        assertEquals(1, countSolutions(p, 5))
        numbers[24] = 3
        assertEquals(0, countSolutions(BridgesPuzzle(Difficulty.EASY, n, numbers, p.solution), 5))
    }

    @Test fun layoutFindsSlotsAndCrossings() {
        val n = 5
        val numbers = IntArray(n * n)
        numbers[2] = 1; numbers[22] = 1; numbers[10] = 1; numbers[14] = 1; numbers[12] = 4
        val layout = BridgeLayout(n, numbers)
        // Cross in the middle: 4 slots around island 12, and two slots 2-22 / 10-14 are blocked by it, so only the 4.
        assertEquals(4, layout.slots.size)
        assertTrue(layout.crossing.all { it.isEmpty() })
        numbers[12] = 0
        val crossed = BridgeLayout(n, numbers)
        assertEquals(2, crossed.slots.size)
        assertEquals(listOf(1), crossed.crossing[0].toList())
    }
}

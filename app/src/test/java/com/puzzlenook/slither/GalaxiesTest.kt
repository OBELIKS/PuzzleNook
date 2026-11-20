package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GalaxiesTest {
    private class Dot(val x: Int, val y: Int)

    private fun dotsOf(n: Int, dots: IntArray) = dots.map { Dot(it % (2 * n + 1), it / (2 * n + 1)) }

    /** The cells touching a dot, by plain coordinate arithmetic. */
    private fun touching(n: Int, d: Dot): List<Int> {
        val out = ArrayList<Int>()
        for (r in 0 until n) for (c in 0 until n) {
            if (Math.abs(2 * c + 1 - d.x) <= 1 && Math.abs(2 * r + 1 - d.y) <= 1) out += r * n + c
        }
        return out
    }

    /**
     * Independent rule checker. [wall] says whether the edge between neighbouring cells a and b (or the
     * border) is a wall. Returns the area index of each cell, or null if a rule is broken: every area
     * holds exactly one dot, is point-symmetric around it, and no wall runs through a dot.
     */
    private fun areasOf(n: Int, dots: IntArray, solution: BooleanArray): IntArray? {
        val g = SlitherGrid(n)
        fun wallBetween(a: Int, b: Int): Boolean {
            val e = if (b == a + 1) g.v(a / n, a % n + 1) else g.h(a / n + 1, a % n)
            return solution[e]
        }
        // Border must be walls.
        for (e in 0 until g.edges) if (g.edgeCells[e].any { it < 0 } && !solution[e]) return null
        val area = IntArray(n * n) { -1 }
        var count = 0
        for (s in 0 until n * n) {
            if (area[s] >= 0) continue
            val stack = ArrayDeque<Int>()
            stack.addLast(s)
            area[s] = count
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                val r = c / n
                val col = c % n
                if (col + 1 < n && area[c + 1] < 0 && !wallBetween(c, c + 1)) { area[c + 1] = count; stack.addLast(c + 1) }
                if (r + 1 < n && area[c + n] < 0 && !wallBetween(c, c + n)) { area[c + n] = count; stack.addLast(c + n) }
                if (col > 0 && area[c - 1] < 0 && !wallBetween(c - 1, c)) { area[c - 1] = count; stack.addLast(c - 1) }
                if (r > 0 && area[c - n] < 0 && !wallBetween(c - n, c)) { area[c - n] = count; stack.addLast(c - n) }
            }
            count++
        }
        val ds = dotsOf(n, dots)
        val dotsIn = IntArray(count)
        for (d in ds) {
            val t = touching(n, d)
            val a = area[t[0]]
            if (t.any { area[it] != a }) return null
            dotsIn[a]++
            // No wall between two cells touching the dot (its line would run through the dot).
            for (p in t) for (q in t) {
                if (q == p + 1 && p % n + 1 < n && wallBetween(p, q)) return null
                if (q == p + n && wallBetween(p, q)) return null
            }
            for (c in 0 until n * n) if (area[c] == a) {
                val mr = d.y - c / n - 1
                val mc = d.x - c % n - 1
                if (mr !in 0 until n || mc !in 0 until n || area[mr * n + mc] != a) return null
            }
        }
        if (dotsIn.any { it != 1 }) return null
        return area
    }

    /** Counts the dot assignments that make a valid partition (search over cells and dots; no logic code). */
    private fun countSolutions(n: Int, dots: IntArray, limit: Int): Int {
        val ds = dotsOf(n, dots)
        val cells = n * n
        val assign = IntArray(cells) { -1 }
        for ((k, d) in ds.withIndex()) for (c in touching(n, d)) assign[c] = k
        fun mirror(c: Int, d: Dot): Int {
            val mr = d.y - c / n - 1
            val mc = d.x - c % n - 1
            return if (mr in 0 until n && mc in 0 until n) mr * n + mc else -1
        }
        fun connectedAll(): Boolean {
            for (k in ds.indices) {
                val mine = (0 until cells).filter { assign[it] == k }
                val seen = HashSet<Int>()
                val stack = ArrayDeque<Int>()
                stack.addLast(mine[0])
                seen += mine[0]
                while (stack.isNotEmpty()) {
                    val c = stack.removeLast()
                    for (nb in intArrayOf(c - n, c + n, c - 1, c + 1)) {
                        if (nb < 0 || nb >= cells) continue
                        if ((nb == c - 1 && c % n == 0) || (nb == c + 1 && nb % n == 0)) continue
                        if (assign[nb] == k && seen.add(nb)) stack.addLast(nb)
                    }
                }
                if (seen.size != mine.size) return false
            }
            return true
        }
        var found = 0
        fun dfs(from: Int) {
            if (found >= limit) return
            var c = from
            while (c < cells && assign[c] >= 0) c++
            if (c == cells) {
                if (connectedAll()) found++
                return
            }
            for ((k, d) in ds.withIndex()) {
                val m = mirror(c, d)
                if (m < 0 || (assign[m] >= 0 && assign[m] != k)) continue
                val mWas = assign[m]
                assign[c] = k
                assign[m] = k
                dfs(c + 1)
                assign[c] = -1
                assign[m] = mWas
            }
        }
        dfs(0)
        return found
    }

    @Test(timeout = 300_000)
    fun everyPuzzleSolvesByLogicToItsWalls() {
        for (d in Difficulty.entries) {
            val n = GalaxiesGenerator.size(d)
            val times = mutableListOf<Long>()
            val counts = mutableListOf<Int>()
            var top = 0
            for (seed in 0 until 5) {
                val start = System.currentTimeMillis()
                val p = GalaxiesGenerator.generate(d, Random(seed + 11))
                times += System.currentTimeMillis() - start
                assertTrue("$d seed $seed dots ${p.dots.size}", p.dots.size <= MAX_GALAXY_DOTS)
                assertTrue("$d seed $seed rules", areasOf(n, p.dots, p.solution) != null)
                counts += p.dots.size
                if (d == Difficulty.EASY || d == Difficulty.MEDIUM) {
                    assertTrue("$d seed $seed has ${p.dots.size} dots", p.dots.size >= GalaxiesGenerator.minClues(d, n))
                }
                val gg = GalaxyGrid(n, p.dots)
                val result = GalaxiesLogic.forLevel(d, gg).run(gg.startEdges())
                assertTrue("$d seed $seed solvable by logic", result.solved)
                for (e in 0 until gg.grid.edges) assertEquals("$d seed $seed edge $e", p.solution[e], result.state[e] == SHADED)
                if (d.ordinal >= 2 && !GalaxiesLogic.forLevel(Difficulty.entries[d.ordinal - 1], gg).run(gg.startEdges()).solved) top++
            }
            val sorted = times.sorted()
            println("GALAXIES ${d.name} $n: median ${sorted[2]} ms, max ${sorted.last()} ms, dots $counts, top-level $top/5")
            assertTrue("$d median ${sorted[2]} ms", sorted[2] <= 1500)
            assertTrue("$d max ${sorted.last()} ms", sorted.last() <= 4000 + 1000)
            if (d == Difficulty.HARD) assertTrue("Hard needs level 2 in $top/5", top >= 3)
        }
    }

    /** Report only: 20 more seeds for Hard (level 1 fails) and Expert (level 2 fails), and the level 3 solve time of Expert. */
    @Test(timeout = 300_000)
    fun topLevelReport() {
        for (d in listOf(Difficulty.HARD, Difficulty.EXPERT)) {
            var top = 0
            val ms = mutableListOf<Long>()
            val solveMs = mutableListOf<Long>()
            for (seed in 100 until 120) {
                val t = System.currentTimeMillis()
                val p = GalaxiesGenerator.generate(d, Random(seed))
                ms += System.currentTimeMillis() - t
                val gg = GalaxyGrid(p.n, p.dots)
                val t2 = System.currentTimeMillis()
                assertTrue(GalaxiesLogic.forLevel(d, gg).run(gg.startEdges()).solved)
                solveMs += System.currentTimeMillis() - t2
                if (!GalaxiesLogic.forLevel(Difficulty.entries[d.ordinal - 1], gg).run(gg.startEdges()).solved) top++
            }
            println("GALAXIES extra ${d.name}: top-level $top/20, generation median ${ms.sorted()[10]} ms max ${ms.max()} ms, level solve median ${solveMs.sorted()[10]} ms")
        }
    }

    /** A plain search finds exactly one partition on 5×5 (Easy, Medium) and 7×7 Medium puzzles. */
    @Test(timeout = 300_000)
    fun smallPuzzlesAreUnique() {
        var checked = 0
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
            for (seed in 0 until 6) {
                val p = GalaxiesGenerator.generate(d, Random(seed + 70), n = 5)
                assertEquals("5x5 $d seed $seed", 1, countSolutions(5, p.dots, 2))
                checked++
            }
        }
        for (seed in 0 until 3) {
            val p = GalaxiesGenerator.generate(Difficulty.MEDIUM, Random(seed + 90))
            assertEquals("7x7 medium seed $seed", 1, countSolutions(p.n, p.dots, 2))
            checked++
        }
        assertEquals(15, checked)
    }

    /** From random partial solutions no level ever rules out the true dot of a cell or contradicts a known wall. */
    @Test(timeout = 300_000)
    fun logicIsSound() {
        val random = Random(5)
        for (d in Difficulty.entries) {
            val p = GalaxiesGenerator.generate(d, Random(21))
            val n = p.n
            val gg = GalaxyGrid(n, p.dots)
            val area = areasOf(n, p.dots, p.solution)!!
            // True dot of every cell: the dot whose touching cells lie in its area.
            val trueDot = IntArray(n * n)
            for (k in 0 until gg.count) for (c in 0 until n * n) if (area[c] == area[gg.own[k][0]]) trueDot[c] = k
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = gg.startEdges()
                for (e in start.indices) if (start[e] == UNKNOWN && random.nextInt(100) < 25) start[e] = if (p.solution[e]) SHADED else WHITE
                val logic = GalaxiesLogic(gg, level)
                val r = logic.run(start)
                for (c in 0 until n * n) assertTrue("$d level $level cell $c keeps its dot", logic.finalCand[c] and (1L shl trueDot[c]) != 0L)
                for (e in r.state.indices) if (r.state[e] != UNKNOWN) assertEquals("$d level $level edge $e", p.solution[e], r.state[e] == SHADED)
            }
        }
    }
}

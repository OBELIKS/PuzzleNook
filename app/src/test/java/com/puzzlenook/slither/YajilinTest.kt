package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YajilinTest {
    /** Cells of the loop in order (walked by cell coordinates, no code shared with the logic), null if the edges are not one closed loop. */
    private fun loopCells(n: Int, loop: BooleanArray): List<Int>? {
        val g = SlitherGrid(n - 1)
        val adj = Array(n * n) { mutableListOf<Int>() }
        for (e in 0 until g.edges) if (loop[e]) {
            val (a, b) = g.edgeDots[e]
            adj[a] += b
            adj[b] += a
        }
        val used = (0 until n * n).filter { adj[it].isNotEmpty() }
        if (used.isEmpty() || used.any { adj[it].size != 2 }) return null
        val order = mutableListOf(used[0])
        var prev = -1
        var cur = used[0]
        while (true) {
            val next = if (prev == -1) adj[cur][0] else adj[cur].first { it != prev }
            if (next == used[0]) break
            order += next
            prev = cur
            cur = next
            if (order.size > used.size) return null
        }
        return if (order.size == used.size) order else null
    }

    private val DR = intArrayOf(-1, 1, 0, 0)
    private val DC = intArrayOf(0, 0, -1, 1)

    /** Independent rule checker for a stored solution. */
    private fun solutionHolds(p: YajilinPuzzle): String? {
        val n = p.n
        val order = loopCells(n, p.loop) ?: return "not one loop"
        val onLoop = BooleanArray(n * n)
        for (c in order) onLoop[c] = true
        for (i in 0 until n * n) {
            val clue = p.clues[i] >= 0
            val kinds = (if (clue) 1 else 0) + (if (onLoop[i]) 1 else 0) + (if (p.shaded[i]) 1 else 0)
            if (kinds != 1) return "cell $i is $kinds things"
            if (p.shaded[i]) for (d in 0 until 4) {
                val r = i / n + DR[d]
                val c = i % n + DC[d]
                if (r in 0 until n && c in 0 until n && p.shaded[r * n + c]) return "shaded cells $i and ${r * n + c} touch"
            }
        }
        for (i in 0 until n * n) if (p.clues[i] >= 0) {
            val d = p.clues[i] / 100
            var r = i / n + DR[d]
            var c = i % n + DC[d]
            var count = 0
            while (r in 0 until n && c in 0 until n) {
                if (p.shaded[r * n + c]) count++
                r += DR[d]
                c += DC[d]
            }
            if (count != p.clues[i] % 100) return "clue $i says ${p.clues[i] % 100}, counted $count"
        }
        return null
    }

    @Test(timeout = 300_000)
    fun everyPuzzleSolvesByLogicToItsSolution() {
        for (d in Difficulty.entries) {
            val n = YajilinGenerator.size(d)
            val mg = MasyuGrid(n)
            val unknown = IntArray(mg.grid.edges + n * n) { UNKNOWN }
            val times = mutableListOf<Long>()
            val counts = mutableListOf<Int>()
            val solveMs = mutableListOf<Long>()
            var top = 0
            for (seed in 0 until 5) {
                val start = System.currentTimeMillis()
                val p = YajilinGenerator.generate(d, Random(seed + 7))
                times += System.currentTimeMillis() - start
                assertEquals("$d seed $seed rules", null, solutionHolds(p))
                val clueCount = p.clues.count { it >= 0 }
                counts += clueCount
                if (d == Difficulty.EASY || d == Difficulty.MEDIUM) {
                    assertTrue("$d seed $seed has $clueCount clues", clueCount >= YajilinGenerator.minClues(d, n))
                }
                val t = System.currentTimeMillis()
                val result = YajilinLogic.forLevel(d, mg, p.clues).run(unknown)
                solveMs += System.currentTimeMillis() - t
                assertTrue("$d seed $seed solvable by logic", result.solved)
                val edges = mg.grid.edges
                for (e in 0 until edges) assertEquals("$d seed $seed edge $e", p.loop[e], result.state[e] == SHADED)
                for (i in 0 until n * n) assertEquals("$d seed $seed cell $i", p.shaded[i], result.state[edges + i] == SHADED)
                if (d.ordinal >= 2 && !YajilinLogic.forLevel(Difficulty.entries[d.ordinal - 1], mg, p.clues).run(unknown).solved) top++
            }
            val sorted = times.sorted()
            println("YAJILIN ${d.name} $n: median ${sorted[2]} ms, max ${sorted.last()} ms, clues $counts, solve ${solveMs.sorted()[2]} ms, top-level $top/5")
            assertTrue("$d median ${sorted[2]} ms", sorted[2] <= 1500)
            assertTrue("$d max ${sorted.last()} ms", sorted.last() <= 4000 + 1000)
            if (d == Difficulty.HARD) assertTrue("Hard needs level 2 in $top/5", top >= 3)
            if (d == Difficulty.EXPERT) assertTrue("Expert needs level 3 in $top/5", top >= 3)
        }
    }

    /** Every level, from a random part of the solution, never decides against the stored solution. */
    @Test(timeout = 300_000)
    fun logicIsSound() {
        val random = Random(5)
        for (d in Difficulty.entries) {
            val p = YajilinGenerator.generate(d, Random(21))
            val n = p.n
            val mg = MasyuGrid(n)
            val edges = mg.grid.edges
            val truth = IntArray(edges + n * n) { if (it < edges) (if (p.loop[it]) SHADED else WHITE) else (if (p.shaded[it - edges]) SHADED else WHITE) }
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = IntArray(truth.size) { if (random.nextInt(100) < 25) truth[it] else UNKNOWN }
                val r = YajilinLogic(mg, p.clues, level).run(start)
                for (i in truth.indices) if (r.state[i] != UNKNOWN) assertEquals("$d level $level element $i", truth[i], r.state[i])
            }
        }
    }

    /**
     * Counts the solutions of a puzzle by search alone: every set of shaded cells that fits the arrows and
     * the no-touching rule, and for each the Hamiltonian cycles through the remaining non-clue cells.
     */
    private fun countSolutions(n: Int, clues: IntArray, limit: Int): Int {
        val cells = n * n
        val shaded = BooleanArray(cells)
        // A clue is checked once the last cell of its ray (or the clue itself) has been decided.
        val checkAt = Array(cells) { mutableListOf<Int>() }
        for (i in 0 until cells) if (clues[i] >= 0) {
            val d = clues[i] / 100
            var last = i
            var r = i / n + DR[d]
            var c = i % n + DC[d]
            while (r in 0 until n && c in 0 until n) { last = maxOf(last, r * n + c); r += DR[d]; c += DC[d] }
            checkAt[last] += i
        }
        fun clueOk(i: Int): Boolean {
            val d = clues[i] / 100
            var r = i / n + DR[d]
            var c = i % n + DC[d]
            var count = 0
            while (r in 0 until n && c in 0 until n) { if (shaded[r * n + c]) count++; r += DR[d]; c += DC[d] }
            return count == clues[i] % 100
        }
        fun neighbours(i: Int) = (0 until 4).mapNotNull { d ->
            val r = i / n + DR[d]
            val c = i % n + DC[d]
            if (r in 0 until n && c in 0 until n) r * n + c else null
        }
        val nb = Array(cells) { neighbours(it) }

        var total = 0
        fun cycles(): Int {
            val free = BooleanArray(cells) { clues[it] < 0 && !shaded[it] }
            val freeCount = free.count { it }
            if (freeCount < 4) return 0
            if (nb.indices.any { free[it] && nb[it].count { j -> free[j] } < 2 }) return 0
            val start = free.indexOfFirst { it }
            val visited = BooleanArray(cells)
            var found = 0
            fun dfs(cur: Int, len: Int) {
                if (found > 2 * limit) return
                for (next in nb[cur]) {
                    if (!free[next]) continue
                    if (next == start) { if (len == freeCount) found++; continue }
                    if (visited[next]) continue
                    visited[next] = true
                    // Every unvisited free cell must still have two ways in.
                    var alive = true
                    for (u in 0 until cells) if (free[u] && !visited[u] && u != start) {
                        var avail = 0
                        for (j in nb[u]) if (free[j] && (!visited[j] || j == next || j == start)) avail++
                        if (avail < 2) { alive = false; break }
                    }
                    if (alive) dfs(next, len + 1)
                    visited[next] = false
                }
            }
            visited[start] = true
            dfs(start, 1)
            return found / 2
        }
        fun place(i: Int) {
            if (total >= limit) return
            if (i == cells) { total += cycles(); return }
            val options = if (clues[i] >= 0) listOf(false) else listOf(false, true)
            for (v in options) {
                if (v && ((i % n > 0 && shaded[i - 1]) || (i >= n && shaded[i - n]))) continue
                shaded[i] = v
                if (checkAt[i].all { clueOk(it) }) place(i + 1)
                shaded[i] = false
            }
        }
        place(0)
        return total
    }

    /** A plain search finds exactly one solution on 6×6 puzzles. */
    @Test(timeout = 300_000)
    fun smallPuzzlesAreUnique() {
        var checked = 0
        for (d in Difficulty.entries) {
            for (seed in 0 until 5) {
                val p = YajilinGenerator.generate(d, Random(seed + 70), n = 6)
                assertEquals("$d seed $seed rules", null, solutionHolds(p))
                assertEquals("$d seed $seed", 1, countSolutions(6, p.clues, 2))
                checked++
            }
        }
        assertEquals(20, checked)
    }

    /** Report only: how often 20 more seeds need the top level on Hard and Expert (see PLAN, batch 5b). */
    @Test(timeout = 300_000)
    fun extraSeedsReport() {
        for (d in listOf(Difficulty.HARD, Difficulty.EXPERT)) {
            val n = YajilinGenerator.size(d)
            val mg = MasyuGrid(n)
            val unknown = IntArray(mg.grid.edges + n * n) { UNKNOWN }
            var top = 0
            repeat(20) { seed ->
                val p = YajilinGenerator.generate(d, Random(seed + 500))
                if (!YajilinLogic.forLevel(Difficulty.entries[d.ordinal - 1], mg, p.clues).run(unknown).solved) top++
            }
            println("YAJILIN extra ${d.name}: top level needed in $top/20")
        }
    }
}

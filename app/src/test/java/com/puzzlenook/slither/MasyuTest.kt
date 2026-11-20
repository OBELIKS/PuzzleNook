package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MasyuTest {
    /**
     * Independent rule checker: walks the loop by cell coordinates (no code shared with the logic) and
     * returns the cells in loop order, or null if the edges are not one closed loop.
     */
    private fun loopCells(n: Int, solution: BooleanArray): List<Int>? {
        val g = SlitherGrid(n - 1)
        val adj = Array(n * n) { mutableListOf<Int>() }
        for (e in 0 until g.edges) if (solution[e]) {
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

    /** Pearl rules from the loop order: black bends with straight cells on both sides, white is straight with a bend next to it. */
    private fun pearlsHold(n: Int, pearls: IntArray, order: List<Int>): Boolean {
        val k = order.size
        fun r(i: Int) = order[(i + k) % k] / n
        fun c(i: Int) = order[(i + k) % k] % n
        fun straight(i: Int) = (r(i - 1) - r(i)) == -(r(i + 1) - r(i)) && (c(i - 1) - c(i)) == -(c(i + 1) - c(i))
        val position = order.withIndex().associate { it.value to it.index }
        for (cell in pearls.indices) {
            if (pearls[cell] == 0) continue
            val i = position[cell] ?: return false
            if (pearls[cell] == 2) {
                if (straight(i) || !straight(i - 1) || !straight(i + 1)) return false
            } else {
                if (!straight(i) || (straight(i - 1) && straight(i + 1))) return false
            }
        }
        return true
    }

    @Test(timeout = 300_000)
    fun everyPuzzleSolvesByLogicToItsLoop() {
        for (d in Difficulty.entries) {
            val n = MasyuGenerator.size(d)
            val mg = MasyuGrid(n)
            val times = mutableListOf<Long>()
            var top = 0
            val counts = mutableListOf<Int>()
            for (seed in 0 until 5) {
                val start = System.currentTimeMillis()
                val p = MasyuGenerator.generate(d, Random(seed + 11))
                times += System.currentTimeMillis() - start
                val order = loopCells(n, p.solution)
                assertTrue("$d seed $seed single loop", order != null)
                assertTrue("$d seed $seed pearl rules", pearlsHold(n, p.pearls, order!!))
                val pearlCount = p.pearls.count { it != 0 }
                counts += pearlCount
                if (d == Difficulty.EASY || d == Difficulty.MEDIUM) {
                    assertTrue("$d seed $seed has ${pearlCount} pearls", pearlCount >= MasyuGenerator.minClues(d, n))
                }
                val unknown = IntArray(mg.grid.edges) { UNKNOWN }
                val result = MasyuLogic.forLevel(d, mg, p.pearls).run(unknown)
                assertTrue("$d seed $seed solvable by logic", result.solved)
                for (e in 0 until mg.grid.edges) assertEquals("$d seed $seed edge $e", p.solution[e], result.state[e] == SHADED)
                if (d.ordinal >= 2 && !MasyuLogic.forLevel(Difficulty.entries[d.ordinal - 1], mg, p.pearls).run(unknown).solved) top++
            }
            val sorted = times.sorted()
            println("MASYU ${d.name} $n: median ${sorted[2]} ms, max ${sorted.last()} ms, pearls $counts, top-level $top/5")
            assertTrue("$d median ${sorted[2]} ms", sorted[2] <= 1500)
            assertTrue("$d max ${sorted.last()} ms", sorted.last() <= 4000 + 1000)
            if (d == Difficulty.HARD) assertTrue("Hard needs its top level in $top/5", top >= 3)
        }
    }

    /** Counts the loops of a puzzle by searching every simple cycle (smallest cell first); no logic code involved. */
    fun countLoops(n: Int, pearls: IntArray, limit: Int): Int {
        val cells = n * n
        val minPearl = pearls.indices.firstOrNull { pearls[it] != 0 } ?: 0
        val visited = BooleanArray(cells)
        val path = IntArray(cells)
        var found = 0
        // dir: 0 up, 1 down, 2 left, 3 right
        fun step(cell: Int, d: Int): Int {
            val r = cell / n
            val c = cell % n
            return when (d) {
                0 -> if (r > 0) cell - n else -1
                1 -> if (r < n - 1) cell + n else -1
                2 -> if (c > 0) cell - 1 else -1
                else -> if (c < n - 1) cell + 1 else -1
            }
        }
        fun dirOf(a: Int, b: Int) = (0 until 4).first { step(a, it) == b }
        fun pearlsFit(len: Int): Boolean {
            // path[0..len-1] closed into a cycle; check every pearl cell is on it and satisfied.
            val onLoop = BooleanArray(cells)
            for (i in 0 until len) onLoop[path[i]] = true
            for (cell in pearls.indices) if (pearls[cell] != 0 && !onLoop[cell]) return false
            fun din(i: Int) = dirOf(path[(i + len - 1) % len], path[i])
            fun dout(i: Int) = dirOf(path[i], path[(i + 1) % len])
            for (i in 0 until len) {
                val kind = pearls[path[i]]
                if (kind == 0) continue
                val straight = din(i) == dout(i)
                if (kind == 2) {
                    if (straight || din((i + 1) % len) != dout((i + 1) % len) || din((i + len - 1) % len) != dout((i + len - 1) % len)) return false
                } else {
                    if (!straight) return false
                    val a = din((i + len - 1) % len) == dout((i + len - 1) % len)
                    val b = din((i + 1) % len) == dout((i + 1) % len)
                    if (a && b) return false
                }
            }
            return true
        }
        fun dfs(start: Int, len: Int, lastDir: Int) {
            if (found >= limit * 2) return
            val cur = path[len - 1]
            for (d in 0 until 4) {
                val next = step(cur, d)
                if (next < start) continue
                // Pearl rule at the cell being left (not the start, which closes the loop later).
                if (len >= 2) {
                    val kind = pearls[cur]
                    if (kind == 2 && d == lastDir) continue
                    if (kind == 1 && d != lastDir) continue
                }
                if (next == start) {
                    if (len >= 4 && pearlsFit(len)) found++
                    continue
                }
                if (visited[next]) continue
                visited[next] = true
                path[len] = next
                dfs(start, len + 1, d)
                visited[next] = false
            }
        }
        for (start in 0..minPearl) {
            visited[start] = true
            path[0] = start
            dfs(start, 1, -1)
            visited[start] = false
        }
        return found / 2
    }

    /** A plain search finds exactly one loop on 6×6 puzzles of the easier levels. */
    @Test(timeout = 300_000)
    fun smallPuzzlesAreUnique() {
        var checked = 0
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
            for (seed in 0 until 5) {
                val p = MasyuGenerator.generate(d, Random(seed + 70), n = 6)
                assertEquals("$d seed $seed", 1, countLoops(6, p.pearls, 2))
                checked++
            }
        }
        assertEquals(10, checked)
    }
}


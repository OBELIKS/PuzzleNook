package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE

/**
 * Loop reasoning shared by the games that draw one closed loop on a [SlitherGrid] (Slitherlink:
 * along cell borders, Masyu: through cell centres on `SlitherGrid(n - 1)`). Edge states are
 * [SHADED] = line, [WHITE] = ✕, [UNKNOWN].
 */
object Loop {
    /** True if the [line] edges form exactly one closed loop. */
    fun isSingleLoop(grid: SlitherGrid, line: BooleanArray): Boolean {
        val used = (0 until grid.edges).filter { line[it] }
        if (used.isEmpty()) return false
        for (d in 0 until grid.dots) {
            val deg = grid.dotEdges[d].count { line[it] }
            if (deg != 0 && deg != 2) return false
        }
        // Walk from one edge and see that we reach every line edge.
        val seen = BooleanArray(grid.edges)
        val stack = ArrayDeque(listOf(used[0]))
        seen[used[0]] = true
        var count = 0
        while (stack.isNotEmpty()) {
            val e = stack.removeLast()
            count++
            for (d in grid.edgeDots[e]) for (f in grid.dotEdges[d]) if (line[f] && !seen[f]) {
                seen[f] = true
                stack.addLast(f)
            }
        }
        return count == used.size
    }

    /**
     * An open edge joining the two ends of one path would close a loop. That's only allowed
     * if it's the whole answer: the path holds every line and [accepts] the lines after closing
     * (the game's other conditions: numbers met, pearls on the loop...). Returns true if it crossed
     * something out, null on a contradiction (a closed loop that is not everything).
     */
    fun closeLoops(grid: SlitherGrid, s: IntArray, accepts: (IntArray) -> Boolean): Boolean? {
        val parent = IntArray(grid.dots) { it }
        fun find(x: Int): Int {
            var y = x
            while (parent[y] != y) { parent[y] = parent[parent[y]]; y = parent[y] }
            return y
        }
        var lineCount = 0
        for (e in s.indices) if (s[e] == SHADED) {
            lineCount++
            val (a, b) = grid.edgeDots[e]
            parent[find(a)] = find(b)
        }
        // A closed loop already: it must be everything.
        val degree = IntArray(grid.dots) { d -> grid.dotEdges[d].count { s[it] == SHADED } }
        val compEdges = HashMap<Int, Int>()
        val compEnds = HashMap<Int, Int>()
        for (e in s.indices) if (s[e] == SHADED) compEdges.merge(find(grid.edgeDots[e][0]), 1, Int::plus)
        for (d in 0 until grid.dots) if (degree[d] == 1) compEnds.merge(find(d), 1, Int::plus)
        for ((root, edges) in compEdges) {
            if (compEnds[root] == null && edges < lineCount) return null
        }
        var did = false
        for (e in s.indices) {
            if (s[e] != UNKNOWN) continue
            val (a, b) = grid.edgeDots[e]
            if (degree[a] != 1 || degree[b] != 1 || find(a) != find(b)) continue
            // Closing here is the answer only if this path is every line and the rest fits.
            val t = s.copyOf()
            t[e] = SHADED
            val allLines = compEdges[find(a)] == lineCount
            if (!(allLines && accepts(t))) { s[e] = WHITE; did = true }
        }
        return did
    }

    /**
     * All lines, and every dot for which [mustBeOnLoop] holds, can still join up through open edges.
     */
    fun connectable(grid: SlitherGrid, s: IntArray, mustBeOnLoop: (Int) -> Boolean = { false }): Boolean {
        val start = s.indices.firstOrNull { s[it] == SHADED }
            ?: (0 until grid.dots).firstOrNull(mustBeOnLoop)?.let { d -> grid.dotEdges[d].firstOrNull { s[it] != WHITE } ?: return false }
            ?: return true
        val seen = BooleanArray(grid.edges)
        val stack = ArrayDeque(listOf(start))
        seen[start] = true
        while (stack.isNotEmpty()) {
            val e = stack.removeLast()
            for (d in grid.edgeDots[e]) for (f in grid.dotEdges[d]) if (!seen[f] && s[f] != WHITE) {
                seen[f] = true
                stack.addLast(f)
            }
        }
        return s.indices.all { s[it] != SHADED || seen[it] } &&
            (0 until grid.dots).all { d -> !mustBeOnLoop(d) || grid.dotEdges[d].any { seen[it] } }
    }
}

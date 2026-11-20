package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.ShadeResult
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/**
 * Geometry of an n×n Slitherlink grid. Edges: horizontal edge (r, c) runs from dot (r, c) to
 * (r, c+1) for r in 0..n; vertical edge (r, c) runs from dot (r, c) to (r+1, c) for c in 0..n.
 * Dots are numbered r * (n + 1) + c.
 */
class SlitherGrid(val n: Int) {
    val horizontal = (n + 1) * n
    val edges = horizontal + n * (n + 1)
    val dots = (n + 1) * (n + 1)

    fun h(r: Int, c: Int) = r * n + c
    fun v(r: Int, c: Int) = horizontal + r * (n + 1) + c
    fun dot(r: Int, c: Int) = r * (n + 1) + c

    /** Top, bottom, left, right edges of each cell. */
    val cellEdges: Array<IntArray> = Array(n * n) { i ->
        val r = i / n
        val c = i % n
        intArrayOf(h(r, c), h(r + 1, c), v(r, c), v(r, c + 1))
    }

    /** The two dots of each edge. */
    val edgeDots: Array<IntArray> = Array(edges) { e ->
        if (e < horizontal) {
            val r = e / n
            val c = e % n
            intArrayOf(dot(r, c), dot(r, c + 1))
        } else {
            val k = e - horizontal
            val r = k / (n + 1)
            val c = k % (n + 1)
            intArrayOf(dot(r, c), dot(r + 1, c))
        }
    }

    val dotEdges: Array<IntArray> = run {
        val lists = Array(dots) { mutableListOf<Int>() }
        for (e in 0 until edges) for (d in edgeDots[e]) lists[d] += e
        Array(dots) { lists[it].toIntArray() }
    }

    /** Cells on either side of an edge (-1 outside the grid). */
    val edgeCells: Array<IntArray> = Array(edges) { e ->
        if (e < horizontal) {
            val r = e / n
            val c = e % n
            intArrayOf(if (r > 0) (r - 1) * n + c else -1, if (r < n) r * n + c else -1)
        } else {
            val k = e - horizontal
            val r = k / (n + 1)
            val c = k % (n + 1)
            intArrayOf(if (c > 0) r * n + c - 1 else -1, if (c < n) r * n + c else -1)
        }
    }

    /** Edges in the solution: the border of the [inside] cells. */
    fun loopOf(inside: BooleanArray): BooleanArray = BooleanArray(edges) { e ->
        val (a, b) = edgeCells[e]
        (a >= 0 && inside[a]) != (b >= 0 && inside[b])
    }

    /** True if the [line] edges form exactly one closed loop. */
    fun isSingleLoop(line: BooleanArray): Boolean = Loop.isSingleLoop(this, line)
}

/**
 * Slitherlink logic as a person would use it. Edge states: [SHADED] = line, [WHITE] = ✕.
 * - a number with all its lines gets ✕ on its other sides; one that needs every open side gets lines;
 * - every dot has 0 or 2 lines: a dot with two lines gets ✕ on the rest, a dot with one line and
 *   one open edge continues there, a dot with no way on is crossed out;
 * - a line may not close a small loop while other lines or unmet numbers remain;
 * - short "what if" checks: if a line (or ✕) on an edge leads straight to a broken rule, it's the other.
 */
class SlitherLogic(private val grid: SlitherGrid, private val clues: IntArray) {
    private val n = grid.n

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        val round = IntArray(grid.edges) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            val before = s.copyOf()
            if (!propagate(s)) return ShadeResult(false, s, round)
            if (s.contentEquals(before)) {
                whatIf(s)
                if (!propagate(s)) return ShadeResult(false, s, round)
            }
            for (e in s.indices) if (round[e] < 0 && s[e] != UNKNOWN) round[e] = r
            if (s.none { it == UNKNOWN }) {
                val line = BooleanArray(grid.edges) { s[it] == SHADED }
                return ShadeResult(grid.isSingleLoop(line) && cluesMet(s), s, round)
            }
            if (s.contentEquals(before)) return ShadeResult(false, s, round)
        }
    }

    private fun cluesMet(s: IntArray) = (0 until n * n).all { i ->
        clues[i] < 0 || grid.cellEdges[i].count { s[it] == SHADED } == clues[i]
    }

    /** Applies the direct rules until nothing changes. False on a contradiction. */
    fun propagate(s: IntArray): Boolean {
        var changed = true
        while (changed) {
            changed = false
            for (i in 0 until n * n) {
                val clue = clues[i]
                if (clue < 0) continue
                val es = grid.cellEdges[i]
                val lines = es.count { s[it] == SHADED }
                val open = es.count { s[it] == UNKNOWN }
                if (lines > clue || lines + open < clue) return false
                if (open == 0) continue
                if (lines == clue) { for (e in es) if (s[e] == UNKNOWN) s[e] = WHITE; changed = true }
                else if (lines + open == clue) { for (e in es) if (s[e] == UNKNOWN) s[e] = SHADED; changed = true }
            }
            for (d in 0 until grid.dots) {
                val es = grid.dotEdges[d]
                val lines = es.count { s[it] == SHADED }
                val open = es.count { s[it] == UNKNOWN }
                if (lines > 2 || (lines == 1 && open == 0)) return false
                if (open == 0) continue
                when {
                    lines == 2 -> { for (e in es) if (s[e] == UNKNOWN) s[e] = WHITE; changed = true }
                    lines == 1 && open == 1 -> { for (e in es) if (s[e] == UNKNOWN) s[e] = SHADED; changed = true }
                    lines == 0 && open == 1 -> { for (e in es) if (s[e] == UNKNOWN) s[e] = WHITE; changed = true }
                }
            }
            if (!changed) {
                when (closeLoops(s)) {
                    null -> return false
                    true -> changed = true
                    false -> {}
                }
            }
        }
        return connectable(s)
    }

    private fun closeLoops(s: IntArray): Boolean? = Loop.closeLoops(grid, s) { t ->
        (0 until n * n).all { i -> clues[i] < 0 || grid.cellEdges[i].count { t[it] == SHADED } == clues[i] }
    }

    private fun connectable(s: IntArray) = Loop.connectable(grid, s)

    private fun whatIf(s: IntArray) {
        for (e in s.indices) {
            if (s[e] != UNKNOWN) continue
            for (guess in intArrayOf(SHADED, WHITE)) {
                val t = s.copyOf()
                t[e] = guess
                if (!propagate(t)) {
                    s[e] = if (guess == SHADED) WHITE else SHADED
                    if (!propagate(s)) return
                    break
                }
            }
        }
    }
}

class SlitherPuzzle(val difficulty: Difficulty, val n: Int, val clues: IntArray, val solution: BooleanArray)

object SlitherGenerator {
    fun size(d: Difficulty) = intArrayOf(5, 6, 8, 10)[d.ordinal]

    /** Share of the removable numbers put back on easier levels. */
    private fun extra(d: Difficulty) = doubleArrayOf(0.35, 0.15, 0.05, 0.0)[d.ordinal]

    /**
     * Grows a random blob of cells whose border is one loop, numbers every cell, then removes
     * numbers while the logic can still solve it, and puts some back on easier levels.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): SlitherPuzzle {
        val end = System.nanoTime() + budgetMs * 1_000_000
        val grid = SlitherGrid(n)
        var inside: BooleanArray? = null
        while (inside == null) inside = region(n, random)
        val solution = grid.loopOf(inside)
        val full = IntArray(n * n) { i -> grid.cellEdges[i].count { solution[it] } }
        val clues = full.copyOf()
        val removed = mutableListOf<Int>()
        for (i in (0 until n * n).shuffled(random)) {
            if (System.nanoTime() > end) break
            clues[i] = -1
            if (SlitherLogic(grid, clues).run(IntArray(grid.edges) { UNKNOWN }).solved) removed += i else clues[i] = full[i]
        }
        for (i in removed.shuffled(random).take((removed.size * extra(d)).toInt())) clues[i] = full[i]
        return SlitherPuzzle(d, n, clues, solution)
    }

    /**
     * A random set of cells, connected, with no holes and no two cells meeting only at a corner,
     * so its border is a single loop.
     */
    fun region(n: Int, random: Random, treeBias: Double = 0.0): BooleanArray? {
        val inside = BooleanArray(n * n)
        inside[random.nextInt(n * n)] = true
        val target = (n * n * (0.45 + random.nextDouble() * 0.15)).toInt()
        var count = 1
        var fails = 0
        while (count < target && fails < 400) {
            val options = (0 until n * n).filter { i -> !inside[i] && neighbours(i, n).any { inside[it] } }
            // Masyu wants long, winding borders: with [treeBias] prefer cells that touch the blob once (a thin, tree-like shape).
            val thin = if (treeBias > 0 && random.nextDouble() < treeBias) options.filter { i -> neighbours(i, n).count { inside[it] } == 1 } else emptyList()
            val i = (if (thin.isNotEmpty()) thin else options).randomOrNull(random) ?: return null
            inside[i] = true
            if (ok(n, inside)) count++ else { inside[i] = false; fails++ }
        }
        return inside.takeIf { count >= n * n / 3 }
    }

    private fun neighbours(i: Int, n: Int) = buildList {
        val r = i / n
        val c = i % n
        if (r > 0) add(i - n)
        if (r < n - 1) add(i + n)
        if (c > 0) add(i - 1)
        if (c < n - 1) add(i + 1)
    }

    /** Outside cells all reach the border, and no dot has a checkerboard of cells around it. */
    internal fun ok(n: Int, inside: BooleanArray): Boolean {
        for (r in 1 until n) for (c in 1 until n) {
            val a = inside[(r - 1) * n + c - 1]
            val b = inside[(r - 1) * n + c]
            val x = inside[r * n + c - 1]
            val y = inside[r * n + c]
            if (a == y && b == x && a != b) return false
        }
        val seen = BooleanArray(n * n)
        val stack = ArrayDeque<Int>()
        for (i in 0 until n * n) {
            val r = i / n
            val c = i % n
            if (!inside[i] && (r == 0 || c == 0 || r == n - 1 || c == n - 1)) { seen[i] = true; stack.addLast(i) }
        }
        while (stack.isNotEmpty()) for (j in neighbours(stack.removeLast(), n)) if (!inside[j] && !seen[j]) { seen[j] = true; stack.addLast(j) }
        return (0 until n * n).all { inside[it] || seen[it] }
    }
}

package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant

/**
 * A puzzle where you shade cells or place things: Star Battle, Hitori, Nurikabe, Takuzu, Mosaic,
 * Light Up, Tents, Kurodoko, Norinori, Battleships, Aquarium, Heyawake, LITS. [solution] is true for shaded cells (stars,
 * bulbs, tents, ship cells, water).
 */
class ShadePuzzle(
    val variant: Variant,
    val difficulty: Difficulty,
    val size: Int,
    val solution: BooleanArray,
    /** Cells shown from the start because the logic couldn't settle them otherwise. */
    val givens: BooleanArray,
    /** Hitori: every cell's number. Nurikabe: island sizes on clue cells, 0 elsewhere. Battleships: shown cells (-1 none, 0 water, 1..6 ship part). Heyawake: a room's shaded count on its first cell, -1 elsewhere. */
    val numbers: IntArray = IntArray(size * size),
    /** Star Battle, Norinori, Aquarium, Heyawake, LITS: region (tank, room) of each cell. */
    val regions: IntArray? = null,
    /** Star Battle: stars per row, column and region. */
    val stars: Int = 0,
    /** Tents, Battleships, Aquarium: tents / ship cells / water per row (0..n-1), then per column; `-1` = not shown. Null = no outside counts. */
    val counts: IntArray? = null,
)

/** Where a step-by-step solver got to. Cell states: [UNKNOWN], [WHITE], [SHADED]. */
class ShadeResult(val solved: Boolean, val state: IntArray, val round: IntArray) {
    val unresolved: List<Int> get() = state.indices.filter { state[it] == UNKNOWN }
}

const val UNKNOWN = -1
const val WHITE = 0
const val SHADED = 1

internal fun orth(i: Int, n: Int): IntArray {
    val r = i / n
    val c = i % n
    val out = ArrayList<Int>(4)
    if (r > 0) out += i - n
    if (r < n - 1) out += i + n
    if (c > 0) out += i - 1
    if (c < n - 1) out += i + 1
    return out.toIntArray()
}

internal fun king(i: Int, n: Int): IntArray {
    val r = i / n
    val c = i % n
    val out = ArrayList<Int>(8)
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val rr = r + dr
        val cc = c + dc
        if (rr in 0 until n && cc in 0 until n) out += rr * n + cc
    }
    return out.toIntArray()
}

/** Whether the cells where [inside] is true form one orthogonally connected group (true if none). */
internal fun connected(n: Int, inside: (Int) -> Boolean): Boolean {
    val start = (0 until n * n).firstOrNull(inside) ?: return true
    val seen = BooleanArray(n * n)
    val stack = ArrayDeque<Int>()
    stack.addLast(start)
    seen[start] = true
    var count = 0
    while (stack.isNotEmpty()) {
        val i = stack.removeLast()
        count++
        for (j in orth(i, n)) if (!seen[j] && inside(j)) {
            seen[j] = true
            stack.addLast(j)
        }
    }
    return count == (0 until n * n).count(inside)
}

/**
 * Cut cells of the graph of cells where [open] holds, seen from the [anchor] cells: null when the anchors
 * are not all connected; otherwise, for every open cell, whether leaving it out would cut an anchor off
 * from the first one (the anchors themselves are never marked). One depth-first pass.
 */
internal fun anchorCuts(n: Int, nb: Array<IntArray>, open: (Int) -> Boolean, anchor: (Int) -> Boolean): BooleanArray? {
    val cells = n * n
    val cut = BooleanArray(cells)
    val root = (0 until cells).firstOrNull { anchor(it) } ?: return cut
    val disc = IntArray(cells) { -1 }
    val low = IntArray(cells)
    val sub = IntArray(cells)
    var time = 0
    fun dfs(u: Int, parent: Int) {
        disc[u] = time
        low[u] = time
        time++
        var count = if (anchor(u)) 1 else 0
        for (v in nb[u]) {
            if (!open(v)) continue
            if (disc[v] < 0) {
                dfs(v, u)
                count += sub[v]
                if (low[v] < low[u]) low[u] = low[v]
                if (u != root && low[v] >= disc[u] && sub[v] > 0) cut[u] = true
            } else if (v != parent && disc[v] < low[u]) low[u] = disc[v]
        }
        sub[u] = count
    }
    dfs(root, -1)
    for (i in 0 until cells) if (anchor(i) && disc[i] < 0) return null
    return cut
}

internal fun deadline(ms: Long) = System.nanoTime() + ms * 1_000_000
internal fun past(deadline: Long) = System.nanoTime() > deadline

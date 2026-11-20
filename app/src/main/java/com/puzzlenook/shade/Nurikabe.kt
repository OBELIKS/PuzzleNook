package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Nurikabe logic. Rules: each number is an island of that many white cells with only that
 * number in it; islands don't touch side by side; all shaded cells (the sea) form one
 * connected group; the sea has no 2×2 block.
 *
 * Techniques, as a person would use them:
 * - a finished island is surrounded by sea;
 * - a cell touching two islands with numbers is sea;
 * - a cell no island can reach in time is sea;
 * - an island (or loose white cells) with only one way out grows that way;
 * - a piece of sea with only one way out flows that way;
 * - three sea cells of a 2×2 make the fourth white;
 * - short "what if" checks that lead straight to a broken rule.
 */
class NurikabeLogic(private val n: Int, private val clues: IntArray) {
    private val nbrs = Array(n * n) { orth(it, n) }
    private val seaTotal = n * n - clues.sum()

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        for (i in s.indices) if (clues[i] > 0) s[i] = WHITE
        val round = IntArray(n * n) { if (start[it] != UNKNOWN || clues[it] > 0) 0 else -1 }
        var r = 0
        while (true) {
            r++
            val before = s.copyOf()
            if (!propagate(s)) return ShadeResult(false, s, round)
            if (s.contentEquals(before)) {
                reach(s)
                if (s.contentEquals(before)) whatIf(s)
                if (!propagate(s)) return ShadeResult(false, s, round)
            }
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(consistent(s) && complete(s), s, round)
            if (s.contentEquals(before)) return ShadeResult(false, s, round)
        }
    }

    /** White groups: cells, their number (0 if none, -1 if two). */
    private class Group(val cells: List<Int>, val clue: Int, val liberties: Set<Int>)

    private fun groups(s: IntArray, colour: Int): List<Group> {
        val seen = BooleanArray(n * n)
        val out = mutableListOf<Group>()
        for (start in s.indices) {
            if (s[start] != colour || seen[start]) continue
            val cells = mutableListOf(start)
            seen[start] = true
            var k = 0
            val libs = HashSet<Int>()
            var clue = 0
            while (k < cells.size) {
                val i = cells[k++]
                if (clues[i] > 0) clue = if (clue == 0) clues[i] else -1
                for (j in nbrs[i]) when {
                    s[j] == colour && !seen[j] -> { seen[j] = true; cells += j }
                    s[j] == UNKNOWN -> libs += j
                }
            }
            out += Group(cells, clue, libs)
        }
        return out
    }

    /** Applies the direct rules until nothing changes. False on a contradiction. */
    private fun propagate(s: IntArray): Boolean {
        var changed = true
        while (changed) {
            changed = false
            fun set(i: Int, v: Int) {
                if (s[i] == UNKNOWN) { s[i] = v; changed = true }
            }
            if (!consistent(s)) return false
            val islands = groups(s, WHITE)
            val owner = IntArray(n * n) { -1 }
            islands.forEachIndexed { g, isl -> isl.cells.forEach { owner[it] = g } }
            for (isl in islands) {
                if (isl.clue > 0 && isl.cells.size == isl.clue) isl.liberties.forEach { set(it, SHADED) }
                else if (isl.liberties.size == 1) set(isl.liberties.first(), WHITE)
            }
            for (i in s.indices) {
                if (s[i] != UNKNOWN) continue
                val clued = nbrs[i].map { owner[it] }.filter { it >= 0 && islands[it].clue > 0 }.distinct()
                if (clued.size >= 2) set(i, SHADED)
            }
            val seas = groups(s, SHADED)
            val seaCount = s.count { it == SHADED }
            for (sea in seas) {
                if (sea.cells.size < seaTotal && sea.liberties.size == 1 && (seas.size > 1 || seaCount < seaTotal)) {
                    set(sea.liberties.first(), SHADED)
                }
            }
            for (r in 0 until n - 1) for (c in 0 until n - 1) {
                val block = intArrayOf(r * n + c, r * n + c + 1, (r + 1) * n + c, (r + 1) * n + c + 1)
                if (block.count { s[it] == SHADED } == 3) block.forEach { if (s[it] == UNKNOWN) set(it, WHITE) }
            }
        }
        return consistent(s)
    }

    /** Cells no numbered island can reach (counting cells still needed) are sea. */
    private fun reach(s: IntArray) {
        val islands = groups(s, WHITE)
        val owner = IntArray(n * n) { -1 }
        islands.forEachIndexed { g, isl -> isl.cells.forEach { owner[it] = g } }
        val reachable = BooleanArray(n * n)
        for ((g, isl) in islands.withIndex()) {
            if (isl.clue <= 0) continue
            isl.cells.forEach { reachable[it] = true }
            val left = isl.clue - isl.cells.size
            if (left <= 0) continue
            val dist = IntArray(n * n) { Int.MAX_VALUE }
            val queue = ArrayDeque<Int>()
            for (i in isl.cells) { dist[i] = 0; queue.addLast(i) }
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                if (dist[i] == left) continue
                for (j in nbrs[i]) {
                    if (dist[j] != Int.MAX_VALUE || s[j] == SHADED) continue
                    if (owner[j] >= 0 && islands[owner[j]].clue > 0 && owner[j] != g) continue
                    // Can't step next to another numbered island.
                    if (nbrs[j].any { owner[it] >= 0 && owner[it] != g && islands[owner[it]].clue > 0 }) continue
                    dist[j] = dist[i] + 1
                    reachable[j] = true
                    queue.addLast(j)
                }
            }
        }
        for (i in s.indices) if (s[i] == UNKNOWN && !reachable[i]) s[i] = SHADED
    }

    private fun whatIf(s: IntArray) {
        for (i in s.indices) {
            if (s[i] != UNKNOWN) continue
            for (guess in intArrayOf(SHADED, WHITE)) {
                val t = s.copyOf()
                t[i] = guess
                if (!propagate(t)) {
                    s[i] = if (guess == SHADED) WHITE else SHADED
                    return
                }
            }
        }
    }

    /** No rule is broken yet, and every island and the sea can still be finished. */
    private fun consistent(s: IntArray): Boolean {
        for (isl in groups(s, WHITE)) {
            if (isl.clue < 0) return false
            if (isl.clue > 0 && isl.cells.size > isl.clue) return false
            if (isl.liberties.isEmpty() && (isl.clue == 0 || isl.cells.size < isl.clue)) return false
        }
        for (r in 0 until n - 1) for (c in 0 until n - 1) {
            if (s[r * n + c] == SHADED && s[r * n + c + 1] == SHADED && s[(r + 1) * n + c] == SHADED && s[(r + 1) * n + c + 1] == SHADED) return false
        }
        if (s.count { it == SHADED } > seaTotal) return false
        return connected(n) { s[it] == SHADED } || connectedThrough(s)
    }

    /** The sea can still join up through open cells. */
    private fun connectedThrough(s: IntArray): Boolean {
        val start = s.indices.firstOrNull { s[it] == SHADED } ?: return true
        val seen = BooleanArray(n * n)
        val stack = ArrayDeque(listOf(start))
        seen[start] = true
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            for (j in nbrs[i]) if (!seen[j] && s[j] != WHITE) { seen[j] = true; stack.addLast(j) }
        }
        return s.indices.all { s[it] != SHADED || seen[it] }
    }

    private fun complete(s: IntArray): Boolean =
        connected(n) { s[it] == SHADED } && groups(s, WHITE).all { it.clue > 0 && it.cells.size == it.clue }
}

object NurikabeGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 7, 8, 9)[d.ordinal]
    private fun maxIsland(d: Difficulty) = intArrayOf(4, 5, 6, 6)[d.ordinal]

    /**
     * Grows islands at random (never touching, the sea kept connected), breaks up any 2×2 sea,
     * puts each island's size on one of its cells, then moves numbers around inside their
     * islands until the logic solves it.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        var best: Triple<BooleanArray, IntArray, Int>? = null
        while (best == null || !past(end)) {
            val island = layout(n, maxIsland(d), random) ?: continue
            val sea = BooleanArray(n * n) { island[it] < 0 }
            val members = island.indices.filter { island[it] >= 0 }.groupBy { island[it] }.values.toMutableList()
            val clues = IntArray(n * n)
            for (cells in members) clues[cells.random(random)] = cells.size
            fun solve() = NurikabeLogic(n, clues).run(IntArray(n * n) { UNKNOWN })
            var result = solve()
            var stuck = result.unresolved.size
            var tries = 0
            while (stuck > 0 && tries++ < 120 && !past(end)) {
                // Where the sea is stuck, a new "1" island can settle it.
                val lone = result.unresolved.filter { i ->
                    sea[i] && orth(i, n).all { sea[it] } && connected(n) { it != i && sea[it] }
                }
                if (lone.isNotEmpty() && random.nextBoolean()) {
                    val i = lone.random(random)
                    sea[i] = false
                    clues[i] = 1
                    val next = solve()
                    if (next.unresolved.size < stuck) {
                        members += listOf(i)
                        result = next
                        stuck = next.unresolved.size
                    } else {
                        sea[i] = true
                        clues[i] = 0
                    }
                    continue
                }
                // Otherwise move an island's number to another of its cells.
                val cells = members.random(random)
                if (cells.size == 1) continue
                val from = cells.first { clues[it] > 0 }
                val to = cells.filter { it != from }.random(random)
                clues[to] = clues[from]
                clues[from] = 0
                val next = solve()
                if (next.unresolved.size <= stuck) {
                    result = next
                    stuck = next.unresolved.size
                } else {
                    clues[from] = clues[to]
                    clues[to] = 0
                }
            }
            if (best == null || stuck < best.third) best = Triple(sea, clues.copyOf(), stuck)
            if (stuck == 0) break
        }
        val (solution, clues, _) = best!!
        val givens = BooleanArray(n * n)
        val logic = NurikabeLogic(n, clues)
        while (true) {
            val start = IntArray(n * n) { if (givens[it]) (if (solution[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            // If the logic stopped with nothing left open, fall back to any cell not yet given.
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] } }
            givens[open.randomOrNull(random) ?: break] = true
        }
        return ShadePuzzle(Variant.NURIKABE, d, n, solution, givens, numbers = clues)
    }

    /** Island number of each cell, -1 for sea. */
    private fun layout(n: Int, maxSize: Int, random: Random): IntArray? {
        val island = IntArray(n * n) { -1 }
        var count = 0
        fun touchesOther(i: Int, g: Int) = orth(i, n).any { island[it] >= 0 && island[it] != g }
        fun seaOk() = connected(n) { island[it] < 0 }
        val target = n * n * 45 / 100
        for (seed in (0 until n * n).shuffled(random)) {
            if (island.count { it >= 0 } >= target) break
            if (island[seed] >= 0 || touchesOther(seed, count)) continue
            island[seed] = count
            if (!seaOk()) { island[seed] = -1; continue }
            val want = 1 + random.nextInt(maxSize)
            var size = 1
            while (size < want) {
                val options = island.indices.filter { i ->
                    island[i] < 0 && orth(i, n).any { island[it] == count } && !touchesOther(i, count)
                }.shuffled(random)
                val next = options.firstOrNull { i -> island[i] = count; val ok = seaOk(); island[i] = -1; ok } ?: break
                island[next] = count
                size++
            }
            count++
        }
        // Break up 2×2 blocks of sea: add a cell to a neighbouring island or start a new one.
        for (r in 0 until n - 1) for (c in 0 until n - 1) {
            val block = listOf(r * n + c, r * n + c + 1, (r + 1) * n + c, (r + 1) * n + c + 1)
            if (block.any { island[it] >= 0 }) continue
            val fixed = block.shuffled(random).any { i ->
                val near = orth(i, n).map { island[it] }.filter { it >= 0 }.distinct()
                val g = when {
                    near.isEmpty() -> count
                    near.size == 1 && island.count { it == near[0] } < maxSize + 1 -> near[0]
                    else -> return@any false
                }
                island[i] = g
                if (seaOk()) { if (g == count) count++; true } else { island[i] = -1; false }
            }
            if (!fixed) return null
        }
        return island
    }
}

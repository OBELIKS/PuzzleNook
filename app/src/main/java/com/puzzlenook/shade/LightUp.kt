package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/** `ShadePuzzle.numbers` of Light Up: a plain wall (numbered walls hold 0..4, white cells `-1`). */
const val WALL = 9

/**
 * Light Up logic. Walls (`numbers >= 0`) are fixed; white cells hold a bulb ([SHADED]) or no bulb
 * ([WHITE]). A bulb lights its row and column up to the next wall; every white cell must be lit,
 * no bulb may light another, and a numbered wall has that many bulbs next to it.
 *
 * Levels (0 = Easy … 3 = Expert):
 * - 0: a bulb leaves the cells it lights bulb-free; a numbered wall that has enough bulbs leaves the
 *   rest free, one whose open neighbours are all needed gets bulbs; an unlit cell with a single
 *   possible lighter gets that bulb;
 * - 1: adds look-ahead on a single cell: no bulb where it would take the last lighter of another
 *   cell, or leave a numbered wall too few free neighbours;
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most `TRIAL_SWEEPS` sweeps of
 *   the level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on a cell, and on every way
 *   to place a numbered wall's missing bulbs, settle what all non-contradicting options share.
 *
 * Marks: bulb = FILLED, no bulb = CROSSED. Walls are locked and `ShadeState.isBlocked` makes strokes skip them.
 */
class LightUpLogic(private val n: Int, private val numbers: IntArray, private val level: Int) {
    private val cells = n * n
    private val isWall = BooleanArray(cells) { numbers[it] >= 0 }

    /** For each white cell: itself plus the white cells in its row and column up to the walls. */
    private val see: Array<IntArray> = Array(cells) { i -> if (isWall[i]) IntArray(0) else lineOfSight(i) }
    private val seeM = BooleanArray(cells * cells).also { m -> for (i in 0 until cells) for (j in see[i]) m[i * cells + j] = true }
    private val numberedWalls = (0 until cells).filter { numbers[it] in 0..4 }
    private val wallNb: Map<Int, IntArray> = numberedWalls.associateWith { w -> orth(w, n).filter { !isWall[it] }.toIntArray() }
    private val whites = (0 until cells).filter { !isWall[it] }

    private fun lineOfSight(i: Int): IntArray {
        val out = ArrayList<Int>()
        out += i
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            var r = i / n + dr
            var c = i % n + dc
            while (r in 0 until n && c in 0 until n && !isWall[r * n + c]) {
                out += r * n + c
                r += dr
                c += dc
            }
        }
        return out.toIntArray()
    }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        for (i in 0 until cells) if (isWall[i]) s[i] = WHITE
        val round = IntArray(cells) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = propagate(s, level >= 1, false)
            if (res == NONE && level >= 2) res = if (level >= 3) branches(s) else whatIf(s)
            if (res == CONTRA) return ShadeResult(false, s, round)
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (res == NONE) return ShadeResult(false, s, round)
        }
    }

    private fun valid(s: IntArray): Boolean {
        for (w in numberedWalls) if (wallNb.getValue(w).count { s[it] == SHADED } != numbers[w]) return false
        for (c in whites) {
            val bulbs = see[c].count { s[it] == SHADED }
            if (bulbs == 0) return false
            if (s[c] == SHADED && bulbs > 1) return false
        }
        return true
    }

    /** Runs [basic] (and [ahead]) sweeps until nothing changes, at most [maxSweeps] of them; a contradiction counts only within those. */
    private fun propagate(s: IntArray, lookAhead: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(s)
            if (res == NONE && lookAhead) res = ahead(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (b in whites) {
            if (s[b] != SHADED) continue
            for (j in see[b]) {
                if (j == b) continue
                if (s[j] == SHADED) return CONTRA
                if (s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
            }
        }
        for (w in numberedWalls) {
            var bulbs = 0
            var open = 0
            for (j in wallNb.getValue(w)) if (s[j] == SHADED) bulbs++ else if (s[j] == UNKNOWN) open++
            val k = numbers[w]
            if (bulbs > k || bulbs + open < k) return CONTRA
            if (open == 0) continue
            val v = if (bulbs == k) WHITE else if (bulbs + open == k) SHADED else continue
            for (j in wallNb.getValue(w)) if (s[j] == UNKNOWN) s[j] = v
            changed = true
        }
        for (c in whites) {
            var lit = false
            var open = 0
            var last = -1
            for (j in see[c]) {
                if (s[j] == SHADED) { lit = true; break }
                if (s[j] == UNKNOWN) { open++; last = j }
            }
            if (lit) continue
            if (open == 0) return CONTRA
            if (open == 1) { s[last] = SHADED; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: a bulb on [x] is impossible if it would strand an unlit cell or a numbered wall. */
    private fun ahead(s: IntArray): Int {
        var changed = false
        // Unlit cells whose every possible lighter x would also light: x can't hold a bulb.
        for (y in whites) {
            if (see[y].any { s[it] == SHADED }) continue
            val cands = see[y].filter { s[it] == UNKNOWN }
            if (cands.isEmpty()) return CONTRA
            for (x in see[cands[0]]) {
                if (s[x] != UNKNOWN || seeM[x * cells + y]) continue
                if (cands.all { seeM[x * cells + it] }) { s[x] = WHITE; changed = true }
            }
        }
        // A numbered wall that needs bulbs among open neighbours: x can't take them all out.
        for (w in numberedWalls) {
            val nb = wallNb.getValue(w)
            val need = numbers[w] - nb.count { s[it] == SHADED }
            val open = nb.filter { s[it] == UNKNOWN }
            if (need <= 0 || open.isEmpty()) continue
            for (x in whites) {
                if (s[x] != UNKNOWN || x in open) continue
                val left = open.count { !seeM[x * cells + it] }
                if (left < need) { s[x] = WHITE; changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in whites) {
            if (s[i] != UNKNOWN) continue
            val bad = BooleanArray(2)
            for (v in intArrayOf(WHITE, SHADED)) {
                val t = s.copyOf()
                t[i] = v
                bad[v] = propagate(t, false, true, TRIAL_SWEEPS) == CONTRA
            }
            if (bad[WHITE] && bad[SHADED]) return CONTRA
            if (bad[WHITE]) { s[i] = SHADED; changed = true }
            else if (bad[SHADED]) { s[i] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /**
     * Level 3: branch intersection. Choice points are an undecided cell (bulb / no bulb) and a numbered
     * wall with open neighbours (every way to place its missing bulbs). Each option is propagated with
     * the level 1 steps; options that contradict drop out and whatever all survivors agree on is settled.
     */
    private fun branches(s: IntArray): Int {
        var changed = false
        for (w in numberedWalls) {
            val open = wallNb.getValue(w).filter { s[it] == UNKNOWN }
            if (open.isEmpty()) continue
            val need = numbers[w] - wallNb.getValue(w).count { s[it] == SHADED }
            if (need < 0 || need > open.size) return CONTRA
            val options = ArrayList<List<Pair<Int, Int>>>()
            for (mask in 0 until (1 shl open.size)) {
                if (Integer.bitCount(mask) != need) continue
                options += open.indices.map { open[it] to if (mask shr it and 1 == 1) SHADED else WHITE }
            }
            if (options.size < 2) continue
            val res = intersect(s, options)
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        for (i in whites) {
            if (s[i] != UNKNOWN) continue
            val res = intersect(s, listOf(listOf(i to WHITE), listOf(i to SHADED)))
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        return if (changed) CHANGED else NONE
    }

    /** Settles into [s] every cell on which all non-contradicting [options] agree. */
    private fun intersect(s: IntArray, options: List<List<Pair<Int, Int>>>): Int {
        val common = IntArray(cells)
        var alive = 0
        for (o in options) {
            val t = s.copyOf()
            var ok = true
            for ((c, v) in o) if (t[c] == UNKNOWN) t[c] = v else if (t[c] != v) { ok = false; break }
            if (!ok || propagate(t, true, true) == CONTRA) continue
            if (alive++ == 0) System.arraycopy(t, 0, common, 0, cells)
            else for (j in 0 until cells) if (common[j] != t[j]) common[j] = UNKNOWN
        }
        if (alive == 0) return CONTRA
        var changed = false
        for (j in 0 until cells) if (s[j] == UNKNOWN && common[j] != UNKNOWN) { s[j] = common[j]; changed = true }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        /** Level 2 what-if: a trial runs at most this many sweeps of the level 0 steps (tuned). */
        internal const val TRIAL_SWEEPS = 1

        fun forLevel(d: Difficulty, n: Int, numbers: IntArray) = LightUpLogic(n, numbers, d.ordinal)

        /** Cells lit by the bulbs in [bulb] (a bulb lights its own cell, row and column up to walls). */
        fun lit(n: Int, numbers: IntArray, bulb: (Int) -> Boolean): BooleanArray {
            val out = BooleanArray(n * n)
            for (b in 0 until n * n) {
                if (numbers[b] >= 0 || !bulb(b)) continue
                out[b] = true
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    var r = b / n + dr
                    var c = b % n + dc
                    while (r in 0 until n && c in 0 until n && numbers[r * n + c] < 0) {
                        out[r * n + c] = true
                        r += dr
                        c += dc
                    }
                }
            }
            return out
        }

        /** Whether the bulb at [b] lights another bulb. */
        fun clashes(n: Int, numbers: IntArray, b: Int, bulb: (Int) -> Boolean): Boolean {
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                var r = b / n + dr
                var c = b % n + dc
                while (r in 0 until n && c in 0 until n && numbers[r * n + c] < 0) {
                    if (bulb(r * n + c)) return true
                    r += dr
                    c += dc
                }
            }
            return false
        }
    }
}

object LightUpGenerator {
    fun size(d: Difficulty) = intArrayOf(7, 8, 10, 12)[d.ordinal]

    /** Numbered walls to keep at least: easier levels stop removing numbers early. */
    fun minClues(d: Difficulty, walls: Int) = when (d) {
        Difficulty.EASY -> (walls * 0.6).toInt()
        Difficulty.MEDIUM -> (walls * 0.4).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val wall: BooleanArray, val bulb: BooleanArray, val numbers: IntArray)

    /** Random walls, bulbs placed at random until every white cell is lit, every wall numbered. */
    private fun layout(n: Int, random: Random, density: Double): Layout {
        val wall = BooleanArray(n * n) { random.nextDouble() < density }
        val bare = IntArray(n * n) { if (wall[it]) WALL else -1 }
        val bulb = BooleanArray(n * n)
        while (true) {
            val lit = LightUpLogic.lit(n, bare) { bulb[it] }
            val unlit = (0 until n * n).filter { !wall[it] && !lit[it] }
            if (unlit.isEmpty()) break
            val c = unlit.random(random)
            // Every cell that lights c is itself unlit, so the new bulb meets no other bulb.
            val spots = unlit.filter { j -> LightUpLogic.lit(n, bare) { it == j }[c] }
            bulb[spots.random(random)] = true
        }
        val numbers = IntArray(n * n) { i ->
            if (wall[i]) orth(i, n).count { bulb[it] } else -1
        }
        return Layout(n, wall, bulb, numbers)
    }

    private fun removeNumbers(d: Difficulty, n: Int, numbers: IntArray, start: IntArray, random: Random, end: Long) {
        val walls = numbers.indices.filter { numbers[it] >= 0 }
        var left = walls.count { numbers[it] in 0..4 }
        val floor = minClues(d, walls.size)
        for (i in walls.shuffled(random)) {
            if (past(end) || left <= floor) break
            val old = numbers[i]
            numbers[i] = WALL
            if (!LightUpLogic.forLevel(d, n, numbers).run(start).solved) numbers[i] = old else left--
        }
    }

    private fun needsTop(d: Difficulty, n: Int, numbers: IntArray, start: IntArray) =
        d.ordinal < 2 || !LightUpLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, numbers).run(start).solved

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        // Expert only prefers its top level: a valid puzzle is taken after one second.
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        val unknown = IntArray(n * n) { UNKNOWN }
        var best: ShadePuzzle? = null
        var fallback: Layout? = null
        do {
            val l = layout(n, random, 0.16 + random.nextDouble() * 0.06)
            if (l.wall.count { it } < n) continue
            if (fallback == null) fallback = l
            if (!LightUpLogic.forLevel(d, n, l.numbers).run(unknown).solved) continue
            removeNumbers(d, n, l.numbers, unknown, random, end)
            val p = ShadePuzzle(Variant.LIGHT_UP, d, n, l.bulb, BooleanArray(n * n), numbers = l.numbers)
            if (needsTop(d, n, l.numbers, unknown)) return p
            best = p
        } while (!past(end) && !(best != null && past(preferEnd)))
        best?.let { return it }
        // Nothing was solvable in time: give cells away until the all-numbers puzzle solves, then thin the numbers.
        val l = fallback ?: layout(n, random, 0.18)
        val givens = BooleanArray(n * n)
        val logic = LightUpLogic.forLevel(d, n, l.numbers)
        var start: IntArray
        while (true) {
            start = IntArray(n * n) { if (givens[it]) (if (l.bulb[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] && !l.wall[it] } }
            givens[open.filter { l.bulb[it] }.randomOrNull(random) ?: open.randomOrNull(random) ?: break] = true
        }
        removeNumbers(d, n, l.numbers, start, random, deadline(budgetMs))
        return ShadePuzzle(Variant.LIGHT_UP, d, n, l.bulb, givens, numbers = l.numbers)
    }
}

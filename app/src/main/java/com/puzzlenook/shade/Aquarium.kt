package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Aquarium logic. [regions] are the tanks. Water ([SHADED]) lies flat in a tank: every tank has a water
 * level, a row, and its cells from that row down are water, the cells above stay [WHITE]. [counts]
 * (rows 0..n-1, then columns; `-1` hidden) give the water cells per line.
 *
 * A tank's state is its set of still possible levels (a bit mask), worked out from its decided cells.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: the possible levels of each tank settle its cells (water pulls everything level and below it
 *   down, air everything above), a line that has its water frees the rest, one whose open cells are all
 *   needed gets water;
 * - 1: adds combined tank/line limits: for a line, every way its tanks can contribute water is tried
 *   against its count, so levels no combination can use are dropped;
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most `TRIAL_SWEEPS` sweeps of
 *   the level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on every tank (each of its
 *   possible levels) and on single cells, settle what all non-contradicting options share.
 *
 * Each tank row is one run of cells; counts work as in Tents (`ShadeState.counts`, margin). `ShadeState.aquariumClash`
 * flags clashes from the player's own marks. The independent brute force for tests is `Rules` in test
 * `ShadeRules.kt` (Aquarium: levels per tank).
 */
class AquariumLogic(private val n: Int, private val regions: IntArray, private val counts: IntArray, private val level: Int) {
    private val cells = n * n
    private val tankCount = (regions.maxOrNull() ?: -1) + 1
    private val tankCells: List<IntArray> = (0 until tankCount).map { g -> (0 until cells).filter { regions[it] == g }.toIntArray() }
    private val minRow = IntArray(tankCount) { t -> tankCells[t].minOf { it / n } }

    /** Possible levels of tank t are bits 0..levels-1; bit b means water from row minRow + b down. */
    private val levels = IntArray(tankCount) { t -> tankCells[t].maxOf { it / n } - minRow[t] + 2 }
    private val lines: Array<IntArray> = Array(2 * n) { k ->
        if (k < n) IntArray(n) { k * n + it } else IntArray(n) { it * n + (k - n) }
    }

    /** For each line the tanks that reach it, with each tank's cells on the line as row offsets inside the tank. */
    private class OnLine(val tank: Int, val offsets: IntArray)

    private val lineTanks: Array<List<OnLine>> = Array(2 * n) { k ->
        lines[k].map { regions[it] }.distinct().map { t ->
            OnLine(t, lines[k].filter { regions[it] == t }.map { it / n - minRow[t] }.toIntArray())
        }
    }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
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
        for (t in 0 until tankCount) if (feasible(t, s) == 0) return false
        for (k in 0 until 2 * n) if (counts[k] >= 0 && lines[k].count { s[it] == SHADED } != counts[k]) return false
        return true
    }

    /** The levels of tank [t] that agree with its decided cells. */
    private fun feasible(t: Int, s: IntArray): Int {
        var mask = (1 shl levels[t]) - 1
        for (c in tankCells[t]) {
            if (s[c] == UNKNOWN) continue
            val below = (1 shl (c / n - minRow[t] + 1)) - 1
            mask = mask and if (s[c] == SHADED) below else below.inv()
        }
        return mask
    }

    /** Settles the undecided cells of tank [t] that all levels in [mask] agree on; returns whether any changed. */
    private fun derive(t: Int, mask: Int, s: IntArray): Boolean {
        var changed = false
        for (c in tankCells[t]) {
            if (s[c] != UNKNOWN) continue
            val below = (1 shl (c / n - minRow[t] + 1)) - 1
            if (mask and below.inv() == 0) { s[c] = SHADED; changed = true }
            else if (mask and below == 0) { s[c] = WHITE; changed = true }
        }
        return changed
    }

    private fun propagate(s: IntArray, combined: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(s)
            if (res == NONE && combined) res = combine(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (t in 0 until tankCount) {
            val mask = feasible(t, s)
            if (mask == 0) return CONTRA
            if (derive(t, mask, s)) changed = true
        }
        for (k in 0 until 2 * n) {
            val c = counts[k]
            if (c < 0) continue
            var water = 0
            var open = 0
            for (j in lines[k]) if (s[j] == SHADED) water++ else if (s[j] == UNKNOWN) open++
            if (water > c || water + open < c) return CONTRA
            if (open == 0) continue
            val v = if (water == c) WHITE else if (water + open == c) SHADED else continue
            for (j in lines[k]) if (s[j] == UNKNOWN) s[j] = v
            changed = true
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: for each counted line, which levels of its tanks fit its count together. */
    private fun combine(s: IntArray): Int {
        var changed = false
        for (k in 0 until 2 * n) {
            val c = counts[k]
            if (c < 0) continue
            val on = lineTanks[k]
            val masks = IntArray(on.size) { feasible(on[it].tank, s) }
            // Water a tank puts on the line at each of its levels: its cells there that sit at or below the level.
            val give = Array(on.size) { i -> IntArray(levels[on[i].tank]) { li -> on[i].offsets.count { it >= li } } }
            val sums = IntArray(on.size) { i ->
                var m = 0
                for (li in 0 until levels[on[i].tank]) if (masks[i] shr li and 1 == 1) m = m or (1 shl give[i][li])
                m
            }
            val prefix = IntArray(on.size + 1)
            prefix[0] = 1
            for (i in on.indices) prefix[i + 1] = convolve(prefix[i], sums[i])
            val suffix = IntArray(on.size + 1)
            suffix[on.size] = 1
            for (i in on.indices.reversed()) suffix[i] = convolve(suffix[i + 1], sums[i])
            if (prefix[on.size] shr c and 1 == 0) return CONTRA
            for (i in on.indices) {
                val others = convolve(prefix[i], suffix[i + 1])
                var keep = 0
                for (li in 0 until levels[on[i].tank]) {
                    if (masks[i] shr li and 1 == 0) continue
                    val need = c - give[i][li]
                    if (need >= 0 && others shr need and 1 == 1) keep = keep or (1 shl li)
                }
                if (keep == 0) return CONTRA
                if (keep != masks[i] && derive(on[i].tank, keep, s)) changed = true
            }
        }
        return if (changed) CHANGED else NONE
    }

    private fun convolve(a: Int, b: Int): Int {
        var out = 0
        var x = a
        while (x != 0) {
            val v = Integer.numberOfTrailingZeros(x)
            out = out or (b shl v)
            x = x and (x - 1)
        }
        return out
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
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
     * Level 3: branch intersection. Choice points are every tank with several possible levels (one option
     * per level) and every undecided cell. Each option is propagated with the level 1 steps; options that
     * contradict drop out and whatever all survivors agree on is settled.
     */
    private fun branches(s: IntArray): Int {
        var changed = false
        for (t in 0 until tankCount) {
            val mask = feasible(t, s)
            if (Integer.bitCount(mask) < 2) continue
            val options = ArrayList<List<Pair<Int, Int>>>()
            for (li in 0 until levels[t]) {
                if (mask shr li and 1 == 0) continue
                options += tankCells[t].filter { s[it] == UNKNOWN }.map { it to if (it / n - minRow[t] >= li) SHADED else WHITE }
            }
            val res = intersect(s, options)
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        for (i in 0 until cells) {
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
            for ((c, v) in o) t[c] = v
            if (propagate(t, true, true) == CONTRA) continue
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

        /** Level 2 what-if: a trial runs at most this many sweeps of the level 0 steps. */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int, regions: IntArray, counts: IntArray) = AquariumLogic(n, regions, counts, d.ordinal)
        fun forLevel(level: Int, n: Int, regions: IntArray, counts: IntArray) = AquariumLogic(n, regions, counts, level)
    }
}

object AquariumGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** Counts to keep at least (of the 2n lines): easier levels stop hiding counts early. */
    fun minClues(d: Difficulty, n: Int) = when (d) {
        Difficulty.EASY -> (2 * n * 0.8).toInt()
        Difficulty.MEDIUM -> (2 * n * 0.6).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val regions: IntArray, val water: BooleanArray, val counts: IntArray)

    /** Biggest tank the growth aims for. */
    private const val CAP = 9

    /**
     * Tanks grown from random seeds so that each tank's cells in a row are one run (a water level is
     * then a straight line), random water levels, counts from the water.
     */
    private fun layout(n: Int, random: Random): Layout {
        val cells = n * n
        val region = IntArray(cells) { -1 }
        val size = ArrayList<Int>()
        val rowHas = ArrayList<BooleanArray>()
        fun add(c: Int, t: Int) {
            region[c] = t
            size[t]++
            rowHas[t][c / n] = true
        }
        fun newTank(c: Int) {
            size += 0
            rowHas += BooleanArray(n)
            add(c, size.size - 1)
        }
        val seeds = maxOf(3, (cells / (4.5 + random.nextDouble() * 2.5)).toInt())
        for (c in (0 until cells).shuffled(random).take(seeds)) newTank(c)
        while (true) {
            val free = (0 until cells).filter { region[it] < 0 }
            if (free.isEmpty()) break
            fun candidates(cap: Boolean): List<Pair<Int, Int>> {
                val out = ArrayList<Pair<Int, Int>>()
                for (j in free) for (nb in orth(j, n)) {
                    val t = region[nb]
                    if (t < 0 || (cap && size[t] >= CAP)) continue
                    val horizontal = (j % n > 0 && region[j - 1] == t) || (j % n < n - 1 && region[j + 1] == t)
                    if (!rowHas[t][j / n] || horizontal) out += j to t
                }
                return out
            }
            val pick = candidates(true).ifEmpty { candidates(false) }.randomOrNull(random)
            if (pick != null) add(pick.first, pick.second) else newTank(free.random(random))
        }
        val water = BooleanArray(cells)
        val tanks = size.size
        for (t in 0 until tanks) {
            val tc = (0 until cells).filter { region[it] == t }
            val lo = tc.minOf { it / n }
            val hi = tc.maxOf { it / n }
            val l = lo + random.nextInt(hi - lo + 2)
            for (c in tc) if (c / n >= l) water[c] = true
        }
        val counts = IntArray(2 * n) { k ->
            if (k < n) (0 until n).count { water[k * n + it] } else (0 until n).count { water[it * n + (k - n)] }
        }
        return Layout(n, region, water, counts)
    }

    private fun removeCounts(d: Difficulty, l: Layout, counts: IntArray, start: IntArray, random: Random, end: Long) {
        var left = counts.count { it >= 0 }
        val floor = minClues(d, l.n)
        for (k in (0 until 2 * l.n).shuffled(random)) {
            if (past(end) || left <= floor) break
            val old = counts[k]
            counts[k] = -1
            if (!AquariumLogic.forLevel(d, l.n, l.regions, counts).run(start).solved) counts[k] = old else left--
        }
    }

    private fun needsTop(d: Difficulty, l: Layout, counts: IntArray, start: IntArray) =
        d.ordinal < 2 || !AquariumLogic.forLevel(d.ordinal - 1, l.n, l.regions, counts).run(start).solved

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        // Easy and Medium look for a given-free layout for a short while, then give cells away; Expert only prefers its top level.
        val quitEnd = if (d == Difficulty.EASY || d == Difficulty.MEDIUM) deadline(minOf(budgetMs, 500)) else end
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        val unknown = IntArray(n * n) { UNKNOWN }
        var best: ShadePuzzle? = null
        var fallback: Layout? = null
        do {
            val l = layout(n, random)
            if (fallback == null) fallback = l
            if (!AquariumLogic.forLevel(d, n, l.regions, l.counts).run(unknown).solved) continue
            val counts = l.counts.copyOf()
            removeCounts(d, l, counts, unknown, random, end)
            val p = ShadePuzzle(Variant.AQUARIUM, d, n, l.water, BooleanArray(n * n), regions = l.regions, counts = counts)
            if (needsTop(d, l, counts, unknown)) return p
            best = p
        } while (!past(end) && !(best != null && past(preferEnd)) && !past(quitEnd))
        best?.let { return it }
        // No layout solved on its own: give cells away until the full-count puzzle solves, then hide counts.
        val l = fallback ?: layout(n, random)
        val givens = BooleanArray(n * n)
        val logic = AquariumLogic.forLevel(d, n, l.regions, l.counts)
        var start: IntArray
        while (true) {
            start = IntArray(n * n) { if (givens[it]) (if (l.water[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] } }
            givens[open.randomOrNull(random) ?: break] = true
        }
        val counts = l.counts.copyOf()
        removeCounts(d, l, counts, start, random, deadline(budgetMs))
        return ShadePuzzle(Variant.AQUARIUM, d, n, l.water, givens, regions = l.regions, counts = counts)
    }
}

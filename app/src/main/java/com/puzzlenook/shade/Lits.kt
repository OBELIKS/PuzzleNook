package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.math.abs
import kotlin.random.Random

/** Tetromino helpers shared by the LITS logic, generator and player-mark checks. */
internal object Tetromino {
    /** Shape id of four cells: the smallest code over the eight rotations and reflections. */
    fun shape(cells: IntArray, n: Int): Int {
        var best = Int.MAX_VALUE
        for (t in 0 until 8) {
            val rs = IntArray(4)
            val cs = IntArray(4)
            for (k in 0 until 4) {
                var r = cells[k] / n
                var c = cells[k] % n
                if (t and 1 != 0) { val x = r; r = c; c = x }
                if (t and 2 != 0) r = -r
                if (t and 4 != 0) c = -c
                rs[k] = r
                cs[k] = c
            }
            val r0 = rs.min()
            val c0 = cs.min()
            val codes = IntArray(4) { (rs[it] - r0) * 4 + (cs[it] - c0) }
            codes.sort()
            var code = 0
            for (x in codes) code = code * 16 + x
            if (code < best) best = code
        }
        return best
    }

    /** Connected four-cell subsets of [region] that are not a 2×2 square, each as a sorted cell array. */
    fun placements(n: Int, region: IntArray): List<IntArray> {
        val inside = HashSet<Int>()
        for (c in region) inside += c
        fun key(a: IntArray) = a.fold(0) { acc, x -> acc * 128 + x }
        var current: Collection<IntArray> = region.map { intArrayOf(it) }
        repeat(3) {
            val next = HashMap<Int, IntArray>()
            for (p in current) for (c in p) for (j in orth(c, n)) {
                if (j !in inside || p.contains(j)) continue
                val q = (p + j).also { it.sort() }
                next[key(q)] = q
            }
            current = next.values
        }
        return current.filter { p -> !(p.maxOf { it / n } - p.minOf { it / n } == 1 && p.maxOf { it % n } - p.minOf { it % n } == 1) }
    }
}

/**
 * LITS logic. [regions] each hold exactly one tetromino of shaded cells ([SHADED], the rest [WHITE]); the
 * shaded cells form one area, never fill a 2×2 square, and two equal tetrominoes (rotations and
 * reflections count as equal) never touch across a region border.
 *
 * Every region's state is its set of still possible placements.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: placements are kept when they fit the region's own marks and make no 2×2 square with shaded cells;
 *   cells in every placement are shaded, cells in none are white;
 * - 1: adds the shape rule and connectivity: a placement needs, in every neighbouring region, a placement
 *   that fits beside it (no equal touching shapes, no 2×2 across the border) and a shaded neighbour
 *   cell; an open cell that the shaded cells can only be joined through is shaded;
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most `TRIAL_SWEEPS` sweeps of
 *   the level 1 steps (not level 0 as in the other games: level 1 is so much stronger here that a
 *   level 0 trial almost never adds anything) and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on every region with several
 *   placements left (one option per placement) and on single cells, settle what all non-contradicting
 *   options share.
 */
class LitsLogic(private val n: Int, private val regions: IntArray, private val level: Int) {
    private val cells = n * n
    private val regionCount = (regions.maxOrNull() ?: -1) + 1
    private val regionCells: List<IntArray> = (0 until regionCount).map { g -> (0 until cells).filter { regions[it] == g }.toIntArray() }
    private val orthNb = Array(cells) { orth(it, n) }

    /** [blocks] are the 2×2 squares touching the placement, [others] their cells outside it (all shaded would complete a square). */
    private class Pl(val cells: IntArray, val shape: Int, val blocks: Array<IntArray>, val touching: IntArray, val others: Array<IntArray>)

    private val pls: Array<Array<Pl>> = Array(regionCount) { g ->
        Tetromino.placements(n, regionCells[g]).map { p ->
            val blocks = LinkedHashMap<Int, IntArray>()
            for (c in p) {
                val r = c / n
                val col = c % n
                for (dr in -1..0) for (dc in -1..0) {
                    if (r + dr < 0 || col + dc < 0 || r + dr + 1 >= n || col + dc + 1 >= n) continue
                    val tl = (r + dr) * n + col + dc
                    blocks[tl] = intArrayOf(tl, tl + 1, tl + n, tl + n + 1)
                }
            }
            val touching = p.flatMap { orthNb[it].toList() }.filter { it !in p }.distinct().toIntArray()
            val blockArray = blocks.values.toTypedArray()
            Pl(p, Tetromino.shape(p, n), blockArray, touching, Array(blockArray.size) { b -> blockArray[b].filter { it !in p }.toIntArray() })
        }.toTypedArray()
    }

    /** Regions with a cell next to one of region g (king moves, since 2×2 squares reach across corners). */
    private val nbRegions: Array<IntArray> = Array(regionCount) { g ->
        regionCells[g].flatMap { c -> king(c, n).map { regions[it] } }.filter { it != g }.distinct().toIntArray()
    }

    private val compatCache = arrayOfNulls<Array<BooleanArray>>(regionCount * regionCount)

    /** compat(a, b)[p][q]: placement p of region a and q of region b can both stand. */
    private fun compat(a: Int, b: Int): Array<BooleanArray> = compatCache[a * regionCount + b] ?: Array(pls[a].size) { p ->
        BooleanArray(pls[b].size) { q -> compatible(pls[a][p], pls[b][q]) }
    }.also { compatCache[a * regionCount + b] = it }

    private fun compatible(p: Pl, q: Pl): Boolean {
        if (p.shape == q.shape) {
            for (x in p.cells) for (y in q.cells) if (abs(x / n - y / n) + abs(x % n - y % n) == 1) return false
        }
        for (b in p.blocks) {
            var full = true
            for (x in b) if (!p.cells.contains(x) && !q.cells.contains(x)) { full = false; break }
            if (full) return false
        }
        return true
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
        val chosen = IntArray(regionCount)
        for (g in 0 until regionCount) {
            val shadedHere = regionCells[g].count { s[it] == SHADED }
            val k = pls[g].indexOfFirst { p -> shadedHere == 4 && p.cells.all { s[it] == SHADED } }
            if (k < 0) return false
            chosen[g] = k
            for (b in pls[g][k].blocks) if (b.all { s[it] == SHADED }) return false
        }
        for (a in 0 until regionCount) for (b in nbRegions[a]) if (b > a && !compatible(pls[a][chosen[a]], pls[b][chosen[b]])) return false
        return connected(n) { s[it] == SHADED }
    }

    /** Runs [basic] (and [advanced]) sweeps until nothing changes, at most [maxSweeps] of them; a contradiction counts only within those. */
    private fun propagate(s: IntArray, shapes: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(s)
            if (res == NONE && shapes) res = advanced(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    /** Placements of each region that fit its own marks and make no 2×2 square with shaded cells. */
    private fun fitting(s: IntArray): Array<BooleanArray> = Array(regionCount) { g ->
        var shadedHere = 0
        for (c in regionCells[g]) if (s[c] == SHADED) shadedHere++
        BooleanArray(pls[g].size) { k ->
            val p = pls[g][k]
            var ok = true
            var sh = 0
            for (c in p.cells) {
                if (s[c] == WHITE) { ok = false; break }
                if (s[c] == SHADED) sh++
            }
            if (ok && sh != shadedHere) ok = false
            if (ok) for (o in p.others) {
                var full = true
                for (x in o) if (s[x] != SHADED) { full = false; break }
                if (full) { ok = false; break }
            }
            ok
        }
    }

    /** Settles the cells every (or no) alive placement of each region uses. */
    private fun derive(s: IntArray, alive: Array<BooleanArray>): Int {
        var changed = false
        val use = IntArray(cells)
        for (g in 0 until regionCount) {
            var count = 0
            for (k in pls[g].indices) if (alive[g][k]) {
                count++
                for (c in pls[g][k].cells) use[c]++
            }
            if (count == 0) return CONTRA
            for (c in regionCells[g]) {
                if (s[c] != UNKNOWN) continue
                if (use[c] == 0) { s[c] = WHITE; changed = true }
                else if (use[c] == count) { s[c] = SHADED; changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    private fun basic(s: IntArray): Int = derive(s, fitting(s))

    /** Level 1: shape rule between neighbouring regions, a shaded neighbour for every placement, connectivity. */
    private fun advanced(s: IntArray): Int {
        val alive = fitting(s)
        var again = true
        while (again) {
            again = false
            val can = BooleanArray(cells)
            for (g in 0 until regionCount) for (k in pls[g].indices) if (alive[g][k]) for (c in pls[g][k].cells) can[c] = true
            for (a in 0 until regionCount) {
                var left = 0
                for (k in pls[a].indices) {
                    if (!alive[a][k]) continue
                    var ok = true
                    for (b in nbRegions[a]) {
                        val m = compat(a, b)[k]
                        var found = false
                        for (q in pls[b].indices) if (alive[b][q] && m[q]) { found = true; break }
                        if (!found) { ok = false; break }
                    }
                    if (ok && regionCount > 1) ok = pls[a][k].touching.any { can[it] && regions[it] != a }
                    if (!ok) { alive[a][k] = false; again = true } else left++
                }
                if (left == 0) return CONTRA
            }
        }
        val r1 = derive(s, alive)
        if (r1 == CONTRA) return CONTRA
        val r2 = connectivity(s)
        if (r2 == CONTRA) return CONTRA
        return if (r1 == CHANGED || r2 == CHANGED) CHANGED else NONE
    }

    /** The shaded cells must stay joinable: an open cell they can only be joined through is shaded. */
    private fun connectivity(s: IntArray): Int {
        val cut = anchorCuts(n, orthNb, { s[it] != WHITE }, { s[it] == SHADED }) ?: return CONTRA
        var changed = false
        for (u in 0 until cells) if (cut[u] && s[u] == UNKNOWN) { s[u] = SHADED; changed = true }
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN) continue
            val bad = BooleanArray(2)
            for (v in intArrayOf(WHITE, SHADED)) {
                val t = s.copyOf()
                t[i] = v
                bad[v] = propagate(t, true, true, TRIAL_SWEEPS) == CONTRA
            }
            if (bad[WHITE] && bad[SHADED]) return CONTRA
            if (bad[WHITE]) { s[i] = SHADED; changed = true }
            else if (bad[SHADED]) { s[i] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /**
     * Level 3: branch intersection. Choice points are every region with several placements left (one
     * option per placement) and every undecided cell. Each option is propagated with the level 1 steps;
     * options that contradict drop out and whatever all survivors agree on is settled.
     */
    private fun branches(s: IntArray): Int {
        var changed = false
        // Single cells are the cheap choice points: all of them first, then one region at a time (the dear ones).
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN) continue
            val res = intersect(s, listOf(listOf(i to WHITE), listOf(i to SHADED)))
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        if (changed) return CHANGED
        val alive = fitting(s)
        for (step in 0 until regionCount) {
            val g = (cursor + step) % regionCount
            val ks = pls[g].indices.filter { alive[g][it] }
            if (ks.size < 2) continue
            val open = regionCells[g].filter { s[it] == UNKNOWN }
            if (open.isEmpty()) continue
            val options = ks.map { k -> open.map { it to if (pls[g][k].cells.contains(it)) SHADED else WHITE } }
            val res = intersect(s, options)
            if (res != NONE) { cursor = g; return res }
        }
        return NONE
    }

    /** Where the last successful region branch was: the next call starts there, so regions that gave nothing are not retried first. */
    private var cursor = 0

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

        /** Level 2 what-if: a trial runs at most this many sweeps of the level 1 steps. */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int, regions: IntArray) = LitsLogic(n, regions, d.ordinal)
        fun forLevel(level: Int, n: Int, regions: IntArray) = LitsLogic(n, regions, level)
    }
}

object LitsGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 10)[d.ordinal]

    /** Revealed cells to keep at least: easier levels stay friendlier. */
    fun minGivens(d: Difficulty, cells: Int) = when (d) {
        Difficulty.EASY -> (cells * 0.08).toInt()
        Difficulty.MEDIUM -> (cells * 0.04).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val shaded: BooleanArray, val regions: IntArray)

    /**
     * Tetrominoes laid out one touching the last (no 2×2, equal shapes never touch), then regions grown
     * around them until the grid is full.
     */
    private fun layout(n: Int, all: List<IntArray>, shapes: IntArray, random: Random): Layout? {
        val cells = n * n
        val count = maxOf(4, (cells / (6.5 + random.nextDouble() * 2.5)).toInt())
        val shaded = BooleanArray(cells)
        val shapeAt = IntArray(cells) { -1 }
        val region = IntArray(cells) { -1 }
        var placed = 0
        while (placed < count) {
            val candidates = all.indices.filter { k ->
                val p = all[k]
                if (p.any { shaded[it] }) return@filter false
                var touches = placed == 0
                for (c in p) for (j in orth(c, n)) {
                    if (shaded[j]) {
                        touches = true
                        if (shapeAt[j] == shapes[k]) return@filter false
                    }
                }
                if (!touches) return@filter false
                for (c in p) {
                    val r = c / n
                    val col = c % n
                    for (dr in -1..0) for (dc in -1..0) {
                        if (r + dr < 0 || col + dc < 0 || r + dr + 1 >= n || col + dc + 1 >= n) continue
                        val tl = (r + dr) * n + col + dc
                        if (intArrayOf(tl, tl + 1, tl + n, tl + n + 1).all { shaded[it] || p.contains(it) }) return@filter false
                    }
                }
                true
            }
            val k = candidates.randomOrNull(random) ?: return null
            for (c in all[k]) { shaded[c] = true; shapeAt[c] = shapes[k]; region[c] = placed }
            placed++
        }
        val size = IntArray(count) { 4 }
        var left = region.count { it < 0 }
        while (left > 0) {
            val frontier = (0 until cells).filter { i -> region[i] < 0 && orth(i, n).any { region[it] >= 0 } }
            val i = frontier.random(random)
            val near = orth(i, n).filter { region[it] >= 0 }.map { region[it] }.distinct()
            val g = if (random.nextDouble() < 0.6) near.minBy { size[it] } else near.random(random)
            region[i] = g
            size[g]++
            left--
        }
        return Layout(n, shaded, region)
    }

    private fun startOf(l: Layout, givens: BooleanArray) =
        IntArray(l.n * l.n) { if (givens[it]) (if (l.shaded[it]) SHADED else WHITE) else UNKNOWN }

    private fun needsTop(d: Difficulty, l: Layout, givens: BooleanArray) =
        d.ordinal < 2 || !LitsLogic.forLevel(d.ordinal - 1, l.n, l.regions).run(startOf(l, givens)).solved

    /** Gives cells away until [d]'s logic solves the layout, then takes back every one it can. */
    private fun withGivens(d: Difficulty, l: Layout, random: Random, end: Long): BooleanArray? {
        val cells = l.n * l.n
        val givens = BooleanArray(cells)
        val logic = LitsLogic.forLevel(d, l.n, l.regions)
        // The cheap level 1 finds the givens; Hard and Expert then take them back with their own, dearer level.
        val giveLogic = LitsLogic.forLevel(minOf(d.ordinal, 1), l.n, l.regions)
        val limit = cells * 4 / 10
        while (true) {
            val result = giveLogic.run(startOf(l, givens))
            if (result.solved) break
            if (givens.count { it } >= limit || past(end)) return null
            val open = result.unresolved.ifEmpty { return null }
            givens[open.filter { l.shaded[it] }.takeIf { random.nextDouble() < 0.6 }?.randomOrNull(random) ?: open.random(random)] = true
        }
        // Taking givens back is optional and the dear levels are slow at it: stop after a while, the puzzle already solves.
        val trimEnd = if (d.ordinal >= 2) minOf(end, deadline(600)) else end
        val floor = minGivens(d, cells)
        var have = givens.count { it }
        for (i in givens.indices.filter { givens[it] }.shuffled(random)) {
            if (have <= floor || past(trimEnd)) break
            givens[i] = false
            if (logic.run(startOf(l, givens)).solved) have-- else givens[i] = true
        }
        while (have < floor) {
            val i = givens.indices.filter { !givens[it] }.random(random)
            givens[i] = true
            have++
        }
        return givens
    }

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        val preferEnd = deadline(minOf(budgetMs, 800))
        val softEnd = deadline(minOf(budgetMs, 2500))
        val all = Tetromino.placements(n, IntArray(n * n) { it })
        val shapes = IntArray(all.size) { Tetromino.shape(all[it], n) }
        var best: ShadePuzzle? = null
        var bestScore = Int.MAX_VALUE
        var bestTop = false
        do {
            val l = layout(n, all, shapes, random) ?: continue
            val givens = withGivens(d, l, random, end) ?: continue
            val count = givens.count { it }
            val p = ShadePuzzle(Variant.LITS, d, n, l.shaded, givens, regions = l.regions)
            val top = needsTop(d, l, givens)
            if (top && count <= n * n / 8) return p
            val score = (if (top) 0 else 1000) + count
            if (score < bestScore) { best = p; bestScore = score; bestTop = top }
        } while (!past(end) && !(best != null && past(preferEnd) && (bestTop || past(softEnd))))
        best?.let { return it }
        // Out of time without a puzzle: layouts always settle once enough cells are given.
        while (true) {
            val l = layout(n, all, shapes, random) ?: continue
            val givens = withGivens(d, l, random, deadline(60_000)) ?: continue
            return ShadePuzzle(Variant.LITS, d, n, l.shaded, givens, regions = l.regions)
        }
    }
}

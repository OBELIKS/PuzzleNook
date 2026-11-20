package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Norinori logic. [regions] gives every cell's region. Shade exactly two cells ([SHADED]) in each
 * region so that every shaded cell touches exactly one other shaded cell orthogonally (dominoes,
 * which may cross region borders); the other cells are [WHITE].
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: a region with its two shaded cells has the rest white, one that can only just reach two has
 *   them all shaded; a shaded cell with a partner frees its other neighbours, one without a partner
 *   and a single free neighbour takes it; a cell that could not get exactly one shaded neighbour is
 *   white;
 * - 1: adds region placements: every way to put a region's two shaded cells is tried against its
 *   surroundings; cells no placement uses are white, cells every placement uses are shaded;
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most `TRIAL_SWEEPS` sweeps of
 *   the level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on every unfinished region
 *   (each placement of its missing shaded cells) and on single cells, settle what all
 *   non-contradicting options share.
 */
class NorinoriLogic(private val n: Int, private val regions: IntArray, private val level: Int) {
    private val cells = n * n
    private val regionCount = (regions.maxOrNull() ?: -1) + 1
    private val regionCells: List<IntArray> = (0 until regionCount).map { g -> (0 until cells).filter { regions[it] == g }.toIntArray() }
    private val orthNb = Array(cells) { orth(it, n) }

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
        for (cells in regionCells) if (cells.count { s[it] == SHADED } != 2) return false
        for (i in 0 until cells) if (s[i] == SHADED && orthNb[i].count { s[it] == SHADED } != 1) return false
        return true
    }

    /** Runs [basic] (and [placement]) sweeps until nothing changes, at most [maxSweeps] of them; a contradiction counts only within those. */
    private fun propagate(s: IntArray, placements: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(s)
            if (res == NONE && placements) res = placement(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (cl in regionCells) {
            var sh = 0
            var unk = 0
            for (i in cl) if (s[i] == SHADED) sh++ else if (s[i] == UNKNOWN) unk++
            if (sh > 2 || sh + unk < 2) return CONTRA
            if (unk == 0) continue
            val fill = when {
                sh == 2 -> WHITE
                sh + unk == 2 -> SHADED
                else -> continue
            }
            for (i in cl) if (s[i] == UNKNOWN) { s[i] = fill; changed = true }
        }
        for (i in 0 until cells) {
            var nbS = 0
            var nbU = 0
            for (j in orthNb[i]) if (s[j] == SHADED) nbS++ else if (s[j] == UNKNOWN) nbU++
            when (s[i]) {
                SHADED -> {
                    if (nbS > 1) return CONTRA
                    if (nbS == 1) {
                        for (j in orthNb[i]) if (s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
                    } else {
                        if (nbU == 0) return CONTRA
                        if (nbU == 1) {
                            val j = orthNb[i].first { s[it] == UNKNOWN }
                            s[j] = SHADED
                            changed = true
                        }
                    }
                }
                UNKNOWN -> if (nbS >= 2 || nbS + nbU == 0) { s[i] = WHITE; changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: try every placement of a region's two shaded cells against what is already known. */
    private fun placement(s: IntArray): Int {
        var changed = false
        for (cl in regionCells) {
            val shaded = cl.filter { s[it] == SHADED }
            val open = cl.filter { s[it] == UNKNOWN }
            if (shaded.size > 2) return CONTRA
            val pairs = ArrayList<IntArray>()
            when (shaded.size) {
                2 -> pairs += intArrayOf(shaded[0], shaded[1])
                1 -> for (a in open) pairs += intArrayOf(shaded[0], a)
                else -> for (x in open.indices) for (y in x + 1 until open.size) pairs += intArrayOf(open[x], open[y])
            }
            val use = IntArray(cells)
            var feasible = 0
            for (p in pairs) {
                if (!fits(s, cl, p)) continue
                feasible++
                for (i in p) use[i]++
            }
            if (feasible == 0) return CONTRA
            for (i in open) {
                if (use[i] == 0) { s[i] = WHITE; changed = true } else if (use[i] == feasible) { s[i] = SHADED; changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /** Whether shading exactly [p] in region [cl] (the rest of it white) breaks no domino rule around it. */
    private fun fits(s: IntArray, cl: IntArray, p: IntArray): Boolean {
        fun shaded(i: Int) = if (i == p[0] || i == p[1]) true else if (regions[i] == regions[p[0]]) false else s[i] == SHADED
        fun open(i: Int) = if (regions[i] == regions[p[0]]) false else s[i] == UNKNOWN
        for (x in p) {
            for (y in intArrayOf(x) + orthNb[x]) {
                if (!shaded(y)) continue
                val count = orthNb[y].count { shaded(it) }
                if (count > 1) return false
                if (count == 0 && orthNb[y].none { open(it) }) return false
            }
        }
        return true
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
     * Level 3: branch intersection. Choice points are every unfinished region (each placement of its
     * missing shaded cells) and every undecided cell. Each option is propagated with the level 1 steps;
     * options that contradict drop out and whatever all survivors agree on is settled.
     */
    private fun branches(s: IntArray): Int {
        var changed = false
        for (cl in regionCells) {
            val shaded = cl.count { s[it] == SHADED }
            val open = cl.filter { s[it] == UNKNOWN }
            if (open.isEmpty() || shaded > 2) continue
            val options = ArrayList<List<Pair<Int, Int>>>()
            fun add(p: List<Int>) {
                options += open.map { it to if (it in p) SHADED else WHITE }
            }
            when (shaded) {
                1 -> for (a in open) add(listOf(a))
                0 -> for (x in open.indices) for (y in x + 1 until open.size) add(listOf(open[x], open[y]))
                else -> add(emptyList())
            }
            if (options.size < 2) continue
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

        /**
         * Level 2 what-if: a trial runs at most this many sweeps of the level 0 steps. 2 keeps Hard
         * needing level 2 (5/5) and Expert generation fast; no value makes Expert need level 3 (batch 5b).
         */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int, regions: IntArray) = NorinoriLogic(n, regions, d.ordinal)
    }
}

object NorinoriGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** Revealed cells to keep at least: easier levels stay friendlier. */
    fun minGivens(d: Difficulty, cells: Int) = when (d) {
        Difficulty.EASY -> (cells * 0.08).toInt()
        Difficulty.MEDIUM -> (cells * 0.04).toInt()
        else -> 0
    }

    /** Biggest region the growth aims for; dominoes fill about a third of the grid, so regions run to ~6-7 cells on average. */
    private const val CAP = 9

    private class Layout(val n: Int, val shaded: BooleanArray, val regions: IntArray)

    /** Random dominoes that never touch, then regions each holding exactly two shaded cells. */
    private fun layout(n: Int, random: Random): Layout? {
        val cells = n * n
        val shaded = BooleanArray(cells)
        val partner = IntArray(cells) { -1 }
        val target = (cells * (0.27 + random.nextDouble() * 0.07)).toInt()
        val dominoes = ArrayList<IntArray>()
        for (i in 0 until cells) {
            if (i % n < n - 1) dominoes += intArrayOf(i, i + 1)
            if (i / n < n - 1) dominoes += intArrayOf(i, i + n)
        }
        dominoes.shuffle(random)
        var count = 0
        for (d in dominoes) {
            if (count >= target) break
            val ok = d.all { c -> !shaded[c] && orth(c, n).all { nb -> nb == d[0] || nb == d[1] || !shaded[nb] } }
            if (!ok) continue
            shaded[d[0]] = true
            shaded[d[1]] = true
            partner[d[0]] = d[1]
            partner[d[1]] = d[0]
            count += 2
        }
        val region = IntArray(cells) { -1 }
        var id = 0
        val order = (0 until cells).filter { shaded[it] }.shuffled(random)
        for (u in order) {
            if (region[u] >= 0) continue
            // Shaded cells reachable over free white cells (not through shaded ones).
            val parent = IntArray(cells) { -2 }
            val dist = IntArray(cells)
            val queue = ArrayDeque<Int>()
            queue.addLast(u)
            parent[u] = -1
            val found = ArrayList<Int>()
            while (queue.isNotEmpty()) {
                val x = queue.removeFirst()
                if (dist[x] >= 5) continue
                for (y in orth(x, n)) {
                    if (parent[y] != -2) continue
                    if (shaded[y]) {
                        if (region[y] < 0 && y != u) { parent[y] = x; dist[y] = dist[x] + 1; found += y }
                        continue
                    }
                    if (region[y] >= 0) continue
                    parent[y] = x
                    dist[y] = dist[x] + 1
                    queue.addLast(y)
                }
            }
            val pa = partner[u]
            val w = if (pa >= 0 && region[pa] < 0 && (found.isEmpty() || random.nextDouble() < 0.45)) pa
            else found.randomOrNull(random) ?: return null
            var x = w
            while (x != -1) { region[x] = id; x = parent[x] }
            id++
        }
        val size = IntArray(id)
        for (c in region) if (c >= 0) size[c]++
        var left = region.count { it < 0 }
        while (left > 0) {
            val frontier = ArrayList<Int>()
            val fallback = ArrayList<Int>()
            for (i in 0 until cells) {
                if (region[i] >= 0) continue
                for (j in orth(i, n)) if (region[j] >= 0) {
                    if (size[region[j]] < CAP) frontier += i else fallback += i
                    break
                }
            }
            val i = (frontier.ifEmpty { fallback }).randomOrNull(random) ?: return null
            val options = orth(i, n).filter { region[it] >= 0 && (size[region[it]] < CAP || frontier.isEmpty()) }
            val g = region[options.random(random)]
            region[i] = g
            size[g]++
            left--
        }
        return Layout(n, shaded, region)
    }

    private fun startOf(n: Int, l: Layout, givens: BooleanArray) =
        IntArray(n * n) { if (givens[it]) (if (l.shaded[it]) SHADED else WHITE) else UNKNOWN }

    private fun needsTop(d: Difficulty, n: Int, regions: IntArray, start: IntArray) =
        d.ordinal < 2 || !NorinoriLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, regions).run(start).solved

    /** Gives cells away until [d]'s logic solves the layout, then takes back every one it can. */
    private fun withGivens(d: Difficulty, l: Layout, random: Random, end: Long): BooleanArray? {
        val n = l.n
        val cells = n * n
        val givens = BooleanArray(cells)
        val logic = NorinoriLogic.forLevel(d, n, l.regions)
        val limit = cells * 4 / 10
        while (true) {
            val result = logic.run(startOf(n, l, givens))
            if (result.solved) break
            if (givens.count { it } >= limit || past(end)) return null
            val open = result.unresolved.ifEmpty { return null }
            givens[open.filter { l.shaded[it] }.randomOrNull(random) ?: open.random(random)] = true
        }
        val floor = minGivens(d, cells)
        var have = givens.count { it }
        for (i in givens.indices.filter { givens[it] }.shuffled(random)) {
            if (have <= floor || past(end)) break
            givens[i] = false
            if (logic.run(startOf(n, l, givens)).solved) have-- else givens[i] = true
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
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        var best: ShadePuzzle? = null
        var bestGivens = Int.MAX_VALUE
        do {
            val l = layout(n, random) ?: continue
            val givens = withGivens(d, l, random, end) ?: continue
            val count = givens.count { it }
            val p = ShadePuzzle(Variant.NORINORI, d, n, l.shaded, givens, regions = l.regions)
            val top = needsTop(d, n, l.regions, startOf(n, l, givens))
            if (top && (d.ordinal < 2 || count <= n * n / 10)) return p
            // Else keep the best so far: one that needs the top level beats one that doesn't, then fewer givens.
            val score = (if (top) 0 else 1000) + count
            if (score < bestGivens) { best = p; bestGivens = score }
        } while (!past(end) && !(best != null && past(preferEnd)))
        best?.let { return it }
        // Out of time without a puzzle: smaller dominoes layouts always settle once enough cells are given.
        while (true) {
            val l = layout(n, random) ?: continue
            val givens = withGivens(d, l, random, deadline(60_000)) ?: continue
            return ShadePuzzle(Variant.NORINORI, d, n, l.shaded, givens, regions = l.regions)
        }
    }
}

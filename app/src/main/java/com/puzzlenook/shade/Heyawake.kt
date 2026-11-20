package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Heyawake logic. [regions] are rectangular rooms; [numbers] holds, on the first cell of a room in
 * reading order, how many cells of it are shaded (`-1` everywhere else). Shade cells ([SHADED], the
 * rest [WHITE]) so that every number is met, shaded cells never touch orthogonally, the white cells
 * form one area, and no straight run of white cells crosses two room borders (spans three rooms).
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: room counts, a shaded cell frees its neighbours, and every stretch of three rooms in a row or
 *   column needs a shaded cell (the last cell that can be one takes it);
 * - 1: adds connectivity: an open cell whose shading would cut the white cells apart is white, and
 *   white cells that can no longer reach each other contradict;
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most `TRIAL_SWEEPS` sweeps of
 *   the level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on every numbered room that
 *   still misses shaded cells (each way to place them) and on single cells, settle what all
 *   non-contradicting options share.
 */
class HeyawakeLogic(private val n: Int, private val regions: IntArray, numbers: IntArray, private val level: Int) {
    private val cells = n * n
    private val roomCount = (regions.maxOrNull() ?: -1) + 1
    private val roomCells: List<IntArray> = (0 until roomCount).map { g -> (0 until cells).filter { regions[it] == g }.toIntArray() }
    private val target = IntArray(roomCount) { -1 }.also { t -> for (i in 0 until cells) if (numbers[i] >= 0) t[regions[i]] = numbers[i] }
    private val orthNb = Array(cells) { orth(it, n) }

    /** Shortest stretches of cells in a row or column that span three rooms: each must hold a shaded cell. */
    private val windows: List<IntArray> = buildList {
        for (k in 0 until 2 * n) {
            val line = IntArray(n) { if (k < n) k * n + it else it * n + (k - n) }
            for (a in 0 until n) {
                val seen = HashSet<Int>()
                for (b in a until n) {
                    seen += regions[line[b]]
                    if (seen.size == 3) { add(line.copyOfRange(a, b + 1)); break }
                }
            }
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
        for (g in 0 until roomCount) if (target[g] >= 0 && roomCells[g].count { s[it] == SHADED } != target[g]) return false
        for (i in 0 until cells) if (s[i] == SHADED && orthNb[i].any { s[it] == SHADED }) return false
        for (w in windows) if (w.none { s[it] == SHADED }) return false
        return connected(n) { s[it] != SHADED }
    }

    /** Runs [basic] (and [connectivity]) sweeps until nothing changes, at most [maxSweeps] of them; a contradiction counts only within those. */
    private fun propagate(s: IntArray, connect: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(s)
            if (res == NONE && connect) res = connectivity(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (g in 0 until roomCount) {
            val t = target[g]
            if (t < 0) continue
            var sh = 0
            var unk = 0
            for (i in roomCells[g]) if (s[i] == SHADED) sh++ else if (s[i] == UNKNOWN) unk++
            if (sh > t || sh + unk < t) return CONTRA
            if (unk == 0) continue
            val fill = when {
                sh == t -> WHITE
                sh + unk == t -> SHADED
                else -> continue
            }
            for (i in roomCells[g]) if (s[i] == UNKNOWN) { s[i] = fill; changed = true }
        }
        for (i in 0 until cells) {
            if (s[i] != SHADED) continue
            for (j in orthNb[i]) {
                if (s[j] == SHADED) return CONTRA
                if (s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
            }
        }
        for (w in windows) {
            var open = 0
            var last = -1
            var has = false
            for (i in w) if (s[i] == SHADED) { has = true; break } else if (s[i] == UNKNOWN) { open++; last = i }
            if (has) continue
            if (open == 0) return CONTRA
            if (open == 1) { s[last] = SHADED; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: the white cells must stay one area, so an open cell they can only be joined through is white. */
    private fun connectivity(s: IntArray): Int {
        val cut = anchorCuts(n, orthNb, { s[it] != SHADED }, { s[it] == WHITE }) ?: return CONTRA
        var changed = false
        for (u in 0 until cells) if (cut[u] && s[u] == UNKNOWN) { s[u] = WHITE; changed = true }
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
                bad[v] = propagate(t, false, true, TRIAL_SWEEPS) == CONTRA
            }
            if (bad[WHITE] && bad[SHADED]) return CONTRA
            if (bad[WHITE]) { s[i] = SHADED; changed = true }
            else if (bad[SHADED]) { s[i] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /**
     * Level 3: branch intersection. Choice points are every numbered room that misses shaded cells (each
     * way to place them, never touching) and every undecided cell. Each option is propagated with the
     * level 1 steps; options that contradict drop out and whatever all survivors agree on is settled.
     */
    private fun branches(s: IntArray): Int {
        var changed = false
        // Single cells are the cheap choice points: all of them first, then one room at a time (the dear ones).
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN) continue
            val res = intersect(s, listOf(listOf(i to WHITE), listOf(i to SHADED)))
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        if (changed) return CHANGED
        for (step in 0 until roomCount) {
            val g = (cursor + step) % roomCount
            if (target[g] < 0) continue
            val open = roomCells[g].filter { s[it] == UNKNOWN }
            val need = target[g] - roomCells[g].count { s[it] == SHADED }
            if (open.isEmpty() || need <= 0 || need > open.size) continue
            val options = ArrayList<List<Pair<Int, Int>>>()
            val chosen = ArrayList<Int>()
            var overflow = false
            fun rec(from: Int) {
                if (overflow) return
                if (chosen.size == need) {
                    options += open.map { it to if (it in chosen) SHADED else WHITE }
                    if (options.size > MAX_OPTIONS) overflow = true
                    return
                }
                for (k in from until open.size) {
                    if (open.size - k < need - chosen.size) break
                    val c = open[k]
                    if (chosen.any { it in orthNb[c] }) continue
                    chosen += c
                    rec(k + 1)
                    chosen.removeAt(chosen.size - 1)
                }
            }
            rec(0)
            if (overflow || options.size < 2) continue
            val res = intersect(s, options)
            if (res != NONE) { cursor = g; return res }
        }
        return NONE
    }

    /** Where the last successful room branch was: the next call starts there, so rooms that gave nothing are not retried first. */
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
        private const val MAX_OPTIONS = 40

        /** Level 2 what-if: a trial runs at most this many sweeps of the level 0 steps. */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int, regions: IntArray, numbers: IntArray) = HeyawakeLogic(n, regions, numbers, d.ordinal)
        fun forLevel(level: Int, n: Int, regions: IntArray, numbers: IntArray) = HeyawakeLogic(n, regions, numbers, level)
    }
}

object HeyawakeGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 7, 9, 10)[d.ordinal]

    /** Room numbers to keep at least: easier levels stop removing numbers early. */
    fun minNumbers(d: Difficulty, rooms: Int) = when (d) {
        Difficulty.EASY -> (rooms * 0.7).toInt()
        Difficulty.MEDIUM -> (rooms * 0.5).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val shaded: BooleanArray, val regions: IntArray, val numbers: IntArray)

    /** Rectangles from random cuts; every room has at least two cells. */
    private fun rooms(n: Int, random: Random): IntArray {
        val region = IntArray(n * n)
        var id = 0
        fun split(r0: Int, c0: Int, h: Int, w: Int) {
            // Bigger grids get bigger rooms: fewer rooms leave fewer places where a run may not cross two borders.
            val limit = 3 + random.nextInt(8 + (n - 6) * 2)
            if (h * w <= limit) {
                for (r in r0 until r0 + h) for (c in c0 until c0 + w) region[r * n + c] = id
                id++
                return
            }
            val horizontal = when {
                h == 1 -> false
                w == 1 -> true
                h > w -> random.nextDouble() < 0.7
                else -> random.nextDouble() < 0.3
            }
            if (horizontal) {
                val lo = (2 + w - 1) / w
                val cut = lo + random.nextInt(h - 2 * lo + 1)
                split(r0, c0, cut, w)
                split(r0 + cut, c0, h - cut, w)
            } else {
                val lo = (2 + h - 1) / h
                val cut = lo + random.nextInt(w - 2 * lo + 1)
                split(r0, c0, h, cut)
                split(r0, c0 + cut, h, w - cut)
            }
        }
        split(0, 0, n, n)
        return region
    }

    /** A straight run of white cells that spans three rooms, or null. */
    private fun violation(n: Int, regions: IntArray, shaded: BooleanArray): List<Int>? {
        for (k in 0 until 2 * n) {
            val run = ArrayList<Int>()
            for (t in 0..n) {
                val i = if (t == n) -1 else if (k < n) k * n + t else t * n + (k - n)
                if (i >= 0 && !shaded[i]) { run += i; continue }
                if (run.map { regions[it] }.toSet().size >= 3) return run
                run.clear()
            }
        }
        return null
    }

    /** A shading with no touching cells, connected white and no run through three rooms; several tries, since a try can paint itself into a corner. */
    private fun solution(n: Int, regions: IntArray, random: Random): BooleanArray? {
        val cells = n * n
        repeat(12) {
            val shaded = BooleanArray(cells)
            fun allowed(c: Int) = !shaded[c] && orth(c, n).none { shaded[it] } && connected(n) { !shaded[it] && it != c }
            // Mostly the runs through three rooms decide where to shade; a few random cells first for variety.
            val p = random.nextDouble() * 0.12
            for (c in (0 until cells).shuffled(random)) if (random.nextDouble() < p && allowed(c)) shaded[c] = true
            var ok = false
            repeat(cells) {
                if (ok) return@repeat
                val run = violation(n, regions, shaded)
                if (run == null) { ok = true; return@repeat }
                val pick = run.filter { allowed(it) }.randomOrNull(random) ?: return@repeat
                shaded[pick] = true
            }
            if (ok || violation(n, regions, shaded) == null) return shaded
        }
        return null
    }

    private fun layout(n: Int, random: Random): Layout? {
        val regions = rooms(n, random)
        val shaded = solution(n, regions, random) ?: return null
        val numbers = IntArray(n * n) { -1 }
        for (g in 0..regions.max()) {
            val cs = (0 until n * n).filter { regions[it] == g }
            numbers[cs.first()] = cs.count { shaded[it] }
        }
        return Layout(n, shaded, regions, numbers)
    }

    private fun startOf(l: Layout, givens: BooleanArray) =
        IntArray(l.n * l.n) { if (givens[it]) (if (l.shaded[it]) SHADED else WHITE) else UNKNOWN }

    private class Attempt(val numbers: IntArray, val givens: BooleanArray)

    private fun needsTop(d: Difficulty, l: Layout, a: Attempt) =
        d.ordinal < 2 || !HeyawakeLogic.forLevel(d.ordinal - 1, l.n, l.regions, a.numbers).run(startOf(l, a.givens)).solved

    /** Gives cells away until [d]'s logic solves the layout, then takes back numbers and givens while it still does. */
    private fun attempt(d: Difficulty, l: Layout, random: Random, end: Long): Attempt? {
        val n = l.n
        val cells = n * n
        val numbers = l.numbers.copyOf()
        val givens = BooleanArray(cells)
        val limit = cells * 3 / 10
        fun solves(nums: IntArray) = HeyawakeLogic.forLevel(d.ordinal, n, l.regions, nums).run(startOf(l, givens)).solved
        // The cheap level 1 finds the givens; Hard and Expert then take numbers and givens back with their own, dearer level.
        val giveLevel = minOf(d.ordinal, 1)
        while (true) {
            val result = HeyawakeLogic.forLevel(giveLevel, n, l.regions, numbers).run(startOf(l, givens))
            if (result.solved) break
            if (givens.count { it } >= limit || past(end)) return null
            val open = result.unresolved.ifEmpty { return null }
            givens[open.filter { l.shaded[it] }.takeIf { random.nextDouble() < 0.5 }?.randomOrNull(random) ?: open.random(random)] = true
        }
        // Taking clues back is optional and the dear levels are slow at it: stop after a while, the puzzle already solves.
        val trimEnd = if (d.ordinal >= 2) minOf(end, deadline(600)) else end
        val rooms = l.regions.max() + 1
        var have = numbers.count { it >= 0 }
        val floor = minNumbers(d, rooms)
        for (i in numbers.indices.filter { numbers[it] >= 0 }.shuffled(random)) {
            if (have <= floor || past(trimEnd)) break
            val old = numbers[i]
            numbers[i] = -1
            if (solves(numbers)) have-- else numbers[i] = old
        }
        for (i in givens.indices.filter { givens[it] }.shuffled(random)) {
            if (past(trimEnd)) break
            givens[i] = false
            if (!solves(numbers)) givens[i] = true
        }
        return Attempt(numbers, givens)
    }

    private fun puzzle(d: Difficulty, l: Layout, a: Attempt) =
        ShadePuzzle(Variant.HEYAWAKE, d, l.n, l.shaded, a.givens, numbers = a.numbers, regions = l.regions)

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        val preferEnd = deadline(minOf(budgetMs, 800))
        val softEnd = deadline(minOf(budgetMs, 2500))
        var best: ShadePuzzle? = null
        var bestScore = Int.MAX_VALUE
        var bestTop = false
        do {
            val l = layout(n, random) ?: continue
            val a = attempt(d, l, random, end) ?: continue
            val count = a.givens.count { it }
            val top = needsTop(d, l, a)
            if (top && count <= n * n / 8) return puzzle(d, l, a)
            // Else keep the best so far: one that needs the top level beats one that doesn't, then fewer givens.
            val score = (if (top) 0 else 1000) + count
            if (score < bestScore) { best = puzzle(d, l, a); bestScore = score; bestTop = top }
        } while (!past(end) && !(best != null && past(preferEnd) && (bestTop || past(softEnd))))
        best?.let { return it }
        // Out of time without a puzzle: keep giving cells away until some layout solves.
        while (true) {
            val l = layout(n, random) ?: continue
            val a = attempt(d, l, random, deadline(60_000)) ?: continue
            return puzzle(d, l, a)
        }
    }
}

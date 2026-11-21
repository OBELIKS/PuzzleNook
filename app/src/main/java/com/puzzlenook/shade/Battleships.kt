package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * `ShadePuzzle.numbers` of Battleships: `-1` no clue, `0` water, then a ship part that is shown from the
 * start: [SHIP_SINGLE], [SHIP_TOP], [SHIP_BOTTOM], [SHIP_LEFT], [SHIP_RIGHT] (the end where the ship
 * stops, the ship goes on the other way) and [SHIP_MIDDLE]. Shown cells are locked but not blocked
 * (`ShadeState.isBlocked`).
 */
const val SHIP_SINGLE = 1
const val SHIP_TOP = 2
const val SHIP_BOTTOM = 3
const val SHIP_LEFT = 4
const val SHIP_RIGHT = 5
const val SHIP_MIDDLE = 6

/**
 * Battleships logic. Ships are [SHADED] cells, water is [WHITE]. The [fleet] (lengths) is placed so that
 * ships are straight, never touch (not even diagonally), and [counts] (rows 0..n-1, then columns;
 * `-1` hidden) give the ship cells per line. [numbers] hold the cells shown from the start.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: counts (a full line gets water, one whose open cells are all needed gets ships), water around
 *   every ship cell diagonally and across its axis, the shown parts and what they say about their
 *   neighbours;
 * - 1: adds fleet reasoning: the number of ship cells, ships that are as long as the longest ship
 *   still missing are closed, nothing grows past that length, a ship shorter than the shortest one
 *   still missing must grow, no room for the shortest missing ship means water, and where the longest
 *   missing ship fits (every placement);
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most `TRIAL_SWEEPS` sweeps of
 *   the level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on single cells and on every
 *   way to place a line's missing ship cells, settle what all non-contradicting options share.
 *
 * Counts work as in Tents (`ShadeState.counts`, margin). `BattleshipsGenerator.fleet(n)` gives the fleet. A ship is
 * FILLED and drawn by `ShadeBoard` as capsules joined to its own FILLED neighbours; a correct ship dots its
 * diagonals. `ShadeState.fleetStatus` feeds the status row (ticks ships closed by own water marks), `shipClash`
 * flags clashes. The independent brute force for tests is `Rules` in test `ShadeRules.kt`.
 */
class BattleshipsLogic(
    private val n: Int,
    private val numbers: IntArray,
    private val counts: IntArray,
    private val fleet: List<Int>,
    private val level: Int,
) {
    private val cells = n * n
    private val total = fleet.sum()
    private val maxLen = fleet.max()
    private val fleetCount = IntArray(maxLen + 1).also { for (l in fleet) it[l]++ }
    private val lines: Array<IntArray> = Array(2 * n) { k ->
        if (k < n) IntArray(n) { k * n + it } else IntArray(n) { it * n + (k - n) }
    }

    /** Neighbour of [i] in direction [d] (0 up, 1 down, 2 left, 3 right), -1 outside the board. */
    private fun step(i: Int, d: Int): Int {
        val r = i / n
        val c = i % n
        return when (d) {
            0 -> if (r > 0) i - n else -1
            1 -> if (r < n - 1) i + n else -1
            2 -> if (c > 0) i - 1 else -1
            else -> if (c < n - 1) i + 1 else -1
        }
    }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        val round = IntArray(cells)
        for (i in 0 until cells) {
            val v = when {
                numbers[i] < 0 -> continue
                numbers[i] == 0 -> WHITE
                else -> SHADED
            }
            if (s[i] != UNKNOWN && s[i] != v) return ShadeResult(false, s, round)
            s[i] = v
        }
        for (i in 0 until cells) round[i] = if (s[i] != UNKNOWN) 0 else -1
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

    /** Components of the shaded cells (orthogonal) or null if one is not a straight line. */
    private class Ship(val cells: IntArray, val horizontal: Boolean)

    private fun ships(s: IntArray): List<Ship>? {
        val seen = BooleanArray(cells)
        val out = ArrayList<Ship>()
        for (i in 0 until cells) {
            if (s[i] != SHADED || seen[i]) continue
            val comp = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            stack.addLast(i)
            seen[i] = true
            while (stack.isNotEmpty()) {
                val x = stack.removeLast()
                comp += x
                for (d in 0..3) {
                    val y = step(x, d)
                    if (y >= 0 && s[y] == SHADED && !seen[y]) { seen[y] = true; stack.addLast(y) }
                }
            }
            val sameRow = comp.all { it / n == comp[0] / n }
            val sameCol = comp.all { it % n == comp[0] % n }
            if (!sameRow && !sameCol) return null
            comp.sort()
            out += Ship(comp.toIntArray(), sameRow)
        }
        return out
    }

    private fun valid(s: IntArray): Boolean {
        for (k in 0 until 2 * n) if (counts[k] >= 0 && lines[k].count { s[it] == SHADED } != counts[k]) return false
        val ships = ships(s) ?: return false
        // Ships that touch diagonally are separate orthogonal components: look for them.
        for (i in 0 until cells) if (s[i] == SHADED) for (j in king(i, n)) if (s[j] == SHADED && j / n != i / n && j % n != i % n) return false
        return ships.map { it.cells.size }.sorted() == fleet.sorted()
    }

    private fun propagate(s: IntArray, fleetRules: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(s)
            if (res == NONE && fleetRules) res = fleetStep(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        fun put(j: Int, v: Int): Boolean {
            if (j < 0) return v == WHITE
            if (s[j] == v) return true
            if (s[j] != UNKNOWN) return false
            s[j] = v
            changed = true
            return true
        }
        for (k in 0 until 2 * n) {
            val c = counts[k]
            if (c < 0) continue
            var ships = 0
            var open = 0
            for (j in lines[k]) if (s[j] == SHADED) ships++ else if (s[j] == UNKNOWN) open++
            if (ships > c || ships + open < c) return CONTRA
            if (open == 0) continue
            val v = if (ships == c) WHITE else if (ships + open == c) SHADED else continue
            for (j in lines[k]) if (s[j] == UNKNOWN) s[j] = v
            changed = true
        }
        for (i in 0 until cells) {
            if (s[i] != SHADED) continue
            val up = step(i, 0)
            val down = step(i, 1)
            val left = step(i, 2)
            val right = step(i, 3)
            for (a in intArrayOf(up, down)) for (b in intArrayOf(left, right)) {
                if (a < 0 || b < 0) continue
                if (!put(step(a, if (b == left) 2 else 3), WHITE)) return CONTRA
            }
            val h = (left >= 0 && s[left] == SHADED) || (right >= 0 && s[right] == SHADED)
            val v = (up >= 0 && s[up] == SHADED) || (down >= 0 && s[down] == SHADED)
            if (h && v) return CONTRA
            if (h && !(put(up, WHITE) && put(down, WHITE))) return CONTRA
            if (v && !(put(left, WHITE) && put(right, WHITE))) return CONTRA
        }
        for (i in 0 until cells) {
            val t = numbers[i]
            if (t <= 0) continue
            val up = step(i, 0)
            val down = step(i, 1)
            val left = step(i, 2)
            val right = step(i, 3)
            val ok = when (t) {
                SHIP_SINGLE -> put(up, WHITE) && put(down, WHITE) && put(left, WHITE) && put(right, WHITE)
                SHIP_TOP -> put(up, WHITE) && put(left, WHITE) && put(right, WHITE) && put(down, SHADED)
                SHIP_BOTTOM -> put(down, WHITE) && put(left, WHITE) && put(right, WHITE) && put(up, SHADED)
                SHIP_LEFT -> put(left, WHITE) && put(up, WHITE) && put(down, WHITE) && put(right, SHADED)
                SHIP_RIGHT -> put(right, WHITE) && put(up, WHITE) && put(down, WHITE) && put(left, SHADED)
                else -> {
                    fun usable(a: Int, b: Int) = a >= 0 && b >= 0 && s[a] != WHITE && s[b] != WHITE
                    val canV = usable(up, down)
                    val canH = usable(left, right)
                    val vertical = (up >= 0 && s[up] == SHADED) || (down >= 0 && s[down] == SHADED)
                    val horizontal = (left >= 0 && s[left] == SHADED) || (right >= 0 && s[right] == SHADED)
                    when {
                        !canV && !canH -> false
                        !canV || (horizontal && !vertical) -> put(left, SHADED) && put(right, SHADED) && put(up, WHITE) && put(down, WHITE)
                        !canH || vertical -> put(up, SHADED) && put(down, SHADED) && put(left, WHITE) && put(right, WHITE)
                        else -> true
                    }
                }
            }
            if (!ok) return CONTRA
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: reasoning about the fleet. */
    private fun fleetStep(s: IntArray): Int {
        var changed = false
        var shaded = 0
        var unknown = 0
        for (v in s) if (v == SHADED) shaded++ else if (v == UNKNOWN) unknown++
        if (shaded > total || shaded + unknown < total) return CONTRA
        if (unknown > 0 && (shaded == total || shaded + unknown == total)) {
            val v = if (shaded == total) WHITE else SHADED
            for (i in 0 until cells) if (s[i] == UNKNOWN) s[i] = v
            return CHANGED
        }
        val ships = ships(s) ?: return CONTRA
        fun free(j: Int) = j >= 0 && s[j] != WHITE
        // A ship's two end cells (all four neighbours for a single cell).
        fun ends(sh: Ship): IntArray {
            if (sh.cells.size == 1) return IntArray(4) { step(sh.cells[0], it) }
            val a = sh.cells.first()
            val b = sh.cells.last()
            return if (sh.horizontal) intArrayOf(step(a, 2), step(b, 3)) else intArrayOf(step(a, 0), step(b, 1))
        }
        val closed = IntArray(maxLen + 2)
        val open = ArrayList<Ship>()
        for (sh in ships) {
            if (sh.cells.size > maxLen) return CONTRA
            if (ends(sh).none { free(it) }) {
                if (++closed[sh.cells.size] > fleetCount[sh.cells.size]) return CONTRA
            } else open += sh
        }
        var maxRem = 0
        var minRem = 0
        for (l in 1..maxLen) if (closed[l] < fleetCount[l]) { maxRem = l; if (minRem == 0) minRem = l }
        if (maxRem == 0) return if (open.isEmpty()) NONE else CONTRA
        for (sh in open) {
            val len = sh.cells.size
            if (len > maxRem) return CONTRA
            val e = ends(sh)
            if (len == maxRem) {
                for (j in e) if (j >= 0 && s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
            } else if (len < minRem) {
                val cand = e.filter { free(it) }
                if (cand.isEmpty()) return CONTRA
                if (cand.size == 1 && s[cand[0]] == UNKNOWN) { s[cand[0]] = SHADED; changed = true }
            }
        }
        if (changed) return CHANGED
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN) continue
            var runH = 1
            var runV = 1
            var shadedH = 0
            var shadedV = 0
            for (d in 0..3) {
                var j = step(i, d)
                var shadedRun = 0
                while (j >= 0 && s[j] == SHADED) { shadedRun++; j = step(j, d) }
                if (d >= 2) shadedH += shadedRun else shadedV += shadedRun
                // Free cells in the line through i (any cell that isn't water).
                j = step(i, d)
                var freeRun = 0
                while (j >= 0 && s[j] != WHITE) { freeRun++; j = step(j, d) }
                if (d >= 2) runH += freeRun else runV += freeRun
            }
            val white = when {
                shadedH > 0 && shadedV > 0 -> true
                shadedH + shadedV > 0 -> shadedH + shadedV + 1 > maxRem
                else -> maxOf(runH, runV) < minRem
            }
            if (white) { s[i] = WHITE; changed = true }
        }
        if (changed) return CHANGED
        return placements(s, maxRem, fleetCount[maxRem] - closed[maxRem])
    }

    /** Every way the longest missing ship ([len] cells, [need] of them still to place) could lie. */
    private fun placements(s: IntArray, len: Int, need: Int): Int {
        val use = IntArray(cells)
        var count = 0
        for (horizontal in booleanArrayOf(true, false)) {
            if (len == 1 && !horizontal) break
            for (a in 0 until cells) {
                val r = a / n
                val c = a % n
                if (if (horizontal) c + len > n else r + len > n) continue
                val seg = IntArray(len) { if (horizontal) a + it else a + it * n }
                if (seg.any { s[it] == WHITE }) continue
                var ok = true
                for (x in seg) for (y in king(x, n)) if (s[y] == SHADED && y !in seg) { ok = false; break }
                if (!ok) continue
                count++
                for (x in seg) use[x]++
            }
        }
        if (count < need) return CONTRA
        var changed = false
        // As many placements as ships: all are used. One ship: only what every placement shares.
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN || use[i] == 0) continue
            if (count == need || (need == 1 && use[i] == count)) { s[i] = SHADED; changed = true }
        }
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
     * Level 3: branch intersection. Choice points are every line with open cells (each way to place its
     * missing ship cells, if there are few enough) and every undecided cell. Each option is propagated with
     * the level 1 steps; options that contradict drop out and whatever all survivors agree on is settled.
     */
    private fun branches(s: IntArray): Int {
        var changed = false
        for (k in 0 until 2 * n) {
            val c = counts[k]
            if (c < 0) continue
            val open = lines[k].filter { s[it] == UNKNOWN }
            val need = c - lines[k].count { s[it] == SHADED }
            if (open.size < 2 || need < 1 || need >= open.size) continue
            if (choose(open.size, need) > MAX_LINE_OPTIONS) continue
            val options = ArrayList<List<Pair<Int, Int>>>()
            fun rec(from: Int, left: Int, picked: List<Int>) {
                if (left == 0) { options += open.map { it to if (it in picked) SHADED else WHITE }; return }
                for (x in from..open.size - left) rec(x + 1, left - 1, picked + open[x])
            }
            rec(0, need, emptyList())
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

    private fun choose(a: Int, b: Int): Int {
        var r = 1L
        for (x in 1..b) r = r * (a - b + x) / x
        return r.coerceAtMost(1_000_000).toInt()
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
        private const val MAX_LINE_OPTIONS = 60

        /** Level 2 what-if: a trial runs at most this many sweeps of the level 0 steps. */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int, numbers: IntArray, counts: IntArray) =
            BattleshipsLogic(n, numbers, counts, BattleshipsGenerator.fleet(n), d.ordinal)

        fun forLevel(level: Int, n: Int, numbers: IntArray, counts: IntArray) =
            BattleshipsLogic(n, numbers, counts, BattleshipsGenerator.fleet(n), level)
    }
}

object BattleshipsGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 10)[d.ordinal]

    /** Ship lengths, longest first: 10x10 has 1x4, 2x3, 3x2 and 4x1. */
    fun fleet(n: Int): List<Int> = when {
        n <= 6 -> listOf(3, 2, 2, 1, 1, 1)
        n <= 8 -> listOf(4, 3, 3, 2, 2, 1, 1, 1)
        else -> listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)
    }

    /** Cells to show at least: easier levels stay friendlier. */
    fun minGivens(d: Difficulty, cells: Int) = when (d) {
        Difficulty.EASY -> (cells * 0.12).toInt()
        Difficulty.MEDIUM -> (cells * 0.06).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val ship: BooleanArray, val counts: IntArray, val parts: IntArray)

    /** The fleet placed at random, ships apart; null if a ship finds no room. */
    private fun layout(n: Int, random: Random): Layout? {
        val ship = BooleanArray(n * n)
        val blocked = BooleanArray(n * n)
        for (len in fleet(n)) {
            var done = false
            repeat(300) {
                if (done) return@repeat
                val horizontal = len == 1 || random.nextBoolean()
                val r = random.nextInt(if (horizontal) n else n - len + 1)
                val c = random.nextInt(if (horizontal) n - len + 1 else n)
                val seg = IntArray(len) { if (horizontal) r * n + c + it else (r + it) * n + c }
                if (seg.any { blocked[it] }) return@repeat
                for (x in seg) {
                    ship[x] = true
                    blocked[x] = true
                    for (y in king(x, n)) blocked[y] = true
                }
                done = true
            }
            if (!done) return null
        }
        val counts = IntArray(2 * n) { k ->
            if (k < n) (0 until n).count { ship[k * n + it] } else (0 until n).count { ship[it * n + (k - n)] }
        }
        val parts = IntArray(n * n) { i ->
            if (!ship[i]) 0 else {
                val up = i >= n && ship[i - n]
                val down = i + n < n * n && ship[i + n]
                val left = i % n > 0 && ship[i - 1]
                val right = i % n < n - 1 && ship[i + 1]
                when {
                    up && down || left && right -> SHIP_MIDDLE
                    down -> SHIP_TOP
                    up -> SHIP_BOTTOM
                    right -> SHIP_LEFT
                    left -> SHIP_RIGHT
                    else -> SHIP_SINGLE
                }
            }
        }
        return Layout(n, ship, counts, parts)
    }

    private fun numbersOf(l: Layout, givens: BooleanArray) = IntArray(l.n * l.n) { if (givens[it]) l.parts[it] else -1 }

    private fun needsTop(d: Difficulty, l: Layout, givens: BooleanArray) =
        d.ordinal < 2 || !BattleshipsLogic.forLevel(d.ordinal - 1, l.n, numbersOf(l, givens), l.counts).run(IntArray(l.n * l.n) { UNKNOWN }).solved

    /** Shows cells until [d]'s logic solves the layout, then takes back every one it can. */
    private fun withGivens(d: Difficulty, l: Layout, random: Random, end: Long): BooleanArray? {
        val cells = l.n * l.n
        val unknown = IntArray(cells) { UNKNOWN }
        val givens = BooleanArray(cells)
        val limit = cells * 4 / 10
        while (true) {
            val result = BattleshipsLogic.forLevel(d, l.n, numbersOf(l, givens), l.counts).run(unknown)
            if (result.solved) break
            if (givens.count { it } >= limit || past(end)) return null
            val open = result.unresolved.ifEmpty { return null }
            repeat(maxOf(1, open.size / 8)) {
                val pool = open.filter { !givens[it] }
                val pick = (if (random.nextInt(10) < 6) pool.filter { l.ship[it] }.randomOrNull(random) else null) ?: pool.randomOrNull(random)
                if (pick != null) givens[pick] = true
            }
        }
        val floor = minGivens(d, cells)
        var have = givens.count { it }
        for (i in givens.indices.filter { givens[it] }.shuffled(random)) {
            if (have <= floor || past(end)) break
            givens[i] = false
            if (BattleshipsLogic.forLevel(d, l.n, numbersOf(l, givens), l.counts).run(unknown).solved) have-- else givens[i] = true
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
        var bestScore = Int.MAX_VALUE
        do {
            val l = layout(n, random) ?: continue
            val givens = withGivens(d, l, random, end) ?: continue
            val count = givens.count { it }
            val p = ShadePuzzle(Variant.BATTLESHIPS, d, n, l.ship, BooleanArray(n * n), numbers = numbersOf(l, givens), counts = l.counts)
            val top = needsTop(d, l, givens)
            if (top && (d.ordinal < 2 || count <= n * n / 10)) return p
            val score = (if (top) 0 else 1000) + count
            if (score < bestScore) { best = p; bestScore = score }
        } while (!past(end) && !(best != null && past(preferEnd)))
        best?.let { return it }
        while (true) {
            val l = layout(n, random) ?: continue
            val givens = withGivens(d, l, random, deadline(60_000)) ?: continue
            return ShadePuzzle(Variant.BATTLESHIPS, d, n, l.ship, BooleanArray(n * n), numbers = numbersOf(l, givens), counts = l.counts)
        }
    }
}

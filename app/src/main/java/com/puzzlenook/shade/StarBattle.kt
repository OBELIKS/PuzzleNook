package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Star Battle logic, as a person would solve it:
 * - stars don't touch, not even diagonally, so a star's neighbours are empty;
 * - a row, column or region with all its stars is empty elsewhere; one with exactly as many
 *   open cells as stars still needed gets stars in all of them;
 * - a cell is empty if a star there would leave some row, column or region without room
 *   for its stars;
 * - if some regions fit inside the same number of rows (or columns), the rest of those rows
 *   is empty, and the other way round.
 */
class StarBattleLogic(
    private val n: Int,
    private val k: Int,
    private val region: IntArray,
    /** Largest group of regions or lines in the confinement rule (1–3). */
    private val maxGroup: Int = 3,
    /** Whether to use the "a star here would leave no room" rule. */
    private val lookahead: Boolean = true,
) {
    private val rows = List(n) { r -> IntArray(n) { r * n + it } }
    private val cols = List(n) { c -> IntArray(n) { it * n + c } }
    private val regions = List(n) { g -> (0 until n * n).filter { region[it] == g }.toIntArray() }
    private val units = rows + cols + regions
    private val unitsOf = Array(n * n) { i -> intArrayOf(i / n, n + i % n, 2 * n + region[i]) }
    private val kings = Array(n * n) { king(it, n) }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        val round = IntArray(n * n) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            val before = s.copyOf()
            if (!basic(s)) return ShadeResult(false, s, round)
            if (s.contentEquals(before)) {
                if (lookahead) lookahead(s)
                confinement(s)
            }
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (s.contentEquals(before)) return ShadeResult(false, s, round)
        }
    }

    /** Neighbours of stars, full units, units that need every open cell. False on a contradiction. */
    private fun basic(s: IntArray): Boolean {
        for (i in s.indices) if (s[i] == SHADED) for (j in kings[i]) {
            if (s[j] == SHADED) return false
            s[j] = WHITE
        }
        for (u in units) {
            val stars = u.count { s[it] == SHADED }
            val open = u.count { s[it] == UNKNOWN }
            if (stars > k || stars + open < k) return false
            if (stars == k) for (i in u) if (s[i] == UNKNOWN) s[i] = WHITE
            if (open > 0 && stars + open == k) for (i in u) if (s[i] == UNKNOWN) s[i] = SHADED
        }
        return true
    }

    private fun lookahead(s: IntArray) {
        for (c in s.indices) {
            if (s[c] != UNKNOWN) continue
            val blocked = kings[c].toHashSet().also { it += c }
            val breaks = units.indices.any { u ->
                val cells = units[u]
                val need = k - cells.count { s[it] == SHADED } - (if (u in unitsOf[c]) 1 else 0)
                if (need <= 0) return@any need < 0
                val free = cells.filter { s[it] == UNKNOWN && it !in blocked }
                !fits(free, need)
            }
            if (breaks) s[c] = WHITE
        }
    }

    /** Whether [need] (1 or 2) non-touching stars fit in [cells]. */
    private fun fits(cells: List<Int>, need: Int): Boolean = when {
        need <= 0 -> true
        need == 1 -> cells.isNotEmpty()
        else -> cells.indices.any { a ->
            (a + 1 until cells.size).any { b -> cells[b] !in kings[cells[a]] }
        }
    }

    private fun confinement(s: IntArray) {
        fun candidates(cells: IntArray) = cells.filter { s[it] != WHITE }
        for (byRow in listOf(true, false)) {
            val lines = if (byRow) rows else cols
            fun lineOf(i: Int) = if (byRow) i / n else i % n
            for (size in 1..maxGroup) {
                // Regions inside `size` lines: the rest of those lines is empty.
                subsets(n, size) { group ->
                    val cells = group.flatMap { candidates(regions[it]) }
                    val ls = cells.map(::lineOf).toSet()
                    if (ls.size == size) {
                        for (l in ls) for (i in lines[l]) if (s[i] == UNKNOWN && region[i] !in group) s[i] = WHITE
                    }
                }
                // Lines inside `size` regions: the rest of those regions is empty.
                subsets(n, size) { group ->
                    val cells = group.flatMap { candidates(lines[it]) }
                    val gs = cells.map { region[it] }.toSet()
                    if (gs.size == size) {
                        for (g in gs) for (i in regions[g]) if (s[i] == UNKNOWN && lineOf(i) !in group) s[i] = WHITE
                    }
                }
            }
        }
    }

    private fun valid(s: IntArray): Boolean =
        units.all { u -> u.count { s[it] == SHADED } == k } &&
            s.indices.all { i -> s[i] != SHADED || kings[i].none { s[it] == SHADED } }

    companion object {
        /** Calls [f] with every set of [size] numbers from 0 until [n]. */
        fun subsets(n: Int, size: Int, f: (List<Int>) -> Unit) {
            val pick = IntArray(size)
            fun rec(pos: Int, from: Int) {
                if (pos == size) {
                    f(pick.toList())
                    return
                }
                for (x in from until n) {
                    pick[pos] = x
                    rec(pos + 1, x + 1)
                }
            }
            rec(0, 0)
        }
    }
}

object StarBattleGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 9, 10)[d.ordinal]
    fun stars(d: Difficulty) = if (d.ordinal >= 2) 2 else 1

    /**
     * The steps a puzzle of each level may need. Easy: full rows, columns and regions, and a
     * region or line squeezed into one line or region. Medium: also groups of two. Hard: also
     * "a star here would leave no room". Expert: also groups of three. Puzzles that need a
     * harder step than their level feel like guesswork, with two places that both look right.
     */
    fun logicFor(d: Difficulty, n: Int, k: Int, region: IntArray) = when (d) {
        Difficulty.EASY -> StarBattleLogic(n, k, region, maxGroup = 1, lookahead = false)
        Difficulty.MEDIUM -> StarBattleLogic(n, k, region, maxGroup = 2, lookahead = false)
        Difficulty.HARD -> StarBattleLogic(n, k, region, maxGroup = 2, lookahead = true)
        Difficulty.EXPERT -> StarBattleLogic(n, k, region)
    }

    /**
     * Places stars, grows one region around each star (two per region on bigger grids), then
     * reshapes regions cell by cell until the logic solves it. If time runs out, a few stars are given.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000): ShadePuzzle {
        val n = size(d)
        val k = stars(d)
        val end = deadline(budgetMs)
        var best: Triple<BooleanArray, IntArray, Int>? = null
        while (best == null || !past(end)) {
            val stars = placeStars(n, k, random) ?: continue
            val region = grow(n, k, stars, random) ?: continue
            var stuck = logicFor(d, n, k, region).run(IntArray(n * n) { UNKNOWN }).unresolved.size
            // Reshape: move a border cell (never a star) to a neighbouring region if that doesn't hurt.
            var tries = 0
            while (stuck > 0 && tries++ < 300 && !past(end)) {
                val i = (0 until n * n).random(random)
                if (stars[i]) continue
                val to = orth(i, n).map { region[it] }.filter { it != region[i] }.randomOrNull(random) ?: continue
                val from = region[i]
                if ((0 until n * n).count { region[it] == from } <= 1) continue
                region[i] = to
                if (!connected(n) { region[it] == from }) {
                    region[i] = from
                    continue
                }
                val now = logicFor(d, n, k, region).run(IntArray(n * n) { UNKNOWN }).unresolved.size
                if (now <= stuck) stuck = now else region[i] = from
            }
            if (best == null || stuck < best.third) best = Triple(stars, region.copyOf(), stuck)
            if (stuck == 0) break
        }
        val (solution, region, _) = best!!
        val givens = BooleanArray(n * n)
        val logic = logicFor(d, n, k, region)
        while (true) {
            val start = IntArray(n * n) { if (givens[it]) (if (solution[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            // Give a star where the logic is stuck (or the nearest open cell if no star is left there).
            // If the logic stopped with nothing left open, fall back to any cell not yet given.
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] } }
            val pick = open.filter { solution[it] }.randomOrNull(random) ?: open.randomOrNull(random) ?: break
            givens[pick] = true
        }
        return ShadePuzzle(Variant.STAR_BATTLE, d, n, solution, givens, regions = region, stars = k)
    }

    /** k stars per row and column, none touching. */
    private fun placeStars(n: Int, k: Int, random: Random): BooleanArray? {
        val stars = BooleanArray(n * n)
        val colCount = IntArray(n)
        var steps = 0
        fun ok(r: Int, c: Int) = colCount[c] < k && king(r * n + c, n).none { stars[it] }
        fun rec(r: Int): Boolean {
            if (r == n) return colCount.all { it == k }
            if (++steps > 20_000) return false
            val options = (0 until n).shuffled(random)
            if (k == 1) {
                for (c in options) if (ok(r, c)) {
                    stars[r * n + c] = true; colCount[c]++
                    if (rec(r + 1)) return true
                    stars[r * n + c] = false; colCount[c]--
                }
            } else {
                for (a in options) for (b in options) {
                    if (b <= a + 1 || !ok(r, a) || !ok(r, b)) continue
                    stars[r * n + a] = true; colCount[a]++
                    stars[r * n + b] = true; colCount[b]++
                    if (rec(r + 1)) return true
                    stars[r * n + a] = false; colCount[a]--
                    stars[r * n + b] = false; colCount[b]--
                }
            }
            return false
        }
        return if (rec(0)) stars else null
    }

    /** Grows n regions from n of the stars; a region takes in at most k stars. */
    private fun grow(n: Int, k: Int, stars: BooleanArray, random: Random): IntArray? {
        val region = IntArray(n * n) { -1 }
        val count = IntArray(n)
        val starCells = (0 until n * n).filter { stars[it] }.shuffled(random)
        // Seed each region with a star; for two stars per region pick seeds far apart.
        val seeds = mutableListOf<Int>()
        for (s in starCells) {
            if (seeds.size == n) break
            seeds += s
        }
        seeds.forEachIndexed { g, s -> region[s] = g; count[g] = 1 }
        var left = n * n - n
        while (left > 0) {
            val frontier = (0 until n * n).filter { i -> region[i] < 0 && orth(i, n).any { region[it] >= 0 } }
            if (frontier.isEmpty()) return null
            var placed = false
            for (i in frontier.shuffled(random).take(4)) {
                val options = orth(i, n).map { region[it] }.filter { it >= 0 }.distinct()
                    .filter { g -> !stars[i] || count[g] < k }
                // Prefer small regions so sizes even out a bit.
                val g = options.minByOrNull { g -> region.count { it == g } + random.nextInt(4) } ?: continue
                region[i] = g
                if (stars[i]) count[g]++
                left--
                placed = true
                break
            }
            if (!placed) return null
        }
        return region.takeIf { count.all { it == k } }
    }
}

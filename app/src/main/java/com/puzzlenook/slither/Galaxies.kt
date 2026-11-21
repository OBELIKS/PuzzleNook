package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.ShadeResult
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.shade.deadline
import com.puzzlenook.shade.past
import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/** Most dots a puzzle may hold: the logic keeps each cell's candidate dots in one [Long]. */
const val MAX_GALAXY_DOTS = 62

/**
 * Geometry of an n×n Galaxies board. Walls are edges of `SlitherGrid(n)`. A dot sits on a half-grid
 * point (x, y), x and y in 1..2n-1 (cell (r, c) has its centre at (2c + 1, 2r + 1)), encoded
 * `y * (2n + 1) + x`: even x and odd y is the middle of a vertical edge, both even a corner.
 */
class GalaxyGrid(val n: Int, val dots: IntArray) {
    val grid = grids.getOrPut(n) { SlitherGrid(n) }
    val cells = n * n
    val count = dots.size
    private val w = 2 * n + 1

    fun dx(d: Int) = dots[d] % w
    fun dy(d: Int) = dots[d] / w

    /** The cells that touch dot [d]: they belong to it. */
    val own: Array<IntArray> = Array(count) { d ->
        val x = dx(d)
        val y = dy(d)
        val cols = if (x % 2 == 0) listOf(x / 2 - 1, x / 2) else listOf((x - 1) / 2)
        val rows = if (y % 2 == 0) listOf(y / 2 - 1, y / 2) else listOf((y - 1) / 2)
        val out = ArrayList<Int>()
        for (r in rows) for (c in cols) if (r in 0 until n && c in 0 until n) out += r * n + c
        out.toIntArray()
    }

    /** Edges whose line passes through dot [d]: never walls. */
    val through: Array<IntArray> = Array(count) { d ->
        val x = dx(d)
        val y = dy(d)
        when {
            x % 2 == 0 && y % 2 == 1 -> intArrayOf(grid.v(y / 2, x / 2))
            x % 2 == 1 && y % 2 == 0 -> intArrayOf(grid.h(y / 2, x / 2))
            x % 2 == 0 && y % 2 == 0 -> intArrayOf(grid.h(y / 2, x / 2 - 1), grid.h(y / 2, x / 2), grid.v(y / 2 - 1, x / 2), grid.v(y / 2, x / 2))
            else -> IntArray(0)
        }
    }

    /** The cell opposite [cell] through dot [d], -1 if it falls outside the board. */
    fun mirror(cell: Int, d: Int): Int {
        val mr = dy(d) - cell / n - 1
        val mc = dx(d) - cell % n - 1
        return if (mr in 0 until n && mc in 0 until n) mr * n + mc else -1
    }

    /** Edge between each pair of neighbouring cells: [edgeCells] of the grid, both sides inside. */
    val neighbourEdges: Array<IntArray> = Array(cells) { i ->
        grid.cellEdges[i].filter { e -> grid.edgeCells[e].all { it >= 0 } }.toIntArray()
    }
    val neighbours: Array<IntArray> = Array(cells) { i ->
        neighbourEdges[i].map { e -> grid.edgeCells[e].first { it != i } }.toIntArray()
    }

    /** Candidate dots of every cell before any reasoning: dots whose mirror image of the cell is on the board; own cells are fixed. */
    val initial: LongArray = LongArray(cells).also { m ->
        for (c in 0 until cells) for (d in 0 until count) if (mirror(c, d) >= 0) m[c] = m[c] or (1L shl d)
        for (d in 0 until count) for (o in own[d]) m[o] = 1L shl d
    }

    /** Edge states at the start: only the outer border is a wall. */
    fun startEdges() = IntArray(grid.edges) { e -> if (grid.edgeCells[e].any { it < 0 }) SHADED else UNKNOWN }

    companion object {
        private val grids = java.util.concurrent.ConcurrentHashMap<Int, SlitherGrid>()

        fun encode(n: Int, x: Int, y: Int) = y * (2 * n + 1) + x
    }
}

/**
 * Galaxies logic. Every cell has a set of dots it may belong to; walls follow from it. Edge states:
 * [SHADED] = wall, [WHITE] = no wall.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: a cell can only belong to a dot whose mirror cell may belong to it too; cells touching a dot belong to it;
 *   when a cell is settled its mirror is; neighbours with no dot in common have a wall between them, a wall
 *   or no wall between neighbours carries over to their dots;
 * - 1: adds reachability: a cell can only belong to a dot it can still be joined to without crossing a wall
 *   or a cell that can't belong to that dot;
 * - 2: as 1, plus a bounded what-if: settle one cell's dot and run [TRIAL_SWEEPS] sweeps of the level 0 steps;
 *   a contradiction rules that dot out;
 * - 3: as 1, plus an unbounded what-if over the level 1 steps with branch intersection: whatever is true
 *   in every dot choice that survives is settled.
 */
class GalaxiesLogic(private val gg: GalaxyGrid, private val level: Int) {
    private val cells = gg.cells
    private val grid = gg.grid

    /** The cells' candidate dots when [run] stopped (more than one dot = still open). */
    var finalCand: LongArray = gg.initial.copyOf()
        private set

    private class W(val cand: LongArray, val s: IntArray) {
        fun copy() = W(cand.copyOf(), s.copyOf())
    }

    fun run(start: IntArray): ShadeResult {
        val w = W(gg.initial.copyOf(), start.copyOf())
        val round = IntArray(start.size) { if (start[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = sweep(w)
            if (res == NONE && level >= 1) res = reach(w)
            if (res == NONE && level >= 2) res = whatIf(w)
            if (res == CONTRA) {
                finalCand = w.cand
                return ShadeResult(false, w.s, round)
            }
            derive(w)
            for (e in w.s.indices) if (round[e] < 0 && w.s[e] != UNKNOWN) round[e] = r
            finalCand = w.cand
            if (w.cand.all { single(it) }) return ShadeResult(valid(w) && w.s.none { it == UNKNOWN }, w.s, round)
            if (res == NONE) return ShadeResult(false, w.s, round)
        }
    }

    private fun single(m: Long) = m != 0L && m and (m - 1) == 0L

    private fun valid(w: W): Boolean {
        val owner = IntArray(cells) { java.lang.Long.numberOfTrailingZeros(w.cand[it]) }
        for (d in 0 until gg.count) {
            val total = owner.count { it == d }
            val seen = BooleanArray(cells)
            val stack = ArrayDeque<Int>()
            stack.addLast(gg.own[d][0])
            seen[gg.own[d][0]] = true
            var n = 0
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                n++
                for (nb in gg.neighbours[c]) if (!seen[nb] && owner[nb] == d) { seen[nb] = true; stack.addLast(nb) }
            }
            if (n != total) return false
        }
        return true
    }

    /** Walls between cells with no dot in common, no wall between settled cells of one dot. */
    private fun derive(w: W): Boolean {
        var changed = false
        for (e in 0 until grid.edges) {
            val (a, b) = grid.edgeCells[e].let { it[0] to it[1] }
            if (a < 0 || b < 0 || w.s[e] != UNKNOWN) continue
            val ca = w.cand[a]
            val cb = w.cand[b]
            if (ca and cb == 0L) { w.s[e] = SHADED; changed = true }
            else if (ca == cb && single(ca)) { w.s[e] = WHITE; changed = true }
        }
        return changed
    }

    /** One pass of the level 0 steps over the board. */
    private fun sweep(w: W): Int {
        var changed = false
        val cand = w.cand
        for (c in 0 until cells) {
            var m = cand[c]
            if (m == 0L) return CONTRA
            var bits = m
            while (bits != 0L) {
                val d = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                val mc = gg.mirror(c, d)
                if (mc < 0 || (cand[mc] shr d) and 1L == 0L) m = m and (1L shl d).inv()
            }
            if (m != cand[c]) {
                cand[c] = m
                changed = true
                if (m == 0L) return CONTRA
            }
            if (single(m)) {
                val d = java.lang.Long.numberOfTrailingZeros(m)
                val mc = gg.mirror(c, d)
                val nm = cand[mc] and m
                if (nm != cand[mc]) {
                    cand[mc] = nm
                    changed = true
                    if (nm == 0L) return CONTRA
                }
            }
        }
        for (e in 0 until grid.edges) {
            val a = grid.edgeCells[e][0]
            val b = grid.edgeCells[e][1]
            if (a < 0 || b < 0) continue
            when (w.s[e]) {
                WHITE -> {
                    val x = cand[a] and cand[b]
                    if (x != cand[a] || x != cand[b]) {
                        cand[a] = x
                        cand[b] = x
                        changed = true
                        if (x == 0L) return CONTRA
                    }
                }
                SHADED -> {
                    if (single(cand[a])) {
                        val nb = cand[b] and cand[a].inv()
                        if (nb != cand[b]) { cand[b] = nb; changed = true; if (nb == 0L) return CONTRA }
                    }
                    if (single(cand[b])) {
                        val na = cand[a] and cand[b].inv()
                        if (na != cand[a]) { cand[a] = na; changed = true; if (na == 0L) return CONTRA }
                    }
                }
                else -> {
                    if (cand[a] and cand[b] == 0L) { w.s[e] = SHADED; changed = true }
                    else if (cand[a] == cand[b] && single(cand[a])) { w.s[e] = WHITE; changed = true }
                }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: a cell keeps a dot only if it can still be reached from the dot's own cells. */
    private fun reach(w: W): Int {
        var changed = false
        val cand = w.cand
        val seen = BooleanArray(cells)
        val stack = IntArray(cells)
        for (d in 0 until gg.count) {
            val bit = 1L shl d
            seen.fill(false)
            var top = 0
            for (o in gg.own[d]) {
                if (cand[o] and bit == 0L) return CONTRA
                seen[o] = true
                stack[top++] = o
            }
            while (top > 0) {
                val c = stack[--top]
                val nbs = gg.neighbours[c]
                val es = gg.neighbourEdges[c]
                for (k in nbs.indices) {
                    val nb = nbs[k]
                    if (!seen[nb] && cand[nb] and bit != 0L && w.s[es[k]] != SHADED) { seen[nb] = true; stack[top++] = nb }
                }
            }
            for (c in 0 until cells) if (!seen[c] && cand[c] and bit != 0L) {
                cand[c] = cand[c] and bit.inv()
                changed = true
                if (cand[c] == 0L) return CONTRA
            }
        }
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(w: W): Int {
        var changed = false
        for (c in 0 until cells) {
            val m = w.cand[c]
            if (single(m)) continue
            if (level >= 3) {
                val union = LongArray(cells)
                var survivors = 0
                var bits = m
                while (bits != 0L) {
                    val d = java.lang.Long.numberOfTrailingZeros(bits)
                    bits = bits and (bits - 1)
                    val t = w.copy()
                    t.cand[c] = 1L shl d
                    if (fixpoint(t) == CONTRA) continue
                    survivors++
                    for (x in 0 until cells) union[x] = union[x] or t.cand[x]
                }
                if (survivors == 0) return CONTRA
                for (x in 0 until cells) {
                    val nm = w.cand[x] and union[x]
                    if (nm != w.cand[x]) { w.cand[x] = nm; changed = true }
                }
            } else {
                var keep = m
                var bits = m
                while (bits != 0L) {
                    val d = java.lang.Long.numberOfTrailingZeros(bits)
                    bits = bits and (bits - 1)
                    val t = w.copy()
                    t.cand[c] = 1L shl d
                    var bad = false
                    for (k in 0 until TRIAL_SWEEPS) {
                        val res = sweep(t)
                        if (res == CONTRA) { bad = true; break }
                        if (res == NONE) break
                    }
                    if (bad) keep = keep and (1L shl d).inv()
                }
                if (keep == 0L) return CONTRA
                if (keep != m) { w.cand[c] = keep; changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1 steps until nothing changes. */
    private fun fixpoint(t: W): Int {
        while (true) {
            var res = sweep(t)
            if (res == CONTRA) return CONTRA
            if (res == NONE) {
                res = reach(t)
                if (res == CONTRA) return CONTRA
                if (res == NONE) return NONE
            }
        }
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        /** Sweeps of the level 0 steps a level 2 trial may run. */
        const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, gg: GalaxyGrid) = GalaxiesLogic(gg, d.ordinal)
    }
}

/** [dots] are half-grid points (see [GalaxyGrid]); [solution] marks the wall edges of `SlitherGrid(n)`, outer border included. */
class GalaxiesPuzzle(val difficulty: Difficulty, val n: Int, val dots: IntArray, val solution: BooleanArray)

/**
 * Galaxies (home section "Regions"): splits the board into symmetric galaxies, peels cells off until the level
 * solves, then merges neighbouring galaxies while it still does.
 */
object GalaxiesGenerator {
    fun size(d: Difficulty) = intArrayOf(5, 7, 9, 10)[d.ordinal]

    /** Dots to keep at least: the easier levels have plenty. */
    fun minClues(d: Difficulty, n: Int) = when (d) {
        Difficulty.EASY -> (n * n * 0.2).toInt()
        Difficulty.MEDIUM -> (n * n * 0.17).toInt()
        else -> 0
    }

    /** Most extra cell pairs a galaxy may grow by when the board is first split. */
    private fun growth(d: Difficulty) = intArrayOf(2, 3, 5, 7)[d.ordinal]

    private class Region(var dot: Int, val cells: MutableList<Int>)

    private class Layout(val n: Int, val regions: MutableList<Region>) {
        val owner = IntArray(n * n)

        init { reindex() }

        fun reindex() {
            for ((k, r) in regions.withIndex()) for (c in r.cells) owner[c] = k
        }

        fun gg() = GalaxyGrid(n, IntArray(regions.size) { regions[it].dot })

        fun walls(gg: GalaxyGrid) = BooleanArray(gg.grid.edges) { e ->
            val (a, b) = gg.grid.edgeCells[e].let { it[0] to it[1] }
            a < 0 || b < 0 || owner[a] != owner[b]
        }
    }

    /** Splits the board into connected, point-symmetric galaxies, each with its dot. */
    private fun partition(n: Int, d: Difficulty, random: Random): Layout {
        val free = BooleanArray(n * n) { true }
        val regions = ArrayList<Region>()
        for (c in (0 until n * n).shuffled(random)) {
            if (!free[c]) continue
            val r = c / n
            val col = c % n
            val kind = random.nextInt(100)
            var dot = GalaxyGrid.encode(n, 2 * col + 1, 2 * r + 1)
            var cells = mutableListOf(c)
            val nbs = intArrayOf(c - n, c + n, c - 1, c + 1).filterIndexed { k, x ->
                x in 0 until n * n && free[x] && (k < 2 || x / n == r)
            }
            if (kind < 40 && nbs.isNotEmpty()) {
                val o = nbs.random(random)
                dot = GalaxyGrid.encode(n, col + o % n + 1, r + o / n + 1)
                cells = mutableListOf(c, o)
            } else if (kind < 52) {
                // A corner: the 2×2 block to the lower right, if it is all free.
                if (col + 1 < n && r + 1 < n && free[c + 1] && free[c + n] && free[c + n + 1]) {
                    dot = GalaxyGrid.encode(n, 2 * col + 2, 2 * r + 2)
                    cells = mutableListOf(c, c + 1, c + n, c + n + 1)
                }
            }
            for (x in cells) free[x] = false
            val region = Region(dot, cells)
            val gg0 = GalaxyGrid(n, intArrayOf(dot))
            repeat(random.nextInt(growth(d) + 1)) {
                val inRegion = region.cells.toSet()
                val options = ArrayList<Int>()
                for (x in region.cells) for (nb in gg0.neighbours[x]) {
                    if (!free[nb] || nb in inRegion) continue
                    val m = gg0.mirror(nb, 0)
                    if (m >= 0 && free[m] && m != nb) options += nb
                }
                if (options.isEmpty()) return@repeat
                val x = options.random(random)
                val m = gg0.mirror(x, 0)
                free[x] = false
                free[m] = false
                region.cells += x
                region.cells += m
            }
            regions += region
        }
        return Layout(n, regions)
    }

    /**
     * Peels a cell off a galaxy (with its mirror cell, as one galaxy if they touch, else two) when the
     * rest of the galaxy stays connected. False if nothing could be peeled from [x]'s galaxy.
     */
    private fun peel(layout: Layout, gg: GalaxyGrid, x: Int, random: Random): Boolean {
        val k = layout.owner[x]
        val region = layout.regions[k]
        if (region.cells.size <= 1 || x in gg.own[k]) return false
        val m = gg.mirror(x, k)
        val rest = region.cells.filter { it != x && it != m }.toSet()
        if (rest.isEmpty()) return false
        // The rest must stay connected.
        val start = gg.own[k][0]
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        seen += start
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            for (nb in gg.neighbours[c]) if (nb in rest && seen.add(nb)) stack.addLast(nb)
        }
        if (seen.size != rest.size) return false
        region.cells.clear()
        region.cells.addAll(rest)
        val n = layout.n
        if (gg.neighbours[x].contains(m) && random.nextBoolean()) {
            val dot = GalaxyGrid.encode(n, x % n + m % n + 1, x / n + m / n + 1)
            layout.regions += Region(dot, mutableListOf(x, m))
        } else {
            layout.regions += Region(GalaxyGrid.encode(n, 2 * (x % n) + 1, 2 * (x / n) + 1), mutableListOf(x))
            layout.regions += Region(GalaxyGrid.encode(n, 2 * (m % n) + 1, 2 * (m / n) + 1), mutableListOf(m))
        }
        layout.reindex()
        return true
    }

    /** Makes every galaxy of [r] a set of single cells. */
    private fun shatter(layout: Layout, k: Int) {
        val region = layout.regions[k]
        val n = layout.n
        val cells = region.cells.toList()
        region.cells.clear()
        region.cells += cells[0]
        region.dot = GalaxyGrid.encode(n, 2 * (cells[0] % n) + 1, 2 * (cells[0] / n) + 1)
        for (c in cells.drop(1)) layout.regions += Region(GalaxyGrid.encode(n, 2 * (c % n) + 1, 2 * (c / n) + 1), mutableListOf(c))
        layout.reindex()
    }

    /** Peels galaxies until the logic at [level] solves the dots; null when it runs out of time or dots. */
    private fun solve(layout: Layout, level: Int, random: Random, end: Long): Layout? {
        val d = Difficulty.entries[level]
        while (!past(end)) {
            if (layout.regions.size > MAX_GALAXY_DOTS) return null
            val gg = layout.gg()
            val logic = GalaxiesLogic.forLevel(d, gg)
            val res = logic.run(gg.startEdges())
            if (res.solved) return layout
            val stuck = (0 until gg.cells).filter { java.lang.Long.bitCount(logic.finalCand[it]) > 1 }.shuffled(random)
            if (stuck.isEmpty()) return null
            var peeled = 0
            val batch = maxOf(1, stuck.size / 10)
            var tried = 0
            for (x in stuck) {
                // The cell may have moved since the dots were computed: check against the current layout.
                val g = layout.gg()
                if (peel(layout, g, x, random) && ++peeled >= batch) break
                if (++tried > 30) break
            }
            if (peeled == 0) {
                val k = layout.owner[stuck.first()]
                shatter(layout, k)
            }
        }
        return null
    }

    /** [cells] as one galaxy, if they are point-symmetric around a dot that touches them all around; else null. */
    private fun mergedDot(n: Int, cells: List<Int>): Int? {
        val xs = cells.map { 2 * (it % n) + 1 }
        val ys = cells.map { 2 * (it / n) + 1 }
        val x = (xs.min() + xs.max()) / 2
        val y = (ys.min() + ys.max()) / 2
        val set = cells.toHashSet()
        for (c in cells) {
            val mc = x - c % n - 1
            val mr = y - c / n - 1
            if (mc !in 0 until n || mr !in 0 until n || mr * n + mc !in set) return null
        }
        val dot = GalaxyGrid.encode(n, x, y)
        if (x !in 1..2 * n - 1 || y !in 1..2 * n - 1) return null
        val own = GalaxyGrid(n, intArrayOf(dot)).own[0]
        return if (own.all { it in set }) dot else null
    }

    /**
     * Removes dots: merges two or three neighbouring galaxies into one when together they are symmetric
     * around a dot and the logic at [level] still solves the board. Stops at [floor] dots or when [end] passes.
     */
    private fun merge(layout: Layout, level: Int, floor: Int, random: Random, end: Long): Layout {
        val d = Difficulty.entries[level]
        var cur = layout
        var progress = true
        while (progress && !past(end)) {
            progress = false
            val n = cur.n
            val gg = cur.gg()
            val adj = Array(cur.regions.size) { HashSet<Int>() }
            for (c in 0 until n * n) for (nb in gg.neighbours[c]) if (cur.owner[nb] != cur.owner[c]) adj[cur.owner[c]] += cur.owner[nb]
            val groups = ArrayList<List<Int>>()
            for (a in adj.indices) for (b in adj[a]) {
                if (b > a) groups += listOf(a, b)
                for (c in adj[b] + adj[a]) if (c != a && c != b && c > b && (c in adj[a] || c in adj[b])) groups += listOf(a, b, c)
            }
            groups.shuffle(random)
            for (g in groups) {
                if (past(end) || cur.regions.size - g.size + 1 < floor) continue
                val cells = g.flatMap { cur.regions[it].cells }
                val dot = mergedDot(n, cells) ?: continue
                val regions = cur.regions.filterIndexed { k, _ -> k !in g }.map { Region(it.dot, it.cells.toMutableList()) }.toMutableList()
                regions += Region(dot, cells.toMutableList())
                val trial = Layout(n, regions)
                val tg = trial.gg()
                if (GalaxiesLogic.forLevel(d, tg).run(tg.startEdges()).solved) {
                    cur = trial
                    progress = true
                    break
                }
            }
        }
        return cur
    }

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): GalaxiesPuzzle {
        val end = deadline(budgetMs)
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1500)) else end
        var best: GalaxiesPuzzle? = null
        while (true) {
            if (best != null && (past(end) || past(preferEnd))) return best
            // Without a valid puzzle yet there is no time limit: the peeling always ends.
            val solved = solve(partition(n, d, random), d.ordinal, random, if (best == null) Long.MAX_VALUE else end)
            if (solved == null || solved.regions.size < minClues(d, n)) continue
            val layout = merge(solved, d.ordinal, minClues(d, n), random, minOf(end, deadline(budgetMs / 2)))
            val gg = layout.gg()
            val p = GalaxiesPuzzle(d, n, gg.dots, layout.walls(gg))
            val top = d.ordinal < 2 || !GalaxiesLogic.forLevel(Difficulty.entries[d.ordinal - 1], gg).run(gg.startEdges()).solved
            if (top) return p
            best = p
            if (past(end)) return p
        }
    }
}

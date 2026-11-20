package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.ShadeResult
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.shade.connected
import com.puzzlenook.shade.deadline
import com.puzzlenook.shade.past
import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/** Arrow clue: `dir * 100 + count` (dir 0 up, 1 down, 2 left, 3 right), [NO_CLUE] on every other cell. */
const val NO_CLUE = -1

fun yajilinDir(clue: Int) = clue / 100
fun yajilinCount(clue: Int) = clue % 100

/**
 * Cells met when walking from clue cell [i] in direction [dir] (0 up, 1 down, 2 left, 3 right) to the border.
 */
fun yajilinRay(mg: MasyuGrid, i: Int, dir: Int): IntArray {
    val out = ArrayList<Int>()
    var c = mg.nb(i, dir)
    while (c >= 0) {
        out += c
        c = mg.nb(c, dir)
    }
    return out.toIntArray()
}

/**
 * Yajilin logic. Elements: the first `mg.grid.edges` entries are loop edges ([SHADED] = line, [WHITE] = no
 * line), the next n×n are cells ([SHADED] = shaded, [WHITE] = not shaded). Clue cells are neither shaded nor
 * on the loop. Every other cell is shaded or on the one loop, shaded cells never touch, and an arrow clue
 * counts the shaded cells in its direction.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: cell rules (a shaded cell has no lines and no shaded neighbour, a loop cell has two lines, a cell that
 *   can't have two lines is shaded) and arrow counts (count met: the rest is clear, count only just possible:
 *   all shaded);
 * - 1: adds the premature-loop rule (a loop closed too early, or closed while cells are still off it, is
 *   refused), a finished loop clears every other edge, and everything known to be on the loop must stay
 *   reachable;
 * - 2: as 1, plus what-if on single elements whose trial runs at most [TRIAL_SWEEPS] sweeps of the level 0 steps;
 * - 3: as 1, plus what-if on single elements propagated to the end with the level 0 and 1 steps; what the
 *   two options agree on is settled too (branch intersection).
 */
class YajilinLogic(
    private val mg: MasyuGrid,
    private val clues: IntArray,
    private val level: Int,
    private val trialSweeps: Int = TRIAL_SWEEPS,
) {
    private val cells = mg.n * mg.n
    private val edges = mg.grid.edges
    private val isClue = BooleanArray(cells) { clues[it] >= 0 }
    private val clueList = (0 until cells).filter { isClue[it] }.toIntArray()
    private val rays = Array(cells) { if (isClue[it]) yajilinRay(mg, it, yajilinDir(clues[it])) else IntArray(0) }
    private val nbrs = Array(cells) { i -> (0 until 4).map { mg.nb(i, it) }.filter { it >= 0 }.toIntArray() }
    private var bad = false

    /** Number of elements: edges, then cells. */
    val size: Int get() = edges + cells

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        for (i in clueList) {
            s[edges + i] = WHITE
            for (e in mg.edgesOf(i)) s[e] = WHITE
        }
        val round = IntArray(s.size) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = propagate(s, level >= 1, 1)
            if (res == NONE && level >= 2) res = whatIf(s)
            if (res == CONTRA) return ShadeResult(false, s, round)
            for (e in s.indices) if (round[e] < 0 && s[e] != UNKNOWN) round[e] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (res == NONE) return ShadeResult(false, s, round)
        }
    }

    /** A fully decided state is the answer: one loop, every other cell shaded, counts exact. */
    private fun valid(s: IntArray): Boolean {
        val line = BooleanArray(edges) { s[it] == SHADED }
        return Loop.isSingleLoop(mg.grid, line) && complete(s, s.copyOf(edges))
    }

    /**
     * With the lines of [u] (SHADED = line) as the loop, the cells without a line are shaded: do they fit
     * what [s] says about the cells, never touch, and meet every arrow?
     */
    private fun complete(s: IntArray, u: IntArray): Boolean {
        val shaded = BooleanArray(cells)
        for (i in 0 until cells) {
            if (isClue[i]) continue
            var lines = 0
            for (e in mg.edgesOf(i)) if (u[e] == SHADED) lines++
            if (lines == 0) {
                if (s[edges + i] == WHITE) return false
                shaded[i] = true
            } else if (s[edges + i] == SHADED) return false
        }
        for (i in 0 until cells) if (shaded[i]) for (j in nbrs[i]) if (shaded[j]) return false
        for (c in clueList) if (rays[c].count { shaded[it] } != yajilinCount(clues[c])) return false
        return true
    }

    private fun put(s: IntArray, idx: Int, v: Int): Boolean {
        if (s[idx] == v) return false
        if (s[idx] != UNKNOWN) { bad = true; return false }
        s[idx] = v
        return true
    }

    private fun propagate(s: IntArray, extra: Boolean, maxSweeps: Int): Int {
        var any = false
        var sweeps = 0
        while (sweeps < maxSweeps) {
            bad = false
            var res = basic(s)
            if (res == NONE && extra && !bad) res = level1(s)
            if (bad || res == CONTRA) return CONTRA
            if (res == NONE) break
            any = true
            sweeps++
        }
        return if (any) CHANGED else NONE
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            val es = mg.edgesOf(i)
            var lines = 0
            var open = 0
            for (e in es) if (s[e] == SHADED) lines++ else if (s[e] == UNKNOWN) open++
            if (lines > 2) return CONTRA
            if (isClue[i]) {
                if (lines > 0) return CONTRA
                continue
            }
            val ci = edges + i
            if (s[ci] == UNKNOWN) {
                if (lines > 0) { s[ci] = WHITE; changed = true }
                else if (lines + open < 2) { s[ci] = SHADED; changed = true }
                else if (nbrs[i].any { s[edges + it] == SHADED }) { s[ci] = WHITE; changed = true }
            }
            if (s[ci] == SHADED) {
                if (lines > 0) return CONTRA
                for (e in es) if (s[e] == UNKNOWN) { s[e] = WHITE; changed = true }
                for (j in nbrs[i]) {
                    if (s[edges + j] == SHADED) return CONTRA
                    if (s[edges + j] == UNKNOWN) { s[edges + j] = WHITE; changed = true }
                }
            } else if (s[ci] == WHITE) {
                if (lines + open < 2) return CONTRA
                if (open > 0) {
                    if (lines == 2) { for (e in es) if (s[e] == UNKNOWN) { s[e] = WHITE; changed = true } }
                    else if (lines + open == 2) { for (e in es) if (s[e] == UNKNOWN) { s[e] = SHADED; changed = true } }
                }
            }
        }
        for (c in clueList) {
            val ray = rays[c]
            val need = yajilinCount(clues[c])
            var shaded = 0
            var unknown = 0
            for (j in ray) when (s[edges + j]) { SHADED -> shaded++; UNKNOWN -> unknown++ }
            if (shaded > need || shaded + unknown < need) return CONTRA
            if (unknown > 0) {
                val v = if (shaded == need) WHITE else if (shaded + unknown == need) SHADED else UNKNOWN
                if (v != UNKNOWN) for (j in ray) if (s[edges + j] == UNKNOWN) { s[edges + j] = v; changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1. */
    private fun level1(s: IntArray): Int {
        var changed = false
        val line = BooleanArray(edges) { s[it] == SHADED }
        if (line.any { it } && Loop.isSingleLoop(mg.grid, line)) {
            // The loop is closed and holds every line: nothing else may be drawn.
            for (e in 0 until edges) if (s[e] == UNKNOWN) { s[e] = WHITE; changed = true }
        } else {
            val t = s.copyOf(edges)
            when (Loop.closeLoops(mg.grid, t) { u -> complete(s, u) }) {
                null -> return CONTRA
                true -> {
                    for (e in 0 until edges) if (s[e] == UNKNOWN && t[e] != UNKNOWN) s[e] = t[e]
                    changed = true
                }
                false -> {}
            }
        }
        if (!Loop.connectable(mg.grid, s.copyOf(edges)) { d -> !isClue[d] && s[edges + d] == WHITE }) return CONTRA
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (idx in s.indices) {
            if (s[idx] != UNKNOWN) continue
            val t = arrayOf(s.copyOf(), s.copyOf())
            val contra = BooleanArray(2)
            for (k in 0..1) {
                t[k][idx] = if (k == 0) WHITE else SHADED
                contra[k] = if (level >= 3) propagate(t[k], true, Int.MAX_VALUE) == CONTRA
                else propagate(t[k], false, trialSweeps) == CONTRA
            }
            if (contra[0] && contra[1]) return CONTRA
            if (level >= 3) {
                if (contra[0] || contra[1]) {
                    val keep = t[if (contra[0]) 1 else 0]
                    for (i in s.indices) if (s[i] == UNKNOWN && keep[i] != UNKNOWN) { s[i] = keep[i]; changed = true }
                } else {
                    for (i in s.indices) if (s[i] == UNKNOWN && t[0][i] != UNKNOWN && t[0][i] == t[1][i]) { s[i] = t[0][i]; changed = true }
                }
            } else if (contra[0]) { s[idx] = SHADED; changed = true }
            else if (contra[1]) { s[idx] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        /** Sweeps of the level 0 steps a level 2 trial may run. */
        const val TRIAL_SWEEPS = 2
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        fun forLevel(d: Difficulty, mg: MasyuGrid, clues: IntArray) = YajilinLogic(mg, clues, d.ordinal)
    }
}

/**
 * [clues]: [NO_CLUE] or `dir * 100 + count` per cell; [shaded] marks the cells shaded in the solution and
 * [loop] its edges of `SlitherGrid(n - 1)` (cell centres).
 */
class YajilinPuzzle(val difficulty: Difficulty, val n: Int, val clues: IntArray, val shaded: BooleanArray, val loop: BooleanArray)

object YajilinGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 7, 8, 10)[d.ordinal]

    /** Clue cells to keep at least: easier levels stop removing them early. */
    fun minClues(d: Difficulty, n: Int) = when (d) {
        Difficulty.EASY -> (n * n * 0.16).toInt()
        Difficulty.MEDIUM -> (n * n * 0.12).toInt()
        else -> 0
    }

    /** Share of the greedy shaded set that starts as clue cells. */
    private const val START_CLUES = 0.6

    /** How thin the blob that makes the loop is (see `SlitherGenerator.region`). */
    private const val BLOB_BIAS = 0.5

    /** Random choices fixed for one generation, so that nearby loops get nearby clue layouts. */
    private class Seed(val mg: MasyuGrid, random: Random) {
        val cells = mg.n * mg.n
        val rank = IntArray(cells).also { r -> (0 until cells).shuffled(random).forEachIndexed { k, c -> r[c] = k } }
        val dirPref = IntArray(cells) { random.nextInt(4) }
        val nbrs = Array(cells) { i -> (0 until 4).map { mg.nb(i, it) }.filter { it >= 0 }.toIntArray() }
    }

    /** A loop, the cells off it in priority order, and a greedy non-touching shaded set among them. */
    private class Layout(val loop: BooleanArray, val off: IntArray, val base: BooleanArray)

    private fun layoutOf(seed: Seed, inside: BooleanArray): Layout {
        val mg = seed.mg
        val loop = mg.grid.loopOf(inside)
        val off = (0 until seed.cells).filter { i -> mg.edgesOf(i).none { loop[it] } }.sortedBy { seed.rank[it] }.toIntArray()
        val base = BooleanArray(seed.cells)
        for (i in off) if (seed.nbrs[i].none { base[it] }) base[i] = true
        return Layout(loop, off, base)
    }

    /** The clue array when the off-loop cells that are not in [shaded] are clue cells. */
    private fun cluesFor(seed: Seed, off: IntArray, shaded: BooleanArray): IntArray {
        val mg = seed.mg
        val clues = IntArray(seed.cells) { NO_CLUE }
        for (i in off) {
            if (shaded[i]) continue
            for (k in 0 until 4) {
                val d = (seed.dirPref[i] + k) % 4
                if (mg.nb(i, d) < 0) continue
                clues[i] = d * 100 + yajilinRay(mg, i, d).count { shaded[it] }
                break
            }
        }
        return clues
    }

    private fun startShaded(seed: Seed, l: Layout): BooleanArray {
        val shaded = l.base.copyOf()
        val firstOnes = l.off.filter { l.base[it] }
        for (i in firstOnes.take((firstOnes.size * START_CLUES).toInt())) shaded[i] = false
        return shaded
    }

    private class Climbed(val layout: Layout, val shaded: BooleanArray)

    /**
     * A random loop rarely leaves one answer for its clues, so this hill-climbs: flip a cell of the blob
     * whose border is the loop, keep the change if the logic at [level] leaves no more elements open than
     * before, until the starting clues solve. Starts again from a new blob if it gets stuck.
     */
    private fun climb(seed: Seed, level: Int, random: Random): Climbed {
        val mg = seed.mg
        val m = mg.n - 1
        val unknown = IntArray(mg.grid.edges + seed.cells) { UNKNOWN }
        val base = Difficulty.entries[level]
        fun open(l: Layout, shaded: BooleanArray): Int {
            val r = YajilinLogic.forLevel(base, mg, cluesFor(seed, l.off, shaded)).run(unknown)
            return if (r.solved) 0 else r.state.count { it == UNKNOWN }.coerceAtLeast(1)
        }
        while (true) {
            var cur = SlitherGenerator.region(m, random, BLOB_BIAS) ?: continue
            var layout = layoutOf(seed, cur)
            var shaded = startShaded(seed, layout)
            var score = open(layout, shaded)
            var tries = 0
            while (score > 0 && tries < 40 * mg.n * mg.n) {
                tries++
                val next = cur.copyOf()
                val i = random.nextInt(m * m)
                next[i] = !next[i]
                if (next.none { it } || !SlitherGenerator.ok(m, next) || !connected(m) { next[it] }) continue
                val l = layoutOf(seed, next)
                val sh = startShaded(seed, l)
                val s = open(l, sh)
                if (s <= score) { cur = next; layout = l; shaded = sh; score = s }
            }
            if (score == 0) return Climbed(layout, shaded)
        }
    }

    /** Turns clue cells into shaded cells (their loop neighbours stay) while the level's logic still solves. */
    private fun removeClues(d: Difficulty, seed: Seed, l: Layout, shaded: BooleanArray, start: IntArray, random: Random, end: Long) {
        val mg = seed.mg
        var left = l.off.count { !shaded[it] }
        val floor = minClues(d, mg.n)
        for (i in l.off.filter { l.base[it] && !shaded[it] }.shuffled(random)) {
            if (past(end) || left <= floor) break
            if (seed.nbrs[i].any { shaded[it] }) continue
            shaded[i] = true
            if (YajilinLogic.forLevel(d, mg, cluesFor(seed, l.off, shaded)).run(start).solved) left-- else shaded[i] = false
        }
    }

    private fun needsTop(d: Difficulty, mg: MasyuGrid, clues: IntArray, start: IntArray) =
        d.ordinal < 2 || !YajilinLogic.forLevel(Difficulty.entries[d.ordinal - 1], mg, clues).run(start).solved

    /**
     * A climbed loop whose starting clues the logic solves (Easy: level 0, others: level 1), then clue cells
     * turned into shaded cells while the level's logic still solves it. Hard and Expert keep trying for a
     * puzzle that needs the level's top technique (Expert for 1 s, then it takes the best valid one).
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): YajilinPuzzle {
        val end = deadline(budgetMs)
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        val mg = MasyuGrid(n)
        val unknown = IntArray(mg.grid.edges + n * n) { UNKNOWN }
        var best: YajilinPuzzle? = null
        while (true) {
            if (best != null && (past(end) || past(preferEnd))) return best
            val seed = Seed(mg, random)
            val c = climb(seed, minOf(d.ordinal, 1), random)
            val shaded = c.shaded.copyOf()
            removeClues(d, seed, c.layout, shaded, unknown, random, end)
            val clues = cluesFor(seed, c.layout.off, shaded)
            val p = YajilinPuzzle(d, n, clues, shaded, c.layout.loop)
            if (needsTop(d, mg, clues, unknown)) return p
            best = p
        }
    }
}

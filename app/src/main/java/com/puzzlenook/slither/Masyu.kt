package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.ShadeResult
import com.puzzlenook.shade.connected
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.shade.deadline
import com.puzzlenook.shade.past
import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/** `MasyuPuzzle.pearls` values: no pearl, white (straight), black (turn). */
const val NO_PEARL = 0
const val WHITE_PEARL = 1
const val BLACK_PEARL = 2

private const val UP = 0
private const val DOWN = 1
private const val LEFT = 2
private const val RIGHT = 3

/**
 * Geometry of an n×n Masyu board: the loop runs through cell centres, so it is a Slitherlink loop on
 * `SlitherGrid(n - 1)` whose dots are the cells (cell i = dot i). Directions: 0 up, 1 down, 2 left, 3 right.
 */
class MasyuGrid(val n: Int) {
    val grid = SlitherGrid(n - 1)
    private val edgeAt = IntArray(n * n * 4) { -1 }
    private val neighbourAt = IntArray(n * n * 4) { -1 }
    private val existing: Array<IntArray>

    init {
        for (i in 0 until n * n) {
            val r = i / n
            val c = i % n
            if (r > 0) { edgeAt[i * 4 + UP] = grid.v(r - 1, c); neighbourAt[i * 4 + UP] = i - n }
            if (r < n - 1) { edgeAt[i * 4 + DOWN] = grid.v(r, c); neighbourAt[i * 4 + DOWN] = i + n }
            if (c > 0) { edgeAt[i * 4 + LEFT] = grid.h(r, c - 1); neighbourAt[i * 4 + LEFT] = i - 1 }
            if (c < n - 1) { edgeAt[i * 4 + RIGHT] = grid.h(r, c); neighbourAt[i * 4 + RIGHT] = i + 1 }
        }
        existing = Array(n * n) { i -> (0 until 4).map { edgeAt[i * 4 + it] }.filter { it >= 0 }.toIntArray() }
    }

    /** The edge of cell [i] in direction [d], -1 at the border. */
    fun e(i: Int, d: Int) = edgeAt[i * 4 + d]

    /** The neighbouring cell of [i] in direction [d], -1 at the border. */
    fun nb(i: Int, d: Int) = neighbourAt[i * 4 + d]

    /** The edges that exist around cell [i]. */
    fun edgesOf(i: Int) = existing[i]

    /**
     * Whether the pearl of [kind] on cell [i] is satisfied by the lines where [line] is true: two lines;
     * white = straight and a turn in at least one neighbouring cell; black = a turn and both neighbouring
     * cells on the loop going straight on.
     */
    fun pearlOk(i: Int, kind: Int, line: (Int) -> Boolean): Boolean {
        if (kind == NO_PEARL) return true
        val has = BooleanArray(4) { e(i, it).let { e -> e >= 0 && line(e) } }
        if (has.count { it } != 2) return false
        val straight = (has[UP] && has[DOWN]) || (has[LEFT] && has[RIGHT])
        if (kind == WHITE_PEARL) {
            if (!straight) return false
            val a = if (has[UP]) UP else LEFT
            return turns(nb(i, a), line) || turns(nb(i, a + 1), line)
        }
        if (straight) return false
        for (d in 0 until 4) if (has[d]) {
            val c = nb(i, d)
            val onward = e(c, d)
            if (onward < 0 || !line(onward)) return false
        }
        return true
    }

    /** A cell whose two lines bend. */
    private fun turns(c: Int, line: (Int) -> Boolean): Boolean {
        val up = e(c, UP).let { it >= 0 && line(it) }
        val down = e(c, DOWN).let { it >= 0 && line(it) }
        val left = e(c, LEFT).let { it >= 0 && line(it) }
        val right = e(c, RIGHT).let { it >= 0 && line(it) }
        return (up || down) && (left || right) && listOf(up, down, left, right).count { it } == 2
    }

    /** All pearls satisfied (and so every pearl cell has its two lines). */
    fun pearlsOk(pearls: IntArray, line: (Int) -> Boolean) = pearls.indices.all { pearlOk(it, pearls[it], line) }
}

/**
 * Masyu logic. Edge states: [SHADED] = line, [WHITE] = no line, [UNKNOWN]. Every cell has 0 or 2 lines,
 * a pearl cell exactly 2; it is one closed loop.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: cell degree rules, a black pearl bends (one vertical, one horizontal line) and the line goes on
 *   through the next cell, a white pearl goes straight and not both of its neighbours do;
 * - 1: adds black pearls that can't go a way (next cell is a pearl that bends, or the board ends there),
 *   three white pearls in a row, no closing of a loop before everything is on it, and all pearls must stay
 *   reachable;
 * - 2: as 1, plus what-if on single edges whose trial uses only the level 0 steps;
 * - 3: as 1, plus what-if on single edges whose trial uses the level 0 and 1 steps.
 */
class MasyuLogic(private val mg: MasyuGrid, private val pearls: IntArray, private val level: Int) {
    private val cells = mg.n * mg.n
    private var bad = false

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        val round = IntArray(s.size) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = propagate(s, level >= 1, false)
            if (res == NONE && level >= 2) res = whatIf(s)
            if (res == CONTRA) return ShadeResult(false, s, round)
            for (e in s.indices) if (round[e] < 0 && s[e] != UNKNOWN) round[e] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (res == NONE) return ShadeResult(false, s, round)
        }
    }

    private fun valid(s: IntArray): Boolean {
        val line = BooleanArray(s.size) { s[it] == SHADED }
        return Loop.isSingleLoop(mg.grid, line) && mg.pearlsOk(pearls) { line[it] }
    }

    private fun put(s: IntArray, e: Int, v: Int): Boolean {
        if (e < 0) { if (v == SHADED) bad = true; return false }
        if (s[e] == v) return false
        if (s[e] != UNKNOWN) { bad = true; return false }
        s[e] = v
        return true
    }

    /** The state of the edge of cell [i] in direction [d]; a missing edge counts as no line. */
    private fun at(s: IntArray, i: Int, d: Int) = mg.e(i, d).let { if (it < 0) WHITE else s[it] }

    private fun propagate(s: IntArray, extra: Boolean, toFixpoint: Boolean): Int {
        var any = false
        while (true) {
            bad = false
            var res = basic(s)
            if (res == NONE && extra && !bad) res = level1(s)
            if (bad || res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            val kind = pearls[i]
            val es = mg.edgesOf(i)
            var lines = 0
            var open = 0
            for (e in es) if (s[e] == SHADED) lines++ else if (s[e] == UNKNOWN) open++
            if (lines > 2) return CONTRA
            if (kind != NO_PEARL && lines + open < 2) return CONTRA
            if (kind == NO_PEARL && lines == 1 && open == 0) return CONTRA
            if (open > 0) {
                val v = when {
                    lines == 2 -> WHITE
                    lines == 1 && open == 1 -> SHADED
                    lines == 0 && open == 1 -> WHITE
                    kind != NO_PEARL && lines + open == 2 -> SHADED
                    else -> UNKNOWN
                }
                if (v != UNKNOWN) for (e in es) if (s[e] == UNKNOWN) { s[e] = v; changed = true }
            }
            if (kind == BLACK_PEARL && black(s, i)) changed = true
            if (kind == WHITE_PEARL && white(s, i)) changed = true
            if (bad) return CONTRA
        }
        return if (changed) CHANGED else NONE
    }

    private fun black(s: IntArray, i: Int): Boolean {
        var changed = false
        for (a in intArrayOf(UP, LEFT)) {
            val b = a + 1
            val sa = at(s, i, a)
            val sb = at(s, i, b)
            if (sa == SHADED && sb == SHADED) bad = true
            if (sa == WHITE && sb == WHITE) bad = true
            if (sa == SHADED && put(s, mg.e(i, b), WHITE)) changed = true
            if (sb == SHADED && put(s, mg.e(i, a), WHITE)) changed = true
            if (sa == WHITE && put(s, mg.e(i, b), SHADED)) changed = true
            if (sb == WHITE && put(s, mg.e(i, a), SHADED)) changed = true
        }
        // A line out of a black pearl goes straight on through the next cell.
        for (d in 0 until 4) if (at(s, i, d) == SHADED && put(s, mg.e(mg.nb(i, d), d), SHADED)) changed = true
        return changed
    }

    private fun white(s: IntArray, i: Int): Boolean {
        var changed = false
        val vertical = at(s, i, UP) != WHITE && at(s, i, DOWN) != WHITE
        val horizontal = at(s, i, LEFT) != WHITE && at(s, i, RIGHT) != WHITE
        if (!vertical && !horizontal) bad = true
        if (!vertical) for (d in intArrayOf(LEFT, RIGHT)) if (put(s, mg.e(i, d), SHADED)) changed = true
        if (!horizontal) for (d in intArrayOf(UP, DOWN)) if (put(s, mg.e(i, d), SHADED)) changed = true
        for (a in intArrayOf(UP, LEFT)) {
            if (at(s, i, a) == SHADED && put(s, mg.e(i, a + 1), SHADED)) changed = true
            if (at(s, i, a + 1) == SHADED && put(s, mg.e(i, a), SHADED)) changed = true
            if (at(s, i, a) == SHADED && at(s, i, a + 1) == SHADED) {
                // Both neighbours on the line may not both go straight on.
                val first = mg.nb(i, a)
                val second = mg.nb(i, a + 1)
                if (at(s, first, a) == SHADED && put(s, mg.e(second, a + 1), WHITE)) changed = true
                if (at(s, second, a + 1) == SHADED && put(s, mg.e(first, a), WHITE)) changed = true
            }
        }
        return changed
    }

    /** Level 1. */
    private fun level1(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            when (pearls[i]) {
                BLACK_PEARL -> for (d in 0 until 4) {
                    val e = mg.e(i, d)
                    if (e < 0 || s[e] != UNKNOWN) continue
                    val c = mg.nb(i, d)
                    val onward = mg.e(c, d)
                    if (onward < 0 || s[onward] == WHITE || pearls[c] == BLACK_PEARL) { s[e] = WHITE; changed = true }
                }
                WHITE_PEARL -> for (a in intArrayOf(UP, LEFT)) {
                    val first = mg.nb(i, a)
                    val second = mg.nb(i, a + 1)
                    if (first >= 0 && second >= 0 && pearls[first] == WHITE_PEARL && pearls[second] == WHITE_PEARL) {
                        if (put(s, mg.e(i, a), WHITE)) changed = true
                        if (put(s, mg.e(i, a + 1), WHITE)) changed = true
                    }
                }
            }
        }
        if (bad) return CONTRA
        if (!changed) {
            val closed = Loop.closeLoops(mg.grid, s) { t -> mg.pearlsOk(pearls) { t[it] == SHADED } }
            when (closed) {
                null -> return CONTRA
                true -> changed = true
                false -> {}
            }
        }
        if (!Loop.connectable(mg.grid, s) { pearls[it] != NO_PEARL }) return CONTRA
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (e in s.indices) {
            if (s[e] != UNKNOWN) continue
            val badGuess = BooleanArray(2)
            for (v in intArrayOf(WHITE, SHADED)) {
                val t = s.copyOf()
                t[e] = v
                badGuess[v] = propagate(t, level >= 3, true) == CONTRA
            }
            if (badGuess[WHITE] && badGuess[SHADED]) return CONTRA
            if (badGuess[WHITE]) { s[e] = SHADED; changed = true }
            else if (badGuess[SHADED]) { s[e] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        fun forLevel(d: Difficulty, mg: MasyuGrid, pearls: IntArray) = MasyuLogic(mg, pearls, d.ordinal)
    }
}

/** [pearls]: [NO_PEARL], [WHITE_PEARL] or [BLACK_PEARL] for each of the n×n cells; [solution] marks the loop's edges of `SlitherGrid(n - 1)`. */
class MasyuPuzzle(val difficulty: Difficulty, val n: Int, val pearls: IntArray, val solution: BooleanArray)

/** Hill-climbs a blob loop until the full pearl set solves, then removes pearls (down to [minClues]). */
object MasyuGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** Pearls to keep at least: easier levels stop removing them early. */
    fun minClues(d: Difficulty, n: Int) = when (d) {
        Difficulty.EASY -> (n * n * 0.22).toInt()
        Difficulty.MEDIUM -> (n * n * 0.17).toInt()
        else -> 0
    }

    private class Layout(val loop: BooleanArray, val full: IntArray)

    /** The loop that is the border of [inside] (cells of `SlitherGrid(n - 1)`), with a pearl on every cell that can hold one. */
    private fun layoutOf(mg: MasyuGrid, inside: BooleanArray): Layout {
        val loop = mg.grid.loopOf(inside)
        val full = IntArray(mg.n * mg.n) { i ->
            when {
                mg.edgesOf(i).none { loop[it] } -> NO_PEARL
                mg.pearlOk(i, BLACK_PEARL) { loop[it] } -> BLACK_PEARL
                mg.pearlOk(i, WHITE_PEARL) { loop[it] } -> WHITE_PEARL
                else -> NO_PEARL
            }
        }
        return Layout(loop, full)
    }

    /**
     * A random loop (the border of a thin blob) rarely has one answer for its pearls, so this hill-climbs:
     * flip a cell of the blob, keep the change if the logic at [level] leaves no more edges open than
     * before, until the full set of pearls is solved. Starts again from a new blob if it gets stuck.
     */
    private fun climb(mg: MasyuGrid, level: Int, random: Random): Layout {
        val m = mg.n - 1
        val unknown = IntArray(mg.grid.edges) { UNKNOWN }
        val base = Difficulty.entries[level]
        fun open(l: Layout): Int {
            val r = MasyuLogic.forLevel(base, mg, l.full).run(unknown)
            return if (r.solved) 0 else r.state.count { it == UNKNOWN }.coerceAtLeast(1)
        }
        while (true) {
            var cur = SlitherGenerator.region(m, random, 1.0) ?: continue
            var layout = layoutOf(mg, cur)
            var score = open(layout)
            var tries = 0
            while (score > 0 && tries < 40 * mg.n * mg.n) {
                tries++
                val next = cur.copyOf()
                val i = random.nextInt(m * m)
                next[i] = !next[i]
                if (next.none { it } || !SlitherGenerator.ok(m, next) || !connected(m) { next[it] }) continue
                val l = layoutOf(mg, next)
                val s = open(l)
                if (s <= score) { cur = next; layout = l; score = s }
            }
            if (score == 0) return layout
        }
    }
    private fun removePearls(d: Difficulty, n: Int, mg: MasyuGrid, pearls: IntArray, start: IntArray, random: Random, end: Long) {
        var left = pearls.count { it != NO_PEARL }
        val floor = minClues(d, n)
        for (i in pearls.indices.filter { pearls[it] != NO_PEARL }.shuffled(random)) {
            if (past(end) || left <= floor) break
            val old = pearls[i]
            pearls[i] = NO_PEARL
            if (!MasyuLogic.forLevel(d, mg, pearls).run(start).solved) pearls[i] = old else left--
        }
    }

    private fun needsTop(d: Difficulty, mg: MasyuGrid, pearls: IntArray, start: IntArray) =
        d.ordinal < 2 || !MasyuLogic.forLevel(Difficulty.entries[d.ordinal - 1], mg, pearls).run(start).solved

    /**
     * A climbed loop whose full pearl set the logic solves (Easy: level 0, others: level 1), then pearls
     * removed while the level's logic still solves it. Hard and Expert keep trying for a puzzle that needs
     * the level's top technique (Expert for 1 s, then it takes the best valid one).
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): MasyuPuzzle {
        val end = deadline(budgetMs)
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        val mg = MasyuGrid(n)
        val unknown = IntArray(mg.grid.edges) { UNKNOWN }
        var best: MasyuPuzzle? = null
        while (true) {
            if (best != null && (past(end) || past(preferEnd))) return best
            val l = climb(mg, minOf(d.ordinal, 1), random)
            if (l.full.count { it != NO_PEARL } < minClues(d, n) + 2) continue
            val pearls = l.full.copyOf()
            removePearls(d, n, mg, pearls, unknown, random, end)
            val p = MasyuPuzzle(d, n, pearls, l.loop)
            if (needsTop(d, mg, pearls, unknown)) return p
            best = p
        }
    }
}
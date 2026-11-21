package com.puzzlenook.sudoku

import kotlin.random.Random

/**
 * Step-by-step solver for Fillomino: every cell holds a digit 1..9, each area of touching equal
 * digits has exactly that many cells (so two areas of the same size never touch). Candidates are
 * bitmasks (bit d = digit d). A "group" is a set of touching cells already known to hold the same digit.
 *
 * Levels (0 = Easy … 3 = Expert):
 * - 0: a group that is full closes (its neighbours lose that digit); a cell that would join groups into
 *   one too big loses the digit; a group that is too small and has one open neighbour grows into it;
 * - 1: adds reachability: a digit can only stand in a cell if the cells that may hold it, joined up,
 *   number at least that digit;
 * - 2: as 1, plus a bounded what-if on single cells: the trial runs at most [TRIAL_SWEEPS] sweeps of the
 *   level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): on a cell, and on the open
 *   neighbours of a group that has to grow, settle what all non-contradicting options share.
 *
 * In the app (home section "Regions"): `GameState` holds no units, peers or regions for Fillomino; hints use this
 * logic and fall back to higher levels when the chosen level settles nothing; `SudokuBoard` draws thick lines
 * between touching cells that show different digits (player data only).
 */
class FillominoLogic(private val n: Int, private val level: Int) {
    private val cells = n * n
    private val nb: Array<IntArray> = Array(cells) { Regions.neighbours(n, it).toIntArray() }

    fun solves(board: IntArray) = run(board).solved

    fun run(board: IntArray): LatinLogic.Result {
        val cand = IntArray(cells) { if (board[it] != 0) 1 shl board[it] else ALL }
        val round = IntArray(cells) { if (board[it] != 0) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = propagate(cand, level >= 1, false)
            if (res == NONE && level >= 2) res = if (level >= 3) branches(cand) else whatIf(cand)
            if (res == CONTRA) return LatinLogic.Result(false, cand, round)
            for (i in 0 until cells) if (round[i] < 0 && Integer.bitCount(cand[i]) == 1) round[i] = r
            if (cand.all { Integer.bitCount(it) == 1 }) return LatinLogic.Result(basic(cand.copyOf()) != CONTRA, cand, round)
            if (res == NONE) return LatinLogic.Result(false, cand, round)
        }
    }

    /** Runs [basic] (and [reach]) sweeps until nothing changes, at most [maxSweeps] of them; a contradiction counts only within those. */
    private fun propagate(c: IntArray, reach: Boolean, toFixpoint: Boolean, maxSweeps: Int = Int.MAX_VALUE): Int {
        var any = false
        var sweeps = 0
        while (true) {
            var res = basic(c)
            if (res == NONE && reach) res = reach(c)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint || ++sweeps >= maxSweeps) return CHANGED
        }
    }

    /** Touching cells that are already decided to the same digit. */
    private class Groups(val count: Int, val of: IntArray, val digit: IntArray, val size: IntArray)

    private fun groups(c: IntArray): Groups {
        val of = IntArray(cells) { -1 }
        val digit = IntArray(cells)
        val size = IntArray(cells)
        val stack = IntArray(cells)
        var k = 0
        for (i in 0 until cells) {
            if (of[i] >= 0 || Integer.bitCount(c[i]) != 1) continue
            of[i] = k
            var sp = 0
            stack[sp++] = i
            var cnt = 0
            while (sp > 0) {
                val x = stack[--sp]
                cnt++
                for (y in nb[x]) if (of[y] < 0 && c[y] == c[i]) {
                    of[y] = k
                    stack[sp++] = y
                }
            }
            digit[k] = Integer.numberOfTrailingZeros(c[i])
            size[k] = cnt
            k++
        }
        return Groups(k, of, digit, size)
    }

    private fun basic(c: IntArray): Int {
        var changed = false
        val g = groups(c)
        for (k in 0 until g.count) if (g.size[k] > g.digit[k]) return CONTRA
        // A cell that would join groups of its digit into an area that is too big can't hold it
        // (this also closes full groups: their neighbours lose the digit).
        val total = IntArray(10)
        for (x in 0 until cells) {
            if (g.of[x] >= 0) continue
            java.util.Arrays.fill(total, 0)
            for (a in nb[x].indices) {
                val k = g.of[nb[x][a]]
                if (k < 0) continue
                var dup = false
                for (b in 0 until a) if (g.of[nb[x][b]] == k) dup = true
                if (!dup) total[g.digit[k]] += g.size[k]
            }
            for (d in 1..9) if (total[d] > 0 && 1 + total[d] > d && c[x] and (1 shl d) != 0) {
                c[x] = c[x] and (1 shl d).inv()
                changed = true
            }
        }
        // A group that is too small needs an open neighbour; with just one, it grows into it.
        val open = IntArray(g.count)
        val last = IntArray(g.count) { -1 }
        for (x in 0 until cells) {
            if (g.of[x] >= 0) continue
            for (a in nb[x].indices) {
                val k = g.of[nb[x][a]]
                if (k < 0 || c[x] and (1 shl g.digit[k]) == 0) continue
                var dup = false
                for (b in 0 until a) if (g.of[nb[x][b]] == k) dup = true
                if (dup) continue
                open[k]++
                last[k] = x
            }
        }
        for (k in 0 until g.count) {
            if (g.size[k] >= g.digit[k]) continue
            if (open[k] == 0) return CONTRA
            if (open[k] == 1) {
                val x = last[k]
                val v = c[x] and (1 shl g.digit[k])
                if (v != c[x]) {
                    c[x] = v
                    changed = true
                }
            }
        }
        for (x in 0 until cells) if (c[x] == 0) return CONTRA
        return if (changed) CHANGED else NONE
    }

    /** Level 1: a digit d needs d joined-up cells that may hold d; elsewhere it is ruled out. */
    private fun reach(c: IntArray): Int {
        var changed = false
        val seen = BooleanArray(cells)
        val stack = IntArray(cells)
        val members = IntArray(cells)
        for (d in 2..9) {
            val bit = 1 shl d
            java.util.Arrays.fill(seen, false)
            for (i in 0 until cells) {
                if (seen[i] || c[i] and bit == 0) continue
                var sp = 0
                var m = 0
                stack[sp++] = i
                seen[i] = true
                while (sp > 0) {
                    val x = stack[--sp]
                    members[m++] = x
                    for (y in nb[x]) if (!seen[y] && c[y] and bit != 0) {
                        seen[y] = true
                        stack[sp++] = y
                    }
                }
                if (m < d) {
                    for (t in 0 until m) c[members[t]] = c[members[t]] and bit.inv()
                    changed = true
                }
            }
        }
        for (x in 0 until cells) if (c[x] == 0) return CONTRA
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(c: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            if (Integer.bitCount(c[i]) < 2) continue
            var m = c[i]
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val t = c.copyOf()
                t[i] = bit
                if (propagate(t, false, true, TRIAL_SWEEPS) == CONTRA) {
                    c[i] = c[i] and bit.inv()
                    changed = true
                }
            }
            if (c[i] == 0) return CONTRA
        }
        return if (changed) CHANGED else NONE
    }

    /**
     * Level 3: branch intersection. A group that has to grow takes one of its open neighbours; an
     * undecided cell takes one of its digits. Each option is propagated with the level 1 steps; options that
     * contradict drop out and every candidate that no survivor keeps is removed.
     */
    private fun branches(c: IntArray): Int {
        var changed = false
        val g = groups(c)
        val open = Array(g.count) { ArrayList<Int>() }
        for (x in 0 until cells) {
            if (g.of[x] >= 0) continue
            for (a in nb[x].indices) {
                val k = g.of[nb[x][a]]
                if (k < 0 || c[x] and (1 shl g.digit[k]) == 0) continue
                var dup = false
                for (b in 0 until a) if (g.of[nb[x][b]] == k) dup = true
                if (!dup) open[k] += x
            }
        }
        for (k in 0 until g.count) {
            if (g.size[k] >= g.digit[k] || open[k].size < 2) continue
            val bit = 1 shl g.digit[k]
            val res = intersect(c, open[k].map { intArrayOf(it, bit) })
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        for (i in 0 until cells) {
            if (Integer.bitCount(c[i]) < 2) continue
            val options = ArrayList<IntArray>()
            var m = c[i]
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                options += intArrayOf(i, bit)
            }
            val res = intersect(c, options)
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        return if (changed) CHANGED else NONE
    }

    /** Options are (cell, mask) pairs, at least one of which holds; keeps in [c] only what some surviving option leaves. */
    private fun intersect(c: IntArray, options: List<IntArray>): Int {
        val union = IntArray(cells)
        var alive = 0
        for (o in options) {
            val t = c.copyOf()
            t[o[0]] = t[o[0]] and o[1]
            if (t[o[0]] == 0 || propagate(t, true, true) == CONTRA) continue
            alive++
            for (j in 0 until cells) union[j] = union[j] or t[j]
        }
        if (alive == 0) return CONTRA
        var changed = false
        for (j in 0 until cells) {
            val v = c[j] and union[j]
            if (v != c[j]) {
                c[j] = v
                changed = true
            }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1
        private const val ALL = 0x3FE

        /** Level 2 what-if: a trial runs at most this many sweeps of the level 0 steps (tuned). */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int) = FillominoLogic(n, d.ordinal)
        fun forLevel(level: Int, n: Int) = FillominoLogic(n, level)
    }
}

/**
 * Fillomino puzzles: a random partition into areas, equal touching areas merged, then the givens thinned as far
 * as the level's logic allows.
 */
object FillominoGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 7, 8, 10)[d.ordinal]

    /** Shown digits to keep at least: easier levels stay friendlier. */
    fun minGivens(d: Difficulty, cells: Int) = when (d) {
        Difficulty.EASY -> (cells * 0.30).toInt()
        Difficulty.MEDIUM -> (cells * 0.22).toInt()
        else -> 0
    }

    /** Hard must need level 2, Expert level 3: the puzzle isn't solved by the level below. */
    fun needsTop(d: Difficulty, n: Int, givens: IntArray) =
        d.ordinal < 2 || !FillominoLogic.forLevel(d.ordinal - 1, n).solves(givens)

    private fun deadline(ms: Long) = System.nanoTime() + ms * 1_000_000
    private fun past(deadline: Long) = System.nanoTime() > deadline

    /** Weights of area sizes 1..9 when growing the partition. */
    private val WEIGHTS = intArrayOf(4, 9, 12, 13, 13, 12, 11, 10, 9)

    private fun pickSize(random: Random): Int {
        var x = random.nextInt(WEIGHTS.sum())
        for (k in WEIGHTS.indices) {
            x -= WEIGHTS[k]
            if (x < 0) return k + 1
        }
        return 1
    }

    /** Random connected areas of 1..9 cells; touching areas of the same size are merged (null if that gets too big). */
    private fun layout(n: Int, random: Random): IntArray? {
        val cells = n * n
        val region = IntArray(cells) { -1 }
        val sizes = ArrayList<Int>()
        for (start in (0 until cells).shuffled(random)) {
            if (region[start] >= 0) continue
            val id = sizes.size
            val target = pickSize(random)
            region[start] = id
            val members = arrayListOf(start)
            while (members.size < target) {
                val frontier = members.flatMap { Regions.neighbours(n, it) }.filter { region[it] < 0 }.distinct()
                if (frontier.isEmpty()) break
                val c = frontier.random(random)
                region[c] = id
                members += c
            }
            sizes += members.size
        }
        while (true) {
            var a = -1
            var b = -1
            find@ for (i in 0 until cells) for (j in Regions.neighbours(n, i)) {
                if (region[i] != region[j] && sizes[region[i]] == sizes[region[j]]) {
                    a = region[i]
                    b = region[j]
                    break@find
                }
            }
            if (a < 0) break
            if (sizes[a] * 2 > 9) return null
            for (i in 0 until cells) if (region[i] == b) region[i] = a
            sizes[a] = sizes[a] * 2
            sizes[b] = 0
        }
        return IntArray(cells) { sizes[region[it]] }
    }

    /** Takes shown digits away while [d]'s logic still solves; Hard/Expert thin with the level below first. */
    private fun withGivens(d: Difficulty, n: Int, solution: IntArray, random: Random, end: Long): IntArray {
        val cells = n * n
        val board = solution.copyOf()
        val logic = FillominoLogic.forLevel(d, n)
        val first = if (d.ordinal >= 2) FillominoLogic.forLevel(d.ordinal - 1, n) else logic
        val floor = minGivens(d, cells)
        var left = cells
        for (i in (0 until cells).shuffled(random)) {
            if (past(end) || left <= floor) break
            val v = board[i]
            board[i] = 0
            if (first.solves(board)) left-- else board[i] = v
        }
        if (d.ordinal >= 2) {
            for (i in (0 until cells).filter { board[it] != 0 }.shuffled(random)) {
                if (past(end)) break
                val v = board[i]
                board[i] = 0
                if (!logic.solves(board)) board[i] = v
            }
        }
        return board
    }

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): Puzzle {
        val end = deadline(budgetMs)
        var best: Puzzle? = null
        var bestScore = Int.MAX_VALUE
        do {
            val solution = layout(n, random) ?: continue
            val givens = withGivens(d, n, solution, random, end)
            val p = Puzzle(givens, solution, d, size = n)
            val top = needsTop(d, n, givens)
            if (top) return p
            // Else keep the best so far: one that needs the top level beats one that doesn't, then fewer givens.
            val score = (if (top) 0 else 1000) + p.clueCount
            if (score < bestScore) {
                best = p
                bestScore = score
            }
        } while (!past(end))
        best?.let { return it }
        while (true) {
            val solution = layout(n, random) ?: continue
            return Puzzle(withGivens(d, n, solution, random, deadline(2000)), solution, d, size = n)
        }
    }
}

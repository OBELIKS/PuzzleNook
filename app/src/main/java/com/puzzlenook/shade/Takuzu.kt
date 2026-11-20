package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Takuzu logic. Rules: every cell is black ([SHADED]) or white ([WHITE]); no three equal side by
 * side in a row or column; every row and column holds as many of each colour; no two rows and no
 * two columns are the same.
 *
 * Levels (0 = Easy … 3 = Expert):
 * - 0: pairs ("a a" → both ends are the other colour), gaps ("a _ a" → middle is the other
 *   colour) and counts (a colour used n/2 times → the rest is the other colour);
 * - 1: line completion: every valid completion of a line's open cells is tried; cells that are
 *   the same in all of them are set;
 * - 2: as 1, but completions equal to an already finished parallel line are dropped;
 * - 3: what-if: try a colour in an open cell, apply levels 0–2; a contradiction means the other.
 */
class TakuzuLogic(private val n: Int, private val level: Int) {
    private val half = n / 2
    private val rows = List(n) { r -> IntArray(n) { r * n + it } }
    private val cols = List(n) { c -> IntArray(n) { it * n + c } }
    private val lines = rows + cols

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        val round = IntArray(n * n) { if (start[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = step(s, 0)
            if (res == CONTRA) return ShadeResult(false, s, round)
            if (res == NONE && level >= 1) res = step(s, if (level >= 2) 2 else 1)
            if (res == CONTRA) return ShadeResult(false, s, round)
            if (res == NONE && level >= 3) res = whatIf(s)
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (res == NONE) return ShadeResult(false, s, round)
        }
    }

    private fun step(s: IntArray, tech: Int): Int {
        var changed = false
        for ((k, line) in lines.withIndex()) {
            val res = if (tech == 0) basic(s, line) else complete(s, line, if (k < n) rows else cols, tech >= 2)
            if (res == CONTRA) return CONTRA
            if (res == CHANGED) changed = true
        }
        return if (changed) CHANGED else NONE
    }

    private fun basic(s: IntArray, line: IntArray): Int {
        var changed = false
        fun put(j: Int, v: Int): Boolean {
            val i = line[j]
            if (s[i] == UNKNOWN) {
                s[i] = v
                changed = true
                return true
            }
            return s[i] == v
        }
        for (k in 0 until n - 1) {
            val a = s[line[k]]
            if (a != UNKNOWN && a == s[line[k + 1]]) {
                if (k > 0 && !put(k - 1, 1 - a)) return CONTRA
                if (k + 2 < n && !put(k + 2, 1 - a)) return CONTRA
            }
        }
        for (k in 0 until n - 2) {
            val a = s[line[k]]
            if (a != UNKNOWN && a == s[line[k + 2]] && !put(k + 1, 1 - a)) return CONTRA
        }
        val shaded = line.count { s[it] == SHADED }
        val white = line.count { s[it] == WHITE }
        if (shaded > half || white > half) return CONTRA
        if (shaded == half) {
            for (j in 0 until n) if (s[line[j]] == UNKNOWN) put(j, WHITE)
        } else if (white == half) {
            for (j in 0 until n) if (s[line[j]] == UNKNOWN) put(j, SHADED)
        }
        return if (changed) CHANGED else NONE
    }

    /** Sets the cells that are the same in every valid completion of [line]. */
    private fun complete(s: IntArray, line: IntArray, group: List<IntArray>, dupes: Boolean): Int {
        val canShade = BooleanArray(n)
        val canWhite = BooleanArray(n)
        val cur = IntArray(n)
        var found = 0
        fun rec(p: Int, sh: Int, wh: Int) {
            if (p == n) {
                if (dupes && group.any { other ->
                        other !== line && other.all { s[it] != UNKNOWN } && (0 until n).all { s[other[it]] == cur[it] }
                    }
                ) return
                found++
                for (q in 0 until n) if (cur[q] == SHADED) canShade[q] = true else canWhite[q] = true
                return
            }
            val fixed = s[line[p]]
            for (v in intArrayOf(WHITE, SHADED)) {
                if (fixed != UNKNOWN && fixed != v) continue
                if (p >= 2 && cur[p - 1] == v && cur[p - 2] == v) continue
                val nsh = sh + if (v == SHADED) 1 else 0
                val nwh = wh + if (v == WHITE) 1 else 0
                if (nsh > half || nwh > half) continue
                cur[p] = v
                rec(p + 1, nsh, nwh)
            }
        }
        rec(0, 0, 0)
        if (found == 0) return CONTRA
        var changed = false
        for (j in 0 until n) {
            val i = line[j]
            if (s[i] != UNKNOWN) continue
            if (canShade[j] && !canWhite[j]) { s[i] = SHADED; changed = true }
            else if (canWhite[j] && !canShade[j]) { s[i] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /** Levels 0–2 to a fixpoint on a copy. False on a contradiction. */
    private fun settle(t: IntArray): Boolean {
        while (true) {
            var res = step(t, 0)
            if (res == CONTRA) return false
            if (res == NONE) res = step(t, 2)
            if (res == CONTRA) return false
            if (res == NONE) break
        }
        return t.any { it == UNKNOWN } || valid(t)
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in s.indices) {
            if (s[i] != UNKNOWN) continue
            for (guess in intArrayOf(SHADED, WHITE)) {
                val t = s.copyOf()
                t[i] = guess
                if (!settle(t)) {
                    s[i] = 1 - guess
                    changed = true
                    break
                }
            }
        }
        return if (changed) CHANGED else NONE
    }

    fun valid(s: IntArray): Boolean {
        if (s.any { it == UNKNOWN }) return false
        for (line in lines) {
            if (line.count { s[it] == SHADED } != half) return false
            for (k in 0 until n - 2) if (s[line[k]] == s[line[k + 1]] && s[line[k]] == s[line[k + 2]]) return false
        }
        if (rows.map { r -> r.map { s[it] } }.toSet().size != n) return false
        return cols.map { c -> c.map { s[it] } }.toSet().size == n
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        fun forLevel(d: Difficulty, n: Int) = TakuzuLogic(n, d.ordinal)
    }
}

object TakuzuGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** A random valid full grid, then givens are removed while the level's logic still solves it. */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000): ShadePuzzle {
        val n = size(d)
        val end = deadline(budgetMs)
        val solution = BooleanArray(n * n)
        while (true) {
            val grid = fullGrid(n, random)
            if (grid != null) {
                for (i in grid.indices) solution[i] = grid[i] == SHADED
                break
            }
        }
        val givens = BooleanArray(n * n) { true }
        val logic = TakuzuLogic.forLevel(d, n)
        for (i in (0 until n * n).shuffled(random)) {
            if (past(end)) break
            givens[i] = false
            val start = IntArray(n * n) { if (givens[it]) (if (solution[it]) SHADED else WHITE) else UNKNOWN }
            if (!logic.run(start).solved) givens[i] = true
        }
        return ShadePuzzle(Variant.TAKUZU, d, n, solution, givens)
    }

    /** Randomised backtracking with a node limit; null when the limit is hit (caller retries). */
    private fun fullGrid(n: Int, random: Random): IntArray? {
        val half = n / 2
        val g = IntArray(n * n) { UNKNOWN }
        val rowSh = IntArray(n)
        val colSh = IntArray(n)
        var nodes = 0
        val limit = 20_000

        fun rec(i: Int): Boolean {
            if (i == n * n) return true
            if (++nodes > limit) return false
            val r = i / n
            val c = i % n
            val order = if (random.nextBoolean()) intArrayOf(SHADED, WHITE) else intArrayOf(WHITE, SHADED)
            for (v in order) {
                if (c >= 2 && g[i - 1] == v && g[i - 2] == v) continue
                if (r >= 2 && g[i - n] == v && g[i - 2 * n] == v) continue
                val sh = if (v == SHADED) 1 else 0
                val rs = rowSh[r] + sh
                val cs = colSh[c] + sh
                // cells placed so far in this row / column, including this one
                if (rs > half || (c + 1) - rs > half) continue
                if (cs > half || (r + 1) - cs > half) continue
                g[i] = v
                rowSh[r] += sh
                colSh[c] += sh
                var ok = true
                if (c == n - 1) {
                    for (pr in 0 until r) if ((0 until n).all { g[pr * n + it] == g[r * n + it] }) { ok = false; break }
                }
                if (ok && r == n - 1) {
                    // column c is finished now: compare with the earlier finished columns
                    for (pc in 0 until c) if ((0 until n).all { g[it * n + pc] == g[it * n + c] }) { ok = false; break }
                }
                if (ok && rec(i + 1)) return true
                g[i] = UNKNOWN
                rowSh[r] -= sh
                colSh[c] -= sh
                if (nodes > limit) return false
            }
            return false
        }
        return if (rec(0)) g else null
    }
}

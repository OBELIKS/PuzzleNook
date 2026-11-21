package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Kurodoko logic. `numbers[i] > 0` is a clue: a white cell that sees that many white cells in its
 * row and column, itself included, up to the next shaded cell or the edge (0 = no clue). Shaded
 * cells ([SHADED]) never touch orthogonally and all white cells ([WHITE]) form one area.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: a shaded cell frees its four neighbours; each clue bounds how far it can see in every
 *   direction (the known-white run is the least, the open run the most, the other directions use
 *   up part of the number), which fixes the nearest cells white and, once a view is settled, the
 *   cell just beyond it shaded;
 * - 1: adds connectivity: a cell whose shading would cut the white cells apart is white, a cell the
 *   white area can't reach is shaded;
 * - 2: as 1, plus what-if on single cells whose trial uses only the level 0 steps;
 * - 3: as 1, plus what-if on single cells whose trial uses the level 0 and 1 steps.
 *
 * Marks: shaded = FILLED. A clue is a blocked white cell; `ShadeState.kurodokoMet` fades it when it is met.
 */
class KurodokoLogic(private val n: Int, private val numbers: IntArray, private val level: Int) {
    private val cells = n * n
    private val clues = (0 until cells).filter { numbers[it] > 0 }
    private val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
    private val orthNb = Array(cells) { orth(it, n) }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        for (c in clues) s[c] = WHITE
        val round = IntArray(cells) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = propagate(s, level >= 1, false)
            if (res == NONE && level >= 2) res = whatIf(s)
            if (res == CONTRA) return ShadeResult(false, s, round)
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (res == NONE) return ShadeResult(false, s, round)
        }
    }

    private fun valid(s: IntArray): Boolean {
        for (i in 0 until cells) if (s[i] == SHADED) for (j in orthNb[i]) if (s[j] == SHADED) return false
        for (c in clues) {
            if (s[c] == SHADED) return false
            var seen = 1
            for (d in dirs) {
                var r = c / n + d[0]
                var cc = c % n + d[1]
                while (r in 0 until n && cc in 0 until n && s[r * n + cc] != SHADED) { seen++; r += d[0]; cc += d[1] }
            }
            if (seen != numbers[c]) return false
        }
        return connected(n) { s[it] != SHADED }
    }

    private fun propagate(s: IntArray, connectivity: Boolean, toFixpoint: Boolean): Int {
        var any = false
        while (true) {
            var res = basic(s)
            if (res == NONE && connectivity) res = connect(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            if (s[i] != SHADED) continue
            for (j in orthNb[i]) {
                if (s[j] == SHADED) return CONTRA
                if (s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
            }
        }
        val minRun = IntArray(4)
        val maxRun = IntArray(4)
        for (c in clues) {
            val k = numbers[c]
            for ((di, d) in dirs.withIndex()) {
                var r = c / n + d[0]
                var cc = c % n + d[1]
                var mn = 0
                var mx = 0
                var whiteSoFar = true
                while (r in 0 until n && cc in 0 until n && s[r * n + cc] != SHADED) {
                    mx++
                    if (whiteSoFar && s[r * n + cc] == WHITE) mn++ else whiteSoFar = false
                    r += d[0]
                    cc += d[1]
                }
                minRun[di] = mn
                maxRun[di] = mx
            }
            val sumMin = minRun.sum()
            val sumMax = maxRun.sum()
            if (1 + sumMin > k || 1 + sumMax < k) return CONTRA
            for ((di, d) in dirs.withIndex()) {
                val lo = maxOf(minRun[di], k - 1 - (sumMax - maxRun[di]))
                val hi = minOf(maxRun[di], k - 1 - (sumMin - minRun[di]))
                if (lo > hi) return CONTRA
                for (t in 1..lo) {
                    val j = (c / n + d[0] * t) * n + c % n + d[1] * t
                    if (s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
                }
                // The view in this direction is settled: the cell just beyond it is shaded (or the edge).
                if (lo == hi && lo < maxRun[di]) {
                    val j = (c / n + d[0] * (lo + 1)) * n + c % n + d[1] * (lo + 1)
                    if (s[j] == WHITE) return CONTRA
                    if (s[j] == UNKNOWN) { s[j] = SHADED; changed = true }
                }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: the white cells must stay in one piece. */
    private fun connect(s: IntArray): Int {
        val first = (0 until cells).firstOrNull { s[it] == WHITE } ?: return NONE
        val reach = reachFrom(s, first, -1)
        for (i in 0 until cells) if (s[i] == WHITE && !reach[i]) return CONTRA
        var changed = false
        for (i in 0 until cells) if (s[i] == UNKNOWN && !reach[i]) { s[i] = SHADED; changed = true }
        if (changed) return CHANGED
        for (x in 0 until cells) {
            if (s[x] != UNKNOWN) continue
            val r = reachFrom(s, first, x)
            if ((0 until cells).any { s[it] == WHITE && !r[it] }) { s[x] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    /** Cells reachable from [from] without crossing a shaded cell or [skip]. */
    private fun reachFrom(s: IntArray, from: Int, skip: Int): BooleanArray {
        val seen = BooleanArray(cells)
        val stack = IntArray(cells)
        var top = 0
        stack[top++] = from
        seen[from] = true
        while (top > 0) {
            val i = stack[--top]
            for (j in orthNb[i]) if (!seen[j] && j != skip && s[j] != SHADED) { seen[j] = true; stack[top++] = j }
        }
        return seen
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN) continue
            val bad = BooleanArray(2)
            for (v in intArrayOf(WHITE, SHADED)) {
                val t = s.copyOf()
                t[i] = v
                bad[v] = propagate(t, level >= 3, true) == CONTRA
            }
            if (bad[WHITE] && bad[SHADED]) return CONTRA
            if (bad[WHITE]) { s[i] = SHADED; changed = true }
            else if (bad[SHADED]) { s[i] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        fun forLevel(d: Difficulty, n: Int, numbers: IntArray) = KurodokoLogic(n, numbers, d.ordinal)

        /**
         * White cells seen from [c] (itself included) when the cells where [shaded] is true are
         * shaded. Used with the player's own marks only.
         */
        fun visible(n: Int, c: Int, shaded: (Int) -> Boolean): Int {
            var seen = 1
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                var r = c / n + dr
                var cc = c % n + dc
                while (r in 0 until n && cc in 0 until n && !shaded(r * n + cc)) { seen++; r += dr; cc += dc }
            }
            return seen
        }
    }
}

object KurodokoGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 7, 9, 11)[d.ordinal]

    /** Clues to keep at least (of the white cells): easier levels stop removing clues early. */
    fun minClues(d: Difficulty, whites: Int) = when (d) {
        Difficulty.EASY -> (whites * 0.45).toInt()
        Difficulty.MEDIUM -> (whites * 0.35).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val shaded: BooleanArray, val numbers: IntArray)

    /** Random non-touching shaded cells that keep the white cells in one piece; every white cell numbered. */
    private fun layout(n: Int, random: Random): Layout {
        val shaded = BooleanArray(n * n)
        val target = (n * n * (0.18 + random.nextDouble() * 0.1)).toInt()
        var count = 0
        for (i in (0 until n * n).shuffled(random)) {
            if (count >= target) break
            if (orth(i, n).any { shaded[it] }) continue
            shaded[i] = true
            if (connected(n) { !shaded[it] }) count++ else shaded[i] = false
        }
        val numbers = IntArray(n * n) { i -> if (shaded[i]) 0 else KurodokoLogic.visible(n, i) { shaded[it] } }
        return Layout(n, shaded, numbers)
    }

    private fun removeClues(d: Difficulty, n: Int, numbers: IntArray, start: IntArray, random: Random, end: Long) {
        val clueCells = numbers.indices.filter { numbers[it] > 0 }
        var left = clueCells.size
        val floor = minClues(d, clueCells.size)
        for (i in clueCells.shuffled(random)) {
            if (past(end) || left <= floor) break
            val old = numbers[i]
            numbers[i] = 0
            if (!KurodokoLogic.forLevel(d, n, numbers).run(start).solved) numbers[i] = old else left--
        }
    }

    private fun needsTop(d: Difficulty, n: Int, numbers: IntArray, start: IntArray) =
        d.ordinal < 2 || !KurodokoLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, numbers).run(start).solved

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        val quitEnd = if (d == Difficulty.EASY || d == Difficulty.MEDIUM) deadline(minOf(budgetMs, 600)) else end
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        val unknown = IntArray(n * n) { UNKNOWN }
        var best: ShadePuzzle? = null
        var fallback: Layout? = null
        do {
            val l = layout(n, random)
            if (fallback == null) fallback = l
            if (!KurodokoLogic.forLevel(d, n, l.numbers).run(unknown).solved) continue
            removeClues(d, n, l.numbers, unknown, random, end)
            val p = ShadePuzzle(Variant.KURODOKO, d, n, l.shaded, BooleanArray(n * n), numbers = l.numbers)
            if (needsTop(d, n, l.numbers, unknown)) return p
            best = p
        } while (!past(end) && !(best != null && past(preferEnd)) && !past(quitEnd))
        best?.let { return it }
        // Nothing solved on its own: give cells away until the all-clues puzzle solves, then thin the clues.
        val l = fallback ?: layout(n, random)
        val givens = BooleanArray(n * n)
        val logic = KurodokoLogic.forLevel(d, n, l.numbers)
        var start: IntArray
        while (true) {
            start = IntArray(n * n) { if (givens[it]) (if (l.shaded[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] && l.numbers[it] == 0 } }
            givens[open.filter { l.shaded[it] }.randomOrNull(random) ?: open.randomOrNull(random) ?: break] = true
        }
        removeClues(d, n, l.numbers, start, random, deadline(budgetMs))
        return ShadePuzzle(Variant.KURODOKO, d, n, l.shaded, givens, numbers = l.numbers)
    }
}

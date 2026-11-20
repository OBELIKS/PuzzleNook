package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Mosaic logic. A number (0..9, `-1` = no clue) counts the [SHADED] cells in its 3×3 block, itself
 * included (fewer cells at the edges).
 *
 * Levels (0 = Easy … 3 = Expert):
 * - 0: one clue at a time: enough shaded already → the rest of the block is white; shaded plus
 *   open cells equal the clue → all open cells are shaded;
 * - 1: two overlapping clues compared: how many shaded cells the shared open cells can hold
 *   limits the cells only one of the clues sees;
 * - 2: as 1, plus what-if on single cells whose trial uses only the level 0 step;
 * - 3: as 1, plus what-if on single cells whose trial uses the level 0 and 1 steps
 *   (a contradiction means the other colour).
 */
class MosaicLogic(private val n: Int, private val numbers: IntArray, private val level: Int) {
    private val clueCells = (0 until n * n).filter { numbers[it] >= 0 }
    private val blocks: Map<Int, IntArray> = clueCells.associateWith { i -> king(i, n) + i }
    private val pairs: List<Pair<Int, Int>> = clueCells.flatMap { a ->
        clueCells.filter { b ->
            b > a && kotlin.math.abs(a / n - b / n) <= 2 && kotlin.math.abs(a % n - b % n) <= 2
        }.map { a to it }
    }
    private val inBlock = BooleanArray(n * n).also { f -> blocks.values.forEach { b -> b.forEach { f[it] = true } } }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        val round = IntArray(n * n) { if (start[it] != UNKNOWN) 0 else -1 }
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

    private fun valid(s: IntArray) = clueCells.all { c -> blocks.getValue(c).count { s[it] == SHADED } == numbers[c] }

    /** Applies the levels until nothing changes; one pass of the top level counts as one round. */
    private fun propagate(s: IntArray, pairsToo: Boolean, toFixpoint: Boolean): Int {
        var any = false
        while (true) {
            var res = single(s)
            if (res == NONE && pairsToo) res = pair(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint) return CHANGED
        }
    }

    private fun single(s: IntArray): Int {
        var changed = false
        for (c in clueCells) {
            var sh = 0
            var open = 0
            for (j in blocks.getValue(c)) {
                if (s[j] == SHADED) sh++ else if (s[j] == UNKNOWN) open++
            }
            val clue = numbers[c]
            if (sh > clue || sh + open < clue) return CONTRA
            if (open == 0) continue
            val v = if (sh == clue) WHITE else if (sh + open == clue) SHADED else continue
            for (j in blocks.getValue(c)) if (s[j] == UNKNOWN) s[j] = v
            changed = true
        }
        return if (changed) CHANGED else NONE
    }

    private fun pair(s: IntArray): Int {
        var changed = false
        for ((a, b) in pairs) {
            val ba = blocks.getValue(a)
            val bb = blocks.getValue(b)
            val ua = ba.filter { s[it] == UNKNOWN }
            val ub = bb.filter { s[it] == UNKNOWN }
            if (ua.isEmpty() && ub.isEmpty()) continue
            val both = ua.filter { it in ub }
            if (both.isEmpty()) continue
            val oa = ua.filter { it !in both }
            val ob = ub.filter { it !in both }
            val needA = numbers[a] - ba.count { s[it] == SHADED }
            val needB = numbers[b] - bb.count { s[it] == SHADED }
            val lo = maxOf(0, needA - oa.size, needB - ob.size)
            val hi = minOf(both.size, needA, needB)
            if (lo > hi) return CONTRA
            fun apply(only: List<Int>, need: Int) {
                if (only.isEmpty()) return
                val v = when {
                    need - hi == only.size -> SHADED
                    need - lo == 0 -> WHITE
                    else -> return
                }
                for (j in only) s[j] = v
                changed = true
            }
            apply(ob, needB)
            apply(oa, needA)
        }
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in s.indices) {
            if (s[i] != UNKNOWN || !inBlock[i]) continue
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

        fun forLevel(d: Difficulty, n: Int, numbers: IntArray) = MosaicLogic(n, numbers, d.ordinal)
    }
}

object MosaicGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** 50 % random picture, then smoothing passes (Easy and Medium 1, Hard/Expert 0) for blobs. */
    private fun picture(n: Int, d: Difficulty, random: Random): BooleanArray {
        var g = BooleanArray(n * n) { random.nextBoolean() }
        repeat(intArrayOf(1, 1, 0, 0)[d.ordinal]) {
            val prev = g
            g = BooleanArray(n * n) { i ->
                val block = king(i, n) + i
                val shaded = block.count { prev[it] }
                val white = block.size - shaded
                if (shaded > white) true else if (white > shaded) false else prev[i]
            }
        }
        return g
    }

    /** Clues to keep at least: easier levels stop removing early so the board isn't bare. */
    fun minClues(d: Difficulty) = intArrayOf(18, 26, 0, 0)[d.ordinal]

    private fun removeClues(d: Difficulty, n: Int, clues: IntArray, start: IntArray, random: Random, end: Long) {
        var left = clues.count { it >= 0 }
        for (i in (0 until n * n).shuffled(random)) {
            if (past(end) || left <= minClues(d)) break
            if (clues[i] < 0) continue
            val old = clues[i]
            clues[i] = -1
            if (!MosaicLogic.forLevel(d, n, clues).run(start).solved) clues[i] = old else left--
        }
    }

    /**
     * A picture whose every-clue puzzle the level's logic solves (new attempts until the budget is
     * past), then clues are removed while it still solves. Fallback: give cells away like Hitori.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000): ShadePuzzle {
        val n = size(d)
        val end = deadline(budgetMs)
        val unknown = IntArray(n * n) { UNKNOWN }
        var solution: BooleanArray
        var clues: IntArray
        do {
            solution = picture(n, d, random)
            clues = IntArray(n * n) { i -> (king(i, n) + i).count { solution[it] } }
            if (MosaicLogic.forLevel(d, n, clues).run(unknown).solved) {
                removeClues(d, n, clues, unknown, random, end)
                return ShadePuzzle(Variant.MOSAIC, d, n, solution, BooleanArray(n * n), numbers = clues)
            }
        } while (!past(end))
        // Nothing was solvable in time: givens first (all clues), then remove clues with them in place.
        val givens = BooleanArray(n * n)
        val logic = MosaicLogic.forLevel(d, n, clues)
        var start = unknown
        while (true) {
            start = IntArray(n * n) { if (givens[it]) (if (solution[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] } }
            givens[open.filter { solution[it] }.randomOrNull(random) ?: open.randomOrNull(random) ?: break] = true
        }
        removeClues(d, n, clues, start, random, deadline(budgetMs))
        return ShadePuzzle(Variant.MOSAIC, d, n, solution, givens, numbers = clues)
    }
}
package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.LatinGenerator
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/**
 * Hitori logic. Rules: shade cells so no number repeats among the white cells of a row or
 * column; shaded cells never touch side by side; white cells stay one connected group; only
 * numbers that repeat in their row or column are ever shaded.
 *
 * Techniques, as a person would use them:
 * - a number that doesn't repeat stays white;
 * - a shaded cell's neighbours are white; a white cell's twins in its row and column are shaded;
 * - two equal neighbours in a line ("a a"): every other copy in that line is shaded;
 *   "a b a": the middle stays white;
 * - a cell whose shading would cut the white cells apart stays white;
 * - short "what if" checks: if shading (or keeping white) a cell leads straight to two
 *   shaded cells touching, a repeated white number or cut-off white cells, it's the other way.
 */
class HitoriLogic(private val n: Int, private val numbers: IntArray) {
    private val lines = List(n) { r -> IntArray(n) { r * n + it } } + List(n) { c -> IntArray(n) { it * n + c } }
    private val twins = Array(n * n) { i ->
        val r = i / n
        val c = i % n
        ((0 until n).map { r * n + it } + (0 until n).map { it * n + c })
            .filter { it != i && numbers[it] == numbers[i] }.distinct().toIntArray()
    }
    private val nbrs = Array(n * n) { orth(it, n) }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        for (i in s.indices) if (twins[i].isEmpty()) s[i] = WHITE
        val round = IntArray(n * n) { if (start[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            val before = s.copyOf()
            if (!propagate(s)) return ShadeResult(false, s, round)
            if (s.contentEquals(before)) {
                patterns(s)
                cutPoints(s)
            }
            if (s.contentEquals(before)) whatIf(s)
            if (!propagate(s)) return ShadeResult(false, s, round)
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (s.contentEquals(before)) return ShadeResult(false, s, round)
        }
    }

    /** Shaded → white neighbours, white → shaded twins, until nothing changes. False on a contradiction. */
    private fun propagate(s: IntArray): Boolean {
        var changed = true
        while (changed) {
            changed = false
            for (i in s.indices) {
                when (s[i]) {
                    SHADED -> for (j in nbrs[i]) when (s[j]) {
                        SHADED -> return false
                        UNKNOWN -> { s[j] = WHITE; changed = true }
                    }
                    WHITE -> for (j in twins[i]) when (s[j]) {
                        WHITE -> return false
                        UNKNOWN -> { s[j] = SHADED; changed = true }
                    }
                }
            }
        }
        return connected(n) { s[it] != SHADED }
    }

    private fun patterns(s: IntArray) {
        for (line in lines) {
            for (k in 0 until n - 1) {
                val a = line[k]
                val b = line[k + 1]
                if (numbers[a] == numbers[b]) {
                    for (j in line) if (j != a && j != b && numbers[j] == numbers[a] && s[j] == UNKNOWN) s[j] = SHADED
                }
                if (k + 2 < n && numbers[a] == numbers[line[k + 2]] && s[b] == UNKNOWN) s[b] = WHITE
            }
        }
    }

    private fun cutPoints(s: IntArray) {
        for (i in s.indices) {
            if (s[i] != UNKNOWN) continue
            if (!connected(n) { it != i && s[it] != SHADED }) s[i] = WHITE
        }
    }

    private fun whatIf(s: IntArray) {
        for (i in s.indices) {
            if (s[i] != UNKNOWN) continue
            for (guess in intArrayOf(SHADED, WHITE)) {
                val t = s.copyOf()
                t[i] = guess
                if (!propagate(t)) {
                    s[i] = if (guess == SHADED) WHITE else SHADED
                    break
                }
            }
        }
    }

    private fun valid(s: IntArray): Boolean = propagate(s.copyOf()) &&
        lines.all { line -> line.filter { s[it] == WHITE }.map { numbers[it] }.let { it.size == it.toSet().size } }
}

object HitoriGenerator {
    fun size(d: Difficulty) = intArrayOf(5, 6, 8, 9)[d.ordinal]

    /**
     * A random Latin square, a random pattern of non-touching shaded cells that keeps the white
     * cells connected, then each shaded cell copies a number from a white cell in its row or
     * column. Numbers of shaded cells are re-picked until the logic solves it.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000): ShadePuzzle {
        val n = size(d)
        val end = deadline(budgetMs)
        var best: Triple<BooleanArray, IntArray, Int>? = null
        while (best == null || !past(end)) {
            val numbers = LatinGenerator.randomSquare(n, random)
            val shaded = BooleanArray(n * n)
            for (i in (0 until n * n).shuffled(random)) {
                if (shaded.count { it } >= n * n * 3 / 10) break
                if (orth(i, n).any { shaded[it] }) continue
                shaded[i] = true
                if (!connected(n) { !shaded[it] }) shaded[i] = false
            }
            fun copyFor(i: Int): Int {
                val r = i / n
                val c = i % n
                val whites = ((0 until n).map { r * n + it } + (0 until n).map { it * n + c })
                    .filter { it != i && !shaded[it] }.map { numbers[it] }.distinct()
                return whites.random(random)
            }
            for (i in shaded.indices) if (shaded[i]) numbers[i] = copyFor(i)
            var stuck = HitoriLogic(n, numbers).run(IntArray(n * n) { UNKNOWN }).unresolved.size
            var tries = 0
            while (stuck > 0 && tries++ < 200 && !past(end)) {
                val i = shaded.indices.filter { shaded[it] }.random(random)
                val old = numbers[i]
                numbers[i] = copyFor(i)
                val result = HitoriLogic(n, numbers).run(IntArray(n * n) { UNKNOWN })
                val now = result.unresolved.size
                // The logic must never reach a different picture than ours.
                val agrees = result.state.indices.all { result.state[it] == UNKNOWN || (result.state[it] == SHADED) == shaded[it] }
                if (agrees && now <= stuck) stuck = now else numbers[i] = old
            }
            if (best == null || stuck < best.third) best = Triple(shaded.copyOf(), numbers.copyOf(), stuck)
            if (stuck == 0) break
        }
        val (solution, numbers, _) = best!!
        val givens = BooleanArray(n * n)
        val logic = HitoriLogic(n, numbers)
        while (true) {
            val start = IntArray(n * n) { if (givens[it]) (if (solution[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            // If the logic stopped with nothing left open, fall back to any cell not yet given.
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] } }
            givens[open.filter { solution[it] }.randomOrNull(random) ?: open.randomOrNull(random) ?: break] = true
        }
        return ShadePuzzle(Variant.HITORI, d, n, solution, givens, numbers = numbers)
    }
}

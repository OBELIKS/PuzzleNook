package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import org.junit.Test
import kotlin.random.Random

/**
 * Counts Star Battle solutions with a row-by-row search that shares no code with the logic,
 * including very short time budgets like a slow device gets.
 */
class StarBattleUniqueTest {
    private fun count(n: Int, k: Int, region: IntArray, givens: BooleanArray, solution: BooleanArray, limit: Int = 2): Int {
        val colCount = IntArray(n)
        val regCount = IntArray(n)
        val star = BooleanArray(n * n)
        var found = 0
        val rowChoices: List<IntArray> = buildList {
            if (k == 1) for (a in 0 until n) add(intArrayOf(a))
            else for (a in 0 until n) for (b in a + 2 until n) add(intArrayOf(a, b))
        }
        fun rec(r: Int) {
            if (found >= limit) return
            if (r == n) {
                if (colCount.all { it == k } && regCount.all { it == k }) found++
                return
            }
            for (cols in rowChoices) {
                var ok = true
                for (c in cols) {
                    val i = r * n + c
                    if (colCount[c] >= k || regCount[region[i]] >= k) ok = false
                    if (r > 0) for (dc in -1..1) { val cc = c + dc; if (cc in 0 until n && star[(r - 1) * n + cc]) ok = false }
                }
                // Givens must match.
                for (c in 0 until n) { val i = r * n + c; if (givens[i] && solution[i] != (c in cols)) ok = false }
                if (!ok) continue
                for (c in cols) { val i = r * n + c; star[i] = true; colCount[c]++; regCount[region[i]]++ }
                rec(r + 1)
                for (c in cols) { val i = r * n + c; star[i] = false; colCount[c]--; regCount[region[i]]-- }
            }
        }
        rec(0)
        return found
    }

    @Test(timeout = 900_000)
    fun sweep() {
        for (d in Difficulty.entries) for (budget in listOf(4000L, 40L)) {
            var multi = 0
            var total = 0
            val seeds = if (d.ordinal < 2) 25 else 6
            for (seed in 0 until seeds) {
                val p = StarBattleGenerator.generate(d, Random(seed * 13 + 1), budgetMs = budget)
                total++
                val c = count(p.size, p.stars, p.regions!!, p.givens, p.solution)
                if (c != 1) {
                    multi++
                    if (multi <= 2) {
                        val logic = StarBattleLogic(p.size, p.stars, p.regions).run(
                            IntArray(p.size * p.size) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN },
                        )
                        println("MULTI $d budget $budget seed $seed: $c solutions, logic solved=${logic.solved}, givens=${p.givens.count { it }}")
                        println(p.regions.toList().chunked(p.size).joinToString("\n") { it.joinToString(" ") })
                    }
                }
            }
            println("SWEEP $d budget $budget: $multi of $total not unique")
            org.junit.Assert.assertEquals("$d budget $budget puzzles with more than one solution", 0, multi)
        }
    }
}

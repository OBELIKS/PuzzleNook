package com.puzzlenook.sudoku

/**
 * Solves a [LatinRules] puzzle the way a person with pencil notes would, without guessing:
 * each round removes candidates that break a single rule (a row or column already holding the
 * digit, an inequality, a Kropki dot, a cage that can't reach its target, a skyscraper line that can't show its
 * count) and places hidden singles. A puzzle this finishes has exactly one solution.
 */
object LatinLogic {
    class Result(
        val solved: Boolean,
        /** Candidates per cell (bits 1..n) when the logic stopped. */
        val candidates: IntArray,
        /** Round in which each cell was pinned down (0 = given or filled), or -1. */
        val round: IntArray,
    ) {
        val unresolved: List<Int> get() = candidates.indices.filter { Integer.bitCount(candidates[it]) != 1 }
    }

    fun solves(rules: LatinRules, givens: IntArray) = run(rules, givens).solved

    fun run(rules: LatinRules, givens: IntArray): Result {
        val n = rules.n
        val all = (1 shl (n + 1)) - 2
        val cand = IntArray(n * n) { if (givens[it] != 0) 1 shl givens[it] else all }
        val round = IntArray(n * n) { if (givens[it] != 0) 0 else -1 }
        val clues = rules.sky?.withIndex()?.filter { it.value > 0 }.orEmpty()
        var r = 0
        while (true) {
            r++
            val before = cand.copyOf()
            // Placed digits leave their row and column.
            for (i in cand.indices) {
                if (Integer.bitCount(cand[i]) != 1) continue
                val row = i / n
                val col = i % n
                for (k in 0 until n) {
                    val a = row * n + k
                    val b = k * n + col
                    if (a != i) cand[a] = cand[a] and cand[i].inv()
                    if (b != i) cand[b] = cand[b] and cand[i].inv()
                }
            }
            // A digit with only one place left in a row or column goes there.
            for (unit in rules.units) {
                for (d in 1..n) {
                    val bit = 1 shl d
                    var only = -1
                    var count = 0
                    for (i in unit) if (cand[i] and bit != 0) {
                        count++
                        only = i
                    }
                    if (count == 1) cand[only] = bit
                }
            }
            for (l in rules.less) {
                val lo = l[0]
                val hi = l[1]
                if (cand[lo] == 0 || cand[hi] == 0) break
                val maxHi = 31 - Integer.numberOfLeadingZeros(cand[hi])
                val minLo = Integer.numberOfTrailingZeros(cand[lo])
                cand[lo] = cand[lo] and ((1 shl maxHi) - 1)
                cand[hi] = cand[hi] and ((1 shl (minLo + 1)) - 1).inv()
            }
            // Kropki: each neighbour pair only keeps digits that fit its dot (or no dot) with the other cell.
            for (p in rules.dotPairs) {
                val a = cand[p[0]]
                val b = cand[p[1]]
                cand[p[0]] = a and rules.dotMask(p[2], b)
                cand[p[1]] = b and rules.dotMask(p[2], a)
            }
            for (cage in rules.cages) cageSupport(cage, cand, n)
            for ((k, clue) in clues) lineSupport(rules.lines[k], clue, cand, n)

            if (cand.any { it == 0 }) return Result(false, cand, round)
            for (i in cand.indices) if (round[i] < 0 && Integer.bitCount(cand[i]) == 1) round[i] = r
            if (cand.all { Integer.bitCount(it) == 1 }) return Result(true, cand, round)
            if (cand.contentEquals(before)) return Result(false, cand, round)
        }
    }

    /** Keeps only the digits that appear in some way of filling [cage] from the candidates. */
    private fun cageSupport(cage: MathCage, cand: IntArray, n: Int) {
        val cells = cage.cells
        val k = cells.size
        val values = IntArray(k)
        val support = IntArray(k)
        fun clash(pos: Int, v: Int): Boolean {
            for (j in 0 until pos) {
                if (values[j] != v) continue
                val a = cells[j]
                val b = cells[pos]
                if (a / n == b / n || a % n == b % n) return true
            }
            return false
        }
        fun rec(pos: Int, acc: Int) {
            if (pos == k) {
                val ok = when (cage.op) {
                    '=', '+', '×' -> acc == cage.target
                    '-' -> kotlin.math.abs(values[0] - values[1]) == cage.target
                    '÷' -> maxOf(values[0], values[1]) == cage.target * minOf(values[0], values[1])
                    else -> false
                }
                if (ok) for (j in 0 until k) support[j] = support[j] or (1 shl values[j])
                return
            }
            var m = cand[cells[pos]]
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val v = Integer.numberOfTrailingZeros(bit)
                if (clash(pos, v)) continue
                val next = when (cage.op) {
                    '+', '=' -> acc + v
                    '×' -> acc * v
                    else -> acc
                }
                if (cage.op == '+' && next > cage.target) continue
                if (cage.op == '×' && cage.target % next != 0) continue
                values[pos] = v
                rec(pos + 1, next)
            }
        }
        rec(0, if (cage.op == '×') 1 else 0)
        for (j in 0 until k) cand[cells[j]] = cand[cells[j]] and support[j]
    }

    /** Keeps only the digits that appear in some ordering of [line] that shows [clue] buildings. */
    private fun lineSupport(line: IntArray, clue: Int, cand: IntArray, n: Int) {
        val support = IntArray(n)
        val values = IntArray(n)
        fun rec(pos: Int, used: Int, max: Int, seen: Int) {
            if (seen > clue || seen + (n - pos) < clue) return
            if (pos == n) {
                if (seen == clue) for (j in 0 until n) support[j] = support[j] or (1 shl values[j])
                return
            }
            var m = cand[line[pos]] and used.inv()
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val v = Integer.numberOfTrailingZeros(bit)
                values[pos] = v
                rec(pos + 1, used or bit, maxOf(max, v), if (v > max) seen + 1 else seen)
            }
        }
        rec(0, 0, 0, 0)
        for (j in 0 until n) cand[line[j]] = cand[line[j]] and support[j]
    }
}

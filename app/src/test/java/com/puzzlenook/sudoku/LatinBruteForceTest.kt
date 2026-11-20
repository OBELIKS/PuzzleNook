package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Checks generated puzzles with a deliberately naive solver that shares no code with
 * [LatinSolver]: fill the most constrained cell first, check each rule once all its cells are filled.
 */
class LatinBruteForceTest {
    private class Naive(val p: Puzzle) {
        val n = p.size
        val rules = p.rules!!
        // Each rule is checked as soon as all its cells are filled (index: rules per cell).
        val checksAt = Array(n * n) { mutableListOf<(IntArray) -> Boolean>() }

        init {
            for (l in rules.less) {
                val check = { b: IntArray -> b[l[0]] == 0 || b[l[1]] == 0 || b[l[0]] < b[l[1]] }
                checksAt[l[0]] += check
                checksAt[l[1]] += check
            }
            if (rules.kropkiAll) {
                val white = rules.white.map { setOf(it[0], it[1]) }.toSet()
                val black = rules.black.map { setOf(it[0], it[1]) }.toSet()
                for (i in 0 until n * n) for (j in listOfNotNull((i + 1).takeIf { i % n < n - 1 }, (i + n).takeIf { it < n * n })) {
                    val pair = setOf(i, j)
                    val check = { b: IntArray ->
                        if (b[i] == 0 || b[j] == 0) true else {
                            val consecutive = Math.abs(b[i] - b[j]) == 1
                            val double = b[i] == 2 * b[j] || b[j] == 2 * b[i]
                            when {
                                pair in white -> consecutive
                                pair in black -> double && !consecutive
                                else -> !consecutive && !double
                            }
                        }
                    }
                    checksAt[i] += check
                    checksAt[j] += check
                }
            }
            for (c in rules.cages) {
                val check = { b: IntArray ->
                    val v = c.cells.map { b[it] }
                    if (v.any { it == 0 }) true else when (c.op) {
                        '=' -> v[0] == c.target
                        '+' -> v.sum() == c.target
                        '×' -> v.fold(1) { a, x -> a * x } == c.target
                        '-' -> kotlin.math.abs(v[0] - v[1]) == c.target
                        '÷' -> maxOf(v[0], v[1]) == c.target * minOf(v[0], v[1])
                        else -> error("op ${c.op}")
                    }
                }
                for (cell in c.cells) checksAt[cell] += check
            }
        }

        val board = IntArray(n * n)
        val rowUsed = IntArray(n)
        val colUsed = IntArray(n)
        val found = mutableListOf<IntArray>()

        // Longest `<` chain ending at / starting at each cell (rules form a DAG).
        private val below = Array(n * n) { mutableListOf<Int>() } // cells that must be smaller
        private val above = Array(n * n) { mutableListOf<Int>() } // cells that must be bigger
        private val low = IntArray(n * n)
        private val high = IntArray(n * n)

        init {
            for (l in rules.less) { below[l[1]] += l[0]; above[l[0]] += l[1] }
            val up = IntArray(n * n) { -1 }
            val down = IntArray(n * n) { -1 }
            fun chainUp(i: Int): Int { if (up[i] < 0) up[i] = 1 + (below[i].maxOfOrNull { chainUp(it) } ?: 0); return up[i] }
            fun chainDown(i: Int): Int { if (down[i] < 0) down[i] = 1 + (above[i].maxOfOrNull { chainDown(it) } ?: 0); return down[i] }
            for (i in 0 until n * n) { low[i] = chainUp(i); high[i] = n + 1 - chainDown(i) }
        }

        private fun allowed(i: Int): Int {
            var m = if (p.givens[i] != 0) 1 shl p.givens[i] else ((1 shl (n + 1)) - 2)
            m = m and (rowUsed[i / n] or colUsed[i % n]).inv()
            // Digits low[i]..high[i] only.
            m = m and ((1 shl (high[i] + 1)) - (1 shl low[i]))
            for (j in below[i]) if (board[j] != 0) m = m and (-1 shl (board[j] + 1)) // must be > board[j]
            for (j in above[i]) if (board[j] != 0) m = m and ((1 shl board[j]) - 1) // must be < board[j]
            return m
        }

        fun solve() {
            if (found.size >= 2) return
            // Next cell: the empty one with the fewest allowed digits.
            var best = -1
            var bestMask = 0
            var bestCount = Int.MAX_VALUE
            for (i in 0 until n * n) {
                if (board[i] != 0) continue
                val m = allowed(i)
                val k = Integer.bitCount(m)
                if (k < bestCount) { best = i; bestMask = m; bestCount = k; if (k == 0) return }
            }
            if (best < 0) {
                found += board.copyOf()
                return
            }
            val r = best / n
            val c = best % n
            for (v in 1..n) {
                if (bestMask and (1 shl v) == 0) continue
                board[best] = v
                rowUsed[r] = rowUsed[r] or (1 shl v)
                colUsed[c] = colUsed[c] or (1 shl v)
                if (checksAt[best].all { it(board) }) solve()
                board[best] = 0
                rowUsed[r] = rowUsed[r] and (1 shl v).inv()
                colUsed[c] = colUsed[c] and (1 shl v).inv()
                if (found.size >= 2) return
            }
        }
    }
    private fun check(variant: Variant, difficulties: List<Difficulty>, seeds: Int) {
        for (d in difficulties) for (seed in 0 until seeds) {
            val p = LatinGenerator.generate(variant, d, Random(seed * 7 + 1000 * d.ordinal))
            val naive = Naive(p).also { it.solve() }
            if (naive.found.size != 1) {
                val other = naive.found.firstOrNull { !it.contentEquals(p.solution) }
                println("$variant $d seed $seed: ${naive.found.size} solutions")
                println(" stored: ${p.solution.toList().chunked(p.size)}")
                println(" other:  ${other?.toList()?.chunked(p.size)}")
                println(" fast solver says ${LatinSolver(p.rules!!).countSolutions(p.givens, 2, 5_000_000)}")
            }
            assertEquals("$variant $d seed $seed", 1, naive.found.size)
        }
    }

    @Test
    fun logicSolvable() {
        for (v in listOf(Variant.FUTOSHIKI, Variant.CALCUDOKU, Variant.SKYSCRAPERS, Variant.KROPKI)) for (d in Difficulty.entries) {
            val stuck = (0 until 10).map { seed ->
                val p = LatinGenerator.generate(v, d, kotlin.random.Random(seed + 500))
                LatinLogic.run(p.rules!!, p.givens).unresolved.size
            }
            println("LOGIC $v $d unresolved cells per puzzle: $stuck")
            assertEquals(List(10) { 0 }, stuck)
        }
    }

    /** The naive solver must be able to find solutions: an empty 4x4 grid has many, it stops at 2. */
    @Test
    fun naiveSolverFindsSolutions() {
        val empty = Puzzle(IntArray(16), IntArray(16), Difficulty.EASY, size = 4, rules = LatinRules(4))
        assertEquals(2, Naive(empty).also { it.solve() }.found.size)
    }

    @Test fun futoshikiSmall() = check(Variant.FUTOSHIKI, listOf(Difficulty.EASY, Difficulty.MEDIUM), 15)
    @Test fun calcudokuSmall() = check(Variant.CALCUDOKU, listOf(Difficulty.EASY, Difficulty.MEDIUM), 15)
    @Test(timeout = 300_000) fun kropkiSmall() = check(Variant.KROPKI, listOf(Difficulty.EASY, Difficulty.MEDIUM), 15)
    @Test fun futoshikiBig()= check(Variant.FUTOSHIKI, listOf(Difficulty.HARD), 2)
    @Test fun calcudokuBig() = check(Variant.CALCUDOKU, listOf(Difficulty.HARD), 4)
}

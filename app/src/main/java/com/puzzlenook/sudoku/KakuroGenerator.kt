package com.puzzlenook.sudoku

import kotlin.random.Random

/**
 * Kakuro: a grid of white cells and clue squares. Each horizontal or vertical run of white
 * cells holds different digits 1–9 adding up to the clue on its left or above.
 *
 * Generation: a symmetric layout of clue squares with runs of 2..maxRun cells, a random
 * digit fill with no repeats in any run, then the run sums. If the sums alone can't be solved
 * without guessing, a few digits are given (fewest possible, as for killer sudoku).
 */
object KakuroGenerator {

    /** Grid size including the clue row and column. */
    fun size(d: Difficulty) = when (d) {
        Difficulty.EASY -> 7
        Difficulty.MEDIUM -> 9
        Difficulty.HARD -> 11
        Difficulty.EXPERT -> 13
    }

    fun maxRun(d: Difficulty) = when (d) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 6
        Difficulty.EXPERT -> 7
    }

    /** Tries layouts until one needs no givens or [budgetMs] runs out; keeps the leanest. */
    fun generate(difficulty: Difficulty, random: Random = Random.Default, budgetMs: Long = 2_500): Puzzle {
        val deadline = System.currentTimeMillis() + budgetMs
        var best: Puzzle? = null
        while (best == null || System.currentTimeMillis() < deadline) {
            val p = attempt(difficulty, random, deadline) ?: continue
            if (best == null || p.clueCount < best.clueCount) best = p
            if (p.clueCount == 0) break
        }
        return best!!
    }

    private fun attempt(difficulty: Difficulty, random: Random, deadline: Long): Puzzle? {
        val n = size(difficulty)
        val blocked = layout(n, maxRun(difficulty), random) ?: return null
        val runs = runs(n, blocked)
        val solution = fill(n, blocked, runs, random) ?: return null
        val active = (0 until n * n).filter { !blocked[it] }.toIntArray()
        val runsOf = Array(n * n) { mutableListOf<List<Int>>() }
        runs.forEach { run -> run.forEach { runsOf[it] += run } }

        var board: IntArray? = null
        var best: Pair<IntArray, IntArray>? = null
        for (round in 0 until REPAIR_ROUNDS) {
            if (best != null && (best.second.none { it != 0 } || System.currentTimeMillis() > deadline)) break
            val logic = DigitLogic.kakuro(n, blocked, cages(runs, solution))
            val minimal = minimize(logic, active, solution, board, random)
            board = minimal
            val givens = active.filter { minimal[it] != 0 }
            if (best == null || givens.size < best.second.count { it != 0 }) best = solution.copyOf() to minimal.copyOf()
            if (givens.isEmpty()) break
            // Why is this given needed? Without it the logic gets stuck somewhere; change a
            // digit there so the sums around it say more.
            val probe = minimal.copyOf().also { it[givens.random(random)] = 0 }
            val stuck = logic.run(probe).unresolved.filter { !blocked[it] }
            if (stuck.isEmpty()) continue
            val i = stuck.random(random)
            solution[i] = betterDigit(i, solution, runsOf[i], random) ?: continue
            if (minimal[i] != 0) minimal[i] = solution[i]
        }
        val (finalSolution, finalBoard) = best!!
        return Puzzle(finalBoard, finalSolution, difficulty, cages(runs, finalSolution), n, blocked)
    }

    private fun cages(runs: List<List<Int>>, solution: IntArray) = runs.map { run -> Cage(run.sumOf { solution[it] }, run) }

    /** Fewest givens that keep it solvable without guessing, starting from [start] when it still works. */
    private fun minimize(logic: DigitLogic, active: IntArray, solution: IntArray, start: IntArray?, random: Random): IntArray {
        val board = start?.takeIf { logic.solves(it) }?.copyOf() ?: solution.copyOf()
        for (i in active.toList().shuffled(random)) {
            val v = board[i]
            if (v == 0) continue
            board[i] = 0
            if (!logic.solves(board)) board[i] = v
        }
        return board
    }

    /**
     * A new digit for cell [i] that keeps its runs repeat-free and makes their sums as
     * telling as possible (few ways to make them, like 3 in two cells = 1 + 2).
     */
    private fun betterDigit(i: Int, solution: IntArray, runs: List<List<Int>>, random: Random): Int? {
        val taken = runs.fold(0) { m, run -> run.fold(m) { mm, j -> if (j != i) mm or (1 shl solution[j]) else mm } }
        val options = (1..9).filter { it != solution[i] && taken and (1 shl it) == 0 }
        if (options.isEmpty()) return null
        fun ways(d: Int) = runs.sumOf { run -> comboCount(run.size, run.sumOf { if (it == i) d else solution[it] }) }
        val fewest = options.minOf(::ways)
        return options.filter { ways(it) <= fewest + 1 }.random(random)
    }

    private val combos = Array(10) { IntArray(46) }.also { t ->
        for (sub in 0 until 512) {
            var sum = 0
            for (b in 0 until 9) if ((sub shr b) and 1 == 1) sum += b + 1
            t[Integer.bitCount(sub)][sum]++
        }
    }

    /** Ways to write [sum] as [k] different digits. */
    fun comboCount(k: Int, sum: Int) = if (k in 0..9 && sum in 0..45) combos[k][sum] else 0

    private const val REPAIR_ROUNDS = 400
    /** Clue squares: the first row and column, plus a symmetric scattering inside. */
    fun layout(n: Int, maxRun: Int, random: Random): BooleanArray? {
        val black = BooleanArray(n * n) { it / n == 0 || it % n == 0 }
        // The inside (1..n-1 in both directions) is symmetric under a half turn.
        fun mirror(i: Int) = (n - i / n) * n + (n - i % n)
        fun setBlack(i: Int) {
            black[i] = true
            black[mirror(i)] = true
        }
        val inner = (0 until n * n).filter { !black[it] }
        for (i in inner) if (random.nextFloat() < 0.16f) setBlack(i)

        fun runLength(i: Int, step: Int): Int {
            var start = i
            while (!black[start - step]) start -= step
            var len = 0
            var j = start
            while (j < n * n && !black[j] && (step == n || j / n == start / n)) {
                len++
                j += step
            }
            return len
        }

        repeat(60) {
            var changed = false
            for (i in inner) {
                if (black[i]) continue
                val across = runLength(i, 1)
                val down = runLength(i, n)
                when {
                    // Every white cell needs a run of at least two cells both ways.
                    across < 2 || down < 2 -> { setBlack(i); changed = true }
                    across > maxRun && random.nextInt(across) == 0 -> { setBlack(i); changed = true }
                    down > maxRun && random.nextInt(down) == 0 -> { setBlack(i); changed = true }
                }
            }
            if (!changed && inner.none { !black[it] && (runLength(it, 1) > maxRun || runLength(it, n) > maxRun) }) {
                // Use the whole grid: every row and column needs some white cells.
                val spread = (1 until n).all { k ->
                    (1 until n).count { !black[k * n + it] } >= 2 && (1 until n).count { !black[it * n + k] } >= 2
                }
                return black.takeIf { spread && connected(n, it) && it.count { b -> !b } >= (n - 1) * (n - 1) / 2 }
            }
        }
        return null
    }

    private fun connected(n: Int, black: BooleanArray): Boolean {
        val whites = (0 until n * n).filter { !black[it] }
        if (whites.isEmpty()) return false
        val seen = BooleanArray(n * n)
        val stack = ArrayDeque(listOf(whites.first()))
        seen[whites.first()] = true
        var count = 0
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            count++
            for (j in listOf(i - 1, i + 1, i - n, i + n)) {
                if (j !in 0 until n * n || seen[j] || black[j]) continue
                if ((j == i - 1 || j == i + 1) && j / n != i / n) continue
                seen[j] = true
                stack.addLast(j)
            }
        }
        return count == whites.size
    }

    /** Every horizontal then vertical run of white cells, in reading order. */
    fun runs(n: Int, black: BooleanArray): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        for (r in 0 until n) {
            var c = 0
            while (c < n) {
                if (black[r * n + c]) { c++; continue }
                val run = mutableListOf<Int>()
                while (c < n && !black[r * n + c]) run += r * n + c++
                if (run.size >= 2) result += run
            }
        }
        for (c in 0 until n) {
            var r = 0
            while (r < n) {
                if (black[r * n + c]) { r++; continue }
                val run = mutableListOf<Int>()
                while (r < n && !black[r * n + c]) { run += r * n + c; r++ }
                if (run.size >= 2) result += run
            }
        }
        return result
    }

    /** Random digits with no repeats in any run (plain backtracking). */
    private fun fill(n: Int, black: BooleanArray, runs: List<List<Int>>, random: Random): IntArray? {
        val runsOf = Array(n * n) { mutableListOf<Int>() }
        runs.forEachIndexed { k, run -> run.forEach { runsOf[it] += k } }
        val used = IntArray(runs.size)
        val grid = IntArray(n * n)
        val order = (0 until n * n).filter { !black[it] }
        var steps = 0
        fun place(k: Int): Boolean {
            if (k == order.size) return true
            if (++steps > 200_000) return false
            val i = order[k]
            for (v in (1..9).shuffled(random)) {
                val bit = 1 shl v
                if (runsOf[i].any { used[it] and bit != 0 }) continue
                runsOf[i].forEach { used[it] = used[it] or bit }
                grid[i] = v
                if (place(k + 1)) return true
                runsOf[i].forEach { used[it] = used[it] and bit.inv() }
                grid[i] = 0
            }
            return false
        }
        return if (place(0)) grid else null
    }
}

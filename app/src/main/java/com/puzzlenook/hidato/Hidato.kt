package com.puzzlenook.hidato

import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/**
 * Hidato on an n×n grid: the numbers 1..n² form a path where each number touches the next,
 * side by side or diagonally.
 */
class HidatoPuzzle(val difficulty: Difficulty, val n: Int, val solution: IntArray, val givens: IntArray)

internal fun kingNeighbours(n: Int): Array<IntArray> = Array(n * n) { i ->
    val r = i / n
    val c = i % n
    buildList {
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr
            val cc = c + dc
            if (rr in 0 until n && cc in 0 until n) add(rr * n + cc)
        }
    }.toIntArray()
}

/**
 * Hidato logic as a person would use it, with no guessing:
 * - a number can only go where it can be reached in time from the nearest placed number
 *   below it and still reach the nearest one above (counting steps through empty cells);
 * - a number must touch some possible place of the number before and of the number after;
 * - a number with one possible cell goes there; a cell only one number can use gets it.
 */
class HidatoLogic(private val n: Int) {
    private val size = n * n
    private val nbrs = kingNeighbours(n)

    class Result(val solved: Boolean, val board: IntArray, val round: IntArray)

    fun run(start: IntArray): Result {
        val board = start.copyOf()
        val round = IntArray(size) { if (board[it] != 0) 0 else -1 }
        var r = 0
        while (true) {
            r++
            val before = board.copyOf()
            if (!step(board)) return Result(false, board, round)
            for (i in 0 until size) if (round[i] < 0 && board[i] != 0) round[i] = r
            if (board.none { it == 0 }) return Result(valid(board), board, round)
            if (board.contentEquals(before)) return Result(false, board, round)
        }
    }

    /** Steps from [from] to every cell, moving only through empty cells. */
    private fun distances(board: IntArray, from: Int): IntArray {
        val dist = IntArray(size) { Int.MAX_VALUE }
        dist[from] = 0
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            for (j in nbrs[i]) if (dist[j] == Int.MAX_VALUE && board[j] == 0) {
                dist[j] = dist[i] + 1
                queue.addLast(j)
            }
        }
        return dist
    }

    /** Possible cells of every number (a placed number's own cell only). Null on a contradiction. */
    fun candidates(board: IntArray): Array<BooleanArray>? {
        val pos = IntArray(size + 1) { -1 }
        for (i in 0 until size) if (board[i] != 0) {
            if (pos[board[i]] >= 0) return null
            pos[board[i]] = i
        }
        val placed = (1..size).filter { pos[it] >= 0 }
        val dist = HashMap<Int, IntArray>()
        for (k in placed) dist[k] = distances(board, pos[k])
        val cand = Array(size + 1) { BooleanArray(size) }
        for (k in 1..size) {
            if (pos[k] >= 0) { cand[k][pos[k]] = true; continue }
            val lo = placed.lastOrNull { it < k }
            val hi = placed.firstOrNull { it > k }
            for (c in 0 until size) {
                if (board[c] != 0) continue
                if (lo != null && dist.getValue(lo)[c] > k - lo) continue
                if (hi != null && dist.getValue(hi)[c] > hi - k) continue
                // Without a placed number below, k still needs room for 1..k-1 behind it (and above, likewise);
                // that's left to the neighbour rule.
                cand[k][c] = true
            }
        }
        // Each number must touch a possible cell of the numbers next to it.
        var changed = true
        while (changed) {
            changed = false
            for (k in 1..size) {
                for (c in 0 until size) {
                    if (!cand[k][c]) continue
                    val prevOk = k == 1 || nbrs[c].any { cand[k - 1][it] }
                    val nextOk = k == size || nbrs[c].any { cand[k + 1][it] }
                    if (!prevOk || !nextOk) {
                        cand[k][c] = false
                        changed = true
                    }
                }
                if (cand[k].none { it }) return null
            }
        }
        return cand
    }

    /** One round of placements. False on a contradiction. */
    private fun step(board: IntArray): Boolean {
        val cand = candidates(board) ?: return false
        var placedSomething = false
        for (k in 1..size) {
            val cells = (0 until size).filter { cand[k][it] }
            if (cells.size == 1 && board[cells[0]] == 0) {
                board[cells[0]] = k
                placedSomething = true
            }
        }
        if (placedSomething) return board.groupBy { it }.all { (v, l) -> v == 0 || l.size == 1 }
        // Every cell gets a number: a cell only one number can use takes it.
        for (c in 0 until size) {
            if (board[c] != 0) continue
            val ks = (1..size).filter { cand[it][c] }
            if (ks.isEmpty()) return false
            if (ks.size == 1) board[c] = ks[0]
        }
        return board.groupBy { it }.all { (v, l) -> v == 0 || l.size == 1 }
    }

    private fun valid(board: IntArray): Boolean {
        val pos = IntArray(size + 1) { -1 }
        for (i in 0 until size) {
            if (board[i] !in 1..size || pos[board[i]] >= 0) return false
            pos[board[i]] = i
        }
        return (1 until size).all { k -> pos[k + 1] in nbrs[pos[k]] }
    }
}

object HidatoGenerator {
    fun size(d: Difficulty) = intArrayOf(5, 6, 7, 8)[d.ordinal]

    /** Share of the removable numbers put back on easier levels. */
    private fun extra(d: Difficulty) = doubleArrayOf(0.3, 0.15, 0.05, 0.0)[d.ordinal]

    /**
     * A random path through every cell, then numbers are removed (always keeping 1 and the last)
     * while the logic can still place them all.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 3000, n: Int = size(d)): HidatoPuzzle {
        val end = System.nanoTime() + budgetMs * 1_000_000
        val solution = randomPath(n, random)
        val givens = solution.copyOf()
        val logic = HidatoLogic(n)
        val removed = mutableListOf<Int>()
        for (i in (0 until n * n).shuffled(random)) {
            if (System.nanoTime() > end) break
            if (solution[i] == 1 || solution[i] == n * n) continue
            givens[i] = 0
            if (logic.run(givens).solved) removed += i else givens[i] = solution[i]
        }
        for (i in removed.shuffled(random).take((removed.size * extra(d)).toInt())) givens[i] = solution[i]
        return HidatoPuzzle(d, n, solution, givens)
    }

    /** Numbers along a random path that visits every cell once (king moves). */
    fun randomPath(n: Int, random: Random): IntArray {
        val nbrs = kingNeighbours(n)
        while (true) {
            val board = IntArray(n * n)
            var cur = random.nextInt(n * n)
            board[cur] = 1
            var k = 1
            while (k < n * n) {
                val options = nbrs[cur].filter { board[it] == 0 }
                if (options.isEmpty()) break
                // Warnsdorff: go where there are fewest ways on, so no corner gets stranded.
                val next = options.minBy { o -> nbrs[o].count { board[it] == 0 } * 4 + random.nextInt(4) }
                board[next] = ++k
                cur = next
            }
            if (k == n * n) return board
        }
    }
}

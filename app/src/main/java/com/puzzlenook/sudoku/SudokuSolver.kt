package com.puzzlenook.sudoku

import kotlin.random.Random

/**
 * Fast backtracking sudoku solver using per-row/column/box bitmasks and the
 * "most constrained cell first" heuristic. Boards are IntArray(81), 0 = empty.
 */
object SudokuSolver {
    private const val ALL = 0x3FE // bits 1..9

    fun box(index: Int): Int = (index / 27) * 3 + (index % 9) / 3

    /** Counts solutions, stopping early once [limit] is reached. Invalid boards have 0. */
    fun countSolutions(board: IntArray, limit: Int = 2): Int {
        val search = Search(board)
        return if (search.valid) search.count(limit) else 0
    }

    fun solve(board: IntArray): IntArray? {
        val search = Search(board)
        return if (search.valid && search.findFirst(null)) search.cells else null
    }

    fun randomSolved(random: Random): IntArray {
        val search = Search(IntArray(81))
        check(search.findFirst(random))
        return search.cells
    }

    private class Search(board: IntArray) {
        val cells = IntArray(81)
        private val rows = IntArray(9)
        private val cols = IntArray(9)
        private val boxes = IntArray(9)
        var valid = true
            private set
        private var found = 0

        init {
            for (i in 0 until 81) {
                val v = board[i]
                if (v != 0) {
                    if (used(i) and (1 shl v) != 0) valid = false else set(i, v)
                }
            }
        }

        private fun used(i: Int) = rows[i / 9] or cols[i % 9] or boxes[box(i)]

        private fun set(i: Int, v: Int) {
            val bit = 1 shl v
            rows[i / 9] = rows[i / 9] or bit
            cols[i % 9] = cols[i % 9] or bit
            boxes[box(i)] = boxes[box(i)] or bit
            cells[i] = v
        }

        private fun clear(i: Int, v: Int) {
            val mask = (1 shl v).inv()
            rows[i / 9] = rows[i / 9] and mask
            cols[i % 9] = cols[i % 9] and mask
            boxes[box(i)] = boxes[box(i)] and mask
            cells[i] = 0
        }

        /** Most constrained empty cell; -1 when the board is full, -2 on a dead end. */
        private fun pick(): Int {
            var best = -1
            var bestCount = 10
            for (i in 0 until 81) {
                if (cells[i] != 0) continue
                val n = Integer.bitCount(ALL and used(i).inv())
                if (n == 0) return -2
                if (n < bestCount) {
                    best = i
                    bestCount = n
                    if (n == 1) break
                }
            }
            return best
        }

        fun count(limit: Int): Int {
            found = 0
            countRec(limit)
            return found
        }

        private fun countRec(limit: Int): Boolean {
            val i = pick()
            if (i == -2) return false
            if (i == -1) {
                found++
                return found >= limit
            }
            var mask = ALL and used(i).inv()
            while (mask != 0) {
                val bit = mask and -mask
                mask = mask xor bit
                val v = Integer.numberOfTrailingZeros(bit)
                set(i, v)
                val stop = countRec(limit)
                clear(i, v)
                if (stop) return true
            }
            return false
        }

        fun findFirst(random: Random?): Boolean {
            val i = pick()
            if (i == -2) return false
            if (i == -1) return true
            val free = ALL and used(i).inv()
            val candidates = (1..9).filter { (free shr it) and 1 == 1 }
                .let { if (random != null) it.shuffled(random) else it }
            for (v in candidates) {
                set(i, v)
                if (findFirst(random)) return true
                clear(i, v)
            }
            return false
        }
    }
}

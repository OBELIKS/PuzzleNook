package com.puzzlenook.sudoku

/** A sum cage (killer cage or kakuro run): its [cells] hold different digits that add up to [sum]. */
data class Cage(val sum: Int, val cells: List<Int>)

/**
 * Backtracking solver for digit grids built from two kinds of constraint:
 * units (cells that must all differ: sudoku rows, columns, boxes) and sum cages
 * (cells that must differ and add up to a total). Only [active] cells take digits.
 * A cell's candidates are cut down to digits that appear in some way of finishing each of its cages.
 */
class GridSolver(
    private val size: Int,
    private val active: IntArray,
    units: List<IntArray>,
    private val cages: List<Cage>,
) {
    private val unitsOf: Array<IntArray>
    private val cagesOf: Array<IntArray>

    init {
        val u = Array(size) { mutableListOf<Int>() }
        units.forEachIndexed { k, cells -> cells.forEach { u[it] += k } }
        unitsOf = Array(size) { u[it].toIntArray() }
        val c = Array(size) { mutableListOf<Int>() }
        cages.forEachIndexed { k, cage -> cage.cells.forEach { c[it] += k } }
        cagesOf = Array(size) { c[it].toIntArray() }
    }

    private val unitCount = units.size

    /** Counts solutions up to [limit]; [GAVE_UP] when the search needed more than [nodeLimit] steps. */
    fun countSolutions(board: IntArray, limit: Int = 2, nodeLimit: Int = 150_000): Int {
        val search = Search(board, nodeLimit)
        return if (search.valid) search.count(limit) else 0
    }

    /** Up to two solutions; null when the search gave up. */
    fun twoSolutions(board: IntArray, nodeLimit: Int = 150_000): List<IntArray>? {
        val search = Search(board, nodeLimit)
        if (!search.valid) return emptyList()
        return if (search.count(2) == GAVE_UP) null else search.solutions
    }

    fun solve(board: IntArray): IntArray? {
        val search = Search(board, Int.MAX_VALUE)
        return if (search.valid && search.count(1) == 1) search.firstSolution else null
    }

    private inner class Search(board: IntArray, private val nodeLimit: Int) {
        private val cells = IntArray(size)
        private val unitUsed = IntArray(unitCount)
        private val cageUsed = IntArray(cages.size)
        private val cageMissing = IntArray(cages.size) { cages[it].sum }
        private val cageEmpty = IntArray(cages.size) { cages[it].cells.size }
        var valid = true
            private set
        val solutions = mutableListOf<IntArray>()
        val firstSolution: IntArray? get() = solutions.firstOrNull()
        private var found = 0
        private var nodes = 0

        init {
            for (i in active) {
                val v = board[i]
                if (v != 0) {
                    if (candidates(i) and (1 shl v) == 0) valid = false else set(i, v)
                }
            }
        }

        private fun candidates(i: Int): Int {
            var mask = ALL
            for (u in unitsOf[i]) mask = mask and unitUsed[u].inv()
            for (c in cagesOf[i]) {
                mask = mask and comboMask(ALL and cageUsed[c].inv(), cageEmpty[c], cageMissing[c])
            }
            return mask
        }

        private fun set(i: Int, v: Int) {
            val bit = 1 shl v
            for (u in unitsOf[i]) unitUsed[u] = unitUsed[u] or bit
            for (c in cagesOf[i]) {
                cageUsed[c] = cageUsed[c] or bit
                cageMissing[c] -= v
                cageEmpty[c]--
            }
            cells[i] = v
        }

        private fun clear(i: Int, v: Int) {
            val mask = (1 shl v).inv()
            for (u in unitsOf[i]) unitUsed[u] = unitUsed[u] and mask
            for (c in cagesOf[i]) {
                cageUsed[c] = cageUsed[c] and mask
                cageMissing[c] += v
                cageEmpty[c]++
            }
            cells[i] = 0
        }

        fun count(limit: Int): Int {
            found = 0
            nodes = 0
            return if (countRec(limit) == Stop.GAVE_UP) GAVE_UP else found
        }

        private fun countRec(limit: Int): Stop {
            if (++nodes > nodeLimit) return Stop.GAVE_UP
            // Most constrained empty cell.
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in active) {
                if (cells[i] != 0) continue
                val m = candidates(i)
                val n = Integer.bitCount(m)
                if (n == 0) return Stop.NO
                if (n < bestCount) {
                    best = i
                    bestMask = m
                    bestCount = n
                    if (n == 1) break
                }
            }
            if (best == -1) {
                found++
                solutions += cells.copyOf()
                return if (found >= limit) Stop.LIMIT else Stop.NO
            }
            var mask = bestMask
            while (mask != 0) {
                val bit = mask and -mask
                mask = mask xor bit
                val v = Integer.numberOfTrailingZeros(bit)
                set(best, v)
                val stop = countRec(limit)
                clear(best, v)
                if (stop != Stop.NO) return stop
            }
            return Stop.NO
        }
    }

    private enum class Stop { NO, LIMIT, GAVE_UP }

    companion object {
        private const val ALL = 0x3FE // bits 1..9

        /** Returned by countSolutions when the search gave up before reaching an answer. */
        const val GAVE_UP = -1

        // combo[avail * 460 + k * 46 + s]: every digit used by some k distinct digits from the
        // set `avail` (bit b = digit b + 1) that add up to s.
        private val combo = IntArray(512 * 460).also { table ->
            for (avail in 0 until 512) {
                var sub = avail
                while (true) {
                    var sum = 0
                    for (b in 0 until 9) if ((sub shr b) and 1 == 1) sum += b + 1
                    val at = avail * 460 + Integer.bitCount(sub) * 46 + sum
                    table[at] = table[at] or (sub shl 1)
                    if (sub == 0) break
                    sub = (sub - 1) and avail
                }
            }
        }

        /** Digits (bits 1..9) that can appear when [k] distinct digits from [avail] add up to [sum]. */
        fun comboMask(avail: Int, k: Int, sum: Int): Int =
            if (k !in 0..9 || sum !in 0..45) 0 else combo[((avail and ALL) shr 1) * 460 + k * 46 + sum]

        /** The 27 rows, columns and boxes of a 9×9 sudoku. */
        val SUDOKU_HOUSES: List<IntArray> = buildList {
            for (k in 0 until 9) {
                add(IntArray(9) { k * 9 + it })
                add(IntArray(9) { it * 9 + k })
                add((0 until 81).filter { SudokuSolver.box(it) == k }.toIntArray())
            }
        }
    }
}

/** Killer sudoku: sudoku houses plus sum cages. */
object KillerSolver {
    const val GAVE_UP = GridSolver.GAVE_UP
    private val ALL_CELLS = IntArray(81) { it }

    fun comboMask(avail: Int, k: Int, sum: Int) = GridSolver.comboMask(avail, k, sum)

    fun countSolutions(board: IntArray, cages: List<Cage>, limit: Int = 2, nodeLimit: Int = 150_000): Int =
        GridSolver(81, ALL_CELLS, GridSolver.SUDOKU_HOUSES, cages).countSolutions(board, limit, nodeLimit)

    fun solve(board: IntArray, cages: List<Cage>): IntArray? =
        GridSolver(81, ALL_CELLS, GridSolver.SUDOKU_HOUSES, cages).solve(board)
}

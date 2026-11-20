package com.puzzlenook.sudoku

/**
 * Step-by-step solver for the 1–9 digit puzzles (sudoku, killer sudoku, kakuro), using the
 * techniques a person would with pencil notes and no guessing:
 * - a placed digit leaves the rest of its units;
 * - hidden singles (a digit with one place left in a full row, column or box);
 * - naked pairs and triples;
 * - pointing and box/line reduction, and X-wings (sudoku grids);
 * - sum cages and kakuro runs: only digits that appear in some valid combination stay;
 *   killer "innies": the cells of a row, column or box not covered by cages lying wholly
 *   inside it add up to 45 minus those cages.
 */
class DigitLogic(
    private val size: Int,
    private val active: IntArray,
    private val units: List<IntArray>,
    cages: List<Cage>,
    /** 9×9 grid with rows, columns and boxes (enables box techniques and innies). */
    private val sudokuGrid: Boolean,
    /** The nine boxes (standard 3×3, or jigsaw regions) used by pointing and box/line reduction. */
    private val boxes: List<IntArray> = BOXES,
) {
    private val boxOf = IntArray(81).also { m -> boxes.forEachIndexed { b, cells -> for (c in cells) m[c] = b } }
    private val sumCages: List<Cage> = if (!sudokuGrid || cages.isEmpty()) cages else cages + innies(cages)
    private val fullUnits = units.filter { it.size == 9 }

    fun solves(board: IntArray) = run(board).solved

    fun run(board: IntArray): LatinLogic.Result {
        val cand = IntArray(size)
        val round = IntArray(size) { -1 }
        for (i in active) {
            cand[i] = if (board[i] != 0) 1 shl board[i] else ALL
            if (board[i] != 0) round[i] = 0
        }
        var r = 0
        while (true) {
            r++
            val before = cand.copyOf()
            for (u in units) {
                for (i in u) {
                    if (Integer.bitCount(cand[i]) != 1) continue
                    for (j in u) if (j != i) cand[j] = cand[j] and cand[i].inv()
                }
            }
            for (u in fullUnits) {
                for (d in 1..9) {
                    val bit = 1 shl d
                    var only = -1
                    var count = 0
                    for (i in u) if (cand[i] and bit != 0) {
                        count++
                        only = i
                    }
                    if (count == 1) cand[only] = bit
                }
            }
            for (cage in sumCages) cageSupport(cage, cand)
            // Cheaper rules first: only reach for subsets and box rules when nothing simpler moved.
            if (cand.contentEquals(before)) {
                for (u in units) nakedSubsets(u, cand)
                if (sudokuGrid) {
                    pointing(cand)
                    xWing(cand)
                }
            }
            if (active.any { cand[it] == 0 }) return LatinLogic.Result(false, cand, round)
            for (i in active) if (round[i] < 0 && Integer.bitCount(cand[i]) == 1) round[i] = r
            if (active.all { Integer.bitCount(cand[it]) == 1 }) return LatinLogic.Result(true, cand, round)
            if (cand.contentEquals(before)) return LatinLogic.Result(false, cand, round)
        }
    }

    /** Keeps the digits of each cage cell that fit some set of different digits with the right sum. */
    private fun cageSupport(cage: Cage, cand: IntArray) {
        val cells = cage.cells
        val k = cells.size
        val support = IntArray(k)
        val values = IntArray(k)
        fun rec(pos: Int, used: Int, sum: Int) {
            if (pos == k) {
                if (sum == cage.sum) for (j in 0 until k) support[j] = support[j] or (1 shl values[j])
                return
            }
            // Remaining cells need at least the smallest unused digits.
            val left = k - pos
            if (sum + left > cage.sum || sum + 9 * left < cage.sum) return
            var m = cand[cells[pos]] and used.inv()
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val v = Integer.numberOfTrailingZeros(bit)
                if (sum + v > cage.sum) break
                values[pos] = v
                rec(pos + 1, used or bit, sum + v)
            }
        }
        rec(0, 0, 0)
        for (j in 0 until k) cand[cells[j]] = cand[cells[j]] and support[j]
    }

    /** n cells of a unit sharing only n digits: those digits leave the unit's other cells. */
    private fun nakedSubsets(u: IntArray, cand: IntArray) {
        val open = u.filter { Integer.bitCount(cand[it]) in 2..3 }
        for (a in open.indices) for (b in a + 1 until open.size) {
            val ab = cand[open[a]] or cand[open[b]]
            if (Integer.bitCount(ab) == 2) clearOthers(u, cand, ab, listOf(open[a], open[b]))
            for (c in b + 1 until open.size) {
                val abc = ab or cand[open[c]]
                if (Integer.bitCount(abc) == 3) clearOthers(u, cand, abc, listOf(open[a], open[b], open[c]))
            }
        }
    }

    private fun clearOthers(u: IntArray, cand: IntArray, digits: Int, keep: List<Int>) {
        for (j in u) if (j !in keep) cand[j] = cand[j] and digits.inv()
    }

    /** A digit confined to one line within a box (or one box within a line) leaves the rest. */
    private fun pointing(cand: IntArray) {
        for (box in boxes.indices) {
            val cells = boxes[box]
            for (d in 1..9) {
                val bit = 1 shl d
                val where = cells.filter { cand[it] and bit != 0 }
                if (where.size < 2) continue
                val row = where[0] / 9
                val col = where[0] % 9
                if (where.all { it / 9 == row }) for (c in 0 until 9) {
                    val j = row * 9 + c
                    if (boxOf[j] != box) cand[j] = cand[j] and bit.inv()
                }
                if (where.all { it % 9 == col }) for (r in 0 until 9) {
                    val j = r * 9 + col
                    if (boxOf[j] != box) cand[j] = cand[j] and bit.inv()
                }
            }
        }
        for (line in 0 until 18) {
            val cells = if (line < 9) IntArray(9) { line * 9 + it } else IntArray(9) { it * 9 + line - 9 }
            for (d in 1..9) {
                val bit = 1 shl d
                val where = cells.filter { cand[it] and bit != 0 }
                if (where.size < 2) continue
                val box = boxOf[where[0]]
                if (where.all { boxOf[it] == box }) for (j in boxes[box]) {
                    if (j !in cells) cand[j] = cand[j] and bit.inv()
                }
            }
        }
    }

    private fun xWing(cand: IntArray) {
        for (d in 1..9) {
            val bit = 1 shl d
            for (byRow in listOf(true, false)) {
                fun cell(line: Int, k: Int) = if (byRow) line * 9 + k else k * 9 + line
                val pairs = (0 until 9).mapNotNull { line ->
                    val ks = (0 until 9).filter { cand[cell(line, it)] and bit != 0 }
                    if (ks.size == 2) line to ks else null
                }
                for (a in pairs.indices) for (b in a + 1 until pairs.size) {
                    if (pairs[a].second != pairs[b].second) continue
                    for (k in pairs[a].second) for (line in 0 until 9) {
                        if (line == pairs[a].first || line == pairs[b].first) continue
                        val j = cell(line, k)
                        cand[j] = cand[j] and bit.inv()
                    }
                }
            }
        }
    }

    /** For each house, the cells left after cages wholly inside it: they sum to 45 minus those cages. */
    private fun innies(cages: List<Cage>): List<Cage> = buildList {
        for (house in GridSolver.SUDOKU_HOUSES) {
            val set = house.toSet()
            val inside = cages.filter { c -> c.cells.all { it in set } }
            val rest = house.filter { i -> inside.none { i in it.cells } }
            if (rest.size in 1..4 && inside.isNotEmpty()) add(Cage(45 - inside.sumOf { it.sum }, rest))
        }
    }

    companion object {
        private const val ALL = 0x3FE
        private val BOXES: List<IntArray> = List(9) { b -> (0 until 81).filter { SudokuSolver.box(it) == b }.toIntArray() }
        private val ALL_CELLS = IntArray(81) { it }

        fun jigsaw(regions: IntArray): DigitLogic {
            val regionCells = Regions.cells(regions)
            return DigitLogic(
                81, ALL_CELLS, Regions.rows(9) + Regions.cols(9) + regionCells, emptyList(),
                sudokuGrid = true, boxes = regionCells,
            )
        }

        fun sudoku() = DigitLogic(81, ALL_CELLS, GridSolver.SUDOKU_HOUSES, emptyList(), true)
        fun killer(cages: List<Cage>) =
            DigitLogic(81, ALL_CELLS, GridSolver.SUDOKU_HOUSES + cages.map { it.cells.toIntArray() }, cages, true)
        fun kakuro(n: Int, blocked: BooleanArray, cages: List<Cage>) = DigitLogic(
            n * n,
            (0 until n * n).filter { !blocked[it] }.toIntArray(),
            cages.map { it.cells.toIntArray() },
            cages,
            false,
        )
    }
}

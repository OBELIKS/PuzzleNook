package com.puzzlenook.sudoku

/**
 * A Calcudoku cage: its digits combine with [op] to give [target].
 * Ops: '+', '-', '×', '÷', or '=' for a single cell that simply holds [target].
 * Unlike killer cages, digits may repeat inside a cage (just not in a row or column).
 */
data class MathCage(val op: Char, val target: Int, val cells: List<Int>) {
    val label: String
        get() = when (op) {
            '=' -> "$target"
            '-' -> "$target−"
            else -> "$target$op"
        }
}

/**
 * Rules of an n×n Latin-square puzzle: every row and column holds 1..[n] once, plus any of
 * Futoshiki inequalities ([less]: first cell < second cell), Calcudoku [cages], and
 * Skyscrapers edge clues ([sky]: 4n entries, 0 = no clue; top of each column, bottom of each
 * column, left of each row, right of each row), and Kropki dots between orthogonal neighbours
 * ([white]: consecutive digits, [black]: one is double the other; 1 and 2 count as consecutive
 * only). With [kropkiAll] every dot is shown, so a neighbour pair without one is neither.
 */
class LatinRules(
    val n: Int,
    val less: List<IntArray> = emptyList(),
    val cages: List<MathCage> = emptyList(),
    val sky: IntArray? = null,
    val white: List<IntArray> = emptyList(),
    val black: List<IntArray> = emptyList(),
    val kropkiAll: Boolean = false,
) {
    /** Kropki pairs as `[a, b, kind]` (kind 1 = white, 2 = black, 0 = no dot); empty outside Kropki. */
    val dotPairs: List<IntArray> = buildList {
        for (p in white) add(intArrayOf(p[0], p[1], 1))
        for (p in black) add(intArrayOf(p[0], p[1], 2))
        if (kropkiAll) {
            val dotted = (white + black).map { minOf(it[0], it[1]) to maxOf(it[0], it[1]) }.toSet()
            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c + 1 < n && (i to i + 1) !in dotted) add(intArrayOf(i, i + 1, 0))
                if (r + 1 < n && (i to i + n) !in dotted) add(intArrayOf(i, i + n, 0))
            }
        }
    }
    private val dotsOf: Array<IntArray> = Array(n * n) { i -> dotPairs.indices.filter { dotPairs[it][0] == i || dotPairs[it][1] == i }.toIntArray() }
    private val all = (1 shl (n + 1)) - 2
    private val smaller: Array<IntArray> // smaller[i]: cells that must be below cell i
    private val bigger: Array<IntArray>
    val cageOf: IntArray = IntArray(n * n) { -1 }.also { a -> cages.forEachIndexed { k, c -> c.cells.forEach { a[it] = k } } }

    /** Skyscraper sight lines, in order away from their clue. */
    val lines: List<IntArray> = List(4 * n) { k ->
        val j = k % n
        when (k / n) {
            0 -> IntArray(n) { it * n + j }
            1 -> IntArray(n) { (n - 1 - it) * n + j }
            2 -> IntArray(n) { j * n + it }
            else -> IntArray(n) { j * n + n - 1 - it }
        }
    }
    private val linesOf: Array<IntArray> // line indices with a clue through each cell

    init {
        val s = Array(n * n) { mutableListOf<Int>() }
        val b = Array(n * n) { mutableListOf<Int>() }
        for ((lo, hi) in less.map { it[0] to it[1] }) {
            s[hi] += lo
            b[lo] += hi
        }
        smaller = Array(n * n) { s[it].toIntArray() }
        bigger = Array(n * n) { b[it].toIntArray() }
        val l = Array(n * n) { mutableListOf<Int>() }
        sky?.forEachIndexed { k, clue -> if (clue > 0) lines[k].forEach { l[it] += k } }
        linesOf = Array(n * n) { l[it].toIntArray() }
    }

    /** Rows and columns. */
    val units: List<IntArray> = List(2 * n) { k ->
        if (k < n) IntArray(n) { k * n + it } else IntArray(n) { it * n + (k - n) }
    }

    /** Digits not yet used in cell [i]'s row and column, as bits 1..n. */
    private fun basic(board: IntArray, i: Int): Int {
        val r = i / n
        val c = i % n
        var used = 0
        for (k in 0 until n) {
            if (k != c) used = used or (1 shl board[r * n + k])
            if (k != r) used = used or (1 shl board[k * n + c])
        }
        return all and used.inv()
    }

    /**
     * Digits (bits 1..n) cell [i] can take given the other cells of [board] (0 = empty).
     * [board] is used as scratch space but is left unchanged.
     */
    fun candidates(board: IntArray, i: Int): Int {
        var mask = basic(board, i)
        for (j in smaller[i]) {
            val lo = if (board[j] != 0) board[j] else basic(board, j).let { if (it == 0) return 0 else Integer.numberOfTrailingZeros(it) }
            mask = mask and ((1 shl (lo + 1)) - 1).inv()
        }
        for (j in bigger[i]) {
            val hi = if (board[j] != 0) board[j] else basic(board, j).let { if (it == 0) return 0 else 31 - Integer.numberOfLeadingZeros(it) }
            mask = mask and ((1 shl hi) - 1)
        }
        for (k in dotsOf[i]) {
            val p = dotPairs[k]
            val j = if (p[0] == i) p[1] else p[0]
            mask = mask and if (board[j] != 0) dotMask(p[2], 1 shl board[j]) else basic(board, j).let { if (it == 0) return 0 else dotMask(p[2], it) }
            if (mask == 0) return 0
        }
        val sightLines = linesOf[i]
        // A building k cells from a clue c can be at most n - c + 1 + k tall.
        for (k in sightLines) {
            val pos = lines[k].indexOf(i)
            val max = n - sky!![k] + 1 + pos
            if (max < n) mask = mask and ((1 shl (max + 1)) - 1)
        }
        val cage = cageOf[i]
        if (mask == 0 || (cage < 0 && sightLines.isEmpty())) return mask
        val old = board[i]
        var m = mask
        while (m != 0) {
            val bit = m and -m
            m = m xor bit
            board[i] = Integer.numberOfTrailingZeros(bit)
            val ok = (cage < 0 || cageOk(cages[cage], board)) && sightLines.all { lineOk(it, board) }
            if (!ok) mask = mask xor bit
        }
        board[i] = old
        return mask
    }

    private fun cageOk(cage: MathCage, board: IntArray): Boolean {
        val t = cage.target
        when (cage.op) {
            '=' -> return board[cage.cells[0]].let { it == 0 || it == t }
            '+' -> {
                var sum = 0
                var empty = 0
                for (c in cage.cells) if (board[c] == 0) empty++ else sum += board[c]
                return sum + empty <= t && sum + empty * n >= t
            }
            '×' -> {
                var product = 1
                var empty = 0
                for (c in cage.cells) if (board[c] == 0) empty++ else product *= board[c]
                if (t % product != 0) return false
                var q = t / product
                if (empty == 0) return q == 1
                var max = 1
                repeat(empty) { max *= n }
                if (q > max) return false
                for (p in intArrayOf(2, 3, 5, 7)) {
                    if (p > n) break
                    while (q % p == 0) q /= p
                }
                return q == 1
            }
            else -> {
                val a = board[cage.cells[0]]
                val b = board[cage.cells[1]]
                if (a == 0 && b == 0) return true
                val minus = cage.op == '-'
                if (a != 0 && b != 0) {
                    return if (minus) kotlin.math.abs(a - b) == t else maxOf(a, b) == t * minOf(a, b)
                }
                val x = if (a != 0) a else b
                return if (minus) x + t <= n || x - t >= 1 else x * t <= n || x % t == 0
            }
        }
    }

    private fun lineOk(k: Int, board: IntArray): Boolean {
        val clue = sky!![k]
        var max = 0
        var count = 0
        for ((pos, idx) in lines[k].withIndex()) {
            val v = board[idx]
            if (v == 0) return count <= clue && count + (n - pos) >= clue
            if (v > max) {
                count++
                max = v
                if (v == n) return count == clue
            }
        }
        return count == clue
    }

    /**
     * Digits (bits 1..n) a Kropki neighbour can take when the other cell holds one of [from]
     * (bits 1..n): [kind] 1 = white, 2 = black, 0 = no dot.
     */
    fun dotMask(kind: Int, from: Int): Int {
        var out = 0
        var m = from
        while (m != 0) {
            val v = Integer.numberOfTrailingZeros(m)
            m = m and (m - 1)
            out = out or related[kind][v]
        }
        return out
    }

    private val related: Array<IntArray> = Array(3) { kind -> IntArray(n + 1) { v -> (1..n).filter { w -> dotKind(v, w) == kind }.fold(0) { a, w -> a or (1 shl w) } } }

    fun withLess(less: List<IntArray>) = LatinRules(n, less, cages, sky, white, black, kropkiAll)
    fun withCages(cages: List<MathCage>) = LatinRules(n, less, cages, sky, white, black, kropkiAll)
    fun withSky(sky: IntArray?) = LatinRules(n, less, cages, sky, white, black, kropkiAll)

    companion object {
        /** Which dot sits between digits [a] and [b]: 1 white (consecutive), 2 black (double), 0 none. */
        fun dotKind(a: Int, b: Int): Int = when {
            kotlin.math.abs(a - b) == 1 -> 1
            a == 2 * b || b == 2 * a -> 2
            else -> 0
        }

        /** How many buildings are seen along [line] of a filled [board]. */
        fun visible(board: IntArray, line: IntArray): Int {
            var max = 0
            var count = 0
            for (i in line) if (board[i] > max) {
                max = board[i]
                count++
            }
            return count
        }

        /** Whether [cells] of a filled [board] meet [op] and [target]. */
        fun cageValue(op: Char, values: List<Int>): Int? = when (op) {
            '=' -> values.single()
            '+' -> values.sum()
            '×' -> values.fold(1) { a, b -> a * b }
            '-' -> kotlin.math.abs(values[0] - values[1])
            '÷' -> if (maxOf(values[0], values[1]) % minOf(values[0], values[1]) == 0) maxOf(values[0], values[1]) / minOf(values[0], values[1]) else null
            else -> null
        }
    }
}

/** Backtracking solver for [LatinRules] puzzles, always picking the most constrained cell. */
class LatinSolver(private val rules: LatinRules) {
    private val n = rules.n

    /** Counts solutions up to [limit]; [GridSolver.GAVE_UP] past [nodeLimit] steps. */
    fun countSolutions(givens: IntArray, limit: Int = 2, nodeLimit: Int = 40_000): Int =
        Search(givens, nodeLimit).run(limit)?.size ?: GridSolver.GAVE_UP

    /** Up to two solutions; null when the search gave up. */
    fun twoSolutions(givens: IntArray, nodeLimit: Int = 40_000): List<IntArray>? = Search(givens, nodeLimit).run(2)

    private inner class Search(givens: IntArray, private val nodeLimit: Int) {
        private val board = givens.copyOf()
        private val solutions = mutableListOf<IntArray>()
        private var nodes = 0
        private var gaveUp = false

        fun run(limit: Int): List<IntArray>? {
            // Givens must agree with each other.
            for (i in board.indices) {
                val v = board[i]
                if (v == 0) continue
                board[i] = 0
                val ok = rules.candidates(board, i) and (1 shl v) != 0
                board[i] = v
                if (!ok) return emptyList()
            }
            rec(limit)
            return if (gaveUp) null else solutions
        }

        private fun rec(limit: Int): Boolean {
            if (++nodes > nodeLimit) {
                gaveUp = true
                return true
            }
            var best = -1
            var bestMask = 0
            var bestCount = 99
            for (i in board.indices) {
                if (board[i] != 0) continue
                val m = rules.candidates(board, i)
                val c = Integer.bitCount(m)
                if (c == 0) return false
                if (c < bestCount) {
                    best = i
                    bestMask = m
                    bestCount = c
                    if (c == 1) break
                }
            }
            if (best < 0) {
                solutions += board.copyOf()
                return solutions.size >= limit
            }
            var m = bestMask
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                board[best] = Integer.numberOfTrailingZeros(bit)
                val stop = rec(limit)
                board[best] = 0
                if (stop) return true
            }
            return false
        }
    }
}

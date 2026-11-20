package com.puzzlenook.sudoku

import kotlin.random.Random

/** Irregular regions of a square grid: `regions[i]` is the region id of cell i. */
object Regions {
    /** Cells grouped by region id (ids 0 until the highest id + 1). */
    fun cells(regions: IntArray): List<IntArray> {
        val count = (regions.maxOrNull() ?: -1) + 1
        return List(count) { id -> regions.indices.filter { regions[it] == id }.toIntArray() }
    }

    fun rows(n: Int): List<IntArray> = List(n) { r -> IntArray(n) { r * n + it } }

    fun cols(n: Int): List<IntArray> = List(n) { c -> IntArray(n) { it * n + c } }

    /** True when the cells of region [id] form one orthogonally connected piece. */
    fun connected(n: Int, regions: IntArray, id: Int): Boolean {
        val start = regions.indexOf(id)
        if (start < 0) return false
        val seen = BooleanArray(regions.size)
        val stack = ArrayDeque<Int>()
        stack.add(start)
        seen[start] = true
        var reached = 0
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            reached++
            for (j in neighbours(n, i)) if (!seen[j] && regions[j] == id) {
                seen[j] = true
                stack.add(j)
            }
        }
        return reached == regions.count { it == id }
    }

    internal fun neighbours(n: Int, i: Int): List<Int> = buildList {
        val r = i / n
        val c = i % n
        if (r > 0) add(i - n)
        if (r < n - 1) add(i + n)
        if (c > 0) add(i - 1)
        if (c < n - 1) add(i + 1)
    }

    /** Nine connected regions of nine cells: the standard boxes, reshaped by random swaps. */
    fun randomJigsaw(random: Random, swaps: Int = 60): IntArray {
        val regions = IntArray(81) { SudokuSolver.box(it) }
        repeat(swaps) {
            val a = random.nextInt(81)
            val regionA = regions[a]
            val next = neighbours(9, a).filter { regions[it] != regionA }
            if (next.isEmpty()) return@repeat
            val regionB = regions[next.random(random)]
            // A cell of B beside region A, other than the cell a itself.
            val partners = (0 until 81).filter { c ->
                regions[c] == regionB && neighbours(9, c).any { regions[it] == regionA }
            }
            if (partners.isEmpty()) return@repeat
            val c = partners.random(random)
            regions[a] = regionB
            regions[c] = regionA
            if (!connected(9, regions, regionA) || !connected(9, regions, regionB)) {
                regions[a] = regionA
                regions[c] = regionB
            }
        }
        return regions
    }
}

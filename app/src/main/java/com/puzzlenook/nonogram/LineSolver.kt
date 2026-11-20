package com.puzzlenook.nonogram

/**
 * Nonogram line logic. Cells are -1 (unknown), 0 (empty) or 1 (filled). For one row or
 * column it tries every way to place the clue's blocks that agrees with what is known,
 * and reports the cells that are the same in all of them.
 */
object LineSolver {
    const val UNKNOWN = -1

    /** Block lengths of filled runs, e.g. [1,1,0,1,1,1] → [2, 3]. Empty line → []. */
    fun clue(line: List<Boolean>): List<Int> {
        val out = mutableListOf<Int>()
        var run = 0
        for (f in line) {
            if (f) run++ else if (run > 0) { out += run; run = 0 }
        }
        if (run > 0) out += run
        return out
    }

    /**
     * Returns the line with every cell forced by the clue filled in, or null when the
     * known cells contradict the clue.
     */
    fun solveLine(clue: List<Int>, known: IntArray): IntArray? {
        val n = known.size
        val canFill = BooleanArray(n)
        val canEmpty = BooleanArray(n)
        val current = IntArray(n)
        var any = false

        // suffixMin[k]: shortest length the blocks k.. need (with single gaps).
        val suffixMin = IntArray(clue.size + 1)
        for (k in clue.indices.reversed()) suffixMin[k] = clue[k] + suffixMin[k + 1] + if (k + 1 < clue.size) 1 else 0

        // Memo of (block, position) states proven to have no valid completion.
        val dead = HashSet<Int>()

        fun place(k: Int, pos: Int): Boolean {
            if (k == clue.size) {
                for (i in pos until n) if (known[i] == 1) return false
                for (i in pos until n) current[i] = 0
                for (i in 0 until n) if (current[i] == 1) canFill[i] = true else canEmpty[i] = true
                any = true
                return true
            }
            val key = k * (n + 1) + pos
            if (key in dead) return false
            var ok = false
            val len = clue[k]
            var start = pos
            while (start + suffixMin[k] <= n) {
                // Cells skipped before this block must be allowed to be empty.
                if (start > pos && known[start - 1] == 1) break
                val end = start + len
                var fits = true
                for (i in start until end) if (known[i] == 0) { fits = false; break }
                if (fits && end < n && known[end] == 1) fits = false
                if (fits) {
                    for (i in pos until start) current[i] = 0
                    for (i in start until end) current[i] = 1
                    var next = end
                    if (end < n) { current[end] = 0; next = end + 1 }
                    if (place(k + 1, next)) ok = true
                }
                start++
            }
            if (!ok) dead += key
            return ok
        }

        if (clue.isEmpty()) {
            if (known.any { it == 1 }) return null
            return IntArray(n) { 0 }
        }
        place(0, 0)
        if (!any) return null
        return IntArray(n) { i ->
            when {
                canFill[i] && !canEmpty[i] -> 1
                canEmpty[i] && !canFill[i] -> 0
                else -> known[i]
            }
        }
    }

    /**
     * Applies line logic to all rows and columns until nothing changes. [grid] is
     * row-major, size [w]×[h], updated in place. Returns false on a contradiction.
     */
    fun solveGrid(w: Int, h: Int, rows: List<List<Int>>, cols: List<List<Int>>, grid: IntArray): Boolean {
        val dirtyRows = BooleanArray(h) { true }
        val dirtyCols = BooleanArray(w) { true }
        var progress = true
        while (progress) {
            progress = false
            for (r in 0 until h) {
                if (!dirtyRows[r]) continue
                dirtyRows[r] = false
                val known = IntArray(w) { grid[r * w + it] }
                val solved = solveLine(rows[r], known) ?: return false
                for (c in 0 until w) if (solved[c] != known[c]) {
                    grid[r * w + c] = solved[c]
                    dirtyCols[c] = true
                    progress = true
                }
            }
            for (c in 0 until w) {
                if (!dirtyCols[c]) continue
                dirtyCols[c] = false
                val known = IntArray(h) { grid[it * w + c] }
                val solved = solveLine(cols[c], known) ?: return false
                for (r in 0 until h) if (solved[r] != known[r]) {
                    grid[r * w + c] = solved[r]
                    dirtyRows[r] = true
                    progress = true
                }
            }
        }
        return true
    }
}

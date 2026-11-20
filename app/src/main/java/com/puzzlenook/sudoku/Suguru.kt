package com.puzzlenook.sudoku

import kotlin.random.Random

/**
 * Step-by-step solver for Suguru: an n×n grid of regions, a region of k cells holds 1..k once,
 * and equal digits never touch (not even diagonally). Candidates are bitmasks (bit d = digit d).
 * - Level 0: a placed digit leaves its region and its king neighbours; hidden singles in a region.
 * - Level 1: + naked pairs/triples in a region; a digit whose places in a region all touch a cell
 *   outside it leaves that cell.
 * - Level 2: + what-if on cells with two candidates (a contradiction settles the other digit).
 * - Level 3: + what-if on cells with two or three candidates (a contradiction removes that digit).
 */
class SuguruLogic(private val n: Int, private val regions: IntArray, private val level: Int) {
    private val total = n * n
    private val cellsOf: List<IntArray> = Regions.cells(regions)
    private val regionSize = IntArray(total) { cellsOf[regions[it]].size }
    private val king: Array<IntArray> = Array(total) { i ->
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

    fun solves(board: IntArray) = run(board).solved

    fun run(board: IntArray): LatinLogic.Result {
        val cand = IntArray(total) { if (board[it] != 0) 1 shl board[it] else (1 shl (regionSize[it] + 1)) - 2 }
        val round = IntArray(total) { if (board[it] != 0) 0 else -1 }
        var r = 0
        while (true) {
            r++
            val before = cand.copyOf()
            var ok = step(cand, level >= 1)
            if (ok && level >= 2 && cand.contentEquals(before)) ok = whatIf(cand, if (level >= 3) 3 else 2)
            if (!ok) return LatinLogic.Result(false, cand, round)
            for (i in 0 until total) if (round[i] < 0 && Integer.bitCount(cand[i]) == 1) round[i] = r
            if (cand.all { Integer.bitCount(it) == 1 }) return LatinLogic.Result(true, cand, round)
            if (cand.contentEquals(before)) return LatinLogic.Result(false, cand, round)
        }
    }

    /** One pass of the rules (cheaper ones first); false on a contradiction. */
    private fun step(cand: IntArray, extra: Boolean): Boolean {
        val before = cand.copyOf()
        for (i in 0 until total) {
            if (Integer.bitCount(cand[i]) != 1) continue
            val keep = cand[i].inv()
            for (j in cellsOf[regions[i]]) if (j != i) cand[j] = cand[j] and keep
            for (j in king[i]) cand[j] = cand[j] and keep
        }
        for (cells in cellsOf) {
            for (d in 1..cells.size) {
                val bit = 1 shl d
                var only = -1
                var count = 0
                for (i in cells) if (cand[i] and bit != 0) {
                    count++
                    only = i
                }
                if (count == 0) return false
                if (count == 1) cand[only] = bit
            }
        }
        if (extra && cand.contentEquals(before)) {
            for (cells in cellsOf) nakedSubsets(cells, cand)
            touching(cand)
        }
        return cand.none { it == 0 }
    }

    private fun nakedSubsets(cells: IntArray, cand: IntArray) {
        val open = cells.filter { Integer.bitCount(cand[it]) in 2..3 }
        for (a in open.indices) for (b in a + 1 until open.size) {
            val ab = cand[open[a]] or cand[open[b]]
            if (Integer.bitCount(ab) == 2) clearOthers(cells, cand, ab, open[a], open[b], -1)
            for (c in b + 1 until open.size) {
                val abc = ab or cand[open[c]]
                if (Integer.bitCount(abc) == 3) clearOthers(cells, cand, abc, open[a], open[b], open[c])
            }
        }
    }

    private fun clearOthers(cells: IntArray, cand: IntArray, digits: Int, a: Int, b: Int, c: Int) {
        for (j in cells) if (j != a && j != b && j != c) cand[j] = cand[j] and digits.inv()
    }

    /** A digit whose every place in a region touches cell X (outside it) can't be in X. */
    private fun touching(cand: IntArray) {
        for (cells in cellsOf) {
            val id = regions[cells[0]]
            for (d in 1..cells.size) {
                val bit = 1 shl d
                val where = cells.filter { cand[it] and bit != 0 }
                if (where.isEmpty()) continue
                for (x in king[where[0]]) {
                    if (regions[x] == id || cand[x] and bit == 0) continue
                    if (where.all { w -> x in king[w] }) cand[x] = cand[x] and bit.inv()
                }
            }
        }
    }

    private fun propagate(cand: IntArray): Boolean {
        while (true) {
            val before = cand.copyOf()
            if (!step(cand, true)) return false
            if (cand.contentEquals(before)) return true
        }
    }

    /**
     * Tries each digit of two-candidate cells; one that breaks the grid settles the other.
     * Stops at the first cell it settles, so the simpler rules take over again.
     */
    private fun whatIf(cand: IntArray, maxCands: Int): Boolean {
        for (size in 2..maxCands) for (i in 0 until total) {
            if (Integer.bitCount(cand[i]) != size) continue
            var m = cand[i]
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val trial = cand.copyOf()
                trial[i] = bit
                if (!propagate(trial)) {
                    cand[i] = cand[i] and bit.inv()
                    return true
                }
            }
        }
        return true
    }

    companion object {
        fun forLevel(d: Difficulty, n: Int, regions: IntArray) = SuguruLogic(n, regions, d.ordinal)
    }
}

/** Suguru: regions of 1–5 cells on a 5×5 to 8×8 grid; solvable by step-by-step logic. */
object SuguruGenerator {
    fun size(d: Difficulty) = intArrayOf(5, 6, 7, 8)[d.ordinal]

    private const val FILL_NODES = 5_000

    /** Givens to keep at least: easier levels stop removing early so they aren't as bare as Expert. */
    fun minGivens(d: Difficulty) = intArrayOf(10, 12, 0, 0)[d.ordinal]

    /** Hard must need level 2, Expert level 3: the puzzle isn't solved by the level below. */
    fun needsTop(d: Difficulty, n: Int, regions: IntArray, board: IntArray) = when (d) {
        Difficulty.HARD -> !SuguruLogic(n, regions, 1).solves(board)
        Difficulty.EXPERT -> !SuguruLogic(n, regions, 2).solves(board)
        else -> true
    }

    /** Attempts the last [generate] call used (for tests and tuning). */
    @Volatile var lastAttempts = 0

    /**
     * Keeps trying within [budgetMs] (at most 30 attempts); returns at once a puzzle that needs its
     * level's top technique, else the best one (needs it first, then fewest givens).
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 3000): Puzzle {
        val deadline = System.currentTimeMillis() + budgetMs
        var best: Puzzle? = null
        var bestTop = false
        var attempts = 0
        do {
            val puzzle = attempt(d, random, deadline)
            if (puzzle != null) {
                attempts++
                val top = needsTop(d, puzzle.size, puzzle.regions!!, puzzle.givens)
                if (top && !bestTop) {
                    best = puzzle
                    bestTop = true
                } else if (top == bestTop && (best == null || puzzle.clueCount < best.clueCount)) best = puzzle
                if (bestTop) break
            }
        } while (best == null || (attempts < 30 && System.currentTimeMillis() < deadline))
        lastAttempts = attempts
        return best!!
    }

    /** Past [deadline] it stops removing givens: the puzzle stays valid, just with a few more. */
    private fun attempt(d: Difficulty, random: Random, deadline: Long): Puzzle? {
        val n = size(d)
        val regions = randomRegions(n, random)
        val solution = fill(n, regions, random) ?: return null
        val board = solution.copyOf()
        val logic = SuguruLogic.forLevel(d, n, regions)
        // Time spent on failed fills must not leave this attempt without time to remove givens.
        val stop = maxOf(deadline, System.currentTimeMillis() + 1500)
        var left = n * n
        // Hard/Expert: first thin out with the level below, then remove what only the top level can.
        val first = if (d.ordinal >= 2) SuguruLogic(n, regions, d.ordinal - 1) else logic
        for (i in (0 until n * n).shuffled(random)) {
            if (System.currentTimeMillis() > stop || left <= minGivens(d)) break
            val v = board[i]
            board[i] = 0
            if (!first.solves(board)) board[i] = v else left--
        }
        if (d.ordinal >= 2) {
            for (i in (0 until n * n).filter { board[it] != 0 }.shuffled(random)) {
                if (System.currentTimeMillis() > stop) break
                val v = board[i]
                board[i] = 0
                if (!logic.solves(board)) board[i] = v
            }
        }
        return Puzzle(board, solution, d, size = n, regions = regions)
    }

    private fun regionSize(random: Random): Int {
        val x = random.nextInt(100)
        return when {
            x < 50 -> 5
            x < 80 -> 4
            x < 92 -> 3
            x < 98 -> 2
            else -> 1
        }
    }

    /** Grows regions from random unassigned cells by random orthogonal steps; blocked ones end smaller. */
    fun randomRegions(n: Int, random: Random): IntArray {
        val reg = IntArray(n * n) { -1 }
        var id = 0
        for (start in (0 until n * n).shuffled(random)) {
            if (reg[start] >= 0) continue
            val target = regionSize(random)
            reg[start] = id
            val cells = mutableListOf(start)
            while (cells.size < target) {
                val frontier = cells.flatMap { Regions.neighbours(n, it) }.filter { reg[it] < 0 }.distinct()
                if (frontier.isEmpty()) break
                val c = frontier.random(random)
                reg[c] = id
                cells += c
            }
            id++
        }
        return reg
    }

    /** Backtracking fill, always on the cell with fewest options: no equal king neighbours, region digits once. */
    private fun fill(n: Int, regions: IntArray, random: Random): IntArray? {
        val cellsOf = Regions.cells(regions)
        val sol = IntArray(n * n)
        val used = IntArray(cellsOf.size)
        val sizeOf = IntArray(n * n) { cellsOf[regions[it]].size }
        var nodes = 0
        fun options(i: Int): Int {
            var mask = ((1 shl (sizeOf[i] + 1)) - 2) and used[regions[i]].inv()
            val r = i / n
            val c = i % n
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr
                val cc = c + dc
                if ((dr != 0 || dc != 0) && rr in 0 until n && cc in 0 until n && sol[rr * n + cc] != 0) {
                    mask = mask and (1 shl sol[rr * n + cc]).inv()
                }
            }
            return mask
        }
        fun rec(): Boolean {
            if (++nodes > FILL_NODES) return false
            var cell = -1
            var cellMask = 0
            var fewest = Int.MAX_VALUE
            for (i in 0 until n * n) {
                if (sol[i] != 0) continue
                val mask = options(i)
                val count = Integer.bitCount(mask)
                if (count == 0) return false
                if (count < fewest) {
                    fewest = count
                    cell = i
                    cellMask = mask
                }
            }
            if (cell < 0) return true
            val reg = regions[cell]
            for (d in (1..sizeOf[cell]).filter { (cellMask shr it) and 1 == 1 }.shuffled(random)) {
                sol[cell] = d
                used[reg] = used[reg] or (1 shl d)
                if (rec()) return true
                sol[cell] = 0
                used[reg] = used[reg] and (1 shl d).inv()
            }
            return false
        }
        return if (rec()) sol else null
    }
}
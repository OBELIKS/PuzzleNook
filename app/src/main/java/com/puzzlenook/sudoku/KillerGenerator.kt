package com.puzzlenook.sudoku

import kotlin.random.Random

/**
 * Killer sudoku: a random solved grid is cut into cages (no digit repeats inside a cage),
 * then given digits are removed while the puzzle can still be solved without guessing.
 * Harder levels use bigger cages (fewer sum combinations) and fewer givens.
 */
object KillerGenerator {
    fun maxCageSize(d: Difficulty) = when (d) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 5
        Difficulty.EXPERT -> 6
    }

    fun targetGivens(d: Difficulty) = when (d) {
        Difficulty.EASY -> 10
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 0
        Difficulty.EXPERT -> 0
    }

    fun generate(difficulty: Difficulty, random: Random = Random.Default): Puzzle {
        // Some cage layouts need a few givens to be unique; try a few and keep the leanest.
        var best: Puzzle? = null
        repeat(5) {
            val p = attempt(difficulty, random)
            if (best == null || p.clueCount < best.clueCount) best = p
            if (p.clueCount <= targetGivens(difficulty)) return p
        }
        return best!!
    }

    private fun attempt(difficulty: Difficulty, random: Random): Puzzle {
        val solution = SudokuSolver.randomSolved(random)
        val cages = makeCages(solution, maxCageSize(difficulty), random)
        val board = solution.copyOf()
        val logic = DigitLogic.killer(cages)
        var clues = 81
        val target = targetGivens(difficulty)
        for (i in (0 until 81).shuffled(random)) {
            if (clues <= target) break
            val v = board[i]
            board[i] = 0
            if (logic.solves(board)) clues-- else board[i] = v
        }
        return Puzzle(board, solution, difficulty, cages)
    }

    private fun neighbours(i: Int): List<Int> = buildList {
        val r = i / 9
        val c = i % 9
        if (r > 0) add(i - 9)
        if (r < 8) add(i + 9)
        if (c > 0) add(i - 1)
        if (c < 8) add(i + 1)
    }

    fun makeCages(solution: IntArray, maxSize: Int, random: Random): List<Cage> {
        val owner = IntArray(81) { -1 }
        val groups = mutableListOf<MutableList<Int>>()
        while (true) {
            val free = (0 until 81).filter { owner[it] < 0 }
            if (free.isEmpty()) break
            // Start in a tight spot (fewest free neighbours) so cells don't get stranded alone.
            val start = free.minBy { i -> neighbours(i).count { owner[it] < 0 } * 10 + random.nextInt(10) }
            val size = random.nextInt(2, maxSize + 1)
            val group = mutableListOf(start)
            owner[start] = groups.size
            var digits = 1 shl solution[start]
            while (group.size < size) {
                val options = group.flatMap(::neighbours).distinct()
                    .filter { owner[it] < 0 && digits and (1 shl solution[it]) == 0 }
                if (options.isEmpty()) break
                val next = options.random(random)
                group += next
                owner[next] = groups.size
                digits = digits or (1 shl solution[next])
            }
            groups += group
        }
        // Join leftover single cells to a neighbouring cage when the digit isn't in it yet.
        for (g in groups.indices) {
            val group = groups[g]
            if (group.size != 1) continue
            val cell = group[0]
            val into = neighbours(cell).map { owner[it] }.distinct().filter { o ->
                o != g && groups[o].size in 1..maxSize && groups[o].none { solution[it] == solution[cell] }
            }.minByOrNull { groups[it].size } ?: continue
            groups[into] += cell
            owner[cell] = into
            group.clear()
        }
        return groups.filter { it.isNotEmpty() }
            .map { cells -> Cage(cells.sumOf { solution[it] }, cells.sorted()) }
            .sortedBy { it.cells.first() }
    }
}

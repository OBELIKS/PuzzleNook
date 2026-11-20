package com.puzzlenook.sudoku

import kotlin.random.Random

/** Jigsaw sudoku: nine irregular regions replace the boxes; solvable by step-by-step logic. */
object JigsawGenerator {
    fun targetClues(d: Difficulty) = when (d) {
        Difficulty.EASY -> 40
        Difficulty.MEDIUM -> 34
        Difficulty.HARD -> 30
        Difficulty.EXPERT -> 27
    }

    /** Keeps trying within [budgetMs] and returns the puzzle with the fewest clues (stops at the target). */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 2500): Puzzle {
        val deadline = System.currentTimeMillis() + budgetMs
        var best: Puzzle? = null
        do {
            val puzzle = attempt(d, random) ?: continue
            if (best == null || puzzle.clueCount < best.clueCount) best = puzzle
            if (puzzle.clueCount <= targetClues(d)) break
        } while (System.currentTimeMillis() < deadline || best == null)
        return best!!
    }

    private fun attempt(d: Difficulty, random: Random): Puzzle? {
        val regions = Regions.randomJigsaw(random)
        val all = IntArray(81) { it }
        val regionCells = Regions.cells(regions)
        val units = Regions.rows(9) + Regions.cols(9) + regionCells
        val start = IntArray(81)
        (1..9).shuffled(random).forEachIndexed { c, v -> start[c] = v }
        val solution = GridSolver(81, all, units, emptyList()).twoSolutions(start, 200_000)?.firstOrNull() ?: return null
        val board = solution.copyOf()
        val logic = DigitLogic.jigsaw(regions)
        var clues = 81
        val target = targetClues(d)
        for (i in (0 until 81).shuffled(random)) {
            if (clues <= target) break
            val v = board[i]
            board[i] = 0
            if (logic.solves(board)) clues-- else board[i] = v
        }
        return Puzzle(board, solution, d, regions = regions)
    }
}

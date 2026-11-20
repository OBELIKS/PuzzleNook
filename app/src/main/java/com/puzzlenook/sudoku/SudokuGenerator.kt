package com.puzzlenook.sudoku

import androidx.annotation.StringRes
import com.puzzlenook.R
import kotlin.random.Random

enum class Difficulty(@StringRes val labelRes: Int, val targetClues: Int) {
    EASY(R.string.difficulty_easy, 38),
    MEDIUM(R.string.difficulty_medium, 32),
    HARD(R.string.difficulty_hard, 27),
    EXPERT(R.string.difficulty_expert, 23),
}

/** Which flavour of sudoku a game is; each keeps its own saved game and stats. */
enum class Variant(@StringRes val titleRes: Int, val storeName: String) {
    CLASSIC(R.string.title_classic, "sudoku"),
    KILLER(R.string.title_killer, "killer"),
    KAKURO(R.string.title_kakuro, "kakuro"),
    NONOGRAM(R.string.title_nonogram, "nonogram"),
    FUTOSHIKI(R.string.title_futoshiki, "futoshiki"),
    CALCUDOKU(R.string.title_calcudoku, "calcudoku"),
    SKYSCRAPERS(R.string.title_skyscrapers, "skyscrapers"),
    STAR_BATTLE(R.string.title_star_battle, "starbattle"),
    HITORI(R.string.title_hitori, "hitori"),
    NURIKABE(R.string.title_nurikabe, "nurikabe"),
    SLITHERLINK(R.string.title_slitherlink, "slitherlink"),
    HIDATO(R.string.title_hidato, "hidato"),
    TAKUZU(R.string.title_takuzu, "takuzu"),
    MOSAIC(R.string.title_mosaic, "mosaic"),
    JIGSAW(R.string.title_jigsaw, "jigsaw"),
    SUGURU(R.string.title_suguru, "suguru"),
    LIGHT_UP(R.string.title_light_up, "lightup"),
    KROPKI(R.string.title_kropki, "kropki"),
    TENTS(R.string.title_tents, "tents"),
    KURODOKO(R.string.title_kurodoko, "kurodoko"),
    MASYU(R.string.title_masyu, "masyu"),
    SHIKAKU(R.string.title_shikaku, "shikaku"),
    NORINORI(R.string.title_norinori, "norinori"),
    BRIDGES(R.string.title_bridges, "bridges"),
    BATTLESHIPS(R.string.title_battleships, "battleships"),
    AQUARIUM(R.string.title_aquarium, "aquarium"),
    HEYAWAKE(R.string.title_heyawake, "heyawake"),
    LITS(R.string.title_lits, "lits"),
    FILLOMINO(R.string.title_fillomino, "fillomino"),
    GALAXIES(R.string.title_galaxies, "galaxies"),
    YAJILIN(R.string.title_yajilin, "yajilin");

    /** Rows, columns and 3×3 boxes must each hold 1–9. */
    val hasHouses: Boolean get() = this == CLASSIC || this == KILLER

    /** The puzzle brings its own irregular regions (see [Puzzle.regions]). */
    val hasRegions: Boolean get() = this == JIGSAW || this == SUGURU

    /** An n×n grid where every row and column holds 1..n once. */
    val isLatin: Boolean get() = this == FUTOSHIKI || this == CALCUDOKU || this == SKYSCRAPERS || this == KROPKI

    /** You shade cells or place stars instead of writing digits. */
    val isShading: Boolean get() = this == STAR_BATTLE || this == HITORI || this == NURIKABE || this == TAKUZU || this == MOSAIC || this == LIGHT_UP || this == TENTS || this == KURODOKO || this == NORINORI || this == BATTLESHIPS || this == AQUARIUM || this == HEYAWAKE || this == LITS
}

class Puzzle(
    val givens: IntArray,
    val solution: IntArray,
    val difficulty: Difficulty,
    val cages: List<Cage> = emptyList(),
    val size: Int = 9,
    /** Kakuro clue squares. */
    val blocked: BooleanArray? = null,
    /** Futoshiki, Calcudoku, Skyscrapers and Kropki rules. */
    val rules: LatinRules? = null,
    /** Jigsaw sudoku (ids 0..8) and Suguru: region id of every cell. */
    val regions: IntArray? = null,
) {
    val clueCount: Int get() = givens.count { it != 0 }
}

object SudokuGenerator {
    /**
     * Builds a random solved grid, then removes clues in symmetric pairs while the
     * puzzle can still be solved step by step without guessing (so it has exactly one solution), until the difficulty's clue target is reached.
     */
    fun generate(difficulty: Difficulty, random: Random = Random.Default): Puzzle {
        val attempts = if (difficulty == Difficulty.EXPERT) 6 else 3
        var best: Puzzle? = null
        repeat(attempts) {
            val puzzle = attempt(difficulty, random)
            if (best == null || puzzle.clueCount < best.clueCount) best = puzzle
            if (puzzle.clueCount <= difficulty.targetClues) return puzzle
        }
        return best!!
    }

    private fun attempt(difficulty: Difficulty, random: Random): Puzzle {
        val solution = SudokuSolver.randomSolved(random)
        val board = solution.copyOf()
        val logic = DigitLogic.sudoku()
        var clues = 81
        for (i in (0..40).shuffled(random)) {
            if (clues <= difficulty.targetClues) break
            val j = 80 - i
            val a = board[i]
            val b = board[j]
            board[i] = 0
            board[j] = 0
            if (logic.solves(board)) {
                clues -= if (i == j) 1 else 2
            } else {
                board[i] = a
                board[j] = b
            }
        }
        return Puzzle(board, solution, difficulty)
    }
}

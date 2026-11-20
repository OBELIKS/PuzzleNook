package com.puzzlenook.hidato

import com.puzzlenook.settings.HelpRules
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState

/**
 * A Hidato game in progress. A number that isn't the one in that cell's place on the path is
 * a mistake (½ point) and shows in red until you change it.
 */
data class HidatoState(
    val difficulty: Difficulty,
    val n: Int,
    val solution: List<Int>,
    val values: List<Int>,
    /** Givens and revealed hints. */
    val fixed: List<Boolean>,
    val selected: Int? = null,
    /** First digit typed on the number pad, waiting for a possible second one. */
    val pending: Int? = null,
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    val hintCell: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: com.puzzlenook.settings.HelpSettings = com.puzzlenook.settings.HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    /** False in "hide errors" mode: wrong numbers look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong cells (cell → number) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    val max: Int get() = n * n

    fun isWrong(i: Int) = values[i] != 0 && values[i] != solution[i]

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(i: Int) = showWrong && isWrong(i)

    /** A number in this cell that counts as placed: any number while errors are hidden. */
    fun placed(i: Int) = values[i] != 0 && !shownWrong(i)

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): HidatoState {
        if (show == showWrong || completed) return this
        val wrong = values.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { values[it] }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { values[it] })
        }
    }

    val isSolved: Boolean get() = values.indices.all { values[it] == solution[it] }

    val activeHintCell: Int? get() = hintCell?.takeIf { !completed && values[it] != solution[it] }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    /** Numbers not yet in their place, in order. */
    val missing: List<Int> get() {
        val inPlace = values.indices.filter { placed(it) }.map { values[it] }.toSet()
        return (1..max).filter { it !in inPlace }
    }

    /** Puts [v] in cell [i] (the same number again clears it). */
    fun enter(i: Int, v: Int): HidatoState {
        if (completed || fixed[i] || v !in 1..max) return this
        if (values[i] == v) return copy(values = values.replaced(i, 0), pending = null, errorsSeen = errorsSeen || showWrong)
        val wrong = v != solution[i]
        return copy(
            values = values.replaced(i, v),
            pending = null,
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
            hintCell = if (!wrong && i == hintCell) null else hintCell,
        ).checkSolved()
    }

    fun erase(i: Int): HidatoState {
        if (completed || fixed[i] || values[i] == 0) return copy(pending = null)
        return copy(values = values.replaced(i, 0), pending = null, errorsSeen = errorsSeen || showWrong)
    }

    fun hint(): HidatoState {
        if (completed) return this
        activeHintCell?.let { h ->
            return copy(
                values = values.replaced(h, solution[h]),
                fixed = fixed.toMutableList().also { it[h] = true },
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintCell = null,
                selected = h,
                pending = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintCell() ?: return this
        return copy(
            hintCell = target, selected = target, pending = null,
            hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /** A wrong number first; else the empty cell the logic fills soonest, nearest the selection. */
    fun pickHintCell(): Int? {
        values.indices.firstOrNull { isWrong(it) }?.let { return it }
        val result = HidatoLogic(n).run(values.toIntArray())
        val open = values.indices.filter { values[it] == 0 }
        return open.filter { result.round[it] > 0 }
            .minWithOrNull(compareBy({ result.round[it] }, { selected?.let { s -> distance(s, it) } ?: 0 }))
            ?: open.firstOrNull()
    }

    private fun distance(a: Int, b: Int) = maxOf(kotlin.math.abs(a / n - b / n), kotlin.math.abs(a % n - b % n))

    private fun checkSolved() = if (isSolved) copy(completed = true, hintCell = null, pending = null) else this

    companion object {
        fun fromPuzzle(p: HidatoPuzzle) = HidatoState(
            difficulty = p.difficulty,
            n = p.n,
            solution = p.solution.toList(),
            values = p.givens.toList(),
            fixed = p.givens.map { it != 0 },
        )
    }
}

private fun List<Int>.replaced(i: Int, v: Int) = toMutableList().also { it[i] = v }

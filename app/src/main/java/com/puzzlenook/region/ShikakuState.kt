package com.puzzlenook.region

import com.puzzlenook.settings.HelpRules
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState
import org.json.JSONObject

/**
 * A Shikaku game in progress. [rects] are the rectangles you drew; [fixed] are the ones a hint
 * revealed (right, locked). A rectangle that isn't part of the solution is a mistake, charged when
 * it is placed (or when errors are shown again, if they were hidden). `hintClue` is the clue a hint points at.
 */
data class ShikakuState(
    val difficulty: Difficulty,
    val n: Int,
    /** Area on each clue cell, 0 elsewhere. */
    val numbers: List<Int>,
    val solution: List<Rect>,
    val rects: List<Rect> = emptyList(),
    val fixed: List<Rect> = emptyList(),
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    /** The clue cell a hint points at; the next hint press reveals its rectangle. */
    val hintClue: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: HelpSettings = HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    /** False in "hide errors" mode: wrong rectangles look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong rectangles (code → 1) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    val clues: List<Int> get() = numbers.indices.filter { numbers[it] > 0 }

    private val solutionCodes: Set<Int> get() = solution.mapTo(HashSet()) { it.code }

    fun isWrong(r: Rect) = r.code !in solutionCodes

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(r: Rect) = showWrong && isWrong(r)

    fun rectAt(cell: Int): Rect? = rects.firstOrNull { it.containsCell(cell, n) }

    fun isFixed(r: Rect) = fixed.any { it == r }

    /** The clue's rectangle is drawn with the right area around exactly that clue: looks at your rectangles only. */
    fun clueDone(clue: Int): Boolean {
        val r = rectAt(clue) ?: return false
        return r.area == numbers[clue] && r.cells(n).count { numbers[it] > 0 } == 1
    }

    /** Hides or shows errors; showing charges the wrong rectangles drawn while hidden. */
    fun withShowWrong(show: Boolean): ShikakuState {
        if (show == showWrong || completed) return this
        val wrong = rects.filter { isWrong(it) }.map { it.code }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { 1 }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { 1 })
        }
    }

    val isSolved: Boolean get() = solution.all { s -> rects.any { it == s } }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    /** The solution rectangle that holds [clue]. */
    fun solutionOf(clue: Int): Rect = solution.first { it.containsCell(clue, n) }

    val activeHintClue: Int? get() = hintClue?.takeIf { !completed && solutionOf(it) !in rects }

    /** Whether [r] may be placed: it doesn't touch a hint-revealed rectangle. */
    fun canPlace(r: Rect) = !completed && fixed.none { it.overlaps(r) }

    /** Places [r], replacing the rectangles it overlaps. */
    fun place(r: Rect): ShikakuState {
        if (!canPlace(r) || rects.any { it == r }) return this
        val wrong = isWrong(r)
        return copy(
            rects = rects.filter { !it.overlaps(r) } + r,
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
        ).checkSolved()
    }

    /** Removes the rectangle at [cell] (not a hint-revealed one). */
    fun remove(cell: Int): ShikakuState {
        if (completed) return this
        val r = rectAt(cell) ?: return this
        if (isFixed(r)) return this
        return copy(rects = rects - r)
    }

    /** Back to an earlier set of rectangles; hint-revealed ones stay. */
    fun restore(previous: List<Rect>): ShikakuState =
        copy(rects = previous.filter { p -> fixed.none { it.overlaps(p) } } + fixed)

    fun hint(): ShikakuState {
        if (completed) return this
        activeHintClue?.let { clue ->
            val r = solutionOf(clue)
            return copy(
                rects = rects.filter { !it.overlaps(r) } + r,
                fixed = fixed + r,
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintClue = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintClue() ?: return this
        return copy(
            hintClue = target, hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /**
     * A wrong rectangle first (the clue of a solution rectangle it overlaps); else the clue whose
     * rectangle the logic settles soonest from your right rectangles.
     */
    fun pickHintClue(): Int? {
        rects.firstOrNull { isWrong(it) }?.let { w ->
            val s = solution.first { it.overlaps(w) }
            return s.cells(n).first { numbers[it] > 0 }
        }
        val right = HashMap<Int, Rect>()
        for (r in rects) {
            val clue = r.cells(n).firstOrNull { numbers[it] > 0 } ?: continue
            right[clue] = r
        }
        val open = clues.filter { solutionOf(it) !in rects }
        // Games saved before the bounded level 2 may be past what their level can settle: use the next level up.
        for (level in difficulty.ordinal..3) {
            val result = ShikakuLogic.forLevel(Difficulty.entries[level], n, numbers.toIntArray()).run(right)
            val soonest = open.mapNotNull { result.round[it] }.minOrNull() ?: continue
            return open.first { result.round[it] == soonest }
        }
        return open.firstOrNull()
    }

    private fun checkSolved(): ShikakuState =
        if (isSolved) copy(completed = true, hintClue = null, rects = solution) else this

    /** The saved-game JSON. */
    fun toJson(): JSONObject = JSONObject()
        .put("difficulty", difficulty.name)
        .put("size", n)
        .put("numbers", numbers.joinToString(","))
        .put("solution", encode(solution))
        .put("rects", encode(rects))
        .put("fixed", encode(fixed))
        .put("mistakes", mistakes)
        .put("showWrong", showWrong)
        .put("errorsSeen", errorsSeen)
        .put("chargedWrong", AnswerCheck.encode(chargedWrong))
        .put("hintHalfPoints", hintHalfPoints)
        .put("hintClue", hintClue ?: -1)
        .put("hintsUsed", hintsUsed)
        .put("lastHintAt", lastHintAt)
        .put("hintLimit", help.hintLimit)
        .put("risingCost", help.risingCost)
        .put("hintCooldown", help.cooldownSeconds)
        .put("elapsed", elapsedSeconds)

    companion object {
        /** Reads a saved game; [h] fills help settings an old save lacks. Null if the JSON is unusable. */
        fun fromJson(json: JSONObject, h: HelpSettings, penaltyPercent: Int): ShikakuState? = runCatching {
            val halfPoints = json.getInt("hintHalfPoints")
            ShikakuState(
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                n = json.getInt("size"),
                numbers = json.getString("numbers").split(",").map(String::toInt),
                solution = decode(json.getString("solution")),
                rects = decode(json.getString("rects")),
                fixed = decode(json.getString("fixed")),
                mistakes = json.getInt("mistakes"),
                showWrong = json.optBoolean("showWrong", true),
                errorsSeen = json.optBoolean("errorsSeen", true),
                chargedWrong = AnswerCheck.decode(json.optString("chargedWrong", "")),
                hintHalfPoints = halfPoints,
                hintClue = json.optInt("hintClue", -1).takeIf { it >= 0 },
                hintsUsed = json.optInt("hintsUsed", HelpRules.hintsUsedFromOldSave(halfPoints)),
                lastHintAt = json.optLong("lastHintAt", -1),
                help = HelpSettings(
                    hintLimit = json.optInt("hintLimit", h.hintLimit),
                    risingCost = json.optBoolean("risingCost", h.risingCost),
                    cooldownSeconds = json.optInt("hintCooldown", h.cooldownSeconds),
                ),
                elapsedSeconds = json.getLong("elapsed"),
                penaltyPercent = penaltyPercent,
            )
        }.getOrNull()

        fun fromPuzzle(p: ShikakuPuzzle) = ShikakuState(
            difficulty = p.difficulty,
            n = p.n,
            numbers = p.numbers.toList(),
            solution = p.solution,
        )

        fun encode(rects: List<Rect>) = rects.joinToString(",") { it.code.toString() }

        fun decode(s: String): List<Rect> = if (s.isEmpty()) emptyList() else s.split(",").map { Rect.fromCode(it.toInt()) }
    }
}

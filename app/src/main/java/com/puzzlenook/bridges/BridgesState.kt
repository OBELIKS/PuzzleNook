package com.puzzlenook.bridges

import com.puzzlenook.settings.HelpRules
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState
import org.json.JSONObject

/**
 * A Bridges game in progress. [bridges] is the count (0..2) you set on every slot of [layout];
 * [fixed] are the slots a hint revealed (right, locked). A slot with more bridges than the solution
 * has is a mistake, charged when it happens (or when errors are shown again, if they were hidden).
 */
data class BridgesState(
    val difficulty: Difficulty,
    val n: Int,
    /** Bridge count on each island cell, 0 elsewhere. */
    val numbers: List<Int>,
    /** The right count of every slot. */
    val solution: List<Int>,
    val bridges: List<Int> = List(solution.size) { 0 },
    val fixed: Set<Int> = emptySet(),
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    /** The slot a hint points at; the next hint press reveals it. */
    val hintSlot: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: HelpSettings = HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    /** False in "hide errors" mode: wrong bridges look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong slots (slot → 1) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    val layout: BridgeLayout by lazy(LazyThreadSafetyMode.NONE) { BridgeLayout(n, numbers.toIntArray()) }

    fun isWrong(s: Int) = bridges[s] > solution[s]

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(s: Int) = showWrong && isWrong(s)

    /** Bridges you have drawn on island [i]. */
    fun countAt(i: Int) = layout.slotsAt[i].sumOf { bridges[it] }

    fun islandNumber(i: Int) = numbers[layout.islands[i]]

    /** The island has all its bridges: looks at your bridges only. */
    fun islandDone(i: Int) = countAt(i) == islandNumber(i)

    /** More bridges than the number: shown only while errors are on. */
    fun islandOver(i: Int) = showWrong && countAt(i) > islandNumber(i)

    /** Hides or shows errors; showing charges the wrong bridges drawn while hidden. */
    fun withShowWrong(show: Boolean): BridgesState {
        if (show == showWrong || completed) return this
        val wrong = bridges.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { 1 }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { 1 })
        }
    }

    val isSolved: Boolean get() = bridges == solution

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    val activeHintSlot: Int? get() = hintSlot?.takeIf { !completed && bridges[it] != solution[it] }

    /** Whether slot [s] may be changed: not revealed by a hint, and a new bridge must not cross one. */
    fun canCycle(s: Int): Boolean {
        if (completed || s !in bridges.indices || s in fixed) return false
        if (bridges[s] == 0 && layout.crossing[s].any { bridges[it] > 0 }) return false
        return true
    }

    /** 0 → 1 → 2 → 0 bridges on slot [s]. */
    fun cycle(s: Int): BridgesState {
        if (!canCycle(s)) return this
        val next = (bridges[s] + 1) % 3
        val newWrong = next > solution[s] && !isWrong(s)
        return copy(
            bridges = bridges.mapIndexed { i, v -> if (i == s) next else v },
            mistakes = mistakes + if (newWrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
        ).checkSolved()
    }

    /** Back to an earlier set of bridges; hint-revealed ones stay. */
    fun restore(previous: List<Int>): BridgesState =
        copy(bridges = previous.mapIndexed { i, v -> if (i in fixed) solution[i] else v })

    fun hint(): BridgesState {
        if (completed) return this
        activeHintSlot?.let { s ->
            val cross = layout.crossing[s].toSet()
            return copy(
                bridges = bridges.mapIndexed { i, v -> if (i == s) solution[s] else if (i in cross && i !in fixed) 0 else v },
                fixed = fixed + s,
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintSlot = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintSlot() ?: return this
        return copy(
            hintSlot = target, hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /**
     * A wrong slot first; else the open slot the logic settles soonest from your right bridges
     * (a slot that shows a bridge before one that is empty).
     */
    fun pickHintSlot(): Int? {
        bridges.indices.firstOrNull { isWrong(it) }?.let { return it }
        val right = HashMap<Int, Int>()
        for (s in bridges.indices) if (bridges[s] > 0 && bridges[s] == solution[s]) right[s] = bridges[s]
        for (s in fixed) right[s] = solution[s]
        val result = BridgesLogic.forLevel(difficulty, layout).run(right)
        val open = bridges.indices.filter { bridges[it] != solution[it] }
        val soonest = open.map { result.round[it] }.filter { it >= 0 }.minOrNull() ?: return open.firstOrNull()
        val atRound = open.filter { result.round[it] == soonest }
        return atRound.firstOrNull { solution[it] > 0 } ?: atRound.first()
    }

    private fun checkSolved(): BridgesState =
        if (isSolved) copy(completed = true, hintSlot = null, bridges = solution) else this

    /** The saved-game JSON. */
    fun toJson(): JSONObject = JSONObject()
        .put("difficulty", difficulty.name)
        .put("size", n)
        .put("numbers", numbers.joinToString(","))
        .put("solution", solution.joinToString(","))
        .put("bridges", bridges.joinToString(","))
        .put("fixed", fixed.sorted().joinToString(","))
        .put("mistakes", mistakes)
        .put("showWrong", showWrong)
        .put("errorsSeen", errorsSeen)
        .put("chargedWrong", AnswerCheck.encode(chargedWrong))
        .put("hintHalfPoints", hintHalfPoints)
        .put("hintSlot", hintSlot ?: -1)
        .put("hintsUsed", hintsUsed)
        .put("lastHintAt", lastHintAt)
        .put("hintLimit", help.hintLimit)
        .put("risingCost", help.risingCost)
        .put("hintCooldown", help.cooldownSeconds)
        .put("elapsed", elapsedSeconds)

    companion object {
        private fun ints(s: String): List<Int> = if (s.isEmpty()) emptyList() else s.split(",").map(String::toInt)

        /** Reads a saved game; [h] fills help settings an old save lacks. Null if the JSON is unusable. */
        fun fromJson(json: JSONObject, h: HelpSettings, penaltyPercent: Int): BridgesState? = runCatching {
            val halfPoints = json.getInt("hintHalfPoints")
            val solution = ints(json.getString("solution"))
            val bridges = ints(json.getString("bridges"))
            require(bridges.size == solution.size)
            BridgesState(
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                n = json.getInt("size"),
                numbers = ints(json.getString("numbers")),
                solution = solution,
                bridges = bridges,
                fixed = ints(json.optString("fixed", "")).toSet(),
                mistakes = json.getInt("mistakes"),
                showWrong = json.optBoolean("showWrong", true),
                errorsSeen = json.optBoolean("errorsSeen", true),
                chargedWrong = AnswerCheck.decode(json.optString("chargedWrong", "")),
                hintHalfPoints = halfPoints,
                hintSlot = json.optInt("hintSlot", -1).takeIf { it >= 0 },
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

        fun fromPuzzle(p: BridgesPuzzle) = BridgesState(
            difficulty = p.difficulty,
            n = p.n,
            numbers = p.numbers.toList(),
            solution = p.solution.toList(),
        )
    }
}

package com.puzzlenook.settings

/** Limits on hints: per-game count (-1 = unlimited), rising cost, and a wait between hints. */
data class HelpSettings(
    val hintLimit: Int = -1,
    val risingCost: Boolean = false,
    val cooldownSeconds: Int = 0,
)

/**
 * A hint has two steps: [frame] (limit and wait apply) and [revealCost] (never blocked once
 * framed). A game's [HelpSettings] are fixed when it starts and saved with it; Settings changes
 * apply to the next game.
 */
object HelpRules {
    /** Half points charged for one step (frame or reveal) of the k-th hint. */
    fun stepCost(hintsUsedAfterFirstStep: Int, rising: Boolean): Int =
        if (rising) maxOf(1, hintsUsedAfterFirstStep) else 1

    /** Seconds of playing time until a new hint is allowed; 0 when free. */
    fun cooldownLeft(elapsed: Long, lastHintAt: Long, cooldown: Int): Long {
        if (cooldown <= 0 || lastHintAt < 0) return 0
        return maxOf(0L, lastHintAt + cooldown - elapsed)
    }

    fun canStartHint(hintsUsed: Int, elapsed: Long, lastHintAt: Long, s: HelpSettings): Boolean {
        if (s.hintLimit >= 0 && hintsUsed >= s.hintLimit) return false
        return cooldownLeft(elapsed, lastHintAt, s.cooldownSeconds) == 0L
    }

    /** Hints still available, or null when unlimited. */
    fun hintsLeft(hintsUsed: Int, s: HelpSettings): Int? =
        if (s.hintLimit < 0) null else maxOf(0, s.hintLimit - hintsUsed)

    /** Frames a new hint, or null when the limit or the wait blocks it. */
    fun frame(hintsUsed: Int, elapsed: Long, lastHintAt: Long, s: HelpSettings): HintFrame? =
        if (!canStartHint(hintsUsed, elapsed, lastHintAt, s)) null
        else HintFrame(hintsUsed + 1, elapsed, stepCost(hintsUsed + 1, s.risingCost))

    /** Half points for revealing the framed hint (the hintsUsed-th hint). */
    fun revealCost(hintsUsed: Int, s: HelpSettings): Int = stepCost(hintsUsed, s.risingCost)

    /** Before 0.12 a full hint cost 2 half points and no count was stored. */
    fun hintsUsedFromOldSave(hintHalfPoints: Int): Int = (hintHalfPoints + 1) / 2
}

/** Counters after framing a new hint, and the half points it costs. */
data class HintFrame(val hintsUsed: Int, val lastHintAt: Long, val costHalfPoints: Int)

package com.puzzlenook.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpRulesTest {
    @Test
    fun frameAllowedGivesCountersAndCost() {
        val f = HelpRules.frame(2, 90, 10, HelpSettings(risingCost = true))
        assertEquals(HintFrame(3, 90, 3), f)
        assertEquals(HintFrame(1, 5, 1), HelpRules.frame(0, 5, -1, HelpSettings()))
    }

    @Test
    fun frameBlockedByLimitOrCooldown() {
        assertNull(HelpRules.frame(1, 100, -1, HelpSettings(hintLimit = 1)))
        assertNull(HelpRules.frame(1, 40, 20, HelpSettings(cooldownSeconds = 30)))
    }

    @Test
    fun revealCostRisingOnOff() {
        assertEquals(3, HelpRules.revealCost(3, HelpSettings(risingCost = true)))
        assertEquals(1, HelpRules.revealCost(3, HelpSettings(risingCost = false)))
    }

    @Test
    fun hintsUsedFromOldSaveRoundsUp() {
        assertEquals(0, HelpRules.hintsUsedFromOldSave(0))
        assertEquals(1, HelpRules.hintsUsedFromOldSave(1))
        assertEquals(1, HelpRules.hintsUsedFromOldSave(2))
        assertEquals(2, HelpRules.hintsUsedFromOldSave(3))
        assertEquals(3, HelpRules.hintsUsedFromOldSave(6))
    }

    @Test
    fun stepCostFlatOrRising() {
        assertEquals(1, HelpRules.stepCost(1, false))
        assertEquals(1, HelpRules.stepCost(5, false))
        assertEquals(1, HelpRules.stepCost(1, true))
        assertEquals(2, HelpRules.stepCost(2, true))
        assertEquals(3, HelpRules.stepCost(3, true))
    }

    @Test
    fun cooldownCountsDownInPlayingTime() {
        assertEquals(0L, HelpRules.cooldownLeft(100, -1, 30))
        assertEquals(0L, HelpRules.cooldownLeft(100, 50, 0))
        assertEquals(30L, HelpRules.cooldownLeft(50, 50, 30))
        assertEquals(10L, HelpRules.cooldownLeft(70, 50, 30))
        assertEquals(0L, HelpRules.cooldownLeft(80, 50, 30))
        assertEquals(0L, HelpRules.cooldownLeft(200, 50, 30))
    }

    @Test
    fun unlimitedAndLimitedHints() {
        val unlimited = HelpSettings()
        assertNull(HelpRules.hintsLeft(7, unlimited))
        assertTrue(HelpRules.canStartHint(7, 0, -1, unlimited))
        val one = HelpSettings(hintLimit = 1)
        assertEquals(1, HelpRules.hintsLeft(0, one))
        assertEquals(0, HelpRules.hintsLeft(1, one))
        assertEquals(0, HelpRules.hintsLeft(4, one))
        assertTrue(HelpRules.canStartHint(0, 0, -1, one))
        assertFalse(HelpRules.canStartHint(1, 0, -1, one))
        assertFalse(HelpRules.canStartHint(0, 0, -1, HelpSettings(hintLimit = 0)))
    }

    @Test
    fun cooldownBlocksNewHintOnly() {
        val s = HelpSettings(cooldownSeconds = 30)
        assertTrue(HelpRules.canStartHint(0, 5, -1, s))
        assertFalse(HelpRules.canStartHint(1, 40, 20, s))
        assertTrue(HelpRules.canStartHint(1, 50, 20, s))
    }
}

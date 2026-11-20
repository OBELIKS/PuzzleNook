package com.puzzlenook.ui

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.WinRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    private fun win(play: Long, half: Int, d: Difficulty = Difficulty.EASY) =
        WinRecord(Variant.CLASSIC, d, play, half, half, 0, 0)

    @Test
    fun timesFollowTheChosenPenalty() {
        val w = win(600, 3) // 1.5 points
        assertEquals(600, w.seconds(0))
        assertEquals(645, w.seconds(5)) // +7.5%
        assertEquals(690, w.seconds(10)) // +15%
    }

    @Test
    fun bestAndAverageChangeWithPenalty() {
        // A slow clean game and a fast game with many mistakes swap places.
        val records = listOf(win(500, 0), win(400, 10))
        val none = Stats.summarize(records, 0, Difficulty.EASY, wins = 2, started = 3, storedBest = 500)
        assertEquals(400L, none.best)
        assertEquals(450L, none.average)
        assertEquals(3, none.started)
        val heavy = Stats.summarize(records, 10, Difficulty.EASY, wins = 2, started = 3, storedBest = 500)
        assertEquals(500L, heavy.best) // 400 + 50% = 600
        assertNull(heavy.oldBest)
    }

    @Test
    fun olderWinsWithoutRecordsKeepTheirSavedBest() {
        val s = Stats.summarize(emptyList(), 10, Difficulty.HARD, wins = 4, started = 0, storedBest = 321)
        assertEquals(4, s.solved)
        assertEquals(4, s.started)
        assertNull(s.best)
        assertEquals(321L, s.oldBest)
    }

    @Test
    fun cleanWinsHaveNoHintPoints() {
        val clean = WinRecord(Variant.CLASSIC, Difficulty.EASY, 500, 2, 4, 0, 0) // mistakes only
        val hinted = WinRecord(Variant.CLASSIC, Difficulty.EASY, 300, 2, 0, 2, 0)
        val s = Stats.summarize(listOf(clean, hinted), 10, Difficulty.EASY, wins = 2, started = 2, storedBest = null)
        assertEquals(1, s.clean)
        assertEquals(clean.seconds(10), s.bestClean)
        val none = Stats.summarize(listOf(hinted), 10, Difficulty.EASY, wins = 1, started = 1, storedBest = null)
        assertEquals(0, none.clean)
        assertNull(none.bestClean)
    }

    @Test
    fun unaidedWinsAreCountedSeparately() {
        val a = WinRecord(Variant.CLASSIC, Difficulty.EASY, 500, 0, 0, 0, 0, unaided = true)
        val b = WinRecord(Variant.CLASSIC, Difficulty.EASY, 300, 2, 4, 0, 0) // clean, errors were on
        val s = Stats.summarize(listOf(a, b), 10, Difficulty.EASY, wins = 2, started = 2, storedBest = null)
        assertEquals(2, s.clean)
        assertEquals(1, s.unaided)
        assertEquals(500L, s.bestUnaided)
        assertEquals(0, Stats.summarize(listOf(b), 10, Difficulty.EASY, 1, 1, null).unaided)
    }

    @Test
    fun winRecordWithoutUnaidedKeyIsNotUnaided() {
        val old = org.json.JSONObject().put("d", "EASY").put("play", 100).put("half", 0).put("at", 5)
        assertEquals(false, WinRecord.fromJson(Variant.CLASSIC, old).unaided)
        val rt = WinRecord(Variant.CLASSIC, Difficulty.EASY, 100, 0, 0, 0, 5, true)
        assertEquals(true, WinRecord.fromJson(Variant.CLASSIC, rt.toJson()).unaided)
    }

    @Test
    fun oldWinWithoutHintDataIsNotClean() {
        val base = { org.json.JSONObject().put("d", "EASY").put("play", 100).put("half", 0).put("at", 5) }
        val old = WinRecord.fromJson(Variant.CLASSIC, base())
        assertEquals(false, old.clean)
        assertEquals(0, Stats.summarize(listOf(old), 0, Difficulty.EASY, 1, 1, null).clean)
        val recorded = WinRecord.fromJson(Variant.CLASSIC, base().put("hints", 0))
        assertEquals(true, recorded.clean)
        assertEquals(1, Stats.summarize(listOf(recorded), 0, Difficulty.EASY, 1, 1, null).clean)
    }

    @Test
    fun counterSurvivesHistoryTrim() {
        val a = WinRecord(Variant.CLASSIC, Difficulty.EASY, 500, 0, 0, 0, 0, unaided = true)
        // Counters larger than the history win.
        val big = Stats.summarize(listOf(a), 0, Difficulty.EASY, 9, 9, null, cleanWins = 7, unaidedWins = 5)
        assertEquals(7, big.clean)
        assertEquals(5, big.unaided)
        // Larger history (old wins, counters 0) wins.
        val hist = Stats.summarize(listOf(a, a), 0, Difficulty.EASY, 2, 2, null)
        assertEquals(2, hist.clean)
        assertEquals(2, hist.unaided)
    }
}

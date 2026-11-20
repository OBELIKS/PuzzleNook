package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.region.ShikakuGenerator
import com.puzzlenook.region.ShikakuLogic
import com.puzzlenook.region.ShikakuState
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Batch 5b: a game whose level can't settle anything more from the player's marks (an old save made
 * with a stronger level, or a puzzle one level harder than its state says) still gets a hint, from the
 * next level up, and the hint is a cell/clue the player still has to place.
 */
class HintFallback5bTest {
    /** A Hard puzzle opened at the Medium level: level 1 stalls on it, level 2 (the fallback) goes on. */
    private fun shadeFallback(v: Variant) {
        var checked = 0
        for (seed in 7..11) {
            val p = ShadeCheck.generate(v, Difficulty.HARD, seed)
            val n = p.size
            val start = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
            val medium = ShadeState.fromPuzzle(p).copy(difficulty = Difficulty.MEDIUM)
            val stuck = medium.solveFrom(start)
            if (stuck.solved) continue
            checked++
            var s = medium
            for (i in 0 until n * n) if (!s.fixed[i] && stuck.state[i] != UNKNOWN) s = s.set(i, if (stuck.state[i] == SHADED) Mark.FILLED else Mark.CROSSED)
            assertFalse(s.completed)
            val hint = s.pickHintCell()
            assertNotNull("$v seed $seed hint", hint)
            assertTrue("$v seed $seed hint on an open cell", s.marks[hint!!] == Mark.NONE && !s.fixed[hint])
            assertTrue("$v seed $seed hint was unresolved at the old level", stuck.state[hint] == UNKNOWN)
        }
        assertTrue("$v: at least one stalled puzzle", checked >= 1)
    }

    @Test(timeout = 300_000) fun lightUpFallsBackToNextLevel() = shadeFallback(Variant.LIGHT_UP)

    @Test(timeout = 300_000) fun norinoriFallsBackToNextLevel() = shadeFallback(Variant.NORINORI)

    @Test(timeout = 300_000) fun shikakuFallsBackToNextLevel() {
        var checked = 0
        for (seed in 7..11) {
            val p = ShikakuGenerator.generate(Difficulty.HARD, Random(seed))
            val stuck = ShikakuLogic.forLevel(Difficulty.MEDIUM, p.n, p.numbers).run()
            if (stuck.solved) continue
            checked++
            var s = ShikakuState.fromPuzzle(p).copy(difficulty = Difficulty.MEDIUM)
            for (r in stuck.settled.values) s = s.place(r)
            val hint = s.pickHintClue()
            assertNotNull("seed $seed hint", hint)
            assertTrue("seed $seed hint on a clue not yet settled", hint!! !in stuck.settled)
        }
        assertTrue("at least one stalled puzzle", checked >= 1)
    }
}

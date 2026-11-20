package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import kotlin.random.Random
import org.junit.Test

class NurikabeGenTest {
    @Test(timeout = 300_000) fun nurikabe() = ShadeCheck.check(Variant.NURIKABE, 5, emptySet())

    /** Brute force can't cover 6×6 nurikabe; check the logic on small 5×5 ones instead. */
    @Test(timeout = 300_000)
    fun nurikabeSmallUnique() {
        repeat(6) { seed ->
            val p = NurikabeGenerator.generate(Difficulty.EASY, Random(seed), n = 5)
            assertEquals("seed $seed", 1, Rules.countSolutions(p, 2))
        }
    }
}

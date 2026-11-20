package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Test

class HitoriGenTest {
    @Test(timeout = 300_000) fun hitori() = ShadeCheck.check(Variant.HITORI, 5, setOf(Difficulty.EASY))
}

package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Test

class MosaicGenTest {
    @Test(timeout = 300_000) fun mosaic() = ShadeCheck.check(Variant.MOSAIC, 5, setOf(Difficulty.EASY))
}

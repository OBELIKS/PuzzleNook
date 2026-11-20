package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Test

class TakuzuGenTest {
    @Test(timeout = 300_000) fun takuzu() = ShadeCheck.check(Variant.TAKUZU, 5, setOf(Difficulty.EASY))
}

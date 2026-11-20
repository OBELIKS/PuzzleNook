package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Test

class StarBattleGenTest {
    @Test(timeout = 300_000) fun starBattle() = ShadeCheck.check(Variant.STAR_BATTLE, 5, setOf(Difficulty.EASY, Difficulty.MEDIUM))
}

package com.puzzlenook.ui

import com.puzzlenook.R
import com.puzzlenook.sudoku.Variant
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class RuleTextTest {
    @Test
    fun everyVariantHasARule() {
        for (v in Variant.entries) for (size in listOf(9, 5)) {
            assertTrue("$v $size", ruleText(v, size).id != 0)
        }
    }

    @Test
    fun usesSizeAndStars() {
        // Futoshiki: "1 to %1$d" with the size 5 as its argument.
        val futoshiki = ruleText(Variant.FUTOSHIKI, 5)
        assertEquals(R.string.rule_futoshiki, futoshiki.id)
        assertEquals(listOf<Any>(5), futoshiki.args)
        // Star Battle: the plural resource with quantity 2 ("2 stars").
        val stars = ruleText(Variant.STAR_BATTLE, 10, stars = 2)
        assertEquals(R.plurals.rule_star_battle, stars.id)
        assertEquals(2, stars.quantity)
        assertEquals(listOf<Any>(2), stars.args)
    }
}

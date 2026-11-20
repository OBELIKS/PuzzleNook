package com.puzzlenook.hidato

import org.junit.Test
import org.junit.Assert.assertEquals

class FormatMissingTest {
    @Test
    fun formats() {
        assertEquals("", formatMissing(emptyList()))
        assertEquals("5", formatMissing(listOf(5)))
        assertEquals("1, 2", formatMissing(listOf(1, 2)))
        assertEquals("1–3", formatMissing(listOf(1, 2, 3)))
        assertEquals("1–4, 7, 9, 10, 12–14", formatMissing(listOf(1, 2, 3, 4, 7, 9, 10, 12, 13, 14)))
        assertEquals("1–4, 7, 9, 10, 12–14", formatMissing(listOf(14, 3, 1, 9, 13, 2, 12, 10, 4, 7)))
    }
}

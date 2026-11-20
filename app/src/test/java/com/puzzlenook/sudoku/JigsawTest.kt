package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JigsawTest {
    private fun assertValidRegions(regions: IntArray) {
        assertEquals(81, regions.size)
        val cells = Regions.cells(regions)
        assertEquals(9, cells.size)
        for (id in 0 until 9) {
            assertEquals("region $id size", 9, cells[id].size)
            assertTrue("region $id connected", Regions.connected(9, regions, id))
        }
    }

    @Test fun randomRegionsAreValidAndNotJustBoxes() {
        var different = 0
        for (seed in 0 until 20) {
            val regions = Regions.randomJigsaw(Random(seed))
            assertValidRegions(regions)
            if ((0 until 81).any { regions[it] != SudokuSolver.box(it) }) different++
        }
        assertTrue("most shapes differ from the boxes", different >= 15)
    }

    @Test fun puzzlesAreValidAndUnique() {
        for (d in Difficulty.entries) for (seed in 0 until 3) {
            val start = System.currentTimeMillis()
            val p = JigsawGenerator.generate(d, Random(seed + 7))
            val ms = System.currentTimeMillis() - start
            println("JIGSAW ${d.name} seed $seed: clues ${p.clueCount}, $ms ms")
            val regions = p.regions!!
            assertValidRegions(regions)
            val units = Regions.rows(9) + Regions.cols(9) + Regions.cells(regions)
            val solver = GridSolver(81, IntArray(81) { it }, units, emptyList())
            assertEquals("${d.name} seed $seed unique", 1, solver.countSolutions(p.givens))
            for (i in 0 until 81) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
        }
    }
}

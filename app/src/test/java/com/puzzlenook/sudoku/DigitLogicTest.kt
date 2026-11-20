package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DigitLogicTest {
    private fun logicFor(v: Variant, p: Puzzle) = when (v) {
        Variant.CLASSIC -> DigitLogic.sudoku()
        Variant.KILLER -> DigitLogic.killer(p.cages)
        Variant.KAKURO -> DigitLogic.kakuro(p.size, p.blocked!!, p.cages)
        Variant.JIGSAW -> DigitLogic.jigsaw(p.regions!!)
        else -> error("$v")
    }

    private fun generate(v: Variant, d: Difficulty, seed: Int) = when (v) {
        Variant.CLASSIC -> SudokuGenerator.generate(d, Random(seed))
        Variant.KILLER -> KillerGenerator.generate(d, Random(seed))
        Variant.KAKURO -> KakuroGenerator.generate(d, Random(seed))
        Variant.JIGSAW -> JigsawGenerator.generate(d, Random(seed))
        else -> error("$v")
    }

    private fun check(v: Variant, seeds: Int) {
        var failures = 0
        for (d in Difficulty.entries) {
            val report = (0 until seeds).map { seed ->
                val start = System.currentTimeMillis()
                val p = generate(v, d, seed + 100)
                val ms = System.currentTimeMillis() - start
                val result = logicFor(v, p).run(p.givens)
                // Whatever the logic pins down must match the answer.
                for (i in p.givens.indices) {
                    if (Integer.bitCount(result.candidates[i]) == 1) {
                        assertEquals(1 shl p.solution[i], result.candidates[i])
                    }
                }
                Triple(result.unresolved.count { p.blocked?.get(it) != true }, p.clueCount, ms)
            }
            println("LOGIC $v ${d.name}: (stuck cells, givens, ms) $report")
            failures += report.count { it.first != 0 }
        }
        assertEquals("$v puzzles the logic can't finish", 0, failures)
    }

    @Test fun sudoku() = check(Variant.CLASSIC, 6)
    @Test fun killer() = check(Variant.KILLER, 4)
    @Test fun kakuro() = check(Variant.KAKURO, 4)
    @Test fun jigsaw() = check(Variant.JIGSAW, 4)
}

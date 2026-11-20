package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.random.Random

/**
 * Every generated puzzle must be solvable by the step-by-step logic, the logic must land on
 * the stored solution, and an independent brute-force search must find no other solution.
 */
internal object ShadeCheck {
    fun generate(v: Variant, d: Difficulty, seed: Int): ShadePuzzle = when (v) {
        Variant.STAR_BATTLE -> StarBattleGenerator.generate(d, Random(seed))
        Variant.HITORI -> HitoriGenerator.generate(d, Random(seed))
        Variant.NURIKABE -> NurikabeGenerator.generate(d, Random(seed))
        Variant.TAKUZU -> TakuzuGenerator.generate(d, Random(seed))
        Variant.MOSAIC -> MosaicGenerator.generate(d, Random(seed))
        Variant.LIGHT_UP -> LightUpGenerator.generate(d, Random(seed))
        Variant.TENTS -> TentsGenerator.generate(d, Random(seed))
        Variant.KURODOKO -> KurodokoGenerator.generate(d, Random(seed))
        Variant.NORINORI -> NorinoriGenerator.generate(d, Random(seed))
        Variant.BATTLESHIPS -> BattleshipsGenerator.generate(d, Random(seed))
        Variant.AQUARIUM -> AquariumGenerator.generate(d, Random(seed))
        Variant.HEYAWAKE -> HeyawakeGenerator.generate(d, Random(seed))
        Variant.LITS -> LitsGenerator.generate(d, Random(seed))
        else -> error("$v")
    }

    fun logic(p: ShadePuzzle, start: IntArray) = when (p.variant) {
        // Each level must be solvable with only the steps that level allows.
        Variant.STAR_BATTLE -> StarBattleGenerator.logicFor(p.difficulty, p.size, p.stars, p.regions!!).run(start)
        Variant.HITORI -> HitoriLogic(p.size, p.numbers).run(start)
        Variant.NURIKABE -> NurikabeLogic(p.size, p.numbers).run(start)
        Variant.TAKUZU -> TakuzuLogic.forLevel(p.difficulty, p.size).run(start)
        Variant.MOSAIC -> MosaicLogic.forLevel(p.difficulty, p.size, p.numbers).run(start)
        Variant.LIGHT_UP -> LightUpLogic.forLevel(p.difficulty, p.size, p.numbers).run(start)
        Variant.TENTS -> TentsLogic.forLevel(p.difficulty, p.size, p.numbers, p.counts!!).run(start)
        Variant.KURODOKO -> KurodokoLogic.forLevel(p.difficulty, p.size, p.numbers).run(start)
        Variant.NORINORI -> NorinoriLogic.forLevel(p.difficulty, p.size, p.regions!!).run(start)
        Variant.BATTLESHIPS -> BattleshipsLogic.forLevel(p.difficulty, p.size, p.numbers, p.counts!!).run(start)
        Variant.AQUARIUM -> AquariumLogic.forLevel(p.difficulty, p.size, p.regions!!, p.counts!!).run(start)
        Variant.HEYAWAKE -> HeyawakeLogic.forLevel(p.difficulty, p.size, p.regions!!, p.numbers).run(start)
        Variant.LITS -> LitsLogic.forLevel(p.difficulty, p.size, p.regions!!).run(start)
        else -> error("${p.variant}")
    }

    fun check(v: Variant, seeds: Int, bruteForce: Set<Difficulty>) {
        for (d in Difficulty.entries) {
            val report = (0 until seeds).map { seed ->
                val start = System.currentTimeMillis()
                val p = generate(v, d, seed + 7)
                val ms = System.currentTimeMillis() - start
                val n = p.size
                assertTrue("$v $d valid", Rules.valid(p, p.solution))
                if (v == Variant.MOSAIC) assertTrue("$v $d seed $seed has fewer clues than cells", p.numbers.count { it >= 0 } < n * n)
                val given = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                val result = logic(p, given)
                assertTrue("$v $d seed $seed solvable by logic", result.solved)
                for (i in 0 until n * n) assertEquals("$v $d cell $i", if (p.solution[i]) SHADED else WHITE, result.state[i])
                if (d in bruteForce) assertEquals("$v $d seed $seed unique", 1, Rules.countSolutions(p, 2))
                "${ms}ms/${p.givens.count { it }}g" + when (v) {
                    Variant.MOSAIC -> "/${p.numbers.count { it >= 0 }}c"
                    Variant.LIGHT_UP -> "/${p.numbers.count { it in 0..4 }}c of ${p.numbers.count { it >= 0 }}w"
                    Variant.TENTS, Variant.AQUARIUM -> "/${p.counts!!.count { it >= 0 }}c of ${2 * n}"
                    Variant.BATTLESHIPS -> "/${p.numbers.count { it >= 0 }}shown"
                    Variant.HEYAWAKE -> "/${p.numbers.count { it >= 0 }}nums of ${p.regions!!.max() + 1}rooms"
                    Variant.KURODOKO -> "/${p.numbers.count { it > 0 }}c of ${n * n - p.solution.count { it }}w"
                    else -> ""
                }
            }
            println("SHADE $v ${d.name} ${generate(v, d, 0).size}: $report")
        }
    }
}

package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LatinTest {
    private fun check(variant: Variant) {
        for (d in Difficulty.entries) {
            repeat(3) { seed ->
                val start = System.currentTimeMillis()
                val p = LatinGenerator.generate(variant, d, Random(seed * 31 + d.ordinal))
                val ms = System.currentTimeMillis() - start
                val n = p.size
                val rules = p.rules!!
                // Solution is a Latin square that meets every rule.
                for (u in rules.units) assertEquals((1..n).toSet(), u.map { p.solution[it] }.toSet())
                for (l in rules.less) assertTrue(p.solution[l[0]] < p.solution[l[1]])
                for (c in rules.cages) assertEquals(c.target, LatinRules.cageValue(c.op, c.cells.map { p.solution[it] }))
                rules.sky?.forEachIndexed { k, clue ->
                    if (clue > 0) assertEquals(clue, LatinRules.visible(p.solution, rules.lines[k]))
                }
                if (rules.kropkiAll) {
                    val dots = HashMap<Pair<Int, Int>, Char>()
                    rules.white.forEach { dots[it[0] to it[1]] = 'w' }
                    rules.black.forEach { dots[it[0] to it[1]] = 'b' }
                    for (i in 0 until n * n) for (j in listOfNotNull((i + 1).takeIf { i % n < n - 1 }, (i + n).takeIf { it < n * n })) {
                        val a = p.solution[i]
                        val b = p.solution[j]
                        val expected = if (Math.abs(a - b) == 1) 'w' else if (a == 2 * b || b == 2 * a) 'b' else null
                        assertEquals("$variant $d dot $i-$j", expected, dots[i to j])
                    }
                }
                for (i in p.givens.indices) assertTrue(p.givens[i] == 0 || p.givens[i] == p.solution[i])
                val count = LatinSolver(rules).countSolutions(p.givens, 2, 2_000_000)
                assertEquals("$variant $d unique", 1, count)
                assertTrue("$variant $d solvable by logic", LatinLogic.solves(rules, p.givens))
                println(
                    "$variant ${d.name} ${n}x$n: ${ms}ms givens=${p.clueCount} signs=${rules.less.size} " +
                        "cages=${rules.cages.size} singles=${rules.cages.count { it.cells.size == 1 }} " +
                        "sky=${rules.sky?.count { it > 0 }}",
                )
            }
        }
    }

    @Test fun futoshiki() = check(Variant.FUTOSHIKI)
    @Test fun calcudoku() = check(Variant.CALCUDOKU)
    @Test(timeout = 300_000) fun kropki() = check(Variant.KROPKI)

    @Test
    fun kropkiSavesAndLoadsRules() {
        val p = LatinGenerator.generate(Variant.KROPKI, Difficulty.EASY, Random(5))
        val r = p.rules!!
        val copy = LatinRules(r.n, white = r.white, black = r.black, kropkiAll = r.kropkiAll)
        assertEquals(r.dotPairs.size, copy.dotPairs.size)
        assertTrue(r.kropkiAll && r.white.isNotEmpty())
        // Hints and the logic solver work through the dots.
        var s = GameState.fromPuzzle(p, Variant.KROPKI)
        var steps = 0
        while (!s.completed && steps++ < 200) s = s.hint()
        assertTrue(s.completed)
    }

    @Test
    fun calcudokuCagesCoverEveryCell() {
        for (seed in 0 until 300) {
            val d = Difficulty.entries[seed % 4]
            val p = CalcudokuGenerator.generate(d, Random(seed), budgetMs = 200)
            val covered = p.rules!!.cages.flatMap { it.cells }.sorted()
            assertEquals(List(p.size * p.size) { it }, covered)
        }
    }
    @Test fun skyscrapers() = check(Variant.SKYSCRAPERS)

    @Test
    fun hintsWorkOnLatinPuzzles() {
        val p = LatinGenerator.generate(Variant.FUTOSHIKI, Difficulty.EASY, Random(1))
        var s = GameState.fromPuzzle(p, Variant.FUTOSHIKI)
        var steps = 0
        while (!s.completed && steps++ < 200) s = s.hint()
        assertTrue(s.completed)
        // Digits above n are ignored.
        val g = GameState.fromPuzzle(p, Variant.FUTOSHIKI).copy(selected = p.givens.indexOfFirst { it == 0 })
        assertEquals(g, g.enter(9))
    }
}

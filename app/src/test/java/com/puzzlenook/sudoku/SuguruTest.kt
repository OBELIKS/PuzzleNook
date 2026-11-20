package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SuguruTest {
    private fun assertValid(p: Puzzle) {
        val n = p.size
        val regions = p.regions!!
        val cells = Regions.cells(regions)
        for (id in cells.indices) {
            assertTrue("region $id size ${cells[id].size}", cells[id].size in 1..5)
            assertTrue("region $id connected", Regions.connected(n, regions, id))
            assertEquals("region $id digits", (1..cells[id].size).toList(), cells[id].map { p.solution[it] }.sorted())
        }
        for (i in 0 until n * n) for (j in i + 1 until n * n) {
            val touching = kotlin.math.abs(i / n - j / n) <= 1 && kotlin.math.abs(i % n - j % n) <= 1
            if (touching) assertTrue("cells $i,$j equal", p.solution[i] != p.solution[j])
        }
        for (i in 0 until n * n) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
    }

    /** Independent backtracker (not SuguruLogic): number of solutions, stopping at [limit]. */
    private fun countSolutions(p: Puzzle, limit: Int = 2): Int {
        val n = p.size
        val regions = p.regions!!
        val size = IntArray(n * n) { i -> regions.count { it == regions[i] } }
        val board = p.givens.copyOf()
        var count = 0
        fun ok(i: Int, d: Int): Boolean {
            if (d > size[i]) return false
            for (j in 0 until n * n) {
                if (j == i || board[j] != d) continue
                if (regions[j] == regions[i]) return false
                if (kotlin.math.abs(i / n - j / n) <= 1 && kotlin.math.abs(i % n - j % n) <= 1) return false
            }
            return true
        }
        fun rec(from: Int) {
            if (count >= limit) return
            var i = from
            while (i < n * n && board[i] != 0) i++
            if (i == n * n) {
                count++
                return
            }
            for (d in 1..size[i]) if (ok(i, d)) {
                board[i] = d
                rec(i + 1)
                board[i] = 0
            }
        }
        // Givens themselves must be consistent.
        for (i in 0 until n * n) if (board[i] != 0) {
            val d = board[i]
            board[i] = 0
            if (!ok(i, d)) return 0
            board[i] = d
        }
        rec(0)
        return count
    }

    @Test fun puzzlesAreValidAndSolvedByLogic() {
        val failures = mutableListOf<String>()
        for (d in Difficulty.entries) {
            var tops = 0
            for (seed in 0 until 5) {
                tops += checkSeed(d, seed)
            }
            // Expert only prefers needsTop within its 3 s budget (attempts cost ~0,4–1 s), so it isn't asserted.
            if (d == Difficulty.HARD && tops < 3) failures += "${d.name} needsTop on $tops of 5"
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }

    /** Returns 1 when the puzzle needs its level's top technique. */
    private fun checkSeed(d: Difficulty, seed: Int): Int {
        run {
            val start = System.currentTimeMillis()
            val p = SuguruGenerator.generate(d, Random(seed + 11))
            val ms = System.currentTimeMillis() - start
            val sizes = Regions.cells(p.regions!!).groupingBy { it.size }.eachCount().toSortedMap()
            val top = SuguruGenerator.needsTop(d, p.size, p.regions!!, p.givens)
            println("SUGURU ${d.name} seed $seed: ${p.size}x${p.size}, givens ${p.clueCount}, $ms ms, attempts ${SuguruGenerator.lastAttempts}, needsTop $top, region sizes $sizes")
            assertValid(p)
            val result = SuguruLogic.forLevel(d, p.size, p.regions!!).run(p.givens)
            assertTrue("${d.name} seed $seed solved by logic", result.solved)
            for (i in p.givens.indices) assertEquals(1 shl p.solution[i], result.candidates[i])
            return if (top) 1 else 0
        }
    }

    @Test fun easyAndMediumAreUnique() {
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) for (seed in 0 until 5) {
            val p = SuguruGenerator.generate(d, Random(seed + 11))
            assertEquals("${d.name} seed $seed unique", 1, countSolutions(p))
        }
    }

    @Test fun hardAndExpertAreUnique() {
        for (d in listOf(Difficulty.HARD, Difficulty.EXPERT)) for (seed in 0 until 2) {
            val p = SuguruGenerator.generate(d, Random(seed + 11))
            assertEquals("${d.name} seed $seed unique", 1, countSolutions(p))
        }
    }

    @Test fun peersIncludeDiagonalNeighbours() {
        val p = SuguruGenerator.generate(Difficulty.EASY, Random(3))
        val n = p.size
        var s = GameState.fromPuzzle(p, Variant.SUGURU)
        assertEquals(p.regions!!.toList(), s.regions)
        assertEquals(Regions.cells(p.regions!!).maxOf { it.size }, s.digits)
        // A cell and a diagonal neighbour in another region.
        var pair: Pair<Int, Int>? = null
        for (i in 0 until n * n) for (j in listOf(i + n + 1, i + n - 1)) {
            if (pair != null || j >= n * n || kotlin.math.abs(i % n - j % n) != 1) continue
            if (p.regions!![i] != p.regions!![j] && p.givens[i] == 0 && p.givens[j] == 0) pair = i to j
        }
        val (a, b) = pair ?: error("no empty diagonal pair")
        assertTrue(s.isPeer(a, b))
        assertTrue(s.isPeer(b, a))
        val d = p.solution[a]
        s = s.copy(cells = s.cells.mapIndexed { i, c -> if (i == b) c.copy(notes = 1 shl d) else c }, selected = a)
        s = s.enter(d)
        assertFalse("note cleared in diagonal neighbour", s.cells[b].hasNote(d))
    }
}

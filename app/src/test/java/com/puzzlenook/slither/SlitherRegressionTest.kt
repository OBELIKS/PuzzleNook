package com.puzzlenook.slither

import com.puzzlenook.shade.SHADED
import com.puzzlenook.shade.UNKNOWN
import com.puzzlenook.shade.WHITE
import com.puzzlenook.sudoku.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Pins `SlitherLogic.run` (state and round arrays) on 10 stored puzzles, from an empty board and
 * from a partly marked one, so refactors of the loop code cannot change Slitherlink's behaviour.
 * The fixture `slither_fixture.txt` is recorded once with the environment variable
 * `SLITHER_FIXTURE_WRITE=1`; puzzles are stored, never regenerated (generation depends on the clock).
 */
class SlitherRegressionTest {
    private fun enc(a: IntArray) = a.joinToString("") { when (it) { UNKNOWN -> "u"; WHITE -> "0"; else -> "1" } }
    private fun dec(s: String) = IntArray(s.length) { when (s[it]) { 'u' -> UNKNOWN; '0' -> WHITE; else -> SHADED } }

    private fun partialStart(solution: BooleanArray, seed: Int): IntArray {
        val random = Random(seed)
        val start = IntArray(solution.size) { UNKNOWN }
        solution.indices.filter { solution[it] }.shuffled(random).take(4).forEach { start[it] = SHADED }
        solution.indices.filter { !solution[it] }.shuffled(random).take(4).forEach { start[it] = WHITE }
        return start
    }

    private fun line(n: Int, clues: IntArray, solution: BooleanArray, partial: IntArray): String {
        val grid = SlitherGrid(n)
        val logic = SlitherLogic(grid, clues)
        val a = logic.run(IntArray(grid.edges) { UNKNOWN })
        val b = logic.run(partial)
        return listOf(
            n.toString(), clues.joinToString(","), solution.joinToString("") { if (it) "1" else "0" }, enc(partial),
            a.solved.toString(), enc(a.state), a.round.joinToString(","),
            b.solved.toString(), enc(b.state), b.round.joinToString(","),
        ).joinToString(";")
    }

    @Test(timeout = 300_000)
    fun recordFixtureWhenAsked() {
        if (System.getenv("SLITHER_FIXTURE_WRITE") != "1") return
        val levels = listOf(
            Difficulty.EASY to 5, Difficulty.EASY to 5, Difficulty.EASY to 6, Difficulty.MEDIUM to 6, Difficulty.MEDIUM to 6,
            Difficulty.HARD to 8, Difficulty.HARD to 8, Difficulty.HARD to 8, Difficulty.EXPERT to 10, Difficulty.EXPERT to 10,
        )
        val lines = levels.mapIndexed { k, (d, n) ->
            val p = SlitherGenerator.generate(d, Random(100 + k), n = n)
            line(n, p.clues, p.solution, partialStart(p.solution, k))
        }
        File("src/test/resources/slither_fixture.txt").writeText(lines.joinToString("\n") + "\n")
    }

    @Test(timeout = 300_000)
    fun logicResultsAreIdenticalToTheRecordedOnes() {
        val text = javaClass.classLoader!!.getResource("slither_fixture.txt")!!.readText()
        val rows = text.lines().filter { it.isNotBlank() }
        assertEquals(10, rows.size)
        for ((k, row) in rows.withIndex()) {
            val f = row.split(";")
            val n = f[0].toInt()
            val clues = f[1].split(",").map(String::toInt).toIntArray()
            val solution = BooleanArray(f[2].length) { f[2][it] == '1' }
            val partial = dec(f[3])
            val grid = SlitherGrid(n)
            val logic = SlitherLogic(grid, clues)
            val a = logic.run(IntArray(grid.edges) { UNKNOWN })
            assertTrue("puzzle $k solves", a.solved)
            assertEquals("puzzle $k solved", f[4].toBoolean(), a.solved)
            assertEquals("puzzle $k state", f[5], enc(a.state))
            assertEquals("puzzle $k round", f[6], a.round.joinToString(","))
            val b = logic.run(partial)
            assertEquals("puzzle $k partial solved", f[7].toBoolean(), b.solved)
            assertEquals("puzzle $k partial state", f[8], enc(b.state))
            assertEquals("puzzle $k partial round", f[9], b.round.joinToString(","))
            // The recorded solution is a single loop and agrees with the logic.
            assertTrue(grid.isSingleLoop(solution))
            for (e in 0 until grid.edges) assertEquals(solution[e], a.state[e] == SHADED)
        }
    }
}

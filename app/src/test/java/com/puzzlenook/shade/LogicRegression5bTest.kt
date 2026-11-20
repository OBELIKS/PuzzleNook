package com.puzzlenook.shade

import com.puzzlenook.region.ShikakuGenerator
import com.puzzlenook.region.ShikakuLogic
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Batch 5b regression: levels 0-2 of Light Up, Shikaku and Norinori must behave exactly as before the
 * level-3 change. The fixture (`src/test/resources/logic_regression_5b.txt`) holds 5 stored puzzles per
 * game and difficulty and the result (state + round) of levels 0-2 on each. It was recorded with the
 * old code (env `REGRESSION_RECORD=1`); the test compares against it. Round 2 made level 2 a bounded
 * what-if, so its expectations were re-recorded (env `REGRESSION_RERECORD_LEVEL2=1`); levels 0-1 never were.
 */
class LogicRegression5bTest {
    private val file = File("src/test/resources/logic_regression_5b.txt")

    private fun csv(a: IntArray) = a.joinToString(",")

    private fun shadeLine(game: String, level: Int, r: ShadeResult) = "R $game $level ${r.solved} ${csv(r.state)} ${csv(r.round)}"

    private fun lines(): List<String> {
        val out = ArrayList<String>()
        for (d in Difficulty.entries) for (seed in 7..11) {
            for (v in listOf(Variant.LIGHT_UP, Variant.NORINORI)) {
                val p = ShadeCheck.generate(v, d, seed)
                val n = p.size
                val start = IntArray(n * n) { if (p.givens[it]) (if (p.solution[it]) SHADED else WHITE) else UNKNOWN }
                val data = if (v == Variant.LIGHT_UP) p.numbers else p.regions!!
                val id = "${v.name}/${d.name}/$seed"
                out += "P $id $n ${csv(data)} ${csv(start)}"
                for (level in 0..2) {
                    val ld = Difficulty.entries[level]
                    val r = if (v == Variant.LIGHT_UP) LightUpLogic.forLevel(ld, n, data).run(start) else NorinoriLogic.forLevel(ld, n, data).run(start)
                    out += shadeLine(id, level, r)
                }
            }
            val sp = ShikakuGenerator.generate(d, Random(seed))
            val id = "SHIKAKU/${d.name}/$seed"
            out += "P $id ${sp.n} ${csv(sp.numbers)}"
            for (level in 0..2) {
                val r = ShikakuLogic.forLevel(Difficulty.entries[level], sp.n, sp.numbers).run()
                val settled = r.settled.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value.code}" }
                val round = r.round.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
                out += "R $id $level ${r.solved} $settled $round"
            }
        }
        return out
    }

    /** Re-runs levels 0-2 on the stored puzzles and returns the result lines. */
    private fun replay(stored: List<String>): List<String> {
        val out = ArrayList<String>()
        for (l in stored) {
            if (!l.startsWith("P ")) continue
            val t = l.split(" ")
            val id = t[1]
            val n = t[2].toInt()
            val data = t[3].split(",").map { it.toInt() }.toIntArray()
            for (level in 0..2) {
                val ld = Difficulty.entries[level]
                when {
                    id.startsWith("LIGHT_UP") -> out += shadeLine(id, level, LightUpLogic.forLevel(ld, n, data).run(t[4].split(",").map { it.toInt() }.toIntArray()))
                    id.startsWith("NORINORI") -> out += shadeLine(id, level, NorinoriLogic.forLevel(ld, n, data).run(t[4].split(",").map { it.toInt() }.toIntArray()))
                    else -> {
                        val r = ShikakuLogic.forLevel(ld, n, data).run()
                        val settled = r.settled.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value.code}" }
                        val round = r.round.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
                        out += "R $id $level ${r.solved} $settled $round"
                    }
                }
            }
        }
        return out
    }

    @Test(timeout = 300_000) fun levels0to2Unchanged() {
        if (System.getenv("REGRESSION_RECORD") == "1") {
            file.parentFile.mkdirs()
            file.writeText(lines().joinToString("\n") + "\n")
        }
        if (System.getenv("REGRESSION_RERECORD_LEVEL2") == "1") {
            // Round 2: level 2 became a bounded what-if; only its expectations are re-recorded (levels 0-1 stay).
            val old = file.readLines().filter { it.isNotBlank() }
            val fresh = replay(old)
            var k = 0
            val out = old.map { l -> if (l.startsWith("R ")) { val f = fresh[k++]; if (l.split(" ")[2] == "2") f else { assertEquals("levels 0-1 changed: ${l.take(40)}", l, f); l } } else l }
            file.writeText(out.joinToString("\n") + "\n")
        }
        assertTrue("fixture missing: ${file.absolutePath}", file.exists())
        val stored = file.readLines().filter { it.isNotBlank() }
        val expected = stored.filter { it.startsWith("R ") }
        val actual = replay(stored)
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertEquals("line $i (${expected[i].take(40)})", expected[i], actual[i])
    }
}

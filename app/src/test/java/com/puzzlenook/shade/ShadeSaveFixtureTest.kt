package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Regression for shared shade-board changes (blocked cells, outside counts): 3 saved games per
 * older shade variant were recorded in `src/test/resources/shade_saves.txt` (one line each: the
 * saved JSON of the original `ShadeViewModel.persist`, a tab, then every cell's drawing inputs).
 * They must still load, draw from the same inputs and write the same JSON.
 */
class ShadeSaveFixtureTest {
    private val variants = listOf(Variant.STAR_BATTLE, Variant.HITORI, Variant.NURIKABE, Variant.TAKUZU, Variant.MOSAIC, Variant.LIGHT_UP)

    /** The JSON the view model wrote before outside counts existed. */
    private fun originalJson(s: ShadeState) = JSONObject()
        .put("difficulty", s.difficulty.name)
        .put("size", s.size)
        .put("solution", s.solution.joinToString("") { if (it) "1" else "0" })
        .put("marks", s.marks.joinToString("") { it.ordinal.toString() })
        .put("fixed", s.fixed.joinToString("") { if (it) "1" else "0" })
        .put("numbers", s.numbers.joinToString(","))
        .put("regions", s.regions?.joinToString(",") ?: "")
        .put("stars", s.stars)
        .put("mistakes", s.mistakes)
        .put("showWrong", s.showWrong)
        .put("errorsSeen", s.errorsSeen)
        .put("chargedWrong", AnswerCheck.encode(s.chargedWrong))
        .put("hintHalfPoints", s.hintHalfPoints)
        .put("hintCell", s.hintCell ?: -1)
        .put("hintsUsed", s.hintsUsed)
        .put("lastHintAt", s.lastHintAt)
        .put("hintLimit", s.help.hintLimit)
        .put("risingCost", s.help.risingCost)
        .put("hintCooldown", s.help.cooldownSeconds)
        .put("elapsed", s.elapsedSeconds)

    /** What the board reads for each cell. */
    private fun drawing(s: ShadeState) = (0 until s.size * s.size).joinToString(";") { i ->
        "${s.marks[i].ordinal},${s.fixed[i]},${s.numbers[i]},${s.regions?.get(i)},${s.shownWrong(i)},${s.clueDone(i)},${s.isBlocked(i)}"
    } + "|${s.activeHintCell}|${s.isSolved}"

    private fun played(v: Variant, seed: Int): ShadeState {
        val d = Difficulty.entries[seed]
        var s = ShadeState.fromPuzzle(ShadeCheck.generate(v, d, seed + 3)).copy(elapsedSeconds = 40L + seed)
        if (seed == 2) s = s.withShowWrong(false)
        val open = s.marks.indices.filter { s.marks[it] == Mark.NONE && !s.fixed[it] }
        for ((k, i) in open.withIndex()) {
            if (k % 5 != 0) continue
            s = s.set(i, if (k % 10 == 0) Mark.FILLED else Mark.CROSSED)
        }
        return s.hint()
    }

    private fun loadSaved(v: Variant, json: JSONObject) = ShadeState.fromJson(v, json, com.puzzlenook.settings.HelpSettings(), 10)!!
    private fun saveJson(s: ShadeState) = s.toJson()

    private fun flat(j: JSONObject) = j.keys().asSequence().sorted().joinToString("&") { "$it=${j.get(it)}" }

    private val file = File("src/test/resources/shade_saves.txt")

    @Test(timeout = 300_000) fun recordOnce() {
        if (file.exists()) return
        val lines = variants.flatMap { v -> (0 until 3).map { seed -> val s = played(v, seed); originalJson(s).toString() + "\t" + drawing(s) } }
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString("\n"))
    }

    @Test(timeout = 300_000) fun savedGamesStillLoadAndRoundTrip() {
        val lines = javaClass.getResource("/shade_saves.txt")!!.readText().lines()
        assertEquals(variants.size * 3, lines.size)
        var k = 0
        for (v in variants) for (seed in 0 until 3) {
            val (json, draw) = lines[k++].split("\t")
            val s = loadSaved(v, JSONObject(json))
            assertEquals("$v $seed drawing", draw, drawing(s))
            assertEquals("$v $seed json", flat(JSONObject(json)), flat(saveJson(s)))
        }
    }
}

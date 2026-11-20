package com.puzzlenook.sudoku

import android.content.Context
import com.puzzlenook.settings.HelpRules
import com.puzzlenook.settings.HelpSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists one game type's in-progress game (as JSON) and its per-difficulty stats in
 * SharedPreferences. Each [Variant] has its own file.
 */
class GameStore(context: Context, val variant: Variant = Variant.CLASSIC) {
    private val prefs = context.getSharedPreferences(variant.storeName, Context.MODE_PRIVATE)

    fun saveJson(json: JSONObject) {
        prefs.edit().putString(KEY_GAME, json.toString()).apply()
    }

    fun loadJson(): JSONObject? = prefs.getString(KEY_GAME, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun save(state: GameState) {
        if (state.completed) {
            clear()
            return
        }
        val json = JSONObject()
            .put("difficulty", state.difficulty.name)
            .put("size", state.size)
            .put("values", state.cells.joinToString("") { it.value.toString() })
            .put("givens", state.cells.joinToString("") { if (it.given) "1" else "0" })
            .put("blocked", state.cells.joinToString("") { if (it.blocked) "1" else "0" })
            .put("notes", JSONArray(state.cells.map { it.notes }))
            .put("solution", state.solution.joinToString(""))
            .put("mistakes", state.mistakes)
            .put("showWrong", state.showWrong)
            .put("errorsSeen", state.errorsSeen)
            .put("chargedWrong", AnswerCheck.encode(state.chargedWrong))
            .put("hintHalfPoints", state.hintHalfPoints)
            .put("hintCell", state.hintCell ?: -1)
            .put("hintsUsed", state.hintsUsed)
            .put("lastHintAt", state.lastHintAt)
            .put("hintLimit", state.help.hintLimit)
            .put("risingCost", state.help.risingCost)
            .put("hintCooldown", state.help.cooldownSeconds)
            .put("elapsed", state.elapsedSeconds)
            .put("cages", JSONArray(state.cages.map { "${it.sum}:${it.cells.joinToString(",")}" }))
        state.regions?.let { json.put("regions", it.joinToString(",")) }
        state.rules?.let { r ->
            json.put(
                "rules",
                JSONObject()
                    .put("n", r.n)
                    .put("less", r.less.joinToString(",") { "${it[0]}<${it[1]}" })
                    .put("math", JSONArray(r.cages.map { "${it.op}:${it.target}:${it.cells.joinToString(",")}" }))
                    .put("sky", r.sky?.joinToString(",") ?: "")
                    .also { o ->
                        if (r.kropkiAll || r.white.isNotEmpty() || r.black.isNotEmpty()) {
                            o.put("white", r.white.joinToString(",") { "${it[0]}-${it[1]}" })
                            o.put("black", r.black.joinToString(",") { "${it[0]}-${it[1]}" })
                            o.put("kropkiAll", r.kropkiAll)
                        }
                    },
            )
        }
        saveJson(json)
    }

    fun load(defaultHelp: HelpSettings = HelpSettings()): GameState? = loadJson()?.let { json ->
        runCatching {
            // Saves from before two-step hints stored a count of full hints (1 point each).
            val halfPoints = if (json.has("hintHalfPoints")) json.getInt("hintHalfPoints") else json.optInt("hints") * 2
            val size = json.optInt("size", 9)
            val values = json.getString("values")
            val givens = json.getString("givens")
            val blocked = json.optString("blocked", "")
            val notes = json.getJSONArray("notes")
            GameState(
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                cells = List(size * size) { i ->
                    Cell(
                        value = values[i].digitToInt(),
                        given = givens[i] == '1',
                        notes = notes.getInt(i),
                        blocked = blocked.getOrNull(i) == '1',
                    )
                },
                solution = json.getString("solution").map { it.digitToInt() },
                mistakes = json.getInt("mistakes"),
                showWrong = json.optBoolean("showWrong", true),
                errorsSeen = json.optBoolean("errorsSeen", true),
                chargedWrong = AnswerCheck.decode(json.optString("chargedWrong", "")),
                hintHalfPoints = halfPoints,
                hintCell = json.optInt("hintCell", -1).takeIf { it >= 0 },
                hintsUsed = json.optInt("hintsUsed", HelpRules.hintsUsedFromOldSave(halfPoints)),
                lastHintAt = json.optLong("lastHintAt", -1),
                help = HelpSettings(
                    hintLimit = json.optInt("hintLimit", defaultHelp.hintLimit),
                    risingCost = json.optBoolean("risingCost", defaultHelp.risingCost),
                    cooldownSeconds = json.optInt("hintCooldown", defaultHelp.cooldownSeconds),
                ),
                elapsedSeconds = json.getLong("elapsed"),
                cages = json.optJSONArray("cages")?.let { a ->
                    List(a.length()) { k ->
                        val (sum, cells) = a.getString(k).split(":")
                        Cage(sum.toInt(), cells.split(",").map(String::toInt))
                    }
                } ?: emptyList(),
                variant = variant,
                size = size,
                rules = json.optJSONObject("rules")?.let(::rulesFromJson),
                regions = json.optString("regions", "").takeIf { it.isNotEmpty() }?.split(",")?.map(String::toInt),
            )
        }.getOrNull()
    }

    private fun rulesFromJson(r: JSONObject): LatinRules {
        val less = r.optString("less").split(",").filter { it.isNotEmpty() }.map { s ->
            s.split("<").map(String::toInt).toIntArray()
        }
        val math = r.optJSONArray("math")?.let { a ->
            List(a.length()) { k ->
                val (op, target, cells) = a.getString(k).split(":")
                MathCage(op[0], target.toInt(), cells.split(",").map(String::toInt))
            }
        } ?: emptyList()
        val sky = r.optString("sky").takeIf { it.isNotEmpty() }?.split(",")?.map(String::toInt)?.toIntArray()
        fun pairs(key: String) = r.optString(key).split(",").filter { it.isNotEmpty() }.map { s ->
            s.split("-").map(String::toInt).toIntArray()
        }
        return LatinRules(r.getInt("n"), less, math, sky, pairs("white"), pairs("black"), r.optBoolean("kropkiAll", false))
    }

    fun clear() {
        prefs.edit().remove(KEY_GAME).apply()
    }

    /** "Errors off" is remembered per difficulty, so new games of that level start the same way. */
    fun errorsHidden(d: Difficulty): Boolean = prefs.getBoolean("errorsHidden_${d.name}", false)

    fun setErrorsHidden(d: Difficulty, hidden: Boolean) {
        prefs.edit().putBoolean("errorsHidden_${d.name}", hidden).apply()
    }

    /** Counters, not counts over the history: they outlive the [MAX_HISTORY]-win history. */
    fun stats(d: Difficulty) = DifficultyStats(
        bestSeconds = prefs.getLong("best_${d.name}", -1).takeIf { it >= 0 },
        wins = prefs.getInt("wins_${d.name}", 0),
        clean = prefs.getInt("clean_${d.name}", 0),
        unaided = prefs.getInt("unaided_${d.name}", 0),
    )

    /**
     * Records a win, keeping its playing time and penalty points so statistics can show it
     * with any penalty setting. Returns true when the final time is a new best.
     */
    fun recordWin(
        d: Difficulty,
        playSeconds: Long,
        penaltyHalfPoints: Int,
        mistakes: Int,
        hintHalfPoints: Int,
        finalSeconds: Long,
        unaided: Boolean,
        atMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val current = stats(d)
        val isBest = current.bestSeconds == null || finalSeconds < current.bestSeconds
        val history = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        history.put(WinRecord(variant, d, playSeconds, penaltyHalfPoints, mistakes, hintHalfPoints, atMillis, unaided).toJson())
        // Keep the newest wins only.
        val trimmed = if (history.length() > MAX_HISTORY) {
            JSONArray((history.length() - MAX_HISTORY until history.length()).map { history.getJSONObject(it) })
        } else {
            history
        }
        prefs.edit().apply {
            putInt("wins_${d.name}", current.wins + 1)
            if (hintHalfPoints == 0) putInt("clean_${d.name}", current.clean + 1)
            if (unaided) putInt("unaided_${d.name}", current.unaided + 1)
            if (isBest) putLong("best_${d.name}", finalSeconds)
            putString(KEY_HISTORY, trimmed.toString())
        }.apply()
        return isBest
    }

    /** Counts a new game as started. */
    fun recordStart(d: Difficulty) {
        prefs.edit().putInt("started_${d.name}", started(d) + 1).apply()
    }

    fun started(d: Difficulty) = prefs.getInt("started_${d.name}", 0)

    /** Deletes wins, best times, started counts and history. Keeps the saved game and "errors off". */
    fun resetStats() {
        prefs.edit().apply {
            for (d in Difficulty.entries) {
                remove("wins_${d.name}")
                remove("best_${d.name}")
                remove("clean_${d.name}")
                remove("unaided_${d.name}")
                remove("started_${d.name}")
            }
            remove(KEY_HISTORY)
        }.apply()
    }

    /** Every recorded win, oldest first (wins from before 0.8 weren't recorded in detail). */
    fun history(): List<WinRecord> = runCatching {
        val a = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        List(a.length()) { WinRecord.fromJson(variant, a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_GAME = "current_game"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 500
    }
}

data class DifficultyStats(val bestSeconds: Long?, val wins: Int, val clean: Int = 0, val unaided: Int = 0)

/** One solved puzzle. */
data class WinRecord(
    val variant: Variant,
    val difficulty: Difficulty,
    val playSeconds: Long,
    val penaltyHalfPoints: Int,
    val mistakes: Int,
    val hintHalfPoints: Int,
    val atMillis: Long,
    /** No hints and Errors off for the whole game. */
    val unaided: Boolean = false,
    /** False for old wins saved without hint data: they can't count as clean. */
    val hintsRecorded: Boolean = true,
) {
    val clean: Boolean get() = hintsRecorded && hintHalfPoints == 0

    /** Final time if every penalty point added [percent]% of the playing time (0 = playing time only). */
    fun seconds(percent: Int): Long = playSeconds + playSeconds * penaltyHalfPoints * percent / 200

    fun toJson(): JSONObject = JSONObject()
        .put("d", difficulty.name)
        .put("play", playSeconds)
        .put("half", penaltyHalfPoints)
        .put("mistakes", mistakes)
        .put("hints", hintHalfPoints)
        .put("at", atMillis)
        .put("unaided", unaided)

    companion object {
        fun fromJson(variant: Variant, o: JSONObject) = WinRecord(
            variant = variant,
            difficulty = Difficulty.valueOf(o.getString("d")),
            playSeconds = o.getLong("play"),
            penaltyHalfPoints = o.getInt("half"),
            mistakes = o.optInt("mistakes"),
            hintHalfPoints = o.optInt("hints"),
            atMillis = o.optLong("at"),
            unaided = o.optBoolean("unaided", false),
            hintsRecorded = o.has("hints"),
        )
    }
}

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

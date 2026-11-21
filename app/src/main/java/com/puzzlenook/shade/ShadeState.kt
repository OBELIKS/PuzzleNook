package com.puzzlenook.shade

import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpRules
import com.puzzlenook.settings.HelpSettings
import org.json.JSONObject
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState
import com.puzzlenook.sudoku.Variant

/**
 * A Star Battle, Hitori, Nurikabe, Takuzu, Mosaic, Light Up, Tents, Kurodoko, Norinori, Battleships, Aquarium, Heyawake or LITS game in progress.
 * [Mark.FILLED] is a star, a shaded cell, a bulb or a tent, [Mark.CROSSED] a cell you've marked as empty / white. Like nonograms, a wrong mark is
 * a mistake (½ point) and stays visible until you change it; marking empty cells is optional. Also owns the saved-game
 * `toJson`/`fromJson`.
 */
data class ShadeState(
    val variant: Variant,
    val difficulty: Difficulty,
    val size: Int,
    val solution: List<Boolean>,
    val marks: List<Mark>,
    /** Givens, numbered Nurikabe cells and revealed hints: shown correctly and locked. */
    val fixed: List<Boolean>,
    val numbers: List<Int>,
    val regions: List<Int>? = null,
    val stars: Int = 0,
    /** Tents, Battleships, Aquarium: tents, ship cells or water per row, then per column (`-1` hidden); null when the game has no outside counts. */
    val counts: List<Int>? = null,
    val mistakes: Int = 0,
    val hintHalfPoints: Int = 0,
    val hintCell: Int? = null,
    val hintsUsed: Int = 0,
    val lastHintAt: Long = -1,
    val help: com.puzzlenook.settings.HelpSettings = com.puzzlenook.settings.HelpSettings(),
    val elapsedSeconds: Long = 0,
    val completed: Boolean = false,
    val penaltyPercent: Int = 10,
    val crossMode: Boolean = false,
    /** False in "hide errors" mode: wrong marks look normal and cost nothing until shown. */
    val showWrong: Boolean = true,
    val errorsSeen: Boolean = false,
    /** Wrong cells (cell → mark) when errors were hidden; already charged. */
    val chargedWrong: Map<Int, Int> = emptyMap(),
) {
    fun isWrong(i: Int) = when (marks[i]) {
        Mark.FILLED -> !solution[i]
        Mark.CROSSED -> solution[i]
        Mark.NONE -> false
    }

    /** Wrong and visible as such: hidden in "hide errors" mode. */
    fun shownWrong(i: Int) = showWrong && isWrong(i)

    /** Hides or shows errors; showing charges the mistakes made while hidden. */
    fun withShowWrong(show: Boolean): ShadeState {
        if (show == showWrong || completed) return this
        val wrong = marks.indices.filter { isWrong(it) }
        return if (show) {
            copy(showWrong = true, errorsSeen = true, chargedWrong = emptyMap(), mistakes = mistakes + AnswerCheck.owed(wrong, { marks[it].ordinal }, chargedWrong))
        } else {
            copy(showWrong = false, chargedWrong = AnswerCheck.snapshot(wrong) { marks[it].ordinal })
        }
    }

    val isSolved: Boolean
        get() = if (variant == Variant.TAKUZU) marks.indices.all { marks[it] == correctMark(it) }
        else marks.indices.all { (marks[it] == Mark.FILLED) == solution[it] }

    val activeHintCell: Int? get() = hintCell?.takeIf { !completed && !fixed[it] && marks[it] != correctMark(it) }

    val penaltyHalfPoints: Int get() = mistakes * GameState.MISTAKE_HALF_POINTS + hintHalfPoints
    val penaltyTotalPercent: Int get() = penaltyHalfPoints * penaltyPercent / 2
    val penaltySeconds: Long get() = elapsedSeconds * penaltyHalfPoints * penaltyPercent / 200
    val finalSeconds: Long get() = elapsedSeconds + penaltySeconds

    fun correctMark(i: Int) = if (solution[i]) Mark.FILLED else Mark.CROSSED

    /**
     * Mosaic: the clue at [i] is finished: its block holds exactly that many shaded cells and every
     * other block cell is marked white. Looks at marks only, so it can't give the answer away.
     */
    fun clueDone(i: Int): Boolean {
        if (variant == Variant.LIGHT_UP) {
            if (numbers[i] !in 0..4) return false
            val next = orth(i, size).filter { numbers[it] < 0 }
            return next.count { marks[it] == Mark.FILLED } == numbers[i] && next.all { marks[it] != Mark.NONE }
        }
        if (variant != Variant.MOSAIC || numbers[i] < 0) return false
        val block = king(i, size) + i
        return block.count { marks[it] == Mark.FILLED } == numbers[i] && block.all { marks[it] != Mark.NONE }
    }

    /**
     * A blocked cell is part of the board, not of the answer: Light Up walls. Strokes skip it, it
     * starts locked and its (always empty) solution never decides a win. Nurikabe's numbered
     * cells stay ordinary locked island cells.
     */
    fun isBlocked(i: Int) = (variant == Variant.LIGHT_UP && numbers[i] >= 0) || (variant == Variant.TENTS && numbers[i] == TREE) ||
        (variant == Variant.KURODOKO && numbers[i] > 0)

    /**
     * Kurodoko: the clue at [i] sees exactly its number of cells with the player's current shading
     * (unmarked cells count as white). Looks at marks only; the clue fades when it is met.
     */
    fun kurodokoMet(i: Int): Boolean =
        variant == Variant.KURODOKO && numbers[i] > 0 && KurodokoLogic.visible(size, i) { marks[it] == Mark.FILLED } == numbers[i]

    /**
     * Norinori: the shaded cell [i] already breaks a rule with your own shading: it touches two
     * shaded cells, or its region holds more than two. Looks at marks only; shown with errors on.
     */
    fun norinoriClash(i: Int): Boolean {
        if (variant != Variant.NORINORI || marks[i] != Mark.FILLED) return false
        if (orth(i, size).count { marks[it] == Mark.FILLED } > 1) return true
        val r = regions ?: return false
        return r.indices.count { r[it] == r[i] && marks[it] == Mark.FILLED } > 2
    }

    /**
     * Battleships: the ship cell [i] already breaks a rule with your own marks: another ship cell touches
     * it diagonally, or it has ship cells on both axes. Looks at marks only; shown with errors on.
     */
    fun shipClash(i: Int): Boolean {
        if (variant != Variant.BATTLESHIPS || marks[i] != Mark.FILLED) return false
        if (king(i, size).any { j -> j / size != i / size && j % size != i % size && marks[j] == Mark.FILLED }) return true
        val near = orth(i, size).filter { marks[it] == Mark.FILLED }
        return near.any { it / size == i / size } && near.any { it % size == i % size }
    }

    /**
     * Aquarium: the marked cell [i] contradicts your own marks in its tank (water at or below an air cell, or air
     * at or above a water cell). Looks at marks only; shown with errors on.
     */
    fun aquariumClash(i: Int): Boolean {
        if (variant != Variant.AQUARIUM || marks[i] == Mark.NONE) return false
        val g = regions ?: return false
        val row = i / size
        val water = marks[i] == Mark.FILLED
        return g.indices.any { j ->
            g[j] == g[i] && ((water && j / size >= row && marks[j] == Mark.CROSSED) || (!water && j / size <= row && marks[j] == Mark.FILLED))
        }
    }

    /** Heyawake: shaded cells you have marked in the room of cell [i]. */
    private fun roomShaded(i: Int): Int {
        val g = regions ?: return 0
        return g.indices.count { g[it] == g[i] && marks[it] == Mark.FILLED }
    }

    /** Heyawake: the room number at [i] is met by your own shading (it fades). Looks at marks only. */
    fun roomMet(i: Int) = variant == Variant.HEYAWAKE && numbers[i] >= 0 && roomShaded(i) == numbers[i]

    /** Heyawake: your own shading already exceeds the room number at [i]; shown with errors on. */
    fun roomOver(i: Int) = variant == Variant.HEYAWAKE && numbers[i] >= 0 && roomShaded(i) > numbers[i]

    /** Heyawake: the shaded cell [i] touches another shaded cell. Looks at marks only; shown with errors on. */
    fun heyawakeClash(i: Int) = variant == Variant.HEYAWAKE && marks[i] == Mark.FILLED && orth(i, size).any { marks[it] == Mark.FILLED }

    /**
     * LITS: the shaded cell [i] already breaks a rule with your own marks: it sits in a 2×2 shaded square,
     * its region holds more than four shaded cells, or it touches an equal tetromino of another region.
     * Looks at marks only; shown with errors on.
     */
    fun litsClash(i: Int): Boolean {
        if (variant != Variant.LITS || marks[i] != Mark.FILLED) return false
        val g = regions ?: return false
        val n = size
        val r = i / n
        val c = i % n
        for (dr in -1..0) for (dc in -1..0) {
            if (r + dr < 0 || c + dc < 0 || r + dr + 1 >= n || c + dc + 1 >= n) continue
            val tl = (r + dr) * n + c + dc
            if (intArrayOf(tl, tl + 1, tl + n, tl + n + 1).all { marks[it] == Mark.FILLED }) return true
        }
        fun cellsOf(region: Int) = g.indices.filter { g[it] == region && marks[it] == Mark.FILLED }
        val mine = cellsOf(g[i])
        if (mine.size > 4) return true
        if (mine.size != 4 || !connectedCells(mine)) return false
        val shape = Tetromino.shape(mine.toIntArray(), n)
        for (j in orth(i, n)) {
            if (g[j] == g[i] || marks[j] != Mark.FILLED) continue
            val other = cellsOf(g[j])
            if (other.size == 4 && connectedCells(other) && Tetromino.shape(other.toIntArray(), n) == shape) return true
        }
        return false
    }

    private fun connectedCells(cells: List<Int>): Boolean {
        val seen = mutableSetOf(cells[0])
        val stack = ArrayDeque(listOf(cells[0]))
        while (stack.isNotEmpty()) for (j in orth(stack.removeLast(), size)) if (j in cells && seen.add(j)) stack.addLast(j)
        return seen.size == cells.size
    }

    /**
     * Battleships status: the fleet (longest first) with the ships you have finished marked true. A
     * finished ship is a straight run of your ship cells whose two ends are marked water or the edge.
     * Looks at marks only.
     */
    fun fleetStatus(): List<Pair<Int, Boolean>> {
        val fleet = BattleshipsGenerator.fleet(size)
        val seen = BooleanArray(size * size)
        val done = IntArray(fleet.max() + 2)
        for (i in marks.indices) {
            if (marks[i] != Mark.FILLED || seen[i]) continue
            val comp = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            stack.addLast(i)
            seen[i] = true
            while (stack.isNotEmpty()) {
                val x = stack.removeLast()
                comp += x
                for (y in orth(x, size)) if (marks[y] == Mark.FILLED && !seen[y]) { seen[y] = true; stack.addLast(y) }
            }
            val row = comp.all { it / size == comp[0] / size }
            if (!row && !comp.all { it % size == comp[0] % size }) continue
            comp.sort()
            val a = comp.first()
            val b = comp.last()
            val beyond = if (comp.size == 1) orth(a, size).toList() else {
                val step = if (row) 1 else size
                listOfNotNull((a - step).takeIf { if (row) a % size > 0 else a >= size }, (b + step).takeIf { if (row) b % size < size - 1 else b + size < size * size })
            }
            val open = beyond.any { marks[it] != Mark.CROSSED }
            if (!open && comp.size < done.size) done[comp.size]++
        }
        return fleet.map { len -> if (done[len] > 0) { done[len]--; len to true } else len to false }
    }

    /** Cells of outside-count line [k] (rows 0..n-1, then columns). */
    fun lineCells(k: Int): List<Int> = if (k < size) List(size) { k * size + it } else List(size) { it * size + (k - size) }

    /** Tents of line [k] marked so far (from the player's own marks). */
    fun lineMarked(k: Int) = lineCells(k).count { marks[it] == Mark.FILLED }

    /** The count of line [k] is met by the player's tents: it fades. Looks at marks only. */
    fun countDone(k: Int): Boolean {
        val c = counts?.get(k) ?: return false
        return c >= 0 && lineMarked(k) == c
    }

    /** Stars in each row, column and region that are already placed (Star Battle). */
    fun unitStars(cells: List<Int>) = cells.count { marks[it] == Mark.FILLED }

    fun set(i: Int, mark: Mark): ShadeState {
        if (completed || fixed[i] || marks[i] == mark) return this
        val wrong = when (mark) {
            Mark.FILLED -> !solution[i]
            Mark.CROSSED -> solution[i]
            Mark.NONE -> false
        }
        val next = marks.toMutableList().also { it[i] = mark }
        // Dots only around right stars would give the answer away while errors are hidden.
        if (mark == Mark.FILLED && !wrong && showWrong) dotAround(i, next)
        return copy(
            marks = next,
            mistakes = mistakes + if (wrong && showWrong) 1 else 0,
            errorsSeen = errorsSeen || showWrong,
            hintCell = if (!wrong && mark != Mark.NONE && i == hintCell) null else hintCell,
        ).checkSolved()
    }

    /**
     * Star Battle: a correct star marks its eight neighbours empty, since stars never touch.
     * Only correct stars do it (a wrong one is already shown as a mistake), so the dots are
     * always right and never cost a mistake.
     */
    private fun dotAround(i: Int, marks: MutableList<Mark>) {
        if (variant == Variant.BATTLESHIPS) {
            // A ship cell always has water on its four diagonals, so a correct one dots them.
            for (j in king(i, size)) if (j / size != i / size && j % size != i % size && marks[j] == Mark.NONE && !fixed[j]) marks[j] = Mark.CROSSED
            return
        }
        if (variant != Variant.STAR_BATTLE) return
        for (j in king(i, size)) if (marks[j] == Mark.NONE && !fixed[j]) marks[j] = Mark.CROSSED
    }

    fun hint(): ShadeState {
        if (completed) return this
        activeHintCell?.let { h ->
            val next = marks.toMutableList().also { it[h] = correctMark(h) }
            if (solution[h]) dotAround(h, next)
            return copy(
                marks = next,
                fixed = fixed.toMutableList().also { it[h] = true },
                hintHalfPoints = hintHalfPoints + HelpRules.revealCost(hintsUsed, help),
                hintCell = null,
            ).checkSolved()
        }
        val f = HelpRules.frame(hintsUsed, elapsedSeconds, lastHintAt, help) ?: return this
        val target = pickHintCell() ?: return this
        return copy(
            hintCell = target, hintsUsed = f.hintsUsed, lastHintAt = f.lastHintAt,
            hintHalfPoints = hintHalfPoints + f.costHalfPoints,
        )
    }

    /**
     * A wrong mark first; otherwise the open cell the step-by-step logic settles soonest from
     * what's correctly marked, preferring cells to shade (marking empty ones is optional).
     */
    fun pickHintCell(): Int? {
        marks.indices.firstOrNull { isWrong(it) }?.let { return it }
        val start = IntArray(size * size) { i ->
            when (marks[i]) {
                Mark.FILLED -> SHADED
                Mark.CROSSED -> WHITE
                Mark.NONE -> UNKNOWN
            }
        }
        fun openOf(r: ShadeResult) = marks.indices.filter { marks[it] == Mark.NONE && !fixed[it] && r.round[it] > 0 }
        var result = solveFrom(start)
        var open = openOf(result)
        // Games saved before the bounded level 2 may be past what their level can settle: use the next level up.
        if (open.isEmpty() && (variant == Variant.LIGHT_UP || variant == Variant.NORINORI || variant == Variant.BATTLESHIPS || variant == Variant.AQUARIUM || variant == Variant.HEYAWAKE || variant == Variant.LITS)) {
            for (level in difficulty.ordinal + 1..3) {
                result = solveFrom(start, level)
                open = openOf(result)
                if (open.isNotEmpty()) break
            }
        }
        val soonest = open.minOfOrNull { result.round[it] } ?: return marks.indices.firstOrNull { marks[it] == Mark.NONE && (solution[it] || variant == Variant.TAKUZU) }
        val now = open.filter { result.round[it] == soonest }
        return now.firstOrNull { solution[it] } ?: now.first()
    }

    /** Runs this puzzle's step-by-step solver from [start]. */
    fun solveFrom(start: IntArray, level: Int = difficulty.ordinal): ShadeResult = when (variant) {
        // Hints use only the steps this level is built on.
        Variant.STAR_BATTLE -> StarBattleGenerator.logicFor(difficulty, size, stars, regions!!.toIntArray()).run(start)
        Variant.HITORI -> HitoriLogic(size, numbers.toIntArray()).run(start)
        Variant.NURIKABE -> NurikabeLogic(size, numbers.toIntArray()).run(start)
        Variant.TAKUZU -> TakuzuLogic.forLevel(difficulty, size).run(start)
        Variant.MOSAIC -> MosaicLogic.forLevel(difficulty, size, numbers.toIntArray()).run(start)
        Variant.LIGHT_UP -> LightUpLogic.forLevel(Difficulty.entries[level], size, numbers.toIntArray()).run(start)
        Variant.TENTS -> TentsLogic.forLevel(difficulty, size, numbers.toIntArray(), counts!!.toIntArray()).run(start)
        Variant.KURODOKO -> KurodokoLogic.forLevel(difficulty, size, numbers.toIntArray()).run(start)
        Variant.NORINORI -> NorinoriLogic.forLevel(Difficulty.entries[level], size, regions!!.toIntArray()).run(start)
        Variant.BATTLESHIPS -> BattleshipsLogic.forLevel(level, size, numbers.toIntArray(), counts!!.toIntArray()).run(start)
        Variant.AQUARIUM -> AquariumLogic.forLevel(level, size, regions!!.toIntArray(), counts!!.toIntArray()).run(start)
        Variant.HEYAWAKE -> HeyawakeLogic.forLevel(level, size, regions!!.toIntArray(), numbers.toIntArray()).run(start)
        Variant.LITS -> LitsLogic.forLevel(level, size, regions!!.toIntArray()).run(start)
        else -> error("$variant is not a shading puzzle")
    }

    private fun checkSolved(): ShadeState {
        if (!isSolved) return this
        return copy(
            completed = true,
            hintCell = null,
            marks = marks.mapIndexed { i, m -> if (solution[i]) Mark.FILLED else if (m == Mark.NONE) Mark.CROSSED else m },
        )
    }

    /** The saved-game JSON; optional fields are written only when the game has them. */
    fun toJson(): JSONObject = JSONObject()
        .put("difficulty", difficulty.name)
        .put("size", size)
        .put("solution", solution.joinToString("") { if (it) "1" else "0" })
        .put("marks", marks.joinToString("") { it.ordinal.toString() })
        .put("fixed", fixed.joinToString("") { if (it) "1" else "0" })
        .put("numbers", numbers.joinToString(","))
        .put("regions", regions?.joinToString(",") ?: "")
        .put("stars", stars)
        .apply { counts?.let { put("counts", it.joinToString(",")) } }
        .put("mistakes", mistakes)
        .put("showWrong", showWrong)
        .put("errorsSeen", errorsSeen)
        .put("chargedWrong", AnswerCheck.encode(chargedWrong))
        .put("hintHalfPoints", hintHalfPoints)
        .put("hintCell", hintCell ?: -1)
        .put("hintsUsed", hintsUsed)
        .put("lastHintAt", lastHintAt)
        .put("hintLimit", help.hintLimit)
        .put("risingCost", help.risingCost)
        .put("hintCooldown", help.cooldownSeconds)
        .put("elapsed", elapsedSeconds)

    companion object {
        /** Reads a saved game; [h] fills help settings an old save lacks. Null if the JSON is unusable. */
        fun fromJson(variant: Variant, json: JSONObject, h: HelpSettings, penaltyPercent: Int): ShadeState? = runCatching {
            val halfPoints = json.getInt("hintHalfPoints")
            ShadeState(
                variant = variant,
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                size = json.getInt("size"),
                solution = json.getString("solution").map { it == '1' },
                marks = json.getString("marks").map { Mark.entries[it.digitToInt()] },
                fixed = json.getString("fixed").map { it == '1' },
                numbers = json.getString("numbers").split(",").map(String::toInt),
                regions = json.optString("regions").takeIf { it.isNotEmpty() }?.split(",")?.map(String::toInt),
                stars = json.optInt("stars"),
                counts = json.optString("counts").takeIf { it.isNotEmpty() }?.split(",")?.map(String::toInt),
                mistakes = json.getInt("mistakes"),
                showWrong = json.optBoolean("showWrong", true),
                errorsSeen = json.optBoolean("errorsSeen", true),
                chargedWrong = AnswerCheck.decode(json.optString("chargedWrong", "")),
                hintHalfPoints = halfPoints,
                hintCell = json.optInt("hintCell", -1).takeIf { it >= 0 },
                hintsUsed = json.optInt("hintsUsed", HelpRules.hintsUsedFromOldSave(halfPoints)),
                lastHintAt = json.optLong("lastHintAt", -1),
                help = HelpSettings(
                    hintLimit = json.optInt("hintLimit", h.hintLimit),
                    risingCost = json.optBoolean("risingCost", h.risingCost),
                    cooldownSeconds = json.optInt("hintCooldown", h.cooldownSeconds),
                ),
                elapsedSeconds = json.getLong("elapsed"),
                penaltyPercent = penaltyPercent,
            )
        }.getOrNull()

        fun fromPuzzle(p: ShadePuzzle): ShadeState {
            val n = p.size
            // Nurikabe numbers are island cells, Light Up numbers are walls: shown and locked from the start.
            val locked = BooleanArray(n * n) {
                p.givens[it] || (p.variant == Variant.NURIKABE && p.numbers[it] > 0) || (p.variant == Variant.LIGHT_UP && p.numbers[it] >= 0) || (p.variant == Variant.TENTS && p.numbers[it] == TREE) ||
                    (p.variant == Variant.KURODOKO && p.numbers[it] > 0) || (p.variant == Variant.BATTLESHIPS && p.numbers[it] >= 0)
            }
            val state = ShadeState(
                variant = p.variant,
                difficulty = p.difficulty,
                size = n,
                solution = p.solution.toList(),
                marks = List(n * n) { i -> if (locked[i]) (if (p.solution[i]) Mark.FILLED else Mark.CROSSED) else Mark.NONE },
                fixed = locked.toList(),
                numbers = p.numbers.toList(),
                regions = p.regions?.toList(),
                stars = p.stars,
                counts = p.counts?.toList(),
            )
            // Given stars get their dots too.
            val marks = state.marks.toMutableList()
            for (i in 0 until n * n) if (locked[i] && p.solution[i]) state.dotAround(i, marks)
            return state.copy(marks = marks)
        }

        fun generate(variant: Variant, d: Difficulty): ShadePuzzle = when (variant) {
            Variant.STAR_BATTLE -> StarBattleGenerator.generate(d)
            Variant.HITORI -> HitoriGenerator.generate(d)
            Variant.NURIKABE -> NurikabeGenerator.generate(d)
            Variant.TAKUZU -> TakuzuGenerator.generate(d)
            Variant.MOSAIC -> MosaicGenerator.generate(d)
            Variant.LIGHT_UP -> LightUpGenerator.generate(d)
            Variant.TENTS -> TentsGenerator.generate(d)
            Variant.KURODOKO -> KurodokoGenerator.generate(d)
            Variant.NORINORI -> NorinoriGenerator.generate(d)
            Variant.BATTLESHIPS -> BattleshipsGenerator.generate(d)
            Variant.AQUARIUM -> AquariumGenerator.generate(d)
            Variant.HEYAWAKE -> HeyawakeGenerator.generate(d)
            Variant.LITS -> LitsGenerator.generate(d)
            else -> error("$variant is not a shading puzzle")
        }
    }
}

package com.puzzlenook.slither

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzlenook.PuzzleNookApplication
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.settings.HelpRules
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.DifficultyStats
import com.puzzlenook.sudoku.GameStore
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.WinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Yajilin: in Shade mode tap or drag over cells (shaded, then a dot, then empty); in Line mode tap an edge
 * or drag from cell to cell to draw the loop.
 */
class YajilinViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app, Variant.YAJILIN)
    private val settings = (app as PuzzleNookApplication).settings

    private val _state = MutableStateFlow(load())
    val state: StateFlow<YajilinState?> = _state.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _win = MutableStateFlow<WinResult?>(null)
    val win: StateFlow<WinResult?> = _win.asStateFlow()

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<Map<Difficulty, DifficultyStats>> = _stats.asStateFlow()

    /** Re-reads statistics after they were reset from the statistics screen. */
    fun reloadStats() {
        _stats.value = loadStats()
    }

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val undoStack = ArrayDeque<List<Mark>>()
    private var timerJob: Job? = null
    private var generateJob: Job? = null
    private var screenActive = false

    private val _paused = MutableStateFlow(false)
    /** Paused: the timer stops and the screen hides the board. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun pause() {
        _paused.value = true
        persist()
    }

    fun resume() {
        _paused.value = false
    }

    init {
        viewModelScope.launch {
            settings.penaltyPercent.collect { p -> _state.value = _state.value?.copy(penaltyPercent = p) }
        }
    }

    fun newGame(difficulty: Difficulty) {
        generateJob?.cancel()
        store.clear()
        store.recordStart(difficulty)
        undoStack.clear()
        _win.value = null
        _paused.value = false
        _state.value = null
        _loading.value = true
        generateJob = viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { YajilinGenerator.generate(difficulty) }
            _state.value = YajilinState.fromPuzzle(puzzle).copy(penaltyPercent = settings.penaltyPercent.value, help = settings.help.value)
                .withShowWrong(!store.errorsHidden(difficulty))
            _loading.value = false
            persist()
        }
    }

    fun onScreenResumed() {
        screenActive = true
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _state.value ?: continue
                if (s.completed || !screenActive || _paused.value) continue
                _state.value = s.copy(elapsedSeconds = s.elapsedSeconds + 1)
            }
        }
    }

    fun onScreenPaused() {
        screenActive = false
        timerJob?.cancel()
        timerJob = null
        persist()
    }

    /** A tap on an edge (Line mode) toggles a line. */
    fun tapEdge(e: Int) {
        val s = _state.value ?: return
        val before = s.marks
        update(s.set(e, if (s.marks[e] == Mark.FILLED) Mark.NONE else Mark.FILLED))
        pushUndo(before)
    }

    // A line drag draws (or, if its first edge already had a line, erases) every edge it passes.
    private var dragPaint: Mark? = null
    private var dragBefore: List<Mark>? = null

    fun dragStart() {
        dragPaint = null
        dragBefore = _state.value?.marks
    }

    fun dragEdge(e: Int) {
        val s = _state.value ?: return
        val paint = dragPaint ?: (if (s.marks[e] == Mark.FILLED) Mark.NONE else Mark.FILLED).also { dragPaint = it }
        update(s.set(e, paint))
    }

    fun dragEnd() {
        dragBefore?.let(::pushUndo)
        dragBefore = null
        dragPaint = null
        persist()
    }

    private fun next(m: Mark) = when (m) {
        Mark.NONE -> Mark.FILLED
        Mark.FILLED -> Mark.CROSSED
        Mark.CROSSED -> Mark.NONE
    }

    /** A tap on a cell (Shade mode): empty, shaded, dot, empty... */
    fun tapCell(i: Int) {
        val s = _state.value ?: return
        val x = s.edges + i
        val before = s.marks
        update(s.set(x, next(s.marks[x])))
        pushUndo(before)
    }

    // A cell stroke paints every cell it passes with what its first cell turns into, if they look like that first cell did.
    private var strokeFrom: Mark = Mark.NONE
    private var strokePaint: Mark = Mark.NONE
    private var strokeBefore: List<Mark>? = null

    fun cellStrokeStart(i: Int) {
        val s = _state.value ?: return
        val x = s.edges + i
        strokeFrom = s.marks[x]
        strokePaint = next(strokeFrom)
        strokeBefore = s.marks
        update(s.set(x, strokePaint))
    }

    fun cellStrokeMove(i: Int) {
        val s = _state.value ?: return
        if (strokeBefore == null) return
        val x = s.edges + i
        if (s.marks[x] == strokeFrom) update(s.set(x, strokePaint))
    }

    fun cellStrokeEnd() {
        strokeBefore?.let(::pushUndo)
        strokeBefore = null
        persist()
    }

    private fun pushUndo(before: List<Mark>) {
        val s = _state.value ?: return
        if (before != s.marks && !s.completed) {
            undoStack.addLast(before)
            if (undoStack.size > 300) undoStack.removeFirst()
        }
        persist()
    }

    fun setLineMode(on: Boolean) {
        _state.value = _state.value?.copy(lineMode = on)
    }

    /** Hide errors, or show them again (which charges the mistakes made meanwhile). */
    fun toggleShowWrong() {
        val s = _state.value ?: return
        update(s.withShowWrong(!s.showWrong))
        store.setErrorsHidden(s.difficulty, s.showWrong)
        persist()
    }

    fun hint() {
        val s = _state.value ?: return
        update(s.hint())
        persist()
    }

    fun undo() {
        val s = _state.value ?: return
        if (s.completed) return
        val previous = undoStack.removeLastOrNull() ?: return
        _state.value = s.copy(marks = previous.mapIndexed { i, m -> if (s.fixed[i]) s.marks[i] else m })
        persist()
    }

    fun dismissWin() {
        _win.value = null
    }

    private fun update(next: YajilinState) {
        val s = _state.value ?: return
        if (next == s) return
        _state.value = next
        if (next.completed && !s.completed) onWin(next)
    }

    private fun onWin(s: YajilinState) {
        undoStack.clear()
        store.clear()
        val newBest = store.recordWin(s.difficulty, s.elapsedSeconds, s.penaltyHalfPoints, s.mistakes, s.hintHalfPoints, s.finalSeconds, s.hintHalfPoints == 0 && !s.errorsSeen)
        _stats.value = loadStats()
        _canUndo.value = false
        _win.value = WinResult(
            playSeconds = s.elapsedSeconds,
            penaltySeconds = s.penaltySeconds,
            finalSeconds = s.finalSeconds,
            mistakes = s.mistakes,
            hintHalfPoints = s.hintHalfPoints,
            penaltyHalfPoints = s.penaltyHalfPoints,
            penaltyPercent = s.penaltyPercent,
            newBest = newBest,
        )
    }

    private fun persist() {
        val s = _state.value
        _canUndo.value = undoStack.isNotEmpty() && s?.completed == false
        if (s == null) return
        if (s.completed) {
            store.clear()
            return
        }
        store.saveJson(
            JSONObject()
                .put("difficulty", s.difficulty.name)
                .put("size", s.n)
                .put("clues", s.clues.joinToString(","))
                .put("solution", s.solution.joinToString("") { if (it) "1" else "0" })
                .put("marks", s.marks.joinToString("") { it.ordinal.toString() })
                .put("fixed", s.fixed.joinToString("") { if (it) "1" else "0" })
                .put("mistakes", s.mistakes)
                .put("showWrong", s.showWrong)
                .put("errorsSeen", s.errorsSeen)
                .put("chargedWrong", AnswerCheck.encode(s.chargedWrong))
                .put("hintHalfPoints", s.hintHalfPoints)
                .put("hintAt", s.hintAt ?: -1)
                .put("hintsUsed", s.hintsUsed)
                .put("lastHintAt", s.lastHintAt)
                .put("hintLimit", s.help.hintLimit)
                .put("risingCost", s.help.risingCost)
                .put("hintCooldown", s.help.cooldownSeconds)
                .put("elapsed", s.elapsedSeconds),
        )
    }

    private fun load(): YajilinState? = store.loadJson()?.let { json ->
        runCatching {
            val halfPoints = json.getInt("hintHalfPoints")
            val h = settings.help.value
            YajilinState(
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                n = json.getInt("size"),
                clues = json.getString("clues").split(",").map { it.toInt() },
                solution = json.getString("solution").map { it == '1' },
                marks = json.getString("marks").map { Mark.entries[it.digitToInt()] },
                fixed = json.getString("fixed").map { it == '1' },
                mistakes = json.getInt("mistakes"),
                showWrong = json.optBoolean("showWrong", true),
                errorsSeen = json.optBoolean("errorsSeen", true),
                chargedWrong = AnswerCheck.decode(json.optString("chargedWrong", "")),
                hintHalfPoints = halfPoints,
                hintAt = json.optInt("hintAt", -1).takeIf { it >= 0 },
                hintsUsed = json.optInt("hintsUsed", HelpRules.hintsUsedFromOldSave(halfPoints)),
                lastHintAt = json.optLong("lastHintAt", -1),
                help = HelpSettings(
                    hintLimit = json.optInt("hintLimit", h.hintLimit),
                    risingCost = json.optBoolean("risingCost", h.risingCost),
                    cooldownSeconds = json.optInt("hintCooldown", h.cooldownSeconds),
                ),
                elapsedSeconds = json.getLong("elapsed"),
                penaltyPercent = settings.penaltyPercent.value,
            )
        }.getOrNull()
    }

    private fun loadStats() = Difficulty.entries.associateWith(store::stats)
}

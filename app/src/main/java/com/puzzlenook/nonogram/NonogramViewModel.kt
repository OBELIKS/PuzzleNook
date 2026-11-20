package com.puzzlenook.nonogram

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzlenook.PuzzleNookApplication
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

class NonogramViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app, Variant.NONOGRAM)
    private val settings = (app as PuzzleNookApplication).settings

    private val _state = MutableStateFlow(load())
    val state: StateFlow<NonogramState?> = _state.asStateFlow()

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
            val puzzle = withContext(Dispatchers.Default) { NonogramGenerator.generate(difficulty) }
            _state.value = NonogramState.fromPuzzle(puzzle).copy(penaltyPercent = settings.penaltyPercent.value, help = settings.help.value)
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

    // --- Painting: one finger or pen stroke fills, crosses or clears a straight line of cells.

    private var strokeStart = -1
    private var strokePaint = Mark.NONE
    /** Marks a cell must have to be changed by this stroke. */
    private var strokeFrom = Mark.NONE
    private var strokeAxisRow: Boolean? = null
    private var strokeBefore: NonogramState? = null

    fun strokeStart(cell: Int) {
        val s = _state.value ?: return
        if (s.completed) return
        val target = if (s.crossMode) Mark.CROSSED else Mark.FILLED
        val current = s.marks[cell]
        // Starting on a cell that already has this mark clears; otherwise paints empty cells.
        strokePaint = if (current == target) Mark.NONE else target
        strokeFrom = if (current == target) target else Mark.NONE
        if (current != target && current != Mark.NONE) {
            // Starting on the other mark switches it over (e.g. a cross becomes filled).
            strokeFrom = current
        }
        strokeStart = cell
        strokeAxisRow = null
        strokeBefore = s
        paint(cell)
    }

    fun strokeMove(cell: Int) {
        val s = _state.value ?: return
        if (strokeStart < 0) return
        val n = s.size
        val sr = strokeStart / n
        val sc = strokeStart % n
        val r = cell / n
        val c = cell % n
        if (strokeAxisRow == null && cell != strokeStart) {
            strokeAxisRow = kotlin.math.abs(c - sc) >= kotlin.math.abs(r - sr)
        }
        when (strokeAxisRow) {
            true -> for (cc in minOf(sc, c)..maxOf(sc, c)) paint(sr * n + cc)
            false -> for (rr in minOf(sr, r)..maxOf(sr, r)) paint(rr * n + sc)
            null -> {}
        }
    }

    fun strokeEnd() {
        val before = strokeBefore
        strokeStart = -1
        strokeBefore = null
        val s = _state.value ?: return
        if (before != null && before.marks != s.marks && !s.completed) {
            undoStack.addLast(before.marks)
            if (undoStack.size > 200) undoStack.removeFirst()
        }
        persist()
    }

    private fun paint(cell: Int) {
        val s = _state.value ?: return
        if (s.marks[cell] != strokeFrom) return
        update(s.set(cell, strokePaint))
    }

    fun toggleCrossMode() {
        _state.value = _state.value?.let { it.copy(crossMode = !it.crossMode) }
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
        // Revealed hints stay revealed.
        _state.value = s.copy(marks = previous.mapIndexed { i, m -> if (s.fixed[i]) s.marks[i] else m })
        persist()
    }

    fun dismissWin() {
        _win.value = null
    }

    private fun update(next: NonogramState) {
        val s = _state.value ?: return
        if (next == s) return
        _state.value = next
        if (next.completed && !s.completed) onWin(next)
    }

    private fun onWin(s: NonogramState) {
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
                .put("size", s.size)
                .put("solution", s.solution.joinToString("") { if (it) "1" else "0" })
                .put("marks", s.marks.joinToString("") { it.ordinal.toString() })
                .put("fixed", s.fixed.joinToString("") { if (it) "1" else "0" })
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
                .put("elapsed", s.elapsedSeconds),
        )
    }

    private fun load(): NonogramState? = store.loadJson()?.let { json ->
        runCatching {
            val halfPoints = json.getInt("hintHalfPoints")
            val h = settings.help.value
            NonogramState(
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                size = json.getInt("size"),
                solution = json.getString("solution").map { it == '1' },
                marks = json.getString("marks").map { Mark.entries[it.digitToInt()] },
                fixed = json.getString("fixed").map { it == '1' },
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
                penaltyPercent = (getApplication<Application>() as PuzzleNookApplication).settings.penaltyPercent.value,
            )
        }.getOrNull()
    }

    private fun loadStats() = Difficulty.entries.associateWith(store::stats)
}

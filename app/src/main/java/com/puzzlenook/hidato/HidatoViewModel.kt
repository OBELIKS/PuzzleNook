package com.puzzlenook.hidato

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzlenook.PuzzleNookApplication
import com.puzzlenook.R
import com.puzzlenook.ui.UiText
import com.puzzlenook.ink.InkStroke
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

class HidatoViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app, Variant.HIDATO)
    private val settings = (app as PuzzleNookApplication).settings
    private val handwriting = (app as PuzzleNookApplication).handwriting

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HidatoState?> = _state.asStateFlow()

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

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private var messageJob: Job? = null

    private val undoStack = ArrayDeque<List<Int>>()
    private var timerJob: Job? = null
    private var generateJob: Job? = null
    private var pendingJob: Job? = null
    private var screenActive = false

    private val _paused = MutableStateFlow(false)
    /** Paused: the timer stops and the screen hides the board. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun pause() {
        commitPending()
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
            val puzzle = withContext(Dispatchers.Default) { HidatoGenerator.generate(difficulty) }
            _state.value = HidatoState.fromPuzzle(puzzle).copy(penaltyPercent = settings.penaltyPercent.value, help = settings.help.value)
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
        commitPending()
        screenActive = false
        timerJob?.cancel()
        timerJob = null
        persist()
    }

    fun select(cell: Int) {
        commitPending()
        val s = _state.value ?: return
        _state.value = s.copy(selected = if (s.selected == cell) null else cell)
    }

    /**
     * A digit from the number pad. Numbers are typed digit by digit: a digit that can't start a
     * two-digit number here goes in at once; otherwise it waits briefly for a second digit.
     */
    fun digit(d: Int) {
        val s = _state.value ?: return
        val i = s.selected ?: return
        if (s.completed || s.fixed[i]) return
        val p = s.pending
        pendingJob?.cancel()
        if (p != null) {
            val v = p * 10 + d
            if (v <= s.max) {
                update { it.enter(i, v) }
                return
            }
        }
        if (d == 0) {
            _state.value = s.copy(pending = null)
            return
        }
        if (d * 10 > s.max) {
            update { it.enter(i, d) }
            return
        }
        _state.value = s.copy(pending = d)
        pendingJob = viewModelScope.launch {
            delay(PENDING_MS)
            commitPending()
        }
    }

    private fun commitPending() {
        pendingJob?.cancel()
        val s = _state.value ?: return
        val p = s.pending ?: return
        val i = s.selected
        if (i == null) {
            _state.value = s.copy(pending = null)
            return
        }
        update { it.enter(i, p) }
    }

    fun erase() {
        pendingJob?.cancel()
        val s = _state.value ?: return
        if (s.pending != null) {
            _state.value = s.copy(pending = null)
            return
        }
        val i = s.selected ?: return
        update { it.erase(i) }
    }

    /** A number written on [cell]: one or two digits; a lone 0 clears the cell. */
    fun onHandwriting(cell: Int, strokes: List<InkStroke>, cellSize: Float) {
        commitPending()
        val s = _state.value ?: return
        if (s.completed) return
        _state.value = s.copy(selected = cell)
        if (s.fixed[cell]) return
        viewModelScope.launch {
            val v = handwriting.recognizeNumber(strokes, cellSize, cellSize, s.max)
            when {
                v == null -> flash(UiText(R.string.flash_unreadable))
                v == 0 -> update { it.erase(cell) }
                else -> update { it.enter(cell, v) }
            }
        }
    }

    fun hint() = update(recordUndo = false) { it.hint() }

    /** Hide errors, or show them again (which charges the mistakes made meanwhile). */
    fun toggleShowWrong() {
        commitPending()
        update(recordUndo = false) { it.withShowWrong(!it.showWrong) }
        _state.value?.let { store.setErrorsHidden(it.difficulty, !it.showWrong) }
    }

    fun undo() {
        val s = _state.value ?: return
        if (s.completed) return
        val previous = undoStack.removeLastOrNull() ?: return
        _state.value = s.copy(values = previous.mapIndexed { i, v -> if (s.fixed[i]) s.values[i] else v }, pending = null)
        persist()
    }

    fun dismissWin() {
        _win.value = null
    }

    private fun flash(text: UiText) {
        messageJob?.cancel()
        _message.value = text
        messageJob = viewModelScope.launch {
            delay(2_500)
            _message.value = null
        }
    }

    private inline fun update(recordUndo: Boolean = true, transform: (HidatoState) -> HidatoState) {
        val s = _state.value ?: return
        val next = transform(s)
        if (next == s) return
        if (recordUndo && next.values != s.values) {
            undoStack.addLast(s.values)
            if (undoStack.size > 300) undoStack.removeFirst()
        }
        _state.value = next
        if (next.completed && !s.completed) onWin(next) else persist()
    }

    private fun onWin(s: HidatoState) {
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
                .put("solution", s.solution.joinToString(","))
                .put("values", s.values.joinToString(","))
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

    private fun load(): HidatoState? = store.loadJson()?.let { json ->
        runCatching {
            val halfPoints = json.getInt("hintHalfPoints")
            val h = settings.help.value
            HidatoState(
                difficulty = Difficulty.valueOf(json.getString("difficulty")),
                n = json.getInt("size"),
                solution = json.getString("solution").split(",").map(String::toInt),
                values = json.getString("values").split(",").map(String::toInt),
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
                penaltyPercent = settings.penaltyPercent.value,
            )
        }.getOrNull()
    }

    private fun loadStats() = Difficulty.entries.associateWith(store::stats)

    private companion object {
        const val PENDING_MS = 1_200L
    }
}

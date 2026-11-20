package com.puzzlenook.region

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzlenook.PuzzleNookApplication
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

/** Shikaku: drag from cell to cell to draw a rectangle, tap a rectangle to remove it. */
class ShikakuViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app, Variant.SHIKAKU)
    private val settings = (app as PuzzleNookApplication).settings

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ShikakuState?> = _state.asStateFlow()

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

    private val undoStack = ArrayDeque<List<Rect>>()
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
            val puzzle = withContext(Dispatchers.Default) { ShikakuGenerator.generate(difficulty) }
            _state.value = ShikakuState.fromPuzzle(puzzle).copy(penaltyPercent = settings.penaltyPercent.value, help = settings.help.value)
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

    /** A drag from cell [from] to cell [to] draws the rectangle between them (one undo step). */
    fun drawRect(from: Int, to: Int) {
        val s = _state.value ?: return
        val r = Rect.between(from, to, s.n)
        change(s.place(r))
    }

    /** A tap removes the rectangle under it; on an empty clue "1" it draws that single cell. */
    fun tapCell(cell: Int) {
        val s = _state.value ?: return
        if (s.rectAt(cell) != null) change(s.remove(cell))
        else if (s.numbers[cell] == 1) change(s.place(Rect(cell / s.n, cell % s.n, cell / s.n, cell % s.n)))
    }

    private fun change(next: ShikakuState) {
        val s = _state.value ?: return
        if (next.rects == s.rects) return
        update(next)
        if (!next.completed) {
            undoStack.addLast(s.rects)
            if (undoStack.size > 300) undoStack.removeFirst()
        }
        persist()
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
        _state.value = s.restore(previous)
        persist()
    }

    fun dismissWin() {
        _win.value = null
    }

    private fun update(next: ShikakuState) {
        val s = _state.value ?: return
        if (next == s) return
        _state.value = next
        if (next.completed && !s.completed) onWin(next)
    }

    private fun onWin(s: ShikakuState) {
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
        store.saveJson(s.toJson())
    }

    private fun load(): ShikakuState? = store.loadJson()?.let { json ->
        ShikakuState.fromJson(json, settings.help.value, settings.penaltyPercent.value)
    }

    private fun loadStats() = Difficulty.entries.associateWith(store::stats)
}

package com.puzzlenook.shade

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

/** Star Battle, Hitori, Nurikabe, Takuzu, Mosaic, Light Up, Tents, Kurodoko, Norinori, Battleships, Aquarium, Heyawake and LITS: tap or drag to mark cells, like nonograms. */
open class ShadeViewModel(app: Application, val variant: Variant) : AndroidViewModel(app) {
    private val store = GameStore(app, variant)
    private val settings = (app as PuzzleNookApplication).settings

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ShadeState?> = _state.asStateFlow()

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
            val puzzle = withContext(Dispatchers.Default) { ShadeState.generate(variant, difficulty) }
            _state.value = ShadeState.fromPuzzle(puzzle).copy(penaltyPercent = settings.penaltyPercent.value, help = settings.help.value)
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

    // --- Painting: one stroke marks, or clears, a straight line of cells.

    private var strokeStart = -1
    private var strokePaint = Mark.NONE
    private var strokeFrom = Mark.NONE
    private var strokeAxisRow: Boolean? = null
    private var strokeBefore: ShadeState? = null

    fun strokeStart(cell: Int) {
        val s = _state.value ?: return
        if (s.completed || s.isBlocked(cell)) return
        val target = if (s.crossMode) Mark.CROSSED else Mark.FILLED
        val current = s.marks[cell]
        strokePaint = if (current == target) Mark.NONE else target
        strokeFrom = current
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

    fun setCrossMode(on: Boolean) {
        _state.value = _state.value?.copy(crossMode = on)
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

    private fun update(next: ShadeState) {
        val s = _state.value ?: return
        if (next == s) return
        _state.value = next
        if (next.completed && !s.completed) onWin(next)
    }

    private fun onWin(s: ShadeState) {
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

    private fun load(): ShadeState? = store.loadJson()?.let { json ->
        ShadeState.fromJson(variant, json, settings.help.value, settings.penaltyPercent.value)
    }

    private fun loadStats() = Difficulty.entries.associateWith(store::stats)
}

class StarBattleViewModel(app: Application) : ShadeViewModel(app, Variant.STAR_BATTLE)
class HitoriViewModel(app: Application) : ShadeViewModel(app, Variant.HITORI)
class NurikabeViewModel(app: Application) : ShadeViewModel(app, Variant.NURIKABE)
class TakuzuViewModel(app: Application) : ShadeViewModel(app, Variant.TAKUZU)
class MosaicViewModel(app: Application) : ShadeViewModel(app, Variant.MOSAIC)
class LightUpViewModel(app: Application) : ShadeViewModel(app, Variant.LIGHT_UP)
class TentsViewModel(app: Application) : ShadeViewModel(app, Variant.TENTS)
class KurodokoViewModel(app: Application) : ShadeViewModel(app, Variant.KURODOKO)
class NorinoriViewModel(app: Application) : ShadeViewModel(app, Variant.NORINORI)
class BattleshipsViewModel(app: Application) : ShadeViewModel(app, Variant.BATTLESHIPS)
class AquariumViewModel(app: Application) : ShadeViewModel(app, Variant.AQUARIUM)
class HeyawakeViewModel(app: Application) : ShadeViewModel(app, Variant.HEYAWAKE)
class LitsViewModel(app: Application) : ShadeViewModel(app, Variant.LITS)

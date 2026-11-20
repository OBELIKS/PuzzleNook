package com.puzzlenook.sudoku

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.puzzlenook.PuzzleNookApplication
import com.puzzlenook.R
import com.puzzlenook.ui.UiText
import com.puzzlenook.ink.InkStroke
import com.puzzlenook.ink.isSmallWriting
import com.puzzlenook.ink.bounds
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WinResult(
    val playSeconds: Long,
    val penaltySeconds: Long,
    val finalSeconds: Long,
    val mistakes: Int,
    val hintHalfPoints: Int,
    val penaltyHalfPoints: Int,
    val penaltyPercent: Int,
    val newBest: Boolean,
)

open class SudokuViewModel(app: Application, val variant: Variant) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, Variant.CLASSIC)

    private val store = GameStore(app, variant)

    private val settings = (app as PuzzleNookApplication).settings

    private val _state = MutableStateFlow(store.load(settings.help.value))
    val state: StateFlow<GameState?> = _state.asStateFlow()

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

    private val handwriting = (app as PuzzleNookApplication).handwriting

    private val _message = MutableStateFlow<UiText?>(null)
    /** Short-lived status line, e.g. when handwriting couldn't be read. */
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private var messageJob: Job? = null

    private class LastInk(val cell: Int, val strokes: List<InkStroke>, val digit: Int, val notes: Boolean)
    private var lastInk: LastInk? = null

    private val undoStack = ArrayDeque<List<Cell>>()
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
        // The penalty follows the setting, also for a game already in progress.
        viewModelScope.launch {
            settings.penaltyPercent.collect { p ->
                _state.value = _state.value?.copy(penaltyPercent = p)
            }
        }
    }

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

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
            val puzzle = withContext(Dispatchers.Default) {
                when (variant) {
                    Variant.CLASSIC -> SudokuGenerator.generate(difficulty)
                    Variant.KILLER -> KillerGenerator.generate(difficulty)
                    Variant.KAKURO -> KakuroGenerator.generate(difficulty)
                    Variant.JIGSAW -> JigsawGenerator.generate(difficulty)
                    Variant.SUGURU -> SuguruGenerator.generate(difficulty)
                    Variant.FILLOMINO -> FillominoGenerator.generate(difficulty)
                    Variant.FUTOSHIKI, Variant.CALCUDOKU, Variant.SKYSCRAPERS, Variant.KROPKI -> LatinGenerator.generate(variant, difficulty)
                    Variant.NONOGRAM, Variant.STAR_BATTLE, Variant.HITORI, Variant.NURIKABE, Variant.TAKUZU, Variant.MOSAIC, Variant.LIGHT_UP, Variant.TENTS, Variant.KURODOKO, Variant.SLITHERLINK, Variant.MASYU, Variant.SHIKAKU, Variant.NORINORI, Variant.BRIDGES, Variant.HIDATO, Variant.BATTLESHIPS, Variant.AQUARIUM, Variant.HEYAWAKE, Variant.LITS, Variant.GALAXIES, Variant.YAJILIN ->
                        error("$variant has its own view model")
                }
            }
            _state.value = GameState.fromPuzzle(puzzle, variant).copy(penaltyPercent = settings.penaltyPercent.value, help = settings.help.value)
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

    fun select(index: Int) = update(recordUndo = false) {
        if (it.cells[index].blocked) it else it.copy(selected = if (it.selected == index) null else index)
    }

    /** Digit from the number pad. Correcting a just-handwritten digit teaches the recogniser. */
    fun input(digit: Int) {
        val ink = lastInk
        lastInk = null
        val s = _state.value
        if (ink != null && s != null && !ink.notes && !s.notesMode && s.selected == ink.cell &&
            s.cells[ink.cell].value == ink.digit && digit != ink.digit
        ) {
            handwriting.learn(ink.strokes, digit)
        }
        update { it.enter(digit) }
    }

    /** A handwritten character landed on [cell]; strokes are in cell-local pixels. */
    fun onHandwriting(cell: Int, strokes: List<InkStroke>, cellSize: Float) {
        val s = _state.value ?: return
        if (s.completed) return
        lastInk = null
        if (s.cells[cell].blocked) return
        if (s.cells[cell].given) {
            update(recordUndo = false) { it.copy(selected = cell) }
            return
        }
        viewModelScope.launch {
            val result = handwriting.recognize(strokes, cellSize, cellSize)
            update(recordUndo = false) { it.copy(selected = cell) }
            if (result == null) {
                flashMessage(UiText(R.string.flash_unreadable))
                return@launch
            }
            val current = _state.value ?: return@launch
            if (result.digit > current.digits) {
                flashMessage(UiText(R.string.flash_digit_range, listOf(current.digits)))
                return@launch
            }
            val target = current.cells[cell]
            val small = isSmallWriting(strokes, cellSize)
            val asNote = current.notesMode || (settings.smallWritingNotes.value && small)
            val bounds = strokes.bounds()
            bounds?.let { b ->
                Log.d("Handwriting", "size %.2f×%.2f of cell → %s".format(
                    (b[2] - b[0]) / cellSize, (b[3] - b[1]) / cellSize,
                    if (result.digit == 0) "delete" else if (asNote) "note" else "answer"))
            }
            when {
                // 0 is the delete gesture.
                result.digit == 0 -> when {
                    target.value != 0 -> update { it.erase() }
                    target.notes == 0 -> flashMessage(UiText(R.string.flash_nothing_to_delete))
                    // A small 0 deletes just the note it was written over.
                    small && bounds != null -> {
                        val fx = (bounds[0] + bounds[2]) / 2 / cellSize
                        val fy = (bounds[1] + bounds[3]) / 2 / cellSize
                        GameState.nearestNote(target, fx, fy)?.let { d -> update { it.removeNote(d) } }
                    }
                    // A big 0 clears all the notes.
                    else -> update { it.erase() }
                }
                asNote -> when {
                    target.value != 0 -> flashMessage(UiText(R.string.flash_erase_first))
                    // Writing a note that's already there removes it.
                    target.hasNote(result.digit) -> update { it.removeNote(result.digit) }
                    else -> update { it.addNote(result.digit) }
                }
                else -> update { it.enter(result.digit) }
            }
            if (result.digit != 0) lastInk = LastInk(cell, strokes, result.digit, asNote)
        }
    }

    private fun flashMessage(text: UiText) {
        messageJob?.cancel()
        _message.value = text
        messageJob = viewModelScope.launch {
            delay(2_500)
            _message.value = null
        }
    }
    fun erase() = update { it.erase() }
    fun hint() = update { it.hint() }
    fun toggleNotes() = update(recordUndo = false) { it.copy(notesMode = !it.notesMode) }

    /** Hide errors, or show them again (which charges the mistakes made meanwhile). */
    fun toggleShowWrong() {
        update(recordUndo = false) { it.withShowWrong(!it.showWrong) }
        _state.value?.let { store.setErrorsHidden(it.difficulty, !it.showWrong) }
    }

    fun undo() {
        val s = _state.value ?: return
        if (s.completed) return
        val previous = undoStack.removeLastOrNull() ?: return
        _state.value = s.restored(previous)
        persist()
    }

    fun dismissWin() {
        _win.value = null
    }

    private inline fun update(recordUndo: Boolean = true, transform: (GameState) -> GameState) {
        val s = _state.value ?: return
        val next = transform(s)
        if (next == s) return
        if (recordUndo && next.cells != s.cells) {
            undoStack.addLast(s.cells)
            if (undoStack.size > 200) undoStack.removeFirst()
        }
        _state.value = next
        if (next.completed && !s.completed) onWin(next) else persist()
    }

    private fun onWin(s: GameState) {
        undoStack.clear()
        store.clear()
        // Best times compare the final time, penalties included.
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
        _canUndo.value = undoStack.isNotEmpty() && _state.value?.completed == false
        _state.value?.let(store::save)
    }

    private fun loadStats() = Difficulty.entries.associateWith(store::stats)
}

/** Killer sudoku runs on the same game logic with its own saved game and stats. */
class KillerSudokuViewModel(app: Application) : SudokuViewModel(app, Variant.KILLER)

class JigsawViewModel(app: Application) : SudokuViewModel(app, Variant.JIGSAW)

class SuguruViewModel(app: Application) : SudokuViewModel(app, Variant.SUGURU)
class FillominoViewModel(app: Application) : SudokuViewModel(app, Variant.FILLOMINO)
/** Kakuro shares the digit-grid game logic (runs are sum cages). */
class KakuroViewModel(app: Application) : SudokuViewModel(app, Variant.KAKURO)
class FutoshikiViewModel(app: Application) : SudokuViewModel(app, Variant.FUTOSHIKI)
class KropkiViewModel(app: Application) : SudokuViewModel(app, Variant.KROPKI)
class CalcudokuViewModel(app: Application) : SudokuViewModel(app, Variant.CALCUDOKU)
class SkyscrapersViewModel(app: Application) : SudokuViewModel(app, Variant.SKYSCRAPERS)
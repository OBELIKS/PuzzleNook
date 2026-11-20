package com.puzzlenook.ui.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.puzzlenook.ink.InkPoint
import com.puzzlenook.ink.InkStroke
import com.puzzlenook.ink.bounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collects pen strokes written on a square board of [cells]×[cells] (coordinates in board pixels).
 * After the pen has been lifted for [PAUSE_MS] the strokes are handed over as one
 * character, mapped to the cell under their centre. A short pen dab counts as a tap.
 */
class InkController(
    private val scope: CoroutineScope,
    private val onTap: (cell: Int) -> Unit,
    private val onCharacter: (cell: Int, cellLocalStrokes: List<InkStroke>, cellSize: Float) -> Unit,
) {
    /** Board edge length in px; set by the board when it is measured. */
    var boardSize = 0f

    /** Cells per side (9 for sudoku, more for big kakuro); set by the board. */
    var cells = 9

    /** Finished strokes of the character being written (drawn by the board). */
    val strokes = mutableStateListOf<InkStroke>()

    /** The stroke currently under the pen. */
    var current by mutableStateOf<List<InkPoint>>(emptyList())
        private set

    /** Called after a character is handed over, e.g. so Onyx can wipe its raw ink layer. */
    var onCleared: (() -> Unit)? = null

    private var pending: Job? = null
    private val cellSize get() = boardSize / cells

    fun begin(x: Float, y: Float, t: Long) {
        pending?.cancel()
        current = listOf(InkPoint(x, y, t))
    }

    fun move(x: Float, y: Float, t: Long) {
        if (current.isNotEmpty()) current = current + InkPoint(x, y, t)
    }

    fun end() {
        val stroke = InkStroke(current)
        current = emptyList()
        addStroke(stroke)
    }

    /** Adds a complete stroke at once (used by the Onyx raw-input path). */
    fun addStroke(stroke: InkStroke) {
        pending?.cancel()
        if (stroke.points.isEmpty() || boardSize <= 0f) return
        if (strokes.isEmpty() && stroke.length < cellSize * TAP_FRACTION) {
            val p = stroke.points.first()
            cellAt(p.x, p.y)?.let(onTap)
            onCleared?.invoke()
            return
        }
        strokes += stroke
        pending = scope.launch {
            delay(PAUSE_MS)
            submit()
        }
    }

    /** A finger tap on a cell. */
    fun tap(cell: Int) = onTap(cell)

    /** Pen went down again (Onyx path): hold off recognition until it lifts. */
    fun holdPending() {
        pending?.cancel()
    }

    fun clear() {
        pending?.cancel()
        strokes.clear()
        current = emptyList()
        onCleared?.invoke()
    }

    private fun submit() {
        val all = strokes.toList()
        strokes.clear()
        onCleared?.invoke()
        val b = all.bounds() ?: return
        val cell = cellAt((b[0] + b[2]) / 2, (b[1] + b[3]) / 2) ?: return
        val cs = cellSize
        val ox = (cell % cells) * cs
        val oy = (cell / cells) * cs
        onCharacter(cell, all.map { it.translated(-ox, -oy) }, cs)
    }

    private fun cellAt(x: Float, y: Float): Int? {
        if (boardSize <= 0f || x < 0 || y < 0 || x >= boardSize || y >= boardSize) return null
        return (y / cellSize).toInt().coerceIn(0, cells - 1) * cells + (x / cellSize).toInt().coerceIn(0, cells - 1)
    }

    companion object {
        const val PAUSE_MS = 650L
        /** Strokes shorter than this fraction of a cell are taps (a "1" is much longer). */
        const val TAP_FRACTION = 0.15f
    }
}

@Composable
fun rememberInkController(
    onTap: (Int) -> Unit,
    onCharacter: (Int, List<InkStroke>, Float) -> Unit,
): InkController {
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val character by rememberUpdatedState(onCharacter)
    return remember { InkController(scope, { tap(it) }, { c, s, size -> character(c, s, size) }) }
}

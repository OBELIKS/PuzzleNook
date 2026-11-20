package com.puzzlenook.ui.slither

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.floor

/**
 * Tap a cell, or drag over cells (a stroke), on a board of [n]×[n] cells with [margin] cells around it.
 * [onStroke] gets the first cell when the drag starts, [onMove] every other cell the finger enters,
 * [onEnd] when it lifts; a tap goes to [onTap].
 */
fun Modifier.cellGestures(
    n: Int,
    margin: Float,
    onTap: (Int) -> Unit,
    onStroke: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(n) {
    // Read the size on every event: this block isn't restarted when the board is resized.
    fun cellAt(p: Offset): Int? {
        val cs = size.width / (n + 2 * margin)
        val c = floor(p.x / cs - margin).toInt()
        val r = floor(p.y / cs - margin).toInt()
        return if (r in 0 until n && c in 0 until n) r * n + c else null
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val first = cellAt(down.position)
        var dragging = false
        var last = first
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            if (first == null) continue
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                onStroke(first)
            }
            if (!dragging) continue
            val cell = cellAt(change.position) ?: continue
            if (cell != last) {
                last = cell
                onMove(cell)
            }
        }
        if (dragging) onEnd() else first?.let(onTap)
    }
}

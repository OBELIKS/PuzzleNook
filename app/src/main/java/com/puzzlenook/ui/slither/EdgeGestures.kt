package com.puzzlenook.ui.slither

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.puzzlenook.slither.SlitherGrid
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Tap near an edge to toggle it; drag from dot to dot to draw (or erase) a run of edges. Shared by
 * the edge-drawing boards. The board is [cells] cells wide plus [margin] cells on every side; dot
 * (r, c) of [grid] sits at grid position (c + [shift], r + [shift]) in cells: shift 0 puts dots on
 * cell corners (Slitherlink), 0.5 on cell centres (Masyu). With [byMidpoint] a tap picks the edge
 * whose midpoint is nearest (needed when dots are big targets), otherwise the nearest line.
 */
fun Modifier.edgeGestures(
    grid: SlitherGrid,
    cells: Int,
    margin: Float,
    shift: Float,
    byMidpoint: Boolean,
    onTap: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEdge: (Int) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(grid.n, cells) {
    val n = grid.n
    // Read the size on every event: this block isn't restarted when the board is resized.
    fun gridPos(p: Offset): Offset {
        val cs = size.width / (cells + 2 * margin)
        return Offset(p.x / cs - margin - shift, p.y / cs - margin - shift)
    }
    fun nearestEdge(p: Offset): Int? {
        val g = gridPos(p)
        var best: Int? = null
        var bestDist = if (byMidpoint) 0.5f else 0.42f
        val hr = g.y.roundToInt()
        val hc = floor(g.x).toInt()
        if (hr in 0..n && hc in 0 until n) {
            val dist = if (byMidpoint) hypot(g.y - hr, g.x - hc - 0.5f) else abs(g.y - hr)
            if (dist < bestDist) { best = grid.h(hr, hc); bestDist = dist }
        }
        val vc = g.x.roundToInt()
        val vr = floor(g.y).toInt()
        if (vc in 0..n && vr in 0 until n) {
            val dist = if (byMidpoint) hypot(g.x - vc, g.y - vr - 0.5f) else abs(g.x - vc)
            if (dist < bestDist) best = grid.v(vr, vc)
        }
        return best
    }
    fun nearestDot(p: Offset, within: Float): Pair<Int, Int>? {
        val g = gridPos(p)
        val r = g.y.roundToInt().coerceIn(0, n)
        val c = g.x.roundToInt().coerceIn(0, n)
        return if (abs(g.y - r) <= within && abs(g.x - c) <= within) r to c else null
    }
    fun edgeBetween(a: Pair<Int, Int>, b: Pair<Int, Int>): Int? = when {
        a.first == b.first && abs(a.second - b.second) == 1 -> grid.h(a.first, minOf(a.second, b.second))
        a.second == b.second && abs(a.first - b.first) == 1 -> grid.v(minOf(a.first, b.first), a.second)
        else -> null
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        var dragging = false
        var current = nearestDot(down.position, 1f) ?: return@awaitEachGesture
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                onDragStart()
            }
            if (!dragging) continue
            val dot = nearestDot(change.position, 0.32f) ?: continue
            // Fast strokes may skip dots: walk there one edge at a time along a row or column.
            while (dot != current) {
                val dr = dot.first.compareTo(current.first)
                val dc = dot.second.compareTo(current.second)
                if (dr != 0 && dc != 0) {
                    // Cut a corner: no single edge joins them, so just move on.
                    current = dot
                    break
                }
                val step = Pair(current.first + dr, current.second + dc)
                edgeBetween(current, step)?.let(onDragEdge)
                current = step
            }
        }
        if (dragging) onDragEnd() else nearestEdge(down.position)?.let(onTap)
    }
}

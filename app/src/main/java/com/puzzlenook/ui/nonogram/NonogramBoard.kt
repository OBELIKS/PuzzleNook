package com.puzzlenook.ui.nonogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.nonogram.NonogramState
import com.puzzlenook.ui.theme.LocalEink

/** Clue numbers take this fraction of a cell each. */
private const val CLUE = 0.62f

/** Width / height of the board including clue areas. */
fun nonogramAspect(state: NonogramState): Float {
    val rowClues = state.rowClues.maxOf { maxOf(it.size, 1) }
    val colClues = state.colClues.maxOf { maxOf(it.size, 1) }
    return (state.size + rowClues * CLUE + 0.3f) / (state.size + colClues * CLUE + 0.3f)
}

/**
 * Nonogram grid with row clues on the left and column clues on top. A finger or pen
 * stroke starts on one cell and paints along its row or column ([onStrokeMove]).
 */
@Composable
fun NonogramBoard(
    state: NonogramState,
    fillColor: Color?,
    onStrokeStart: (Int) -> Unit,
    onStrokeMove: (Int) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()
    val n = state.size
    val rowClueCount = state.rowClues.maxOf { maxOf(it.size, 1) }
    val colClueCount = state.colClues.maxOf { maxOf(it.size, 1) }

    Canvas(
        modifier
            .aspectRatio(nonogramAspect(state))
            .pointerInput(n, rowClueCount, colClueCount) {
                fun geometry(): Triple<Float, Float, Float> {
                    val cs = size.width / (n + rowClueCount * CLUE + 0.3f)
                    return Triple(cs, cs * (rowClueCount * CLUE + 0.3f), cs * (colClueCount * CLUE + 0.3f))
                }
                fun cellAt(p: Offset, clamp: Boolean): Int? {
                    val (cs, ox, oy) = geometry()
                    var c = ((p.x - ox) / cs).toInt()
                    var r = ((p.y - oy) / cs).toInt()
                    if (p.x < ox || p.y < oy || c >= n || r >= n) {
                        if (!clamp) return null
                        c = if (p.x < ox) 0 else c.coerceIn(0, n - 1)
                        r = if (p.y < oy) 0 else r.coerceIn(0, n - 1)
                    }
                    return r * n + c
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = cellAt(down.position, clamp = false) ?: return@awaitEachGesture
                    down.consume()
                    onStrokeStart(start)
                    var last = start
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val cell = cellAt(change.position, clamp = true) ?: continue
                        if (cell != last) {
                            last = cell
                            onStrokeMove(cell)
                        }
                    }
                    onStrokeEnd()
                }
            }
    ) {
        val cs = size.width / (n + rowClueCount * CLUE + 0.3f)
        val ox = cs * (rowClueCount * CLUE + 0.3f)
        val oy = cs * (colClueCount * CLUE + 0.3f)
        val gridSize = cs * n

        drawRect(scheme.surface)
        drawRect(scheme.surfaceContainerLowest, Offset(ox, oy), Size(gridSize, gridSize))

        // Clues; finished lines fade so the open ones stand out.
        val clueSize = (cs * 0.5f).toSp()
        val ink = if (eink) Color.Black else scheme.onSurface
        val faded = if (eink) Color(0xFFAAAAAA) else scheme.onSurface.copy(alpha = 0.3f)
        for (r in 0 until n) {
            val clue = state.rowClues[r].ifEmpty { listOf(0) }
            val color = if (state.rowDone(r)) faded else ink
            clue.reversed().forEachIndexed { k, v ->
                val l = measurer.measure(v.toString(), TextStyle(fontSize = clueSize, color = color, fontWeight = FontWeight.Medium))
                val cx = ox - cs * 0.15f - (k + 0.5f) * cs * CLUE
                drawText(l, topLeft = Offset(cx - l.size.width / 2, oy + r * cs + (cs - l.size.height) / 2))
            }
        }
        for (c in 0 until n) {
            val clue = state.colClues[c].ifEmpty { listOf(0) }
            val color = if (state.colDone(c)) faded else ink
            clue.reversed().forEachIndexed { k, v ->
                val l = measurer.measure(v.toString(), TextStyle(fontSize = clueSize, color = color, fontWeight = FontWeight.Medium))
                val cy = oy - cs * 0.15f - (k + 0.5f) * cs * CLUE
                drawText(l, topLeft = Offset(ox + c * cs + (cs - l.size.width) / 2, cy - l.size.height / 2))
            }
        }

        // Cells.
        val fill = fillColor ?: if (eink) Color.Black else scheme.onSurface
        val crossColor = if (eink) Color(0xFF555555) else scheme.onSurfaceVariant
        val wrongColor = if (eink) Color.Black else scheme.error
        val gap = maxOf(1f, cs * 0.06f)
        for (i in 0 until n * n) {
            val x = ox + (i % n) * cs
            val y = oy + (i / n) * cs
            val wrong = state.shownWrong(i)
            when (state.marks[i]) {
                Mark.FILLED -> {
                    drawRect(if (wrong) wrongColor else fill, Offset(x + gap, y + gap), Size(cs - 2 * gap, cs - 2 * gap))
                    if (wrong && eink) drawCross(x, y, cs, Color.White, 2.dp.toPx())
                }
                Mark.CROSSED -> {
                    if (wrong && eink) drawRect(Color(0xFFBBBBBB), Offset(x, y), Size(cs, cs))
                    drawCross(x, y, cs, if (wrong) wrongColor else crossColor, (if (wrong) 2.5f else 1.5f).dp.toPx())
                }
                Mark.NONE -> {}
            }
        }

        // Grid: thin lines, thicker every five cells and around the edge.
        val thin = (if (eink) 1.2f else 1f).dp.toPx()
        val thick = (if (eink) 2.5f else 2f).dp.toPx()
        val minor = if (eink) Color(0xFF777777) else scheme.outlineVariant
        val major = if (eink) Color.Black else scheme.onSurface.copy(alpha = 0.85f)
        for (k in 0..n) {
            val heavy = k % 5 == 0 || k == n
            val p = k * cs
            drawLine(if (heavy) major else minor, Offset(ox + p, oy), Offset(ox + p, oy + gridSize), if (heavy) thick else thin)
            drawLine(if (heavy) major else minor, Offset(ox, oy + p), Offset(ox + gridSize, oy + p), if (heavy) thick else thin)
        }

        // Hint (first press): frame the cell.
        state.activeHintCell?.let { h ->
            val w = (if (eink) 4f else 3.5f).dp.toPx()
            val inset = w / 2
            drawRect(
                color = if (eink) Color.Black else scheme.tertiary,
                topLeft = Offset(ox + (h % n) * cs + inset, oy + (h / n) * cs + inset),
                size = Size(cs - 2 * inset, cs - 2 * inset),
                style = Stroke(width = w),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCross(x: Float, y: Float, cs: Float, color: Color, width: Float) {
    val m = cs * 0.28f
    drawLine(color, Offset(x + m, y + m), Offset(x + cs - m, y + cs - m), width)
    drawLine(color, Offset(x + cs - m, y + m), Offset(x + m, y + cs - m), width)
}

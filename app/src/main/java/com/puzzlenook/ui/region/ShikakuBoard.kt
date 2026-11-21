package com.puzzlenook.ui.region

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.region.Rect
import com.puzzlenook.region.ShikakuState
import com.puzzlenook.ui.theme.LocalEink

/**
 * Shikaku board: numbers on a grid, the rectangles you drew (outlined, flat light fill). Drag from
 * one cell to another to draw a rectangle (a preview shows while you drag, it is placed when you let
 * go and replaces the rectangles it overlaps); tap inside a rectangle to remove it, tap a lone 1 for a single cell. Works the same
 * with a finger or a pen (generic pointer events).
 */
@Composable
fun ShikakuBoard(
    state: ShikakuState,
    onDraw: (from: Int, to: Int) -> Unit,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()
    val n = state.n
    var preview by remember { mutableStateOf<Rect?>(null) }
    val draw by rememberUpdatedState(onDraw)
    val tap by rememberUpdatedState(onTap)

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(n) {
                // Read the size on every event: this block isn't restarted when the board is resized.
                fun cellAt(p: Offset): Int {
                    val cs = size.width / n.toFloat()
                    val c = (p.x / cs).toInt().coerceIn(0, n - 1)
                    val r = (p.y / cs).toInt().coerceIn(0, n - 1)
                    return r * n + c
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = cellAt(down.position)
                    var end = start
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            end = cellAt(change.position)
                            preview = Rect.between(start, end, n)
                        }
                    }
                    preview = null
                    if (!dragging) tap(start) else if (end != start) draw(start, end)
                }
            }
    ) {
        val cs = size.width / n
        val gw = cs * n
        val ink = if (eink) Color.Black else scheme.onSurface
        val mutedInk = if (eink) Color(0xFF666666) else scheme.onSurfaceVariant
        val wrongColor = if (eink) Color.Black else scheme.error
        val fillColor = if (eink) Color(0xFFE4E4E4) else scheme.secondaryContainer
        val fixedFill = if (eink) Color(0xFFCFCFCF) else scheme.tertiaryContainer
        val outline = if (eink) Color.Black else scheme.primary
        val thin = (if (eink) 1.2f else 1f).dp.toPx()
        val thick = (if (eink) 3.5f else 3f).dp.toPx()
        drawRect(scheme.surfaceContainerLowest)

        // Rectangles: flat fill first, then the grid over it, then outlines.
        for (r in state.rects) {
            val fixed = state.isFixed(r)
            drawRect(if (fixed) fixedFill else fillColor, Offset(r.c0 * cs, r.r0 * cs), Size(r.w * cs, r.h * cs))
        }
        val minor = if (eink) Color(0xFF999999) else scheme.outlineVariant
        for (k in 1 until n) {
            drawLine(minor, Offset(k * cs, 0f), Offset(k * cs, gw), thin)
            drawLine(minor, Offset(0f, k * cs), Offset(gw, k * cs), thin)
        }
        val inset = thick / 2 + 1.dp.toPx()
        for (r in state.rects) {
            val wrong = state.shownWrong(r)
            drawRect(
                if (wrong) wrongColor else outline,
                Offset(r.c0 * cs + inset, r.r0 * cs + inset),
                Size(r.w * cs - 2 * inset, r.h * cs - 2 * inset),
                style = Stroke(if (wrong && eink) thick * 1.6f else thick),
            )
            if (wrong && eink) {
                // Black on grey is all e-ink has: a diagonal across the rectangle says "wrong".
                drawLine(wrongColor, Offset(r.c0 * cs + inset, r.r0 * cs + inset), Offset((r.c1 + 1) * cs - inset, (r.r1 + 1) * cs - inset), thin * 2)
            }
        }

        // The rectangle being dragged.
        preview?.let { p ->
            val blocked = !state.canPlace(p)
            val color = if (blocked) wrongColor else outline
            drawRect(
                color,
                Offset(p.c0 * cs + inset, p.r0 * cs + inset),
                Size(p.w * cs - 2 * inset, p.h * cs - 2 * inset),
                style = Stroke(thick, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cs * 0.2f, cs * 0.14f))),
            )
        }

        // Numbers.
        for (i in 0 until n * n) {
            val number = state.numbers[i]
            if (number <= 0) continue
            val color = if (state.clueDone(i)) mutedInk else ink
            val l = measurer.measure(
                number.toString(),
                TextStyle(fontSize = (cs * 0.5f).toSp(), color = color, fontWeight = FontWeight.Bold),
            )
            drawText(l, topLeft = Offset((i % n) * cs + (cs - l.size.width) / 2, (i / n) * cs + (cs - l.size.height) / 2))
        }

        val frame = if (eink) Color.Black else scheme.onSurface.copy(alpha = 0.85f)
        drawRect(frame, Offset(thick / 2, thick / 2), Size(gw - thick, gw - thick), style = Stroke(thick))

        state.activeHintClue?.let { h ->
            val w = (if (eink) 4f else 3.5f).dp.toPx()
            val pad = w / 2 + 2.dp.toPx()
            drawRect(
                color = if (eink) Color.Black else scheme.tertiary,
                topLeft = Offset((h % n) * cs + pad, (h / n) * cs + pad),
                size = Size(cs - 2 * pad, cs - 2 * pad),
                style = Stroke(width = w),
            )
        }
    }
}

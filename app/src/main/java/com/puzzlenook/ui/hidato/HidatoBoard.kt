package com.puzzlenook.ui.hidato

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.hidato.HidatoState
import com.puzzlenook.ink.InkPoint
import com.puzzlenook.ui.ink.InkController
import com.puzzlenook.ui.theme.LocalEink

/**
 * Hidato grid. Finger taps select cells; with handwriting on, a pen writes numbers into [ink].
 * Thin links join numbers that follow each other and touch, so the path shows as it grows.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HidatoBoard(
    state: HidatoState,
    ink: InkController,
    handwriting: Boolean,
    stylusHandledExternally: Boolean,
    answerColor: Color?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()
    val n = state.n

    Canvas(
        modifier
            .aspectRatio(1f)
            .onSizeChanged {
                ink.boardSize = it.width.toFloat()
                ink.cells = n
            }
            .pointerInput(handwriting, stylusHandledExternally, n) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    when {
                        pen && handwriting && stylusHandledExternally -> waitForUpOrCancellation()
                        pen && handwriting -> {
                            if (down.type == PointerType.Eraser) {
                                ink.clear()
                                waitForUpOrCancellation()
                                return@awaitEachGesture
                            }
                            down.consume()
                            ink.begin(down.position.x, down.position.y, down.uptimeMillis)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.historical.forEach { ink.move(it.position.x, it.position.y, it.uptimeMillis) }
                                ink.move(change.position.x, change.position.y, change.uptimeMillis)
                                change.consume()
                                if (!change.pressed) break
                            }
                            ink.end()
                        }
                        else -> {
                            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                            if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                                val cs = size.width / n.toFloat()
                                val col = (up.position.x / cs).toInt().coerceIn(0, n - 1)
                                val row = (up.position.y / cs).toInt().coerceIn(0, n - 1)
                                ink.tap(row * n + col)
                            }
                        }
                    }
                }
            }
    ) {
        ink.cells = n
        val cs = size.width / n
        val selected = state.selected
        val selectedValue = selected?.let { state.values[it] } ?: 0
        drawRect(scheme.surfaceContainerLowest)

        val tinted = BooleanArray(n * n)
        for (i in 0 until n * n) {
            val v = state.values[i]
            val wrong = state.shownWrong(i)
            val neighbour = selectedValue != 0 && v != 0 && (v == selectedValue - 1 || v == selectedValue + 1)
            val bg = if (eink) {
                when {
                    wrong -> Color.Black
                    i == selected -> Color(0xFFB0B0B0)
                    neighbour -> Color(0xFFDADADA)
                    else -> null
                }
            } else {
                when {
                    i == selected -> scheme.primary.copy(alpha = 0.32f)
                    wrong -> scheme.error.copy(alpha = 0.14f)
                    neighbour -> scheme.secondary.copy(alpha = 0.30f)
                    else -> null
                }
            } ?: continue
            tinted[i] = true
            drawRect(bg, Offset((i % n) * cs, (i / n) * cs), Size(cs, cs))
        }

        // Links between consecutive numbers that are both in place.
        val pos = IntArray(state.max + 2) { -1 }
        for (i in 0 until n * n) if (state.values[i] != 0 && !state.shownWrong(i)) pos[state.values[i]] = i
        val link = if (eink) Color(0xFF999999) else scheme.primary.copy(alpha = 0.35f)
        for (k in 1 until state.max) {
            val a = pos[k]
            val b = pos[k + 1]
            if (a < 0 || b < 0) continue
            drawLine(
                link,
                Offset((a % n + 0.5f) * cs, (a / n + 0.5f) * cs),
                Offset((b % n + 0.5f) * cs, (b / n + 0.5f) * cs),
                maxOf(2.dp.toPx(), cs * 0.05f),
                cap = StrokeCap.Round,
            )
        }

        val numberSize = (cs * if (state.max >= 10) 0.42f else 0.5f).toSp()
        for (i in 0 until n * n) {
            val v = state.values[i]
            val pending = state.pending.takeIf { i == selected }
            if (v == 0 && pending == null) continue
            val wrong = state.shownWrong(i)
            val text = if (pending != null) "$pending…" else v.toString()
            val given = state.fixed[i]
            val color = when {
                pending != null -> scheme.onSurfaceVariant
                eink && wrong -> Color.White
                given -> scheme.onSurface
                wrong -> scheme.error
                answerColor != null -> answerColor
                eink -> Color.Black
                else -> scheme.primary
            }
            val weight = when {
                given -> if (eink) FontWeight.Bold else FontWeight.SemiBold
                eink && answerColor == null -> FontWeight.Light
                else -> FontWeight.Normal
            }
            // A soft backdrop keeps numbers readable over the links.
            val l = measurer.measure(text, TextStyle(fontSize = numberSize, color = color, fontWeight = weight))
            val x = (i % n) * cs
            val y = (i / n) * cs
            // Not on highlighted cells: the disc would wipe out their tint.
            if (!tinted[i]) {
                drawCircle(scheme.surfaceContainerLowest, cs * 0.3f, Offset(x + cs / 2, y + cs / 2))
            }
            drawText(l, topLeft = Offset(x + (cs - l.size.width) / 2, y + (cs - l.size.height) / 2))
        }

        val thin = (if (eink) 1.2f else 1f).dp.toPx()
        val thick = (if (eink) 3f else 2.5f).dp.toPx()
        val minor = if (eink) Color(0xFF777777) else scheme.outlineVariant
        val major = if (eink) Color.Black else scheme.onSurface.copy(alpha = 0.85f)
        for (k in 1 until n) {
            drawLine(minor, Offset(k * cs, 0f), Offset(k * cs, size.height), thin)
            drawLine(minor, Offset(0f, k * cs), Offset(size.width, k * cs), thin)
        }
        drawRect(major, Offset(thick / 2, thick / 2), Size(size.width - thick, size.height - thick), style = Stroke(thick))

        state.activeHintCell?.let { h ->
            val w = (if (eink) 4f else 3.5f).dp.toPx()
            val inset = w / 2 + 2.dp.toPx()
            drawRect(
                color = if (eink) Color.Black else scheme.tertiary,
                topLeft = Offset((h % n) * cs + inset, (h / n) * cs + inset),
                size = Size(cs - 2 * inset, cs - 2 * inset),
                style = Stroke(width = w),
            )
        }

        if (!stylusHandledExternally) {
            val inkColor = answerColor ?: if (eink) Color.Black else scheme.tertiary
            val width = (if (eink) 3f else 2.5f).dp.toPx()
            ink.strokes.forEach { drawInk(it.points, inkColor, width) }
            drawInk(ink.current, inkColor, width)
        }
    }
}

private fun DrawScope.drawInk(points: List<InkPoint>, color: Color, width: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color, width / 2, Offset(points[0].x, points[0].y))
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (k in 1 until points.size) lineTo(points[k].x, points[k].y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

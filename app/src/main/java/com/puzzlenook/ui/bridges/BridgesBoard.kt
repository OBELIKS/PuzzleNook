package com.puzzlenook.ui.bridges

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.bridges.BridgesState
import com.puzzlenook.ui.theme.LocalEink
import kotlin.math.abs

/**
 * Bridges board: islands as numbered circles, bridges as one or two parallel lines. Drag from an
 * island towards a neighbouring island to add a bridge (0 → 1 → 2 → 0); tap a bridge to cycle it.
 * Everything uses generic pointer events, so a finger and a pen behave the same.
 */
@Composable
fun BridgesBoard(
    state: BridgesState,
    lineColor: Color?,
    onCycle: (slot: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()
    val n = state.n
    /** The island a drag started on and where the pointer is now. */
    var preview by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val current by rememberUpdatedState(state)
    val cycle by rememberUpdatedState(onCycle)

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(n) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val cs = size.width / n.toFloat()
                    val st = current
                    val lay = st.layout
                    fun centre(cell: Int) = Offset((cell % n + 0.5f) * cs, (cell / n + 0.5f) * cs)
                    var start = -1
                    var best = 0.5f * cs
                    for (i in lay.islands.indices) {
                        val d = (centre(lay.islands[i]) - down.position).getDistance()
                        if (d <= best) { best = d; start = i }
                    }
                    var end = down.position
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        end = change.position
                        if (!dragging && (end - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                        if (dragging && start >= 0) preview = start to end
                    }
                    preview = null
                    if (!dragging) {
                        if (start >= 0) return@awaitEachGesture
                        // A tap: the bridge nearest the finger.
                        var slot = -1
                        var nearest = 0.3f * cs
                        for ((s, slot0) in lay.slots.withIndex()) {
                            if (st.bridges[s] == 0) continue
                            val a = centre(slot0.a)
                            val b = centre(slot0.b)
                            val d = if (slot0.horizontal) {
                                if (down.position.x < a.x || down.position.x > b.x) continue
                                abs(down.position.y - a.y)
                            } else {
                                if (down.position.y < a.y || down.position.y > b.y) continue
                                abs(down.position.x - a.x)
                            }
                            if (d <= nearest) { nearest = d; slot = s }
                        }
                        if (slot >= 0) cycle(slot)
                    } else if (start >= 0) {
                        val c0 = centre(lay.islands[start])
                        val dx = end.x - c0.x
                        val dy = end.y - c0.y
                        val major = maxOf(abs(dx), abs(dy))
                        if (major >= 0.4f * cs) {
                            val dir = if (abs(dx) >= abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0)
                            val slot = lay.dirSlot[start][dir]
                            if (slot >= 0) cycle(slot)
                        }
                    }
                }
            }
    ) {
        val cs = size.width / n
        val gw = cs * n
        val lay = state.layout
        val ink = if (eink) Color.Black else scheme.onSurface
        val mutedInk = if (eink) Color(0xFF777777) else scheme.onSurfaceVariant
        val wrongColor = if (eink) Color.Black else scheme.error
        val paper = scheme.surfaceContainerLowest
        val doneFill = if (eink) Color(0xFFE4E4E4) else scheme.surfaceContainerHigh
        val bridgeColor = if (eink) Color.Black else lineColor ?: scheme.primary
        val fixedColor = if (eink) Color(0xFF555555) else scheme.tertiary
        val hintBand = if (eink) Color(0xFFCCCCCC) else scheme.tertiaryContainer
        val thin = (if (eink) 1.2f else 1f).dp.toPx()
        val thick = (if (eink) 3.5f else 3f).dp.toPx()
        val line = (if (eink) 3f else 2.6f).dp.toPx()
        fun centre(cell: Int) = Offset((cell % n + 0.5f) * cs, (cell / n + 0.5f) * cs)
        drawRect(paper)

        // Faint dots mark the grid cells.
        val dot = if (eink) Color(0xFFAAAAAA) else scheme.outlineVariant
        for (i in 0 until n * n) drawCircle(dot, 1.4.dp.toPx(), centre(i))

        // The slot a hint points at.
        state.activeHintSlot?.let { s ->
            val slot = lay.slots[s]
            drawLine(hintBand, centre(slot.a), centre(slot.b), cs * 0.4f, StrokeCap.Round)
        }

        // Bridges.
        val gap = cs * 0.09f
        for ((s, slot) in lay.slots.withIndex()) {
            val count = state.bridges[s]
            val a = centre(slot.a)
            val b = centre(slot.b)
            if (count == 0) {
                if (s in state.fixed) {
                    // Revealed as "no bridge here".
                    val m = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                    val r = cs * 0.13f
                    drawLine(fixedColor, m + Offset(-r, -r), m + Offset(r, r), line)
                    drawLine(fixedColor, m + Offset(-r, r), m + Offset(r, -r), line)
                }
                continue
            }
            val wrong = state.shownWrong(s)
            val color = if (wrong) wrongColor else if (s in state.fixed) fixedColor else bridgeColor
            val width = if (wrong && eink) line * 1.5f else line
            val effect = if (wrong && eink) PathEffect.dashPathEffect(floatArrayOf(cs * 0.16f, cs * 0.1f)) else null
            val offsets = if (count == 1) listOf(0f) else listOf(-gap, gap)
            for (o in offsets) {
                val shift = if (slot.horizontal) Offset(0f, o) else Offset(o, 0f)
                drawLine(color, a + shift, b + shift, width, pathEffect = effect)
            }
        }

        // The bridge being dragged.
        preview?.let { (i, p) ->
            val c0 = centre(lay.islands[i])
            val dx = p.x - c0.x
            val dy = p.y - c0.y
            val dir = if (abs(dx) >= abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0)
            val s = lay.dirSlot[i][dir]
            if (s >= 0) {
                val slot = lay.slots[s]
                val other = centre(if (slot.ia == i) slot.b else slot.a)
                drawLine(
                    bridgeColor, c0, other, line,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(cs * 0.2f, cs * 0.14f)),
                )
            }
        }

        // Islands.
        val radius = cs * 0.4f
        for (i in lay.islands.indices) {
            val c = centre(lay.islands[i])
            val over = state.islandOver(i)
            val done = state.islandDone(i)
            drawCircle(if (done) doneFill else paper, radius, c)
            drawCircle(
                if (over) wrongColor else if (done) mutedInk else ink,
                radius, c,
                style = Stroke(if (over && eink) thick * 1.4f else if (done) thin * 1.6f else thick * 0.8f),
            )
            val l = measurer.measure(
                state.islandNumber(i).toString(),
                TextStyle(fontSize = (cs * 0.46f).toSp(), color = if (over) wrongColor else if (done) mutedInk else ink, fontWeight = FontWeight.Bold),
            )
            drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f))
        }

        val frame = if (eink) Color.Black else scheme.onSurface.copy(alpha = 0.85f)
        drawRect(frame, Offset(thick / 2, thick / 2), Size(gw - thick, gw - thick), style = Stroke(thick))
    }
}

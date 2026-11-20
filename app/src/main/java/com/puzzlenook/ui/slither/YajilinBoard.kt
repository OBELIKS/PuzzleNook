package com.puzzlenook.ui.slither

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.slither.YajilinState
import com.puzzlenook.slither.yajilinCount
import com.puzzlenook.slither.yajilinDir
import com.puzzlenook.ui.theme.LocalEink
import kotlin.math.cos
import kotlin.math.sin

/** Margin around the cells, in cells. */
private const val MARGIN = 0.1f

/**
 * Yajilin board: a cell grid, arrow clue cells (grey, with the count), the cells you shade, your dots, and
 * the loop you draw through the cell centres. In Shade mode tap or drag over cells; in Line mode tap near
 * the middle of an edge between two cells, or drag from cell to cell.
 */
@Composable
fun YajilinBoard(
    state: YajilinState,
    lineColor: Color?,
    onTapCell: (Int) -> Unit,
    onStrokeStart: (Int) -> Unit,
    onStrokeMove: (Int) -> Unit,
    onStrokeEnd: () -> Unit,
    onTapEdge: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEdge: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val n = state.n
    val grid = state.grid
    val edges = state.edges
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(1f)
            .then(
                if (state.lineMode) Modifier.edgeGestures(grid, n, MARGIN, 0.5f, true, onTapEdge, onDragStart, onDragEdge, onDragEnd)
                else Modifier.cellGestures(n, MARGIN, onTapCell, onStrokeStart, onStrokeMove, onStrokeEnd),
            ),
    ) {
        val cs = size.width / (n + 2 * MARGIN)
        val o = MARGIN * cs
        fun centre(d: Int) = Offset(o + (d % n + 0.5f) * cs, o + (d / n + 0.5f) * cs)
        val ink = if (eink) Color.Black else scheme.onSurface
        val muted = if (eink) Color(0xFF888888) else scheme.onSurface.copy(alpha = 0.4f)
        val gridColor = if (eink) Color(0xFFBBBBBB) else scheme.onSurface.copy(alpha = 0.18f)
        val line = lineColor ?: if (eink) Color.Black else scheme.primary
        val wrongColor = if (eink) Color.Black else scheme.error
        val crossColor = if (eink) Color(0xFF555555) else scheme.onSurfaceVariant
        val paper = scheme.surfaceContainerLowest
        val clueFill = if (eink) Color(0xFFD8D8D8) else scheme.onSurface.copy(alpha = 0.13f)
        drawRect(paper)

        // Cell grid.
        val thin = 1.dp.toPx()
        for (k in 0..n) {
            drawLine(gridColor, Offset(o + k * cs, o), Offset(o + k * cs, o + n * cs), thin)
            drawLine(gridColor, Offset(o, o + k * cs), Offset(o + n * cs, o + k * cs), thin)
        }

        for (i in 0 until n * n) {
            val x = o + (i % n) * cs
            val y = o + (i / n) * cs
            val clue = state.clues[i]
            if (clue >= 0) {
                drawRect(clueFill, Offset(x, y), Size(cs, cs))
                // E-ink has no red: a clue with too many shaded cells gets a heavy frame instead.
                if (eink && state.showWrong && state.clueOver(i)) {
                    val w = 3.dp.toPx()
                    drawRect(Color.Black, Offset(x + w / 2, y + w / 2), Size(cs - w, cs - w), style = Stroke(w))
                }
                val color = when {
                    state.showWrong && state.clueOver(i) -> wrongColor
                    state.clueDone(i) -> muted
                    else -> ink
                }
                val dir = yajilinDir(clue)
                val label = measurer.measure(
                    yajilinCount(clue).toString(),
                    TextStyle(fontSize = (cs * 0.4f).toSp(), color = color, fontWeight = FontWeight.Bold),
                )
                val c = Offset(x + cs / 2, y + cs / 2)
                // Vertical arrows sit left of the number, horizontal ones above it.
                val vertical = dir < 2
                val arrowAt = if (vertical) Offset(c.x - cs * 0.22f, c.y) else Offset(c.x, c.y - cs * 0.22f)
                val textAt = if (vertical) Offset(c.x + cs * 0.14f, c.y) else Offset(c.x, c.y + cs * 0.17f)
                drawArrow(arrowAt, dir, cs, color)
                drawText(label, topLeft = Offset(textAt.x - label.size.width / 2f, textAt.y - label.size.height / 2f))
                continue
            }
            val mark = state.marks[edges + i]
            val wrong = state.shownWrong(edges + i)
            when (mark) {
                Mark.FILLED -> {
                    val clash = state.showWrong && state.shadeClash(i)
                    val bad = wrong || clash
                    val inset = cs * 0.06f
                    drawRect(if (bad) wrongColor else ink, Offset(x + inset, y + inset), Size(cs - 2 * inset, cs - 2 * inset))
                    if (bad && eink) {
                        val k = cs * 0.26f
                        val w = 2.5f.dp.toPx()
                        val m = Offset(x + cs / 2, y + cs / 2)
                        drawLine(Color.White, Offset(m.x - k, m.y - k), Offset(m.x + k, m.y + k), w)
                        drawLine(Color.White, Offset(m.x + k, m.y - k), Offset(m.x - k, m.y + k), w)
                    }
                }
                Mark.CROSSED -> {
                    val m = Offset(x + cs / 2, y + cs / 2)
                    if (wrong && eink) drawCircle(Color.Black, cs * 0.2f, m, style = Stroke(2.5f.dp.toPx()))
                    else drawCircle(if (wrong) wrongColor else crossColor, cs * (if (wrong) 0.14f else 0.09f), m)
                }
                Mark.NONE -> {}
            }
        }

        // Hint (first press): a soft band over the edge, or a frame around the cell.
        state.activeHint?.let { h ->
            if (h < edges) {
                val (a, b) = grid.edgeDots[h]
                drawLine(
                    if (eink) Color(0xFFBBBBBB) else scheme.tertiary.copy(alpha = 0.45f),
                    centre(a), centre(b), cs * 0.34f, cap = StrokeCap.Round,
                )
            } else {
                val cell = h - edges
                val w = (if (eink) 4f else 3.5f).dp.toPx()
                val inset = w / 2 + 2.dp.toPx()
                drawRect(
                    color = if (eink) Color.Black else scheme.tertiary,
                    topLeft = Offset(o + (cell % n) * cs + inset, o + (cell / n) * cs + inset),
                    size = Size(cs - 2 * inset, cs - 2 * inset),
                    style = Stroke(width = w),
                )
            }
        }

        val lineWidth = maxOf(3.dp.toPx(), cs * 0.12f)
        for (e in 0 until edges) {
            val (a, b) = grid.edgeDots[e]
            val pa = centre(a)
            val pb = centre(b)
            val wrong = state.shownWrong(e)
            when (state.marks[e]) {
                Mark.FILLED -> drawLine(if (wrong) wrongColor else line, pa, pb, lineWidth, cap = StrokeCap.Round, pathEffect = wrongDash(eink, wrong, cs))
                Mark.CROSSED -> {
                    val m = Offset((pa.x + pb.x) / 2, (pa.y + pb.y) / 2)
                    val k = cs * (if (wrong) 0.13f else 0.09f)
                    val w = (if (wrong) 2.5f else 1.5f).dp.toPx()
                    val c = if (wrong) wrongColor else crossColor
                    drawLine(c, Offset(m.x - k, m.y - k), Offset(m.x + k, m.y + k), w)
                    drawLine(c, Offset(m.x + k, m.y - k), Offset(m.x - k, m.y + k), w)
                }
                Mark.NONE -> {}
            }
        }
    }
}

/** An arrow centred on [c] pointing up (0), down (1), left (2) or right (3). */
private fun DrawScope.drawArrow(c: Offset, dir: Int, cs: Float, color: Color) {
    val (dx, dy) = when (dir) {
        0 -> 0f to -1f
        1 -> 0f to 1f
        2 -> -1f to 0f
        else -> 1f to 0f
    }
    val half = cs * 0.2f
    val tail = Offset(c.x - dx * half, c.y - dy * half)
    val tip = Offset(c.x + dx * half, c.y + dy * half)
    val w = maxOf(2.dp.toPx(), cs * 0.07f)
    drawLine(color, tail, tip, w, cap = StrokeCap.Round)
    val head = cs * 0.15f
    val angle = 0.7f
    for (sign in intArrayOf(-1, 1)) {
        val cosA = cos(sign * angle)
        val sinA = sin(sign * angle)
        // Rotate the backwards direction by ±angle.
        val bx = -dx * cosA - (-dy) * sinA
        val by = -dx * sinA + (-dy) * cosA
        drawLine(color, tip, Offset(tip.x + bx * head, tip.y + by * head), w, cap = StrokeCap.Round)
    }
}

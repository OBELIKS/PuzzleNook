package com.puzzlenook.ui.slither

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.slither.SlitherState
import com.puzzlenook.ui.theme.LocalEink

/** Margin around the dots, in cells. */
private const val MARGIN = 0.35f

/**
 * Slitherlink board: dots, numbers, the lines you draw and your ✕ marks. Tap near an edge to
 * toggle it; drag from dot to dot to draw (or erase) a run of edges.
 */
@Composable
fun SlitherBoard(
    state: SlitherState,
    lineColor: Color?,
    onTap: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEdge: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()
    val n = state.n
    val grid = state.grid

    Canvas(
        modifier
            .aspectRatio(1f)
            .edgeGestures(grid, n, MARGIN, 0f, false, onTap, onDragStart, onDragEdge, onDragEnd)
    ) {
        val cs = size.width / (n + 2 * MARGIN)
        val o = MARGIN * cs
        fun dotPos(d: Int) = Offset(o + (d % (n + 1)) * cs, o + (d / (n + 1)) * cs)
        val ink = if (eink) Color.Black else scheme.onSurface
        val faded = if (eink) Color(0xFFAAAAAA) else scheme.onSurface.copy(alpha = 0.3f)
        val line = lineColor ?: if (eink) Color.Black else scheme.primary
        val wrongColor = if (eink) Color.Black else scheme.error
        val crossColor = if (eink) Color(0xFF555555) else scheme.onSurfaceVariant
        drawRect(scheme.surfaceContainerLowest)

        // Numbers; satisfied ones fade.
        for (i in 0 until n * n) {
            val clue = state.clues[i]
            if (clue < 0) continue
            val over = grid.cellEdges[i].count { state.marks[it] == Mark.FILLED } > clue
            val color = when {
                over -> wrongColor
                state.clueDone(i) -> faded
                else -> ink
            }
            val l = measurer.measure(clue.toString(), TextStyle(fontSize = (cs * 0.5f).toSp(), color = color, fontWeight = FontWeight.Medium))
            drawText(l, topLeft = Offset(o + (i % n + 0.5f) * cs - l.size.width / 2, o + (i / n + 0.5f) * cs - l.size.height / 2))
        }

        // Hint (first press): a soft band over the edge.
        state.activeHintEdge?.let { e ->
            val (a, b) = grid.edgeDots[e]
            drawLine(
                if (eink) Color(0xFFBBBBBB) else scheme.tertiary.copy(alpha = 0.45f),
                dotPos(a), dotPos(b), cs * 0.32f, cap = StrokeCap.Round,
            )
        }

        val lineWidth = maxOf(3.dp.toPx(), cs * 0.11f)
        for (e in 0 until grid.edges) {
            val (a, b) = grid.edgeDots[e]
            val pa = dotPos(a)
            val pb = dotPos(b)
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
        val dotRadius = maxOf(2.5.dp.toPx(), cs * 0.06f)
        for (d in 0 until grid.dots) drawCircle(ink, dotRadius, dotPos(d))
    }
}

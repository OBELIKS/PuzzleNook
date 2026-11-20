package com.puzzlenook.ui.slither

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.slither.BLACK_PEARL
import com.puzzlenook.slither.MasyuState
import com.puzzlenook.slither.NO_PEARL
import com.puzzlenook.ui.theme.LocalEink

/** Margin around the cells, in cells. */
private const val MARGIN = 0.1f

/**
 * Masyu board: a faint cell grid, pearls (black filled, white ringed), the loop you draw through the
 * cell centres and your ✕ marks. Tap near the middle of an edge between two cells to toggle it; drag
 * from cell to cell to draw (or erase) a run of edges.
 */
@Composable
fun MasyuBoard(
    state: MasyuState,
    lineColor: Color?,
    onTap: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEdge: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val n = state.n
    val grid = state.grid

    Canvas(
        modifier
            .aspectRatio(1f)
            .edgeGestures(grid, n, MARGIN, 0.5f, true, onTap, onDragStart, onDragEdge, onDragEnd)
    ) {
        val cs = size.width / (n + 2 * MARGIN)
        val o = MARGIN * cs
        fun centre(d: Int) = Offset(o + (d % n + 0.5f) * cs, o + (d / n + 0.5f) * cs)
        val ink = if (eink) Color.Black else scheme.onSurface
        val gridColor = if (eink) Color(0xFFBBBBBB) else scheme.onSurface.copy(alpha = 0.18f)
        val line = lineColor ?: if (eink) Color.Black else scheme.primary
        val wrongColor = if (eink) Color.Black else scheme.error
        val crossColor = if (eink) Color(0xFF555555) else scheme.onSurfaceVariant
        val paper = scheme.surfaceContainerLowest
        drawRect(paper)

        // Cell grid.
        val thin = 1.dp.toPx()
        for (k in 0..n) {
            drawLine(gridColor, Offset(o + k * cs, o), Offset(o + k * cs, o + n * cs), thin)
            drawLine(gridColor, Offset(o, o + k * cs), Offset(o + n * cs, o + k * cs), thin)
        }

        // Hint (first press): a soft band over the edge.
        state.activeHintEdge?.let { e ->
            val (a, b) = grid.edgeDots[e]
            drawLine(
                if (eink) Color(0xFFBBBBBB) else scheme.tertiary.copy(alpha = 0.45f),
                centre(a), centre(b), cs * 0.34f, cap = StrokeCap.Round,
            )
        }

        val lineWidth = maxOf(3.dp.toPx(), cs * 0.12f)
        for (e in 0 until grid.edges) {
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

        // Pearls on top of the lines; a pearl whose rule your lines meet turns pale.
        val radius = cs * 0.3f
        val ring = maxOf(2.dp.toPx(), cs * 0.05f)
        for (i in 0 until n * n) {
            val kind = state.pearls[i]
            if (kind == NO_PEARL) continue
            val broken = state.showWrong && state.pearlBroken(i)
            val done = state.pearlDone(i)
            val c = centre(i)
            val color = when {
                broken -> wrongColor
                done -> if (eink) Color(0xFF888888) else ink.copy(alpha = 0.4f)
                else -> ink
            }
            if (kind == BLACK_PEARL) {
                drawCircle(color, radius, c)
            } else {
                drawCircle(paper, radius, c)
                drawCircle(color, radius, c, style = Stroke(ring))
            }
        }
    }
}

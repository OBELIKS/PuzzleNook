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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.slither.GalaxiesState
import com.puzzlenook.ui.theme.LocalEink

/** Margin around the board, in cells. */
private const val MARGIN = 0.25f

/**
 * Galaxies board: a faint cell grid, the dots, the walls you draw on the grid lines (the outer border is
 * always a wall) and your ✕ marks. A galaxy your walls have closed correctly (one dot, symmetric around
 * it) gets a flat tint. Tap near an edge to toggle a wall; drag from corner to corner to draw (or erase) a run.
 */
@Composable
fun GalaxiesBoard(
    state: GalaxiesState,
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
            .edgeGestures(grid, n, MARGIN, 0f, false, onTap, onDragStart, onDragEdge, onDragEnd)
    ) {
        val cs = size.width / (n + 2 * MARGIN)
        val o = MARGIN * cs
        fun corner(d: Int) = Offset(o + (d % (n + 1)) * cs, o + (d / (n + 1)) * cs)
        val ink = if (eink) Color.Black else scheme.onSurface
        val gridColor = if (eink) Color(0xFFBBBBBB) else scheme.onSurface.copy(alpha = 0.18f)
        val tint = if (eink) Color(0xFFDDDDDD) else scheme.secondaryContainer
        val line = lineColor ?: if (eink) Color.Black else scheme.primary
        val wrongColor = if (eink) Color.Black else scheme.error
        val crossColor = if (eink) Color(0xFF555555) else scheme.onSurfaceVariant
        val paper = scheme.surfaceContainerLowest
        drawRect(paper)

        // Finished galaxies (from your walls only).
        val areas = state.finishedAreas()
        for (i in 0 until n * n) if (areas[i] >= 0) {
            drawRect(tint, Offset(o + (i % n) * cs, o + (i / n) * cs), Size(cs, cs))
        }

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
                if (eink) Color(0xFFAAAAAA) else scheme.tertiary.copy(alpha = 0.45f),
                corner(a), corner(b), cs * 0.32f, cap = StrokeCap.Round,
            )
        }

        val wallWidth = maxOf(3.dp.toPx(), cs * 0.1f)
        for (e in 0 until grid.edges) {
            val (a, b) = grid.edgeDots[e]
            val pa = corner(a)
            val pb = corner(b)
            val wrong = state.shownWrong(e)
            when (state.marks[e]) {
                Mark.FILLED -> {
                    val c = when {
                        wrong -> wrongColor
                        state.fixed[e] -> ink
                        else -> line
                    }
                    drawLine(c, pa, pb, wallWidth, cap = StrokeCap.Round, pathEffect = wrongDash(eink, wrong, cs))
                }
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

        // Dots on top of the walls.
        val radius = cs * 0.17f
        val ring = maxOf(2.dp.toPx(), cs * 0.04f)
        for (d in state.dots) {
            val x = d % (2 * n + 1)
            val y = d / (2 * n + 1)
            val c = Offset(o + x * cs / 2, o + y * cs / 2)
            drawCircle(paper, radius, c)
            drawCircle(ink, radius, c, style = Stroke(ring))
        }
    }
}

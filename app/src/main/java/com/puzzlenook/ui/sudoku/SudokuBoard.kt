package com.puzzlenook.ui.sudoku

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
import androidx.compose.ui.graphics.compositeOver
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
import com.puzzlenook.ink.InkPoint
import com.puzzlenook.sudoku.GameState
import com.puzzlenook.sudoku.LatinRules
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.ui.ink.InkController
import com.puzzlenook.ui.theme.DigitColors
import com.puzzlenook.ui.theme.LocalEink

/**
 * The 9×9 grid. Finger taps select cells. With [handwriting] on, a stylus writes ink
 * into [ink] instead (unless [stylusHandledExternally], i.e. the Onyx SDK owns the pen). For Fillomino it draws
 * thick lines between touching cells with different shown digits (player data only).
 */
@OptIn(ExperimentalComposeUiApi::class) // PointerInputChange.historical: full-rate pen samples
@Composable
fun SudokuBoard(
    state: GameState,
    ink: InkController,
    handwriting: Boolean,
    stylusHandledExternally: Boolean,
    colors: DigitColors,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(1f)
            .onSizeChanged {
                ink.boardSize = it.width.toFloat()
                ink.cells = state.size
            }
            .pointerInput(handwriting, stylusHandledExternally, state.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    when {
                        pen && handwriting && stylusHandledExternally -> {
                            // Onyx raw input is already capturing this stroke.
                            waitForUpOrCancellation()
                        }
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
                                val n = state.size
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
        val n = state.size
        ink.cells = n
        val cs = size.width / n
        val selected = state.selected
        val selectedValue = selected?.let { state.cells[it].value } ?: 0

        drawRect(scheme.surfaceContainerLowest)
        val cellBg = Array(state.cells.size) { scheme.surfaceContainerLowest }
        val killer = state.variant == Variant.KILLER
        val kakuro = state.variant == Variant.KAKURO

        // Cell backgrounds. E-ink avoids translucent tints: flat greys, wrong cells inverted.
        for (i in state.cells.indices) {
            val wrong = state.shownWrong(i)
            val bg = if (state.cells[i].blocked) {
                if (eink) Color(0xFF3A3A3A) else scheme.inverseSurface.copy(alpha = 0.82f)
            } else if (eink) {
                when {
                    wrong -> Color.Black
                    i == selected -> Color(0xFFB0B0B0)
                    selectedValue != 0 && state.cells[i].value == selectedValue -> Color(0xFFDADADA)
                    else -> null
                }
            } else {
                when {
                    i == selected -> scheme.primary.copy(alpha = 0.32f)
                    wrong -> scheme.error.copy(alpha = 0.14f)
                    selectedValue != 0 && state.cells[i].value == selectedValue -> scheme.secondary.copy(alpha = 0.30f)
                    selected != null && state.isPeer(selected, i) -> scheme.primary.copy(alpha = 0.08f)
                    else -> null
                }
            } ?: continue
            cellBg[i] = bg.compositeOver(scheme.surfaceContainerLowest)
            drawRect(bg, Offset((i % n) * cs, (i / n) * cs), Size(cs, cs))
        }

        val digitStyle = TextStyle(fontSize = (cs * 0.56f).toSp())
        val noteStyle = TextStyle(
            fontSize = (cs * 0.24f).toSp(),
            color = colors.note ?: scheme.onSurfaceVariant,
            // Thin coloured strokes fade on colour e-ink; give coloured notes a bit more weight.
            fontWeight = if (colors.note != null) FontWeight.Medium else FontWeight.Normal,
        )
        val sub = cs / 3f
        // Killer: each cage's sum sits in the top-left corner of its first cell.
        val labelCells = if (killer) state.cages.associateBy { it.cells.first() } else emptyMap()
        // Calcudoku: the same for each cage's target and operation.
        val mathLabels = state.rules?.cages.orEmpty().associateBy { it.cells.min() }
        val inset = cs * 0.09f

        for (i in state.cells.indices) {
            val cell = state.cells[i]
            if (cell.blocked) continue
            val x = (i % n) * cs
            val y = (i / n) * cs
            if (cell.value != 0) {
                val wrong = state.shownWrong(i)
                val style = digitStyle.copy(
                    color = when {
                        eink && wrong -> Color.White
                        cell.given -> scheme.onSurface
                        wrong -> scheme.error
                        colors.answer != null -> colors.answer
                        eink -> Color.Black
                        else -> scheme.primary
                    },
                    // Black-only e-ink tells givens from entries by weight; a chosen colour
                    // already does that, and thin coloured strokes fade on colour e-ink.
                    fontWeight = when {
                        cell.given -> if (eink) FontWeight.Bold else FontWeight.SemiBold
                        eink && colors.answer == null -> FontWeight.Light
                        else -> FontWeight.Normal
                    },
                )
                val layout = measurer.measure(cell.value.toString(), style)
                drawText(layout, topLeft = Offset(x + (cs - layout.size.width) / 2, y + (cs - layout.size.height) / 2))
            } else if (cell.notes != 0) {
                // Inside cages notes keep clear of the dashed outline and the sum label.
                val top = when {
                    i in labelCells || i in mathLabels -> cs * 0.24f
                    killer -> inset
                    else -> 0f
                }
                val side = if (killer) inset else 0f
                val subW = (cs - 2 * side) / 3f
                val subH = (cs - top - side) / 3f
                for (d in 1..9) {
                    if (!cell.hasNote(d)) continue
                    val layout = measurer.measure(d.toString(), noteStyle)
                    val nx = x + side + ((d - 1) % 3) * subW
                    val ny = y + top + ((d - 1) / 3) * subH
                    drawText(layout, topLeft = Offset(nx + (subW - layout.size.width) / 2, ny + (subH - layout.size.height) / 2))
                }
            }
        }

        // Grid: thin cell lines first, then thick box lines on top.
        val thin = (if (eink) 1.2f else 1f).dp.toPx()
        val thick = (if (eink) 3f else 2.5f).dp.toPx()
        val minor = if (eink) Color(0xFF777777) else scheme.outlineVariant
        val regions = state.regions
        for (k in 1 until n) {
            if (regions == null && state.variant.hasHouses && k % 3 == 0) continue
            val p = k * cs
            drawLine(minor, Offset(p, 0f), Offset(p, size.height), thin)
            drawLine(minor, Offset(0f, p), Offset(size.width, p), thin)
        }
        val major = if (eink) Color.Black else scheme.onSurface.copy(alpha = 0.85f)
        if (regions != null) {
            // Jigsaw: thick lines only where two regions meet, then the outer frame.
            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c < n - 1 && regions[i] != regions[i + 1]) {
                    val x = (c + 1) * cs
                    drawLine(major, Offset(x, r * cs), Offset(x, (r + 1) * cs), thick)
                }
                if (r < n - 1 && regions[i] != regions[i + n]) {
                    val y = (r + 1) * cs
                    drawLine(major, Offset(c * cs, y), Offset((c + 1) * cs, y), thick)
                }
            }
            for (k in intArrayOf(0, n)) {
                val p = (k * cs).coerceIn(thick / 2, size.width - thick / 2)
                drawLine(major, Offset(p, 0f), Offset(p, size.height), thick)
                drawLine(major, Offset(0f, p), Offset(size.width, p), thick)
            }
        } else {
            if (state.variant == Variant.FILLOMINO) {
                // Fillomino: a thick line wherever two touching cells show different digits, from what
                // the player sees (given or entered), never from the solution.
                for (r in 0 until n) for (c in 0 until n) {
                    val a = state.cells[r * n + c].value
                    if (a == 0) continue
                    if (c < n - 1) {
                        val b = state.cells[r * n + c + 1].value
                        if (b != 0 && b != a) drawLine(major, Offset((c + 1) * cs, r * cs), Offset((c + 1) * cs, (r + 1) * cs), thick)
                    }
                    if (r < n - 1) {
                        val b = state.cells[(r + 1) * n + c].value
                        if (b != 0 && b != a) drawLine(major, Offset(c * cs, (r + 1) * cs), Offset((c + 1) * cs, (r + 1) * cs), thick)
                    }
                }
            }
            for (k in 0..n step if (state.variant.hasHouses) 3 else n) {
                val p = (k * cs).coerceIn(thick / 2, size.width - thick / 2)
                drawLine(major, Offset(p, 0f), Offset(p, size.height), thick)
                drawLine(major, Offset(0f, p), Offset(size.width, p), thick)
            }
        }

        if (kakuro) drawKakuroClues(state, cs, eink, scheme.inverseOnSurface) { text, size ->
            measurer.measure(
                text,
                TextStyle(
                    fontSize = (cs * size).toSp(),
                    color = if (eink) Color.White else scheme.inverseOnSurface,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        if (killer) drawCages(state, cs, inset, eink, scheme.onSurface, labelCells.keys, { cellBg[it] }) { i ->
            measurer.measure(
                labelCells.getValue(i).sum.toString(),
                TextStyle(
                    fontSize = (cs * 0.2f).toSp(),
                    color = if (eink) Color.Black else scheme.onSurface,
                    fontWeight = if (eink) FontWeight.Bold else FontWeight.Medium,
                ),
            )
        }

        state.rules?.let { rules ->
            if (rules.cages.isNotEmpty()) {
                drawMathCages(state, cs, (thin + thick) / 2, thick, major, mathLabels, { cellBg[it] }) { text ->
                    measurer.measure(
                        text,
                        TextStyle(
                            fontSize = (cs * 0.2f).toSp(),
                            color = if (eink) Color.Black else scheme.onSurface,
                            fontWeight = if (eink) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                }
            }
            if (rules.less.isNotEmpty()) {
                drawSigns(rules.less, n, cs, major, { cellBg[it] }, (if (eink) 2.2f else 1.8f).dp.toPx())
            }
            if (rules.white.isNotEmpty() || rules.black.isNotEmpty()) {
                drawDots(rules, n, cs, major, if (eink) Color.White else scheme.surface, (if (eink) 2.2f else 1.8f).dp.toPx())
            }
        }

        // Hint (first press): a thick frame inside the highlighted cell.
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

        // Ink being written (on Onyx the SDK paints it directly to the panel instead).
        if (!stylusHandledExternally) {
            val inkColor = colors.answer ?: if (eink) Color.Black else scheme.tertiary
            val width = (if (eink) 3f else 2.5f).dp.toPx()
            ink.strokes.forEach { drawInk(it.points, inkColor, width) }
            drawInk(ink.current, inkColor, width)
        }
    }
}

/**
 * Kakuro clue squares: a diagonal, the across sum top-right and the down sum bottom-left.
 * The clue for a run sits in the square just left of it (across) or above it (down).
 */
private fun DrawScope.drawKakuroClues(
    state: GameState,
    cs: Float,
    eink: Boolean,
    lineColor: Color,
    label: (String, Float) -> androidx.compose.ui.text.TextLayoutResult,
) {
    val n = state.size
    val across = mutableMapOf<Int, Int>()
    val down = mutableMapOf<Int, Int>()
    for (cage in state.cages) {
        val first = cage.cells.first()
        if (cage.cells[1] == first + 1) across[first - 1] = cage.sum else down[first - n] = cage.sum
    }
    val line = if (eink) Color(0xFFBBBBBB) else lineColor.copy(alpha = 0.5f)
    for (i in across.keys + down.keys) {
        val x = (i % n) * cs
        val y = (i / n) * cs
        drawLine(line, Offset(x, y), Offset(x + cs, y + cs), 1.dp.toPx())
        across[i]?.let { s ->
            val l = label(s.toString(), 0.3f)
            drawText(l, topLeft = Offset(x + cs * 0.94f - l.size.width, y + cs * 0.06f))
        }
        down[i]?.let { s ->
            val l = label(s.toString(), 0.3f)
            drawText(l, topLeft = Offset(x + cs * 0.08f, y + cs * 0.94f - l.size.height))
        }
    }
}

/**
 * Dashed cage outlines drawn just inside the cells, joined across cells of the same cage,
 * plus each cage's sum on a small patch that interrupts the outline.
 */
private fun DrawScope.drawCages(
    state: GameState,
    cs: Float,
    inset: Float,
    eink: Boolean,
    onSurface: Color,
    labelCells: Set<Int>,
    background: (Int) -> Color,
    label: (Int) -> androidx.compose.ui.text.TextLayoutResult,
) {
    val cage = state.cageOf
    fun same(i: Int, r: Int, c: Int) = r in 0..8 && c in 0..8 && cage[r * 9 + c] == cage[i]
    // (Killer sudoku is always 9×9.)
    val color = if (eink) Color(0xFF333333) else onSurface.copy(alpha = 0.7f)
    val width = (if (eink) 1.6f else 1.2f).dp.toPx()
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        drawLine(color, Offset(x0, y0), Offset(x1, y1), width, pathEffect = dash)

    for (i in 0 until 81) {
        if (cage[i] < 0) continue
        val r = i / 9
        val c = i % 9
        val left = c * cs
        val top = r * cs
        val right = left + cs
        val bottom = top + cs
        // Where a side ends: short of the corner if the outline turns there, past it if it
        // continues around an inner corner into the diagonal cell.
        fun startX(dr: Int) = when {
            !same(i, r, c - 1) -> left + inset
            same(i, r + dr, c - 1) -> left - inset
            else -> left
        }
        fun endX(dr: Int) = when {
            !same(i, r, c + 1) -> right - inset
            same(i, r + dr, c + 1) -> right + inset
            else -> right
        }
        fun startY(dc: Int) = when {
            !same(i, r - 1, c) -> top + inset
            same(i, r - 1, c + dc) -> top - inset
            else -> top
        }
        fun endY(dc: Int) = when {
            !same(i, r + 1, c) -> bottom - inset
            same(i, r + 1, c + dc) -> bottom + inset
            else -> bottom
        }
        if (!same(i, r - 1, c)) line(startX(-1), top + inset, endX(-1), top + inset)
        if (!same(i, r + 1, c)) line(startX(1), bottom - inset, endX(1), bottom - inset)
        if (!same(i, r, c - 1)) line(left + inset, startY(-1), left + inset, endY(-1))
        if (!same(i, r, c + 1)) line(right - inset, startY(1), right - inset, endY(1))
    }

    for (i in labelCells) {
        val layout = label(i)
        val x = (i % 9) * cs + inset * 0.4f
        val y = (i / 9) * cs + inset * 0.2f
        // Clear a patch under the number so the dashes don't run through it.
        drawRect(
            background(i),
            Offset(x, y),
            Size(layout.size.width + inset * 0.6f, layout.size.height.toFloat()),
        )
        drawText(layout, topLeft = Offset(x + inset * 0.3f, y))
    }
}

/** Calcudoku cages: solid lines between cages and each cage's label in its top-left cell. */
private fun DrawScope.drawMathCages(
    state: GameState,
    cs: Float,
    width: Float,
    outer: Float,
    color: Color,
    labels: Map<Int, com.puzzlenook.sudoku.MathCage>,
    background: (Int) -> Color,
    measure: (String) -> androidx.compose.ui.text.TextLayoutResult,
) {
    val n = state.size
    val cage = state.rules!!.cageOf
    for (i in 0 until n * n) {
        val r = i / n
        val c = i % n
        if (c + 1 < n && cage[i] != cage[i + 1]) {
            drawLine(color, Offset((c + 1) * cs, r * cs), Offset((c + 1) * cs, (r + 1) * cs), width)
        }
        if (r + 1 < n && cage[i] != cage[i + n]) {
            drawLine(color, Offset(c * cs, (r + 1) * cs), Offset((c + 1) * cs, (r + 1) * cs), width)
        }
    }
    val pad = cs * 0.06f
    for ((i, mc) in labels) {
        val layout = measure(mc.label)
        // Keep clear of the line above and left (thicker on the outer edge).
        val x = (i % n) * cs + (if (i % n == 0) outer else width / 2) + pad * 0.5f
        val y = (i / n) * cs + (if (i / n == 0) outer else width / 2) + pad * 0.2f
        drawRect(background(i), Offset(x, y), Size(layout.size.width + pad, layout.size.height.toFloat()))
        drawText(layout, topLeft = Offset(x + pad / 2, y))
    }
}

/** Kropki dots on the line between two cells: white = outline, black = filled. */
private fun DrawScope.drawDots(rules: LatinRules, n: Int, cs: Float, ink: Color, paper: Color, width: Float) {
    fun mid(pair: IntArray) = Offset(
        ((pair[0] % n + 0.5f) + (pair[1] % n + 0.5f)) / 2 * cs,
        ((pair[0] / n + 0.5f) + (pair[1] / n + 0.5f)) / 2 * cs,
    )
    val radius = cs * 0.11f
    for (p in rules.white) {
        drawCircle(paper, radius, mid(p))
        drawCircle(ink, radius, mid(p), style = Stroke(width))
    }
    for (p in rules.black) drawCircle(ink, radius + width / 2, mid(p))
}

/** Futoshiki signs on the line between two cells; the point faces the smaller digit. */
private fun DrawScope.drawSigns(
    less: List<IntArray>,
    n: Int,
    cs: Float,
    color: Color,
    background: (Int) -> Color,
    width: Float,
) {
    val h = cs * 0.1f
    for (pair in less) {
        val small = pair[0]
        val big = pair[1]
        val sx = (small % n + 0.5f) * cs
        val sy = (small / n + 0.5f) * cs
        val bx = (big % n + 0.5f) * cs
        val by = (big / n + 0.5f) * cs
        val mid = Offset((sx + bx) / 2, (sy + by) / 2)
        val dx = (sx - bx) / cs
        val dy = (sy - by) / cs
        val tip = Offset(mid.x + dx * h, mid.y + dy * h)
        val a = Offset(mid.x - dx * h - dy * h * 1.1f, mid.y - dy * h - dx * h * 1.1f)
        val b = Offset(mid.x - dx * h + dy * h * 1.1f, mid.y - dy * h + dx * h * 1.1f)
        val path = Path().apply {
            moveTo(a.x, a.y)
            lineTo(tip.x, tip.y)
            lineTo(b.x, b.y)
        }
        drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
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

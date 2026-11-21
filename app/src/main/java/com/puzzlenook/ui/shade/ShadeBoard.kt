package com.puzzlenook.ui.shade

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.shade.LightUpLogic
import com.puzzlenook.shade.SHIP_BOTTOM
import com.puzzlenook.shade.SHIP_LEFT
import com.puzzlenook.shade.SHIP_RIGHT
import com.puzzlenook.shade.SHIP_TOP
import com.puzzlenook.shade.ShadeState
import com.puzzlenook.shade.TREE
import com.puzzlenook.shade.king
import com.puzzlenook.shade.orth
import androidx.compose.ui.graphics.drawscope.translate
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.ui.theme.LocalEink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Board for Star Battle (stars, regions with thick borders), Hitori (numbers, shaded or
 * circled), Nurikabe (island numbers, sea shaded, dots for white), Takuzu, Mosaic and Light Up
 * (black walls with numbers, bulbs, lit cells tinted), Tents (trees, tents, outside counts in a
 * margin), Kurodoko (numbered white cells, shaded cells), Norinori (regions with thick borders,
 * shaded cells), Battleships (ship capsules, outside counts), Aquarium (tanks, water, outside counts),
 * Heyawake (rooms with a number in their first cell, shaded cells) and LITS (regions, shaded cells).
 * Outside counts (Tents, Battleships, Aquarium) sit in a one-cell margin at the top and left; the board stays
 * square and taps in the margin do nothing. A Battleships ship is drawn as capsules joined to its own FILLED
 * neighbours, and a correct ship dots its diagonals. Tap or drag to mark.
 */
@Composable
fun ShadeBoard(
    state: ShadeState,
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
    // Outside counts (Tents) take one extra row above and one extra column left of the grid.
    val m = if (state.counts != null) 1 else 0

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(n, m) {
                fun cellAt(p: Offset): Int {
                    val cs = size.width / (n + m).toFloat()
                    val c = ((p.x / cs).toInt() - m).coerceIn(0, n - 1)
                    val r = ((p.y / cs).toInt() - m).coerceIn(0, n - 1)
                    return r * n + c
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    // Taps in the margin with the counts do nothing.
                    val cs0 = size.width / (n + m).toFloat()
                    if (m > 0 && (down.position.x < cs0 || down.position.y < cs0)) return@awaitEachGesture
                    var last = cellAt(down.position)
                    onStrokeStart(last)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val cell = cellAt(change.position)
                        if (cell != last) {
                            last = cell
                            onStrokeMove(cell)
                        }
                    }
                    onStrokeEnd()
                }
            }
    ) {
        val cs = size.width / (n + m)
        val gw = cs * n
        val ink = if (eink) Color.Black else scheme.onSurface
        val fill = fillColor ?: if (eink) Color.Black else scheme.onSurface
        val wrongColor = if (eink) Color.Black else scheme.error
        val mutedInk = if (eink) Color(0xFF555555) else scheme.onSurfaceVariant
        drawRect(scheme.surfaceContainerLowest)
        val numbers = if (state.variant == Variant.LIGHT_UP) state.numbers.toIntArray() else IntArray(0)
        val lit = if (state.variant == Variant.LIGHT_UP) LightUpLogic.lit(n, numbers) { state.marks[it] == Mark.FILLED } else BooleanArray(0)

        state.counts?.let { counts ->
            for (k in 0 until 2 * n) {
                val c = counts[k]
                if (c < 0) continue
                val color = when {
                    state.countDone(k) -> if (eink) Color(0xFF909090) else mutedInk.copy(alpha = 0.55f)
                    state.showWrong && state.lineMarked(k) > c -> wrongColor
                    else -> ink
                }
                val l = measurer.measure(
                    c.toString(),
                    TextStyle(fontSize = (cs * 0.5f).toSp(), color = color, fontWeight = FontWeight.Bold),
                )
                val cx = if (k < n) cs / 2 else (k - n + 1) * cs + cs / 2
                val cy = if (k < n) (k + 1) * cs + cs / 2 else cs / 2
                drawText(l, topLeft = Offset(cx - l.size.width / 2, cy - l.size.height / 2))
            }
        }
        // Region games (Norinori, Heyawake, LITS): a shaded cell stops short of a region border, so the
        // border stays visible between two shaded cells; cells of one region stay joined. Locked shaded
        // cells (given at the start or revealed by a hint) get a small light dot.
        val borderGap = (if (eink) 3f else 2.5f).dp.toPx() / 2 + 2.dp.toPx()
        val lockedDot = if (eink) Color.White else scheme.surface
        fun shadeCell(i: Int, x: Float, y: Float, color: Color) {
            val g = state.regions
            if (g == null) {
                drawRect(color, Offset(x, y), Size(cs, cs))
                return
            }
            val r = i / n
            val c = i % n
            fun cut(nr: Int, nc: Int) = nr !in 0 until n || nc !in 0 until n || g[nr * n + nc] != g[i]
            val left = if (cut(r, c - 1)) borderGap else 0f
            val top = if (cut(r - 1, c)) borderGap else 0f
            val right = if (cut(r, c + 1)) borderGap else 0f
            val bottom = if (cut(r + 1, c)) borderGap else 0f
            drawRect(color, Offset(x + left, y + top), Size(cs - left - right, cs - top - bottom))
            if (state.fixed[i]) drawCircle(lockedDot, cs * 0.09f, Offset(x + cs / 2, y + cs / 2))
        }
        translate(m * cs, m * cs) {
        for (i in 0 until n * n) {
            val x = (i % n) * cs
            val y = (i / n) * cs
            val mark = state.marks[i]
            // Norinori: shading that already breaks a rule shows red like a wrong mark (errors on only).
            val wrong = state.shownWrong(i) || (state.showWrong && (state.norinoriClash(i) || state.shipClash(i) || state.aquariumClash(i) || state.heyawakeClash(i) || state.litsClash(i)))
            val number = state.numbers[i]
            when (state.variant) {
                Variant.STAR_BATTLE -> when (mark) {
                    Mark.FILLED -> drawStar(x + cs / 2, y + cs / 2, cs * 0.36f, if (wrong) wrongColor else fill)
                    Mark.CROSSED -> drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                    Mark.NONE -> {}
                }
                Variant.TAKUZU -> {
                    if (state.fixed[i]) drawRect(if (eink) Color(0xFFE6E6E6) else scheme.surfaceVariant, Offset(x, y), Size(cs, cs))
                    when (mark) {
                        Mark.FILLED -> drawCircle(if (wrong) wrongColor else fill, cs * 0.34f, Offset(x + cs / 2, y + cs / 2))
                        Mark.CROSSED -> drawCircle(
                            if (wrong) wrongColor else ink,
                            cs * 0.32f,
                            Offset(x + cs / 2, y + cs / 2),
                            style = Stroke((if (wrong) 3f else 2f).dp.toPx()),
                        )
                        Mark.NONE -> {}
                    }
                }
                Variant.MOSAIC -> {
                    if (mark == Mark.FILLED) drawRect(if (wrong) wrongColor else fill, Offset(x, y), Size(cs, cs))
                    if (number >= 0) {
                        val color = when {
                            mark == Mark.FILLED -> if (eink) Color.White else scheme.surface
                            state.clueDone(i) -> mutedInk
                            else -> ink
                        }
                        val l = measurer.measure(
                            number.toString(),
                            TextStyle(fontSize = (cs * 0.5f).toSp(), color = color, fontWeight = FontWeight.Medium),
                        )
                        drawText(l, topLeft = Offset(x + (cs - l.size.width) / 2, y + (cs - l.size.height) / 2))
                    }
                    // White cells: a ring around the number, as in Hitori.
                    if (mark == Mark.CROSSED) drawCircle(
                        if (wrong) wrongColor else mutedInk,
                        cs * 0.4f,
                        Offset(x + cs / 2, y + cs / 2),
                        style = Stroke((if (wrong) 2.5f else 1.5f).dp.toPx()),
                    )
                }
                Variant.LIGHT_UP -> {
                    if (number >= 0) {
                        drawRect(ink, Offset(x, y), Size(cs, cs))
                        if (number in 0..4) {
                            val l = measurer.measure(
                                number.toString(),
                                TextStyle(
                                    fontSize = (cs * 0.5f).toSp(),
                                    color = if (state.clueDone(i)) Color(0xFF909090) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            drawText(l, topLeft = Offset(x + (cs - l.size.width) / 2, y + (cs - l.size.height) / 2))
                        }
                    } else {
                        // Lit cells are tinted from the player's own bulbs, never from the solution.
                        // Pale yellow (dark amber on dark themes): reads as light, not as an error.
                        val litColor = when {
                            eink -> Color(0xFFE2E2E2)
                            scheme.surface.luminance() < 0.5f -> Color(0xFF4A3F1A)
                            else -> Color(0xFFFFF0B3)
                        }
                        if (lit[i]) drawRect(litColor, Offset(x, y), Size(cs, cs))
                        when (mark) {
                            Mark.FILLED -> {
                                val clash = state.showWrong && LightUpLogic.clashes(n, numbers, i) { state.marks[it] == Mark.FILLED }
                                drawCircle(if (wrong || clash) wrongColor else fill, cs * 0.3f, Offset(x + cs / 2, y + cs / 2))
                            }
                            Mark.CROSSED -> drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                            Mark.NONE -> {}
                        }
                    }
                }
                Variant.TENTS -> {
                    if (number == TREE) {
                        drawTree(x, y, cs, eink)
                    } else when (mark) {
                        Mark.FILLED -> {
                            // Tents that touch: shown only from the player's own marks, and only with errors on.
                            val clash = state.showWrong && king(i, n).any { state.marks[it] == Mark.FILLED }
                            drawTent(x, y, cs, if (wrong || clash) wrongColor else fill)
                        }
                        Mark.CROSSED -> drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                        Mark.NONE -> {}
                    }
                }
                Variant.BATTLESHIPS -> {
                    // Cells shown from the start get a flat tint; a ship is drawn as a capsule joined to its marked neighbours.
                    if (number >= 0) drawRect(if (eink) Color(0xFFE6E6E6) else scheme.surfaceVariant, Offset(x, y), Size(cs, cs))
                    when (mark) {
                        Mark.FILLED -> {
                            fun marked(j: Int) = j >= 0 && state.marks[j] == Mark.FILLED
                            val up = if (i >= n) i - n else -1
                            val down = if (i + n < n * n) i + n else -1
                            val left = if (i % n > 0) i - 1 else -1
                            val right = if (i % n < n - 1) i + 1 else -1
                            drawShip(
                                x, y, cs, if (wrong) wrongColor else fill,
                                up = marked(up) || number == SHIP_BOTTOM,
                                down = marked(down) || number == SHIP_TOP,
                                left = marked(left) || number == SHIP_RIGHT,
                                right = marked(right) || number == SHIP_LEFT,
                            )
                        }
                        Mark.CROSSED -> drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                        Mark.NONE -> {}
                    }
                }
                Variant.AQUARIUM -> when (mark) {
                    Mark.FILLED -> {
                        val water = if (wrong) wrongColor else fillColor ?: if (eink) Color(0xFF6E6E6E) else Color(0xFF4F8FD0)
                        drawRect(water, Offset(x, y), Size(cs, cs))
                        // A lighter line marks the surface: no water of the same tank above.
                        val above = if (i >= n) i - n else -1
                        val surface = above < 0 || state.regions?.get(above) != state.regions?.get(i) || state.marks[above] != Mark.FILLED
                        if (surface && !wrong) drawRect(if (eink) Color(0xFFD0D0D0) else Color(0xFFBFDFFF), Offset(x, y), Size(cs, 3.dp.toPx()))
                    }
                    Mark.CROSSED -> drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                    Mark.NONE -> {}
                }
                Variant.HEYAWAKE -> {
                    if (mark == Mark.FILLED) shadeCell(i, x, y, if (wrong) wrongColor else fill)
                    if (mark == Mark.CROSSED) drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                    // The room number sits in the room's first cell, in the corner so a mark in the cell stays visible.
                    if (number >= 0) {
                        val color = when {
                            mark == Mark.FILLED -> if (eink) Color.White else scheme.surface
                            state.showWrong && state.roomOver(i) -> wrongColor
                            state.roomMet(i) -> if (eink) Color(0xFF909090) else mutedInk.copy(alpha = 0.7f)
                            else -> ink
                        }
                        val l = measurer.measure(
                            number.toString(),
                            TextStyle(fontSize = (cs * 0.4f).toSp(), color = color, fontWeight = FontWeight.Bold),
                        )
                        drawText(l, topLeft = Offset(x + cs * 0.08f, y + cs * 0.02f))
                    }
                }
                Variant.KURODOKO -> {
                    if (number > 0) {
                        val l = measurer.measure(
                            number.toString(),
                            TextStyle(
                                fontSize = (cs * 0.5f).toSp(),
                                color = if (state.kurodokoMet(i)) mutedInk else ink,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        drawText(l, topLeft = Offset(x + (cs - l.size.width) / 2, y + (cs - l.size.height) / 2))
                    } else when (mark) {
                        Mark.FILLED -> {
                            // Shaded cells that touch: from the player's own marks, with errors on only.
                            val clash = state.showWrong && orth(i, n).any { state.marks[it] == Mark.FILLED }
                            drawRect(if (wrong || clash) wrongColor else fill, Offset(x, y), Size(cs, cs))
                        }
                        Mark.CROSSED -> drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                        Mark.NONE -> {}
                    }
                }
                else -> {
                    if (mark == Mark.FILLED) {
                        shadeCell(i, x, y, if (wrong) wrongColor else fill)
                        if (wrong && eink) drawRect(Color.White, Offset(x + cs * 0.3f, y + cs * 0.3f), Size(cs * 0.4f, cs * 0.4f))
                    }
                    if (number > 0) {
                        val shaded = mark == Mark.FILLED
                        val color = when {
                            shaded -> if (eink) Color(0xFFBBBBBB) else scheme.surface.copy(alpha = 0.6f)
                            wrong -> wrongColor
                            else -> ink
                        }
                        val bold = state.variant == Variant.NURIKABE
                        val l = measurer.measure(
                            number.toString(),
                            TextStyle(fontSize = (cs * 0.5f).toSp(), color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
                        )
                        drawText(l, topLeft = Offset(x + (cs - l.size.width) / 2, y + (cs - l.size.height) / 2))
                    }
                    if (mark == Mark.CROSSED && !(state.variant == Variant.NURIKABE && number > 0)) {
                        if (state.variant == Variant.HITORI) {
                            // Hitori: circle the numbers you know stay white.
                            drawCircle(
                                if (wrong) wrongColor else mutedInk,
                                cs * 0.4f,
                                Offset(x + cs / 2, y + cs / 2),
                                style = Stroke((if (wrong) 2.5f else 1.5f).dp.toPx()),
                            )
                        } else {
                            drawDot(x, y, cs, if (wrong) wrongColor else mutedInk, wrong)
                        }
                    }
                }
            }
        }

        // Grid lines, then region borders (Star Battle) and the outer frame.
        val thin = (if (eink) 1.2f else 1f).dp.toPx()
        val thick = (if (eink) 3f else 2.5f).dp.toPx()
        val minor = if (eink) Color(0xFF777777) else scheme.outlineVariant
        val major = if (eink) Color.Black else scheme.onSurface.copy(alpha = 0.85f)
        for (k in 1 until n) {
            drawLine(minor, Offset(k * cs, 0f), Offset(k * cs, gw), thin)
            drawLine(minor, Offset(0f, k * cs), Offset(gw, k * cs), thin)
        }
        state.regions?.let { region ->
            for (i in 0 until n * n) {
                val r = i / n
                val c = i % n
                if (c + 1 < n && region[i] != region[i + 1]) {
                    drawLine(major, Offset((c + 1) * cs, r * cs), Offset((c + 1) * cs, (r + 1) * cs), thick)
                }
                if (r + 1 < n && region[i] != region[i + n]) {
                    drawLine(major, Offset(c * cs, (r + 1) * cs), Offset((c + 1) * cs, (r + 1) * cs), thick)
                }
            }
        }
        drawRect(major, Offset(thick / 2, thick / 2), Size(gw - thick, gw - thick), style = Stroke(thick))

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
        }
    }
}

/** A ship cell: a round joint with a bar toward every side it is joined on, so a ship reads as one capsule. */
private fun DrawScope.drawShip(x: Float, y: Float, cs: Float, color: Color, up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
    val t = cs * 0.6f
    val cx = x + cs / 2
    val cy = y + cs / 2
    drawCircle(color, t / 2, Offset(cx, cy))
    if (up) drawRect(color, Offset(cx - t / 2, y), Size(t, cs / 2))
    if (down) drawRect(color, Offset(cx - t / 2, cy), Size(t, cs / 2))
    if (left) drawRect(color, Offset(x, cy - t / 2), Size(cs / 2, t))
    if (right) drawRect(color, Offset(cx, cy - t / 2), Size(cs / 2, t))
}

/** A tent: a triangle with a dark doorway. */
private fun DrawScope.drawTent(x: Float, y: Float, cs: Float, color: Color) {
    val p = Path().apply {
        moveTo(x + cs * 0.5f, y + cs * 0.18f)
        lineTo(x + cs * 0.84f, y + cs * 0.8f)
        lineTo(x + cs * 0.16f, y + cs * 0.8f)
        close()
    }
    drawPath(p, color)
    val door = Path().apply {
        moveTo(x + cs * 0.5f, y + cs * 0.46f)
        lineTo(x + cs * 0.62f, y + cs * 0.8f)
        lineTo(x + cs * 0.38f, y + cs * 0.8f)
        close()
    }
    drawPath(door, Color.White)
}

/** A tree: green crown on a short trunk (flat greys on e-ink). */
private fun DrawScope.drawTree(x: Float, y: Float, cs: Float, eink: Boolean) {
    drawRect(if (eink) Color.Black else Color(0xFF6B4A2B), Offset(x + cs * 0.45f, y + cs * 0.6f), Size(cs * 0.1f, cs * 0.24f))
    drawCircle(if (eink) Color(0xFF555555) else Color(0xFF3E8E41), cs * 0.27f, Offset(x + cs * 0.5f, y + cs * 0.4f))
}

private fun DrawScope.drawDot(x: Float, y: Float, cs: Float, color: Color, big: Boolean) {
    drawCircle(color, cs * (if (big) 0.14f else 0.09f), Offset(x + cs / 2, y + cs / 2))
}

private fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color) {
    val path = Path()
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) radius else radius * 0.45f
        val a = -PI / 2 + k * PI / 5
        val px = cx + (r * cos(a)).toFloat()
        val py = cy + (r * sin(a)).toFloat()
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}

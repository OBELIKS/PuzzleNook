package com.puzzlenook.ink

import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic handwriting templates for the digits 0–9 (0 = delete), in a box ~0.65 wide and 1 tall
 * (y grows downwards). Angles are degrees: 0 = right, 90 = down, 180 = left, 270 = up.
 * Each shape is also added slanted left and right to cover italic handwriting.
 */
object BuiltInDigits {

    fun templates(): List<Pair<Int, List<InkStroke>>> {
        val base = listOf(
            // 0 is the delete gesture: a tall oval, closed or with a small gap / overlap.
            0 to listOf(arcStroke(0.3f, 0.5f, 0.3f, 0.5f, 270f, 630f)),
            0 to listOf(arcStroke(0.3f, 0.5f, 0.28f, 0.5f, 250f, 600f)),
            0 to listOf(arcStroke(0.3f, 0.5f, 0.3f, 0.5f, 280f, 680f)),

            1 to listOf(line(0.3f, 0f, 0.3f, 1f)),
            1 to listOf(line(0.12f, 0.22f, 0.32f, 0f, 0.32f, 1f)),
            1 to listOf(line(0.12f, 0.22f, 0.32f, 0f, 0.32f, 1f), line(0.1f, 1f, 0.55f, 1f)),

            2 to listOf(path(arc(0.3f, 0.28f, 0.28f, 0.28f, 200f, 400f), pts(0.02f, 1f, 0.62f, 1f))),
            2 to listOf(path(arc(0.3f, 0.3f, 0.3f, 0.3f, 190f, 360f), pts(0f, 1f, 0.6f, 1f))),

            3 to listOf(path(arc(0.3f, 0.26f, 0.27f, 0.26f, 210f, 450f), arc(0.3f, 0.74f, 0.3f, 0.26f, 270f, 510f))),
            3 to listOf(path(pts(0.05f, 0f, 0.6f, 0f, 0.25f, 0.42f), arc(0.3f, 0.7f, 0.3f, 0.28f, 250f, 510f))),

            4 to listOf(line(0.42f, 0f, 0f, 0.68f, 0.65f, 0.68f), line(0.47f, 0.3f, 0.47f, 1f)),
            4 to listOf(line(0.08f, 0f, 0.05f, 0.6f, 0.65f, 0.6f), line(0.48f, 0f, 0.48f, 1f)),

            5 to listOf(path(pts(0.6f, 0f, 0.1f, 0f, 0.07f, 0.47f), arc(0.3f, 0.68f, 0.3f, 0.32f, 220f, 520f))),
            5 to listOf(path(pts(0.1f, 0f, 0.07f, 0.47f), arc(0.3f, 0.68f, 0.3f, 0.32f, 220f, 520f)), line(0.1f, 0f, 0.6f, 0f)),

            6 to listOf(path(arc(0.6f, 0.55f, 0.55f, 0.55f, 265f, 180f), arc(0.32f, 0.74f, 0.27f, 0.26f, 180f, -180f))),
            6 to listOf(path(pts(0.45f, 0f, 0.08f, 0.6f), arc(0.33f, 0.75f, 0.27f, 0.25f, 200f, -160f))),

            7 to listOf(line(0f, 0f, 0.62f, 0f, 0.22f, 1f)),
            7 to listOf(line(0f, 0f, 0.62f, 0f, 0.22f, 1f), line(0.18f, 0.5f, 0.55f, 0.5f)),

            8 to listOf(arcStroke(0.3f, 0.25f, 0.23f, 0.25f, 0f, 360f), arcStroke(0.3f, 0.74f, 0.28f, 0.26f, 0f, 360f)),
            8 to listOf(
                path(
                    arc(0.3f, 0.25f, 0.23f, 0.25f, 30f, -270f),
                    arc(0.3f, 0.74f, 0.28f, 0.26f, 270f, 630f),
                )
            ),

            9 to listOf(arcStroke(0.3f, 0.27f, 0.28f, 0.27f, 0f, 360f), line(0.58f, 0.27f, 0.52f, 1f)),
            9 to listOf(path(arc(0.3f, 0.27f, 0.28f, 0.27f, 0f, -360f), pts(0.58f, 0.27f, 0.58f, 1f))),
        )
        return base.flatMap { (d, strokes) ->
            listOf(0f, 0.18f, -0.12f).map { slant -> d to strokes.map { slanted(it, slant) } }
        }
    }

    private fun slanted(s: InkStroke, k: Float) =
        InkStroke(s.points.map { it.copy(x = it.x + k * (1f - it.y)) })

    private fun pts(vararg xy: Float): List<InkPoint> {
        val raw = (xy.indices step 2).map { InkPoint(xy[it], xy[it + 1]) }
        // Densify straight segments so they carry similar weight to arcs.
        return raw.zipWithNext { a, b ->
            (0 until 8).map { i -> val f = i / 8f; InkPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f) }
        }.flatten() + raw.last()
    }

    private fun line(vararg xy: Float) = InkStroke(pts(*xy))

    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float, steps: Int = 24): List<InkPoint> =
        (0..steps).map { i ->
            val a = Math.toRadians((from + (to - from) * i / steps).toDouble())
            InkPoint(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }

    private fun arcStroke(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float) =
        InkStroke(arc(cx, cy, rx, ry, from, to))

    private fun path(vararg parts: List<InkPoint>) = InkStroke(parts.flatMap { it })
}

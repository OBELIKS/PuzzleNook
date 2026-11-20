package com.puzzlenook.ink

import kotlin.math.hypot

/** One sampled pen position; [t] is a timestamp in milliseconds. */
data class InkPoint(val x: Float, val y: Float, val t: Long = 0)

data class InkStroke(val points: List<InkPoint>) {
    val length: Float
        get() = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    fun translated(dx: Float, dy: Float) = InkStroke(points.map { it.copy(x = it.x + dx, y = it.y + dy) })
}

/** Axis-aligned bounds of all points: [minX, minY, maxX, maxY], or null when empty. */
fun List<InkStroke>.bounds(): FloatArray? {
    val all = flatMap { it.points }
    if (all.isEmpty()) return null
    return floatArrayOf(all.minOf { it.x }, all.minOf { it.y }, all.maxOf { it.x }, all.maxOf { it.y })
}

/**
 * Fraction of the cell size below which handwriting counts as "small" (a note).
 * Answers are usually written across most of the cell; notes in a corner, under half of it.
 */
const val SMALL_WRITING_FRACTION = 0.45f

/** True when the character's larger side is under [SMALL_WRITING_FRACTION] of [cellSize]. */
fun isSmallWriting(strokes: List<InkStroke>, cellSize: Float): Boolean {
    val b = strokes.bounds() ?: return false
    return maxOf(b[2] - b[0], b[3] - b[1]) < cellSize * SMALL_WRITING_FRACTION
}

/** A recognised digit and which engine produced it. */
data class Recognition(val digit: Int, val engine: Engine) {
    enum class Engine { ML_KIT, BUILT_IN, LEARNED }
}

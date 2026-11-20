package com.puzzlenook.ink

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Offline digit recogniser based on the $P point-cloud recogniser
 * (Vatavu, Anthony & Wobbrock, 2012). Stroke order and direction don't matter,
 * only the shape. Ships with synthetic templates for 1–9 and can learn user samples.
 */
class TemplateDigitRecognizer(builtIn: List<Pair<Int, List<InkStroke>>> = BuiltInDigits.templates()) {

    data class Match(val digit: Int, val distance: Float, val learned: Boolean)

    private class Template(val digit: Int, val cloud: Array<FloatArray>, val learned: Boolean)

    private val templates = builtIn.mapNotNull { (d, s) -> normalize(s)?.let { Template(d, it, false) } }.toMutableList()

    fun addLearned(digit: Int, strokes: List<InkStroke>) {
        normalize(strokes)?.let { templates += Template(digit, it, true) }
    }

    fun clearLearned() {
        templates.removeAll { it.learned }
    }

    /** Best match per digit, closest first. Empty when the ink can't be normalised. */
    fun rank(strokes: List<InkStroke>): List<Match> {
        val cloud = normalize(strokes) ?: return emptyList()
        return templates
            .map { Match(it.digit, greedyCloudMatch(cloud, it.cloud), it.learned) }
            .groupBy { it.digit }
            .map { (_, ms) -> ms.minBy { it.distance } }
            .sortedBy { it.distance }
    }

    fun recognize(strokes: List<InkStroke>): Match? = rank(strokes).firstOrNull()?.takeIf { it.distance <= MAX_DISTANCE }

    companion object {
        const val N = 32
        // Distance scale (from unit tests): distorted digits match at ~0.4–1.1,
        // random scribbles at 1.3+ (median ~1.8).
        /** Matches further than this are rejected as "not a digit". */
        const val MAX_DISTANCE = 2.0f
        /** A learned sample this close overrides ML Kit (the user's own handwriting). */
        const val STRONG_LEARNED_DISTANCE = 0.85f

        private fun normalize(strokes: List<InkStroke>): Array<FloatArray>? {
            // Flatten to (x, y, strokeId) triples.
            val pts = ArrayList<FloatArray>()
            strokes.forEachIndexed { id, s -> s.points.forEach { pts += floatArrayOf(it.x, it.y, id.toFloat()) } }
            if (pts.isEmpty()) return null
            val resampled = resample(pts, N)
            // Uniform scale into the unit box (keeps a "1" thin), then centre on the centroid.
            val minX = resampled.minOf { it[0] }; val maxX = resampled.maxOf { it[0] }
            val minY = resampled.minOf { it[1] }; val maxY = resampled.maxOf { it[1] }
            val size = max(maxX - minX, maxY - minY).takeIf { it > 1e-6f } ?: 1f
            for (p in resampled) { p[0] = (p[0] - minX) / size; p[1] = (p[1] - minY) / size }
            val cx = resampled.sumOf { it[0].toDouble() }.toFloat() / N
            val cy = resampled.sumOf { it[1].toDouble() }.toFloat() / N
            for (p in resampled) { p[0] -= cx; p[1] -= cy }
            return resampled.toTypedArray()
        }

        private fun resample(input: List<FloatArray>, n: Int): MutableList<FloatArray> {
            val pts = input.map { it.copyOf() }.toMutableList()
            var total = 0f
            for (i in 1 until pts.size) if (pts[i][2] == pts[i - 1][2]) total += dist(pts[i - 1], pts[i])
            if (total <= 1e-6f) return MutableList(n) { pts[0].copyOf() }
            val interval = total / (n - 1)
            var acc = 0f
            val out = mutableListOf(pts[0].copyOf())
            var i = 1
            while (i < pts.size) {
                if (pts[i][2] == pts[i - 1][2]) {
                    val d = dist(pts[i - 1], pts[i])
                    if (acc + d >= interval && d > 0f) {
                        val k = (interval - acc) / d
                        val q = floatArrayOf(
                            pts[i - 1][0] + k * (pts[i][0] - pts[i - 1][0]),
                            pts[i - 1][1] + k * (pts[i][1] - pts[i - 1][1]),
                            pts[i][2],
                        )
                        out += q
                        pts.add(i, q)
                        acc = 0f
                    } else {
                        acc += d
                    }
                }
                i++
            }
            while (out.size < n) out += pts.last().copyOf()
            while (out.size > n) out.removeAt(out.lastIndex)
            return out
        }

        private fun greedyCloudMatch(a: Array<FloatArray>, b: Array<FloatArray>): Float {
            val step = floor(N.toDouble().pow(0.5)).toInt()
            var best = Float.MAX_VALUE
            for (start in 0 until N step step) {
                best = min(best, min(cloudDistance(a, b, start), cloudDistance(b, a, start)))
            }
            return best
        }

        private fun cloudDistance(a: Array<FloatArray>, b: Array<FloatArray>, start: Int): Float {
            val matched = BooleanArray(N)
            var sum = 0f
            var i = start
            do {
                var minD = Float.MAX_VALUE
                var index = -1
                for (j in 0 until N) {
                    if (matched[j]) continue
                    val d = dist(a[i], b[j])
                    if (d < minD) { minD = d; index = j }
                }
                matched[index] = true
                val weight = 1f - ((i - start + N) % N).toFloat() / N
                sum += weight * minD
                i = (i + 1) % N
            } while (i != start)
            return sum
        }

        private fun dist(p: FloatArray, q: FloatArray) = hypot(p[0] - q[0], p[1] - q[1])
    }
}

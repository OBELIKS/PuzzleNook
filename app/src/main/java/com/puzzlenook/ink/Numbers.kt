package com.puzzlenook.ink

/**
 * Splits handwriting into digits from left to right: strokes whose horizontal extents overlap
 * (a 4's two strokes, a 7's bar) stay together; a clear gap starts a new digit.
 */
fun splitDigits(strokes: List<InkStroke>): List<List<InkStroke>> {
    if (strokes.isEmpty()) return emptyList()
    class Group(var left: Float, var right: Float, val strokes: MutableList<InkStroke>)
    val all = strokes.bounds() ?: return listOf(strokes)
    val width = all[2] - all[0]
    // Small overlaps (sloppy writing) still count as separate when the gap rule says so.
    val slack = width * 0.06f
    val groups = mutableListOf<Group>()
    for (s in strokes.sortedBy { st -> st.points.minOf { it.x } }) {
        val l = s.points.minOf { it.x }
        val r = s.points.maxOf { it.x }
        val last = groups.lastOrNull()
        if (last != null && l < last.right - slack) {
            last.right = maxOf(last.right, r)
            last.strokes += s
        } else {
            groups += Group(l, r, mutableListOf(s))
        }
    }
    return groups.map { it.strokes }
}

/** Reads a recognised string as a whole number (look-alike letters allowed), or null. */
fun toNumber(text: String): Int? {
    val t = text.trim().replace(" ", "")
    if (t.isEmpty() || t.length > 2) return null
    var v = 0
    for (ch in t) v = v * 10 + (MlKitDigitRecognizer.toDigit(ch.toString()) ?: return null)
    return v
}

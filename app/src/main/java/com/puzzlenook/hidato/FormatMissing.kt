package com.puzzlenook.hidato

/** "1–4, 7, 9, 10, 12–14": sorted, runs of 3 or more as ranges (en dash), the rest comma-separated. */
fun formatMissing(missing: List<Int>): String {
    val sorted = missing.sorted()
    val parts = ArrayList<String>()
    var i = 0
    while (i < sorted.size) {
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
        if (j - i >= 2) {
            parts += "${sorted[i]}–${sorted[j]}"
        } else {
            for (k in i..j) parts += "${sorted[k]}"
        }
        i = j + 1
    }
    return parts.joinToString(", ")
}

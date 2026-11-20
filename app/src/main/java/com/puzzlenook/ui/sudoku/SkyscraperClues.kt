package com.puzzlenook.ui.sudoku

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.puzzlenook.sudoku.GameState
import com.puzzlenook.sudoku.LatinRules
import com.puzzlenook.ui.theme.LocalEink

/** Width of the clue margin around a skyscrapers grid, in cells. */
const val SKY_MARGIN = 0.7f

/**
 * Skyscrapers clues around the grid. A clue fades once its line is filled in and
 * shows the right number of buildings.
 */
@Composable
fun SkyscraperClues(state: GameState, sky: IntArray, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val measurer = rememberTextMeasurer()
    val rules = state.rules!!
    Canvas(modifier) {
        val n = state.size
        val cs = size.width / (n + 2 * SKY_MARGIN)
        val m = SKY_MARGIN * cs
        val ink = if (eink) Color.Black else scheme.onSurface
        val faded = if (eink) Color(0xFFAAAAAA) else scheme.onSurface.copy(alpha = 0.3f)
        val values = IntArray(n * n) { state.cells[it].value }
        for (k in sky.indices) {
            val clue = sky[k]
            if (clue == 0) continue
            val line = rules.lines[k]
            val done = line.all { values[it] != 0 } && LatinRules.visible(values, line) == clue
            val layout = measurer.measure(
                clue.toString(),
                TextStyle(fontSize = (cs * 0.42f).toSp(), color = if (done) faded else ink, fontWeight = FontWeight.Medium),
            )
            val j = k % n
            val center = when (k / n) {
                0 -> Offset(m + (j + 0.5f) * cs, m / 2)
                1 -> Offset(m + (j + 0.5f) * cs, size.height - m / 2)
                2 -> Offset(m / 2, m + (j + 0.5f) * cs)
                else -> Offset(size.width - m / 2, m + (j + 0.5f) * cs)
            }
            drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, center.y - layout.size.height / 2))
        }
    }
}
package com.puzzlenook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one layout of every game screen. Portrait: rule line directly under the title bar, then
 * status, then board and controls as one block centred in the remaining space (controls, number
 * pad first, directly under the board). Landscape (split screen only): board left, texts and
 * controls right. No fixed dp budgets: the board gets whatever the natural-height parts leave.
 */
@Composable
fun GameLayout(
    rule: UiText,
    status: @Composable (scale: Float) -> Unit,
    controls: @Composable (scale: Float) -> Unit,
    boardAspect: Float = 1f,
    maxScale: Float = 1.6f,
    board: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (maxWidth > maxHeight) {
            val availHeight = maxHeight
            val boardWidth = availHeight * boardAspect
            val scale = ((maxWidth - boardWidth) / 320.dp).coerceIn(1f, maxScale)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                board(Modifier.size(boardWidth, availHeight))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    RuleLine(rule, scale)
                    status(scale)
                    controls(scale)
                }
            }
        } else {
            // Phones (~360 dp wide) keep scale 1; a 10" Boox (~900 dp) gets bigger controls.
            val scale = (maxWidth / 400.dp).coerceIn(1f, maxScale)
            val gap = 12.dp * scale
            // Texts and controls may be wider than a small board on tablets, so they get their own width.
            val contentWidth = minOf(maxWidth, 720.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(contentWidth)) { RuleLine(rule, scale) }
                Spacer(Modifier.height(gap))
                Box(Modifier.width(contentWidth)) { status(scale) }
                Spacer(Modifier.height(gap))
                // Board + controls form one block; free space is split above and below it.
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                        val w = minOf(this.maxWidth, this.maxHeight * boardAspect)
                        board(Modifier.size(w, w / boardAspect))
                    }
                    Spacer(Modifier.height(gap))
                    Box(Modifier.width(contentWidth)) { controls(scale) }
                }
            }
        }
    }
}

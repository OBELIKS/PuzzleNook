package com.puzzlenook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.puzzlenook.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import com.puzzlenook.settings.HelpRules
import com.puzzlenook.settings.HelpSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.WinResult
import com.puzzlenook.ui.theme.LocalEink
import com.puzzlenook.sudoku.formatPoints
import com.puzzlenook.sudoku.formatTime

/** "Sudoku · Easy": the title bar of a game screen. */
@Composable
fun gameTitle(variant: Variant, difficulty: Difficulty?): String {
    val title = stringResource(variant.titleRes)
    return if (difficulty == null) title else "$title · ${stringResource(difficulty.labelRes)}"
}

/**
 * The title bar text of a game screen: always one line. A long title shrinks (down to 14 sp)
 * before it is cut with "…", so the timer and buttons never push it onto a second line.
 */
@Composable
fun GameTitle(variant: Variant, difficulty: Difficulty?) {
    val style = LocalTextStyle.current.copy(color = LocalContentColor.current)
    val max = if (style.fontSize.isSpecified) style.fontSize else 22.sp
    BasicText(
        gameTitle(variant, difficulty),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = max),
    )
}

/** Keeps lists and menus at a readable width, centred, on wide screens. */
fun Modifier.readableWidth(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp)

/** A game's rules in one centred line (may wrap) under the board. */
@Composable
fun RuleLine(text: UiText, scale: Float) {
    val style = MaterialTheme.typography.bodyMedium
    Text(
        text.resolve(),
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Mistakes · Hints · Time +x%", or a short message in their place. */
@Composable
fun StatusRow(
    mistakes: Int,
    hintHalfPoints: Int,
    penaltyPercent: Int,
    message: UiText?,
    scale: Float,
    /** False in "errors off" mode: the count stays hidden so it can't give anything away. */
    showMistakes: Boolean = true,
) {
    val style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * scale) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        if (message != null) {
            Text(message.resolve(), style = style, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
        } else {
            Text(
                if (showMistakes) stringResource(R.string.status_mistakes, mistakes) else stringResource(R.string.status_mistakes_hidden),
                style = style,
                color = if (showMistakes && mistakes > 0) MaterialTheme.colorScheme.error else muted,
            )
            Text(stringResource(R.string.status_hints, formatPoints(hintHalfPoints)), style = style, color = muted)
            Text(
                stringResource(R.string.status_time_penalty, penaltyPercent),
                style = style,
                color = if (penaltyPercent > 0) MaterialTheme.colorScheme.error else muted,
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    scale: Float,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        // The selected mode: a tinted fill, or on e-ink a plain black outline.
        color = if (active && !eink) scheme.primaryContainer else scheme.surface,
        contentColor = when {
            !active -> scheme.onSurfaceVariant
            eink -> scheme.onSurface
            else -> scheme.onPrimaryContainer
        },
        border = if (active && eink) BorderStroke(2.dp, scheme.onSurface) else null,
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp * scale, vertical = 6.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp * scale))
            val labelStyle = MaterialTheme.typography.labelMedium
            Text(label, style = labelStyle.copy(fontSize = labelStyle.fontSize * scale))
        }
    }
}

/** Label of the hint button: "Reveal ½", "Hint 1", "No hints", "Hint 0:25", with the hints left on a second line. */
@Composable
fun hintButtonLabel(revealing: Boolean, costHalfPoints: Int, hintsLeft: Int?, cooldownLeft: Long): String {
    if (revealing) return stringResource(R.string.hint_reveal, formatPoints(costHalfPoints))
    if (hintsLeft != null && hintsLeft <= 0) return stringResource(R.string.hint_none_left)
    val main = if (cooldownLeft > 0) stringResource(R.string.hint_cost, formatTime(cooldownLeft))
    else stringResource(R.string.hint_cost, formatPoints(costHalfPoints))
    return if (hintsLeft != null) pluralStringResource(R.plurals.hint_with_left, hintsLeft, main, hintsLeft) else main
}

fun hintEnabled(revealing: Boolean, hintsLeft: Int?, cooldownLeft: Long): Boolean =
    revealing || ((hintsLeft == null || hintsLeft > 0) && cooldownLeft <= 0)

/** Hint button shared by all games; counts the wait down as [elapsedSeconds] ticks. */
@Composable
fun HintButton(
    revealing: Boolean,
    hintsUsed: Int,
    lastHintAt: Long,
    elapsedSeconds: Long,
    help: HelpSettings,
    scale: Float,
    onClick: () -> Unit,
) {
    val cost = HelpRules.stepCost(if (revealing) hintsUsed else hintsUsed + 1, help.risingCost)
    val left = HelpRules.hintsLeft(hintsUsed, help)
    val wait = HelpRules.cooldownLeft(elapsedSeconds, lastHintAt, help.cooldownSeconds)
    ActionButton(
        Icons.Outlined.Lightbulb,
        hintButtonLabel(revealing, cost, left, wait),
        scale,
        enabled = hintEnabled(revealing, left, wait),
        active = revealing,
        onClick = onClick,
    )
}

@Composable
fun WinDialog(result: WinResult, onNewGame: () -> Unit, onMenu: () -> Unit) {
    AlertDialog(
        // Only the buttons close it: a stray tap (easy with a pen) shouldn't lose the result.
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(stringResource(R.string.win_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.win_playing_time, formatTime(result.playSeconds)))
                Text(stringResource(R.string.win_mistakes_hints, result.mistakes, formatPoints(result.hintHalfPoints)))
                if (result.penaltyHalfPoints > 0) {
                    val percent = result.penaltyHalfPoints * result.penaltyPercent / 2
                    Text(
                        if (result.penaltyPercent == 0) {
                            stringResource(R.string.win_penalty_off)
                        } else {
                            stringResource(
                                R.string.win_penalty,
                                formatPoints(result.penaltyHalfPoints), result.penaltyPercent, percent, formatTime(result.penaltySeconds),
                            )
                        },
                    )
                }
                Text(
                    stringResource(if (result.newBest) R.string.win_final_time_best else R.string.win_final_time, formatTime(result.finalSeconds)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onNewGame) { Text(stringResource(R.string.action_new_game)) } },
        dismissButton = { TextButton(onClick = onMenu) { Text(stringResource(R.string.action_menu)) } },
    )
}

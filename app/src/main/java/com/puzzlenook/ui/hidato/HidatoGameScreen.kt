package com.puzzlenook.ui.hidato

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.puzzlenook.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlenook.hidato.HidatoState
import com.puzzlenook.hidato.HidatoViewModel
import com.puzzlenook.hidato.formatMissing
import com.puzzlenook.settings.Devices
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.formatTime
import com.puzzlenook.ui.ActionButton
import com.puzzlenook.ui.HintButton
import com.puzzlenook.ui.ErrorsButton
import com.puzzlenook.ui.GameLayout
import com.puzzlenook.ui.HelpButton
import com.puzzlenook.ui.PauseButton
import com.puzzlenook.ui.PauseOnStop
import com.puzzlenook.ui.PausedPane
import com.puzzlenook.ui.HowToPlayDialog
import com.puzzlenook.ui.SettingsButton
import com.puzzlenook.ui.UiText
import com.puzzlenook.ui.GameTitle
import com.puzzlenook.ui.gameTitle
import com.puzzlenook.ui.RuleLine
import com.puzzlenook.ui.StatusRow
import com.puzzlenook.ui.ruleText
import androidx.compose.ui.text.style.TextOverflow
import com.puzzlenook.ui.WinDialog
import com.puzzlenook.ui.ink.InkController
import com.puzzlenook.ui.ink.OnyxPenInput
import com.puzzlenook.ui.ink.rememberInkController
import com.puzzlenook.ui.theme.LocalEink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidatoGameScreen(
    vm: HidatoViewModel,
    handwriting: Boolean,
    answerColor: Color?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val win by vm.win.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val eink = LocalEink.current
    var help by rememberSaveable { mutableStateOf(false) }

    // Leaving the app or locking the screen pauses the game.
    PauseOnStop { if (state?.completed == false) vm.pause() }

    val ink = rememberInkController(onTap = vm::select, onCharacter = vm::onHandwriting)
    var boardBounds by remember { mutableStateOf<Rect?>(null) }
    val onyxActive = if (Devices.isOnyx) {
        OnyxPenInput(
            enabled = handwriting && state != null && !loading && win == null && !paused,
            boardBounds = boardBounds,
            controller = ink,
            strokeColor = (answerColor ?: Color.Black).toArgb(),
        )
    } else {
        false
    }

    LifecycleResumeEffect(Unit) {
        vm.onScreenResumed()
        onPauseOrDispose { vm.onScreenPaused() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { GameTitle(Variant.HIDATO, state?.difficulty) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    state?.let { Text(formatTime(it.elapsedSeconds), style = MaterialTheme.typography.titleMedium) }
                    if (state?.completed == false) PauseButton(paused) { if (paused) vm.resume() else vm.pause() }
                    HelpButton { help = true }
                    SettingsButton(onOpenSettings)
                },
            )
        },
    ) { padding ->
        val s = state
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (loading || s == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!eink) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(stringResource(R.string.loading_path), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (paused && !s.completed) {
                PausedPane(s.elapsedSeconds, vm::resume)
            } else {
                Layout(s, vm, canUndo, message) { m ->
                    HidatoBoard(
                        s, ink, handwriting, onyxActive, answerColor,
                        m.onGloballyPositioned { boardBounds = it.boundsInRoot() },
                    )
                }
            }
        }
    }

    win?.let { result ->
        WinDialog(
            result,
            onNewGame = { state?.difficulty?.let(vm::newGame) },
            onMenu = { vm.dismissWin(); onBack() },
        )
    }
    if (help) HowToPlayDialog(Variant.HIDATO, state?.penaltyPercent ?: 10) { help = false }
}

@Composable
private fun Layout(
    state: HidatoState,
    vm: HidatoViewModel,
    canUndo: Boolean,
    message: UiText?,
    board: @Composable (Modifier) -> Unit,
) {
    GameLayout(
        rule = ruleText(Variant.HIDATO, 0),
        status = { scale ->
            StatusRow(state.mistakes, state.hintHalfPoints, state.penaltyTotalPercent, message, scale, state.showWrong)
        },
        controls = { scale -> Controls(state, vm, canUndo, scale) },
        // Capped at 1.2 (not 1.6): the number pad is tall, and on tablets a bigger board matters more.
        maxScale = 1.2f,
        board = board,
    )
}

@Composable
private fun Controls(state: HidatoState, vm: HidatoViewModel, canUndo: Boolean, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        // Number pad first: it sits directly under the board.
        for (row in listOf(1..5, (6..9) + 0)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                for (d in row) {
                    Surface(
                        onClick = { vm.digit(d) },
                        shape = RoundedCornerShape(12.dp * scale),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "$d",
                            fontSize = 24.sp * scale,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp * scale),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.action_undo), scale, enabled = canUndo, onClick = vm::undo)
            ActionButton(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.action_erase), scale, onClick = vm::erase)
            ErrorsButton(state.showWrong, scale, vm::toggleShowWrong)
            val revealing = state.activeHintCell != null
            HintButton(revealing, state.hintsUsed, state.lastHintAt, state.elapsedSeconds, state.help, scale, vm::hint)
        }
        // Last, so its 1–3 lines never move the buttons.
        // Its height only grows within a game, so the shrinking text never resizes the board.
        val missing = state.missing
        var reserved by remember(state.solution) { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        if (missing.isNotEmpty()) {
            Text(
                stringResource(R.string.hidato_still_to_place, formatMissing(missing)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .heightIn(min = reserved)
                    .onSizeChanged { reserved = maxOf(reserved, with(density) { it.height.toDp() }) },
            )
        }
    }
}

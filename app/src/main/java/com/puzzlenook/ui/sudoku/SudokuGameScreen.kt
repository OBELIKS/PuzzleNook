package com.puzzlenook.ui.sudoku

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.puzzlenook.settings.Devices
import com.puzzlenook.settings.InkColor
import com.puzzlenook.ui.theme.DigitColors
import com.puzzlenook.ui.theme.resolveInkColor
import com.puzzlenook.ui.ink.InkController
import com.puzzlenook.ui.ink.OnyxPenInput
import com.puzzlenook.ui.ink.rememberInkController
import com.puzzlenook.ui.theme.LocalEink
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.puzzlenook.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlenook.sudoku.GameState
import com.puzzlenook.sudoku.SudokuViewModel
import com.puzzlenook.sudoku.formatTime
import com.puzzlenook.sudoku.formatPoints
import com.puzzlenook.ui.SettingsButton
import com.puzzlenook.ui.UiText
import com.puzzlenook.ui.GameTitle
import com.puzzlenook.ui.gameTitle
import com.puzzlenook.ui.ActionButton
import com.puzzlenook.ui.HintButton
import com.puzzlenook.ui.ErrorsButton
import com.puzzlenook.ui.GameLayout
import com.puzzlenook.ui.HelpButton
import com.puzzlenook.ui.PauseButton
import com.puzzlenook.ui.PauseOnStop
import com.puzzlenook.ui.PausedPane
import com.puzzlenook.ui.HowToPlayDialog
import com.puzzlenook.ui.RuleLine
import com.puzzlenook.ui.StatusRow
import com.puzzlenook.ui.ruleText
import com.puzzlenook.ui.WinDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuGameScreen(
    vm: SudokuViewModel,
    handwriting: Boolean,
    answerColor: InkColor,
    noteColor: InkColor,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = DigitColors(resolveInkColor(answerColor), resolveInkColor(noteColor))
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
    val boardShown = state != null && !loading
    val onyxActive = if (Devices.isOnyx) {
        OnyxPenInput(
            enabled = handwriting && boardShown && win == null && !paused,
            boardBounds = boardBounds,
            controller = ink,
            strokeColor = (colors.answer ?: Color.Black).toArgb(),
        )
    } else {
        false
    }
    val input = BoardInput(ink, handwriting, onyxActive, colors) { boardBounds = it }

    LifecycleResumeEffect(Unit) {
        vm.onScreenResumed()
        onPauseOrDispose { vm.onScreenPaused() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { GameTitle(vm.variant, state?.difficulty) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    state?.let {
                        Text(formatTime(it.elapsedSeconds), style = MaterialTheme.typography.titleMedium)
                    }
                    if (state?.completed == false) PauseButton(paused) { if (paused) vm.resume() else vm.pause() }
                    HelpButton { help = true }
                    SettingsButton(onOpenSettings)
                },
            )
        },
    ) { padding ->
        val s = state
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                loading || s == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!eink) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(stringResource(R.string.loading_numbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                paused && !s.completed -> PausedPane(s.elapsedSeconds, vm::resume)
                else -> SudokuLayout(s, vm, canUndo, message, input)
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

    if (help) HowToPlayDialog(vm.variant, state?.penaltyPercent ?: 10) { help = false }
}
/** Everything the board needs for pen/finger input. */
private class BoardInput(
    val ink: InkController,
    val handwriting: Boolean,
    val onyxActive: Boolean,
    val colors: DigitColors,
    val onBounds: (Rect) -> Unit,
)

@Composable
private fun Board(state: GameState, input: BoardInput, modifier: Modifier) {
    val board = @Composable { m: Modifier ->
        SudokuBoard(
            state = state,
            ink = input.ink,
            handwriting = input.handwriting,
            stylusHandledExternally = input.onyxActive,
            colors = input.colors,
            modifier = m.onGloballyPositioned { input.onBounds(it.boundsInRoot()) },
        )
    }
    val sky = state.rules?.sky
    if (sky == null) {
        board(modifier)
        return
    }
    // Skyscrapers: clues sit in a margin around the grid.
    BoxWithConstraints(modifier) {
        val margin = maxWidth * (SKY_MARGIN / (state.size + 2 * SKY_MARGIN))
        SkyscraperClues(state, sky, Modifier.fillMaxSize())
        board(Modifier.padding(margin).fillMaxSize())
    }
}

@Composable
private fun SudokuLayout(state: GameState, vm: SudokuViewModel, canUndo: Boolean, message: UiText?, input: BoardInput) {
    GameLayout(
        rule = ruleText(state.variant, state.size),
        status = { scale ->
            StatusRow(state.mistakes, state.hintHalfPoints, state.penaltyTotalPercent, message, scale, state.showWrong)
        },
        controls = { scale -> Controls(state, vm, canUndo, scale) },
    ) { m -> Board(state, input, m) }
}

@Composable
private fun Controls(state: GameState, vm: SudokuViewModel, canUndo: Boolean, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        // Number pad first: it sits directly under the board.
        NumberPad(state, vm::input, scale)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.action_undo), scale, enabled = canUndo, onClick = vm::undo)
            ActionButton(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.action_erase), scale, onClick = vm::erase)
            ActionButton(
                Icons.Outlined.Edit,
                stringResource(if (state.notesMode) R.string.notes_on else R.string.notes_off),
                scale,
                active = state.notesMode,
                onClick = vm::toggleNotes,
            )
            ErrorsButton(state.showWrong, scale, vm::toggleShowWrong)
            // First press highlights a cell (½ point), second reveals its number (½ more).
            val revealing = state.activeHintCell != null
            HintButton(revealing, state.hintsUsed, state.lastHintAt, state.elapsedSeconds, state.help, scale, vm::hint)
        }
    }
}

@Composable
private fun NumberPad(state: GameState, onDigit: (Int) -> Unit, scale: Float) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        for (d in 1..state.digits) {
            val left = state.remaining(d)
            val done = left <= 0
            Surface(
                onClick = { onDigit(d) },
                enabled = !done,
                shape = RoundedCornerShape(12.dp * scale),
                color = if (state.notesMode) scheme.secondaryContainer else scheme.primaryContainer,
                contentColor = if (state.notesMode) scheme.onSecondaryContainer else scheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).alpha(if (done) 0.3f else 1f),
            ) {
                Column(Modifier.padding(vertical = 8.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$d",
                        fontSize = 24.sp * scale,
                        fontWeight = if (state.notesMode) FontWeight.Normal else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Text(if (done) "✓" else "$left", fontSize = 11.sp * scale)
                }
            }
        }
    }
}

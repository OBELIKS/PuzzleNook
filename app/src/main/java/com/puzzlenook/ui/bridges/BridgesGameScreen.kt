package com.puzzlenook.ui.bridges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlenook.R
import com.puzzlenook.bridges.BridgesState
import com.puzzlenook.bridges.BridgesViewModel
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.formatTime
import com.puzzlenook.ui.ActionButton
import com.puzzlenook.ui.ErrorsButton
import com.puzzlenook.ui.GameLayout
import com.puzzlenook.ui.GameTitle
import com.puzzlenook.ui.HelpButton
import com.puzzlenook.ui.HintButton
import com.puzzlenook.ui.HowToPlayDialog
import com.puzzlenook.ui.PauseButton
import com.puzzlenook.ui.PauseOnStop
import com.puzzlenook.ui.PausedPane
import com.puzzlenook.ui.SettingsButton
import com.puzzlenook.ui.StatusRow
import com.puzzlenook.ui.WinDialog
import com.puzzlenook.ui.ruleText
import com.puzzlenook.ui.theme.LocalEink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgesGameScreen(
    vm: BridgesViewModel,
    lineColor: Color?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val win by vm.win.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val eink = LocalEink.current
    var help by rememberSaveable { mutableStateOf(false) }

    // Leaving the app or locking the screen pauses the game.
    PauseOnStop { if (state?.completed == false) vm.pause() }

    LifecycleResumeEffect(Unit) {
        vm.onScreenResumed()
        onPauseOrDispose { vm.onScreenPaused() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { GameTitle(Variant.BRIDGES, state?.difficulty) },
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
                    Text(stringResource(R.string.loading_grid), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (paused && !s.completed) {
                PausedPane(s.elapsedSeconds, vm::resume)
            } else {
                Layout(s, vm, canUndo, lineColor)
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
    if (help) HowToPlayDialog(Variant.BRIDGES, state?.penaltyPercent ?: 10) { help = false }
}

@Composable
private fun Layout(state: BridgesState, vm: BridgesViewModel, canUndo: Boolean, lineColor: Color?) {
    GameLayout(
        rule = ruleText(Variant.BRIDGES, state.n),
        status = { scale ->
            StatusRow(state.mistakes, state.hintHalfPoints, state.penaltyTotalPercent, null, scale, state.showWrong)
        },
        controls = { scale -> Controls(state, vm, canUndo, scale) },
    ) { m -> BridgesBoard(state, lineColor, vm::cycle, m) }
}

@Composable
private fun Controls(state: BridgesState, vm: BridgesViewModel, canUndo: Boolean, scale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.action_undo), scale, enabled = canUndo, onClick = vm::undo)
        ErrorsButton(state.showWrong, scale, vm::toggleShowWrong)
        val revealing = state.activeHintSlot != null
        HintButton(revealing, state.hintsUsed, state.lastHintAt, state.elapsedSeconds, state.help, scale, vm::hint)
    }
}

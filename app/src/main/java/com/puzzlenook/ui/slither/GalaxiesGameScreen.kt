package com.puzzlenook.ui.slither

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Polyline
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
import com.puzzlenook.R
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlenook.slither.GalaxiesState
import com.puzzlenook.slither.GalaxiesViewModel
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
import com.puzzlenook.ui.GameTitle
import com.puzzlenook.ui.gameTitle
import com.puzzlenook.ui.RuleLine
import com.puzzlenook.ui.StatusRow
import com.puzzlenook.ui.ruleText
import com.puzzlenook.ui.WinDialog
import com.puzzlenook.ui.theme.LocalEink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxiesGameScreen(
    vm: GalaxiesViewModel,
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
                title = { GameTitle(Variant.GALAXIES, state?.difficulty) },
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
                    Text(stringResource(R.string.loading_galaxies), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (help) HowToPlayDialog(Variant.GALAXIES, state?.penaltyPercent ?: 10) { help = false }
}

@Composable
private fun Layout(state: GalaxiesState, vm: GalaxiesViewModel, canUndo: Boolean, lineColor: Color?) {
    GameLayout(
        rule = ruleText(Variant.GALAXIES, 0),
        status = { scale ->
            StatusRow(state.mistakes, state.hintHalfPoints, state.penaltyTotalPercent, null, scale, state.showWrong)
        },
        controls = { scale -> Controls(state, vm, canUndo, scale) },
    ) { m -> GalaxiesBoard(state, lineColor, vm::tapEdge, vm::dragStart, vm::dragEdge, vm::dragEnd, m) }
}

@Composable
private fun Controls(state: GalaxiesState, vm: GalaxiesViewModel, canUndo: Boolean, scale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.action_undo), scale, enabled = canUndo, onClick = vm::undo)
        ActionButton(Icons.Outlined.Polyline, stringResource(R.string.mode_wall), scale, active = !state.crossMode) { vm.setCrossMode(false) }
        ActionButton(Icons.Outlined.Close, stringResource(R.string.mode_cross_out), scale, active = state.crossMode) { vm.setCrossMode(true) }
        ErrorsButton(state.showWrong, scale, vm::toggleShowWrong)
        val revealing = state.activeHintEdge != null
        HintButton(revealing, state.hintsUsed, state.lastHintAt, state.elapsedSeconds, state.help, scale, vm::hint)
    }
}

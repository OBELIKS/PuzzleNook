package com.puzzlenook.ui.shade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Square
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.ui.text.style.TextAlign
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.puzzlenook.R
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlenook.shade.ShadeState
import com.puzzlenook.shade.ShadeViewModel
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
import com.puzzlenook.ui.RuleLine
import com.puzzlenook.ui.ruleText
import com.puzzlenook.ui.HowToPlayDialog
import com.puzzlenook.ui.SettingsButton
import com.puzzlenook.ui.GameTitle
import com.puzzlenook.ui.gameTitle
import com.puzzlenook.ui.StatusRow
import com.puzzlenook.ui.WinDialog
import com.puzzlenook.ui.theme.LocalEink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadeGameScreen(
    vm: ShadeViewModel,
    fillColor: Color?,
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
                title = { GameTitle(vm.variant, state?.difficulty) },
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
                Layout(s, vm, canUndo, fillColor)
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

@Composable
private fun Layout(state: ShadeState, vm: ShadeViewModel, canUndo: Boolean, fillColor: Color?) {
    GameLayout(
        rule = ruleText(state.variant, state.size, state.stars),
        status = { scale ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                if (state.variant == Variant.BATTLESHIPS) FleetRow(state, scale)
                StatusRow(state.mistakes, state.hintHalfPoints, state.penaltyTotalPercent, null, scale, state.showWrong)
            }
        },
        controls = { scale -> Controls(state, vm, canUndo, scale) },
    ) { m -> ShadeBoard(state, fillColor, vm::strokeStart, vm::strokeMove, vm::strokeEnd, m) }
}

/** Battleships: the fleet as rows of squares, longest first; a ship you have finished (from your own marks) is grey with a tick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetRow(state: ShadeState, scale: Float) {
    val eink = LocalEink.current
    val ink = if (eink) Color.Black else MaterialTheme.colorScheme.onSurface
    val doneColor = if (eink) Color(0xFF909090) else MaterialTheme.colorScheme.outline
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp * scale),
    ) {
        for ((len, done) in state.fleetStatus()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp * scale)) {
                repeat(len) { Box(Modifier.size(9.dp * scale).background(if (done) doneColor else ink)) }
                if (done) Text("✓", style = MaterialTheme.typography.bodySmall.let { it.copy(fontSize = it.fontSize * scale) }, color = doneColor)
            }
        }
    }
}

/** Labels and icons of the two marking modes; each icon is the shape the board draws. */
private data class Modes(@StringRes val fillLabel: Int, @StringRes val crossLabel: Int, val fillIcon: ImageVector, val crossIcon: ImageVector)

private fun modes(variant: Variant) = when (variant) {
    Variant.STAR_BATTLE -> Modes(R.string.mode_star, R.string.mode_mark_empty, Icons.Filled.Star, Icons.Filled.FiberManualRecord)
    Variant.HITORI -> Modes(R.string.mode_shade, R.string.mode_mark_white, Icons.Filled.Square, Icons.Outlined.Circle)
    Variant.TAKUZU -> Modes(R.string.mode_black, R.string.mode_white, Icons.Filled.Circle, Icons.Outlined.Circle)
    Variant.MOSAIC -> Modes(R.string.mode_shade, R.string.mode_mark_white, Icons.Filled.Square, Icons.Outlined.Circle)
    Variant.LIGHT_UP -> Modes(R.string.mode_bulb, R.string.mode_no_bulb, Icons.Filled.Circle, Icons.Filled.FiberManualRecord)
    Variant.TENTS -> Modes(R.string.mode_tent, R.string.mode_no_tent, Icons.Filled.ChangeHistory,Icons.Filled.FiberManualRecord)
    Variant.KURODOKO -> Modes(R.string.mode_shade, R.string.mode_mark_white, Icons.Filled.Square, Icons.Filled.FiberManualRecord)
    Variant.NORINORI -> Modes(R.string.mode_shade, R.string.mode_mark_white, Icons.Filled.Square, Icons.Filled.FiberManualRecord)
    Variant.BATTLESHIPS -> Modes(R.string.mode_ship, R.string.mode_water, Icons.Filled.Circle, Icons.Filled.FiberManualRecord)
    Variant.AQUARIUM -> Modes(R.string.mode_water_fill, R.string.mode_air, Icons.Filled.Square, Icons.Filled.FiberManualRecord)
    Variant.HEYAWAKE -> Modes(R.string.mode_shade, R.string.mode_mark_white, Icons.Filled.Square, Icons.Filled.FiberManualRecord)
    Variant.LITS -> Modes(R.string.mode_shade, R.string.mode_mark_white, Icons.Filled.Square, Icons.Filled.FiberManualRecord)
    Variant.NURIKABE -> Modes(R.string.mode_sea, R.string.mode_island, Icons.Filled.Square, Icons.Filled.FiberManualRecord)
    else -> error("${variant} is not a shading puzzle")
}
@Composable
private fun Controls(state: ShadeState, vm: ShadeViewModel, canUndo: Boolean, scale: Float) {
    val (fillLabel, crossLabel, fillIcon, crossIcon) = modes(state.variant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.action_undo), scale, enabled = canUndo, onClick = vm::undo)
        ActionButton(fillIcon, stringResource(fillLabel), scale, active = !state.crossMode) { vm.setCrossMode(false) }
        ActionButton(crossIcon, stringResource(crossLabel), scale, active = state.crossMode) { vm.setCrossMode(true) }
        ErrorsButton(state.showWrong, scale, vm::toggleShowWrong)
        val revealing = state.activeHintCell != null
        HintButton(revealing, state.hintsUsed, state.lastHintAt, state.elapsedSeconds, state.help, scale, vm::hint)
    }
}

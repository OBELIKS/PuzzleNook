package com.puzzlenook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import com.puzzlenook.R
import com.puzzlenook.sudoku.formatTime

/** Pause or resume button for the top bar. */
@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            contentDescription = stringResource(if (paused) R.string.action_resume else R.string.action_pause),
        )
    }
}

/** What a paused game shows instead of the board: just the background and a resume button. */
@Composable
fun PausedPane(elapsedSeconds: Long, onResume: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.paused_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            formatTime(elapsedSeconds),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onResume) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_resume), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Calls [onStop] when the app leaves the screen: home button, other app, or screen lock. */
@Composable
fun PauseOnStop(onStop: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onStop() }
}

/**
 * The "hide errors" switch ("Errors on" = shown). While errors are hidden, wrong entries look
 * normal and cost nothing; switching them back on reveals them and charges a mistake for each.
 */
@Composable
fun ErrorsButton(showWrong: Boolean, scale: Float, onClick: () -> Unit) {
    ActionButton(
        if (showWrong) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
        stringResource(if (showWrong) R.string.errors_on else R.string.errors_off),
        scale,
        active = !showWrong,
        onClick = onClick,
    )
}

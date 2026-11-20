package com.puzzlenook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.puzzlenook.R
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.DifficultyStats
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.formatTime

/** The game in progress, as shown on menus. */
data class SavedGame(val difficulty: Difficulty, val elapsedSeconds: Long)

/** Menu for one game: continue, how to play, and new games by difficulty with stats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMenuScreen(
    variant: Variant,
    saved: SavedGame?,
    stats: Map<Difficulty, DifficultyStats>,
    penaltyPercent: Int,
    difficultyDetail: (Difficulty) -> UiText?,
    onNewGame: (Difficulty) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var pending by remember { mutableStateOf<Difficulty?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }

    fun start(d: Difficulty) {
        onNewGame(d)
        onPlay()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(variant.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    HelpButton { help = true }
                    StatsButton(onOpenStats)
                    SettingsButton(onOpenSettings)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                gameSummary(variant).resolve(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { help = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_how_to_play))
            }

            if (saved != null) {
                Surface(
                    onClick = onPlay,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_continue), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.menu_saved_detail, stringResource(saved.difficulty.labelRes), formatTime(saved.elapsedSeconds)))
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    }
                }
            }

            Text(
                stringResource(R.string.menu_new_game_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            for (d in Difficulty.entries) {
                val st = stats[d]
                Surface(
                    onClick = { if (saved != null) pending = d else start(d) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(d.labelRes), style = MaterialTheme.typography.titleLarge)
                            difficultyDetail(d)?.let {
                                Text(it.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                st?.bestSeconds?.let { stringResource(R.string.menu_best, formatTime(it)) } ?: stringResource(R.string.menu_no_best),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.menu_solved, st?.wins ?: 0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (help) HowToPlayDialog(variant, penaltyPercent) { help = false }

    pending?.let { d ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.menu_replace_title)) },
            text = { Text(stringResource(R.string.menu_replace_text, saved?.difficulty?.let { stringResource(it.labelRes) } ?: "")) },
            confirmButton = {
                TextButton(onClick = { pending = null; start(d) }) { Text(stringResource(R.string.menu_start_difficulty, stringResource(d.labelRes))) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

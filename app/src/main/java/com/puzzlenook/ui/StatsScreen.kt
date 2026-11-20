package com.puzzlenook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.puzzlenook.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.puzzlenook.settings.AppSettings
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameStore
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.WinRecord
import com.puzzlenook.sudoku.formatPoints
import com.puzzlenook.sudoku.formatTime
import java.util.Date

/** Results for one game and difficulty, with times worked out for one penalty setting. */
data class DifficultySummary(
    val difficulty: Difficulty,
    val solved: Int,
    val started: Int,
    val best: Long?,
    val average: Long?,
    /** Best time saved before detailed records existed (with the penalty setting of that time). */
    val oldBest: Long?,
    /** Wins without any hint step (mistakes allowed). */
    val clean: Int = 0,
    /** Best final time among clean wins. */
    val bestClean: Long? = null,
    /** Wins with no hints and Errors off the whole game. */
    val unaided: Int = 0,
    val bestUnaided: Long? = null,
)

object Stats {
    fun summarize(
        records: List<WinRecord>,
        percent: Int,
        d: Difficulty,
        wins: Int,
        started: Int,
        storedBest: Long?,
        cleanWins: Int = 0,
        unaidedWins: Int = 0,
    ): DifficultySummary {
        val mine = records.filter { it.difficulty == d }
        val times = mine.map { it.seconds(percent) }
        val cleanTimes = mine.filter { it.clean }.map { it.seconds(percent) }
        val unaidedTimes = mine.filter { it.unaided }.map { it.seconds(percent) }
        return DifficultySummary(
            difficulty = d,
            solved = maxOf(wins, mine.size),
            started = maxOf(started, wins),
            best = times.minOrNull(),
            average = times.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            oldBest = storedBest.takeIf { mine.isEmpty() },
            clean = maxOf(cleanWins, cleanTimes.size),
            bestClean = cleanTimes.minOrNull(),
            unaided = maxOf(unaidedWins, unaidedTimes.size),
            bestUnaided = unaidedTimes.minOrNull(),
        )
    }

    fun penaltyLabel(percent: Int) =
        if (percent == 0) UiText(R.string.stats_penalty_none) else UiText(R.string.stats_penalty_percent, listOf(percent))
}

private class GameData(val variant: Variant, val records: List<WinRecord>, val rows: (Int) -> List<DifficultySummary>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    initialVariant: Variant?,
    currentPenalty: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onReset: (List<Variant>) -> Unit,
) {
    val context = LocalContext.current
    var reloadKey by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    // Each game's store, read when the screen opens and again after a reset.
    val games = remember(reloadKey) {
        Variant.entries.map { v ->
            val store = GameStore(context, v)
            val records = store.history()
            val wins = Difficulty.entries.associateWith { store.stats(it) }
            val started = Difficulty.entries.associateWith { store.started(it) }
            GameData(v, records) { percent ->
                Difficulty.entries.map { d ->
                    Stats.summarize(records, percent, d, wins.getValue(d).wins, started.getValue(d), wins.getValue(d).bestSeconds, wins.getValue(d).clean, wins.getValue(d).unaided)
                }
            }
        }
    }
    var selected by rememberSaveable { mutableStateOf(initialVariant) }
    var percent by rememberSaveable { mutableStateOf(currentPenalty) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { confirmReset = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.stats_reset_description))
                    }
                    SettingsButton(onOpenSettings)
                },
            )
        },
    ) { padding ->
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text(stringResource(R.string.stats_reset_title)) },
                text = {
                    Text(
                        selected?.let { stringResource(R.string.stats_reset_text_game, stringResource(it.titleRes)) }
                            ?: stringResource(R.string.stats_reset_text_all),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val list = selected?.let { listOf(it) } ?: Variant.entries.toList()
                        list.forEach { GameStore(context, it).resetStats() }
                        onReset(list)
                        reloadKey++
                        confirmReset = false
                    }) { Text(stringResource(R.string.action_reset)) }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Dropdown(
                label = stringResource(R.string.stats_game_label),
                value = stringResource(selected?.titleRes ?: R.string.stats_all_games),
                options = listOf<Variant?>(null) + Variant.entries,
                optionLabel = { stringResource(it?.titleRes ?: R.string.stats_all_games) },
                onSelect = { selected = it },
            )
            Dropdown(
                label = stringResource(R.string.stats_times_label),
                value = Stats.penaltyLabel(percent).resolve(),
                options = AppSettings.PENALTY_CHOICES,
                optionLabel = {
                    val label = Stats.penaltyLabel(it).resolve()
                    if (it == currentPenalty) stringResource(R.string.stats_your_setting, label) else label
                },
                onSelect = { percent = it },
            )

            val shown = games.filter { selected == null || it.variant == selected }
            val records = shown.flatMap { it.records }
            val rows = shown.associateWith { it.rows(percent) }
            Overview(rows.values.flatten(), records)

            for (g in shown) {
                val gameRows = rows.getValue(g).filter { it.solved > 0 || it.started > 0 }
                if (selected == null && gameRows.isEmpty()) continue
                GameCard(stringResource(g.variant.titleRes), gameRows)
            }
            if (selected == null && rows.values.flatten().none { it.started > 0 }) {
                Text(stringResource(R.string.stats_nothing_played), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val recent = records.sortedByDescending { it.atMillis }.take(10)
            if (recent.isNotEmpty()) RecentCard(recent, percent, showGame = selected == null)

            Text(
                if (percent == 0) {
                    stringResource(R.string.stats_note_no_penalty)
                } else {
                    stringResource(R.string.stats_note_penalty, percent)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rows.values.flatten().any { it.oldBest != null }) {
                Text(
                    stringResource(R.string.stats_note_old_best),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(label: String, value: String, options: List<T>, optionLabel: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (o in options) {
                DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    onClick = {
                        onSelect(o)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun Overview(rows: List<DifficultySummary>, records: List<WinRecord>) {
    val solved = rows.sumOf { it.solved }
    val started = rows.sumOf { it.started }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.stats_solved), "$solved", Modifier.weight(1f))
                Figure(stringResource(R.string.stats_started), "$started", Modifier.weight(1f))
                Figure(
                    stringResource(R.string.stats_finished),
                    if (started > 0) stringResource(R.string.stats_percent, solved * 100 / started) else "–",
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.stats_time_played), formatTime(records.sumOf { it.playSeconds }), Modifier.weight(1f))
                Figure(
                    stringResource(R.string.stats_mistakes_per_game),
                    if (records.isEmpty()) "–" else String.format(java.util.Locale.forLanguageTag("sl"), "%.1f", records.map { it.mistakes }.average()),
                    Modifier.weight(1f),
                )
                Figure(
                    stringResource(R.string.stats_hints_per_game),
                    if (records.isEmpty()) "–" else formatPoints(kotlin.math.round(records.sumOf { it.hintHalfPoints }.toDouble() / records.size).toInt()),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GameCard(title: String, rows: List<DifficultySummary>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) {
                Text(stringResource(R.string.stats_not_played), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            TableRow("", stringResource(R.string.stats_solved), stringResource(R.string.stats_header_best), stringResource(R.string.stats_header_average), header = true)
            HorizontalDivider()
            for (r in rows) {
                TableRow(
                    stringResource(r.difficulty.labelRes),
                    stringResource(R.string.stats_solved_of, r.solved, r.started),
                    r.best?.let(::formatTime) ?: r.oldBest?.let { formatTime(it) + "*" } ?: "–",
                    r.average?.let(::formatTime) ?: "–",
                )
                Text(
                    stringResource(
                        R.string.stats_clean_unaided,
                        r.clean, r.bestClean?.let(::formatTime) ?: "–", r.unaided, r.bestUnaided?.let(::formatTime) ?: "–",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val weight = if (header) FontWeight.Normal else null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(a, style = style, fontWeight = weight ?: FontWeight.Medium, modifier = Modifier.weight(1.1f))
        Text(b, style = style, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(c, style = style, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(d, style = style, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecentCard(recent: List<WinRecord>, percent: Int, showGame: Boolean) {
    val dates = remember { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.stats_recent_wins), style = MaterialTheme.typography.titleMedium)
            for (w in recent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (showGame) "${stringResource(w.variant.titleRes)} · ${stringResource(w.difficulty.labelRes)}" else stringResource(w.difficulty.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.stats_recent_detail, w.mistakes,
                                dates.format(Date(w.atMillis)), w.mistakes, formatPoints(w.hintHalfPoints),
                            ) + when {
                                w.unaided -> " · " + stringResource(R.string.stats_tag_unaided)
                                w.clean -> " · " + stringResource(R.string.stats_tag_clean)
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
                        Text(formatTime(w.seconds(percent)), style = MaterialTheme.typography.bodyLarge)
                        if (percent > 0 && w.penaltyHalfPoints > 0) {
                            Text(
                                stringResource(R.string.stats_played_time, formatTime(w.playSeconds)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

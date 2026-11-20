package com.puzzlenook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.puzzlenook.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlenook.ink.HandwritingRecognizer
import com.puzzlenook.ink.HandwritingRecognizer.MlKitState
import com.puzzlenook.settings.AppLanguage
import com.puzzlenook.settings.AppSettings
import com.puzzlenook.settings.Devices
import com.puzzlenook.settings.ThemeMode
import com.puzzlenook.settings.InkColor
import com.puzzlenook.ui.theme.LocalEink
import com.puzzlenook.ui.theme.resolveInkColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, recognizer: HandwritingRecognizer, onBack: () -> Unit) {
    val language by settings.language.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    val handwriting by settings.handwriting.collectAsStateWithLifecycle()
    val mlKit by recognizer.mlKitState.collectAsStateWithLifecycle()
    val learned by recognizer.learnedCount.collectAsStateWithLifecycle()
    val smallNotes by settings.smallWritingNotes.collectAsStateWithLifecycle()
    val answerColor by settings.answerColor.collectAsStateWithLifecycle()
    val noteColor by settings.noteColor.collectAsStateWithLifecycle()
    val penalty by settings.penaltyPercent.collectAsStateWithLifecycle()
    val hintLimit by settings.hintLimit.collectAsStateWithLifecycle()
    val risingCost by settings.risingHintCost.collectAsStateWithLifecycle()
    val cooldown by settings.hintCooldown.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_language))
            Column(Modifier.selectableGroup()) {
                for (l in AppLanguage.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = language == l, role = Role.RadioButton) { settings.setLanguage(l) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == l, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(l.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup()) {
                for (mode in ThemeMode.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = theme == mode, role = Role.RadioButton) { settings.setTheme(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(mode.labelRes), style = MaterialTheme.typography.bodyLarge)
                            if (mode == ThemeMode.EINK) {
                                Text(
                                    stringResource(R.string.theme_eink_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_number_colours))
            Text(
                stringResource(R.string.settings_number_colours_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ColorPreview(answerColor, noteColor)
            Text(stringResource(R.string.settings_your_answers), style = MaterialTheme.typography.labelLarge)
            ColorSwatches(selected = answerColor, onSelect = settings::setAnswerColor)
            Text(stringResource(R.string.settings_notes), style = MaterialTheme.typography.labelLarge)
            ColorSwatches(selected = noteColor, onSelect = settings::setNoteColor)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_penalty))
            Text(
                stringResource(R.string.settings_penalty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PenaltyChoices(penalty, settings::setPenaltyPercent)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_help))
            Text(
                stringResource(R.string.settings_help_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.settings_hints_per_game), style = MaterialTheme.typography.labelLarge)
            ChipChoices(
                AppSettings.HINT_LIMIT_CHOICES, hintLimit,
                { if (it < 0) stringResource(R.string.hint_limit_unlimited) else if (it == 0) stringResource(R.string.hint_limit_none) else "$it" },
                settings::setHintLimit,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = risingCost, role = Role.Switch) { settings.setRisingHintCost(it) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_rising_cost), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_rising_cost_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = risingCost, onCheckedChange = null)
            }
            Text(stringResource(R.string.settings_wait_between_hints), style = MaterialTheme.typography.labelLarge)
            ChipChoices(
                AppSettings.COOLDOWN_CHOICES, cooldown,
                { if (it == 0) stringResource(R.string.cooldown_off) else if (it < 60) stringResource(R.string.cooldown_seconds, it) else stringResource(R.string.cooldown_minutes, it / 60) },
                settings::setHintCooldown,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_handwriting))
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = handwriting, role = Role.Switch) {
                        settings.setHandwriting(it)
                        if (it) recognizer.prepare()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_stylus), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_stylus_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = handwriting, onCheckedChange = null)
            }

            if (handwriting) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = smallNotes, role = Role.Switch) { settings.setSmallWritingNotes(it) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_small_notes), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_small_notes_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = smallNotes, onCheckedChange = null)
                }
                InfoLine(
                    stringResource(R.string.settings_recogniser),
                    stringResource(
                        when (mlKit) {
                            MlKitState.READY -> R.string.recogniser_ready
                            MlKitState.DOWNLOADING, MlKitState.NOT_STARTED -> R.string.recogniser_downloading
                            MlKitState.UNAVAILABLE -> R.string.recogniser_unavailable
                        },
                    ),
                )
                if (Devices.isOnyx) InfoLine(stringResource(R.string.settings_pen), stringResource(R.string.settings_pen_onyx))
                InfoLine(
                    stringResource(R.string.settings_learned),
                    if (learned == 0) {
                        stringResource(R.string.learned_none)
                    } else {
                        pluralStringResource(R.plurals.learned_samples, learned, learned)
                    },
                )
                if (learned > 0) {
                    OutlinedButton(onClick = recognizer::resetLearning) { Text(stringResource(R.string.settings_forget_learned)) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_about))
            InfoLine(stringResource(R.string.settings_version), appVersion())
        }
    }
}

/** "0.10.2 (build 21)", read from the installed package. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
        ?: return stringResource(R.string.version_unknown)
    @Suppress("DEPRECATION")
    return stringResource(R.string.version_value, info.versionName ?: "", info.versionCode)
}

/** A sample cell row: a starting number, one of your answers, and a cell of notes. */
@Composable
private fun ColorPreview(answer: InkColor, note: InkColor) {
    val scheme = MaterialTheme.colorScheme
    val eink = LocalEink.current
    val answerColor = resolveInkColor(answer) ?: if (eink) Color.Black else scheme.primary
    val noteColor = resolveInkColor(note) ?: scheme.onSurfaceVariant
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .border(2.dp, scheme.onSurface)
            .background(scheme.surfaceContainerLowest),
    ) {
        PreviewCell { Text("5", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface) }
        PreviewCell {
            Text(
                "8",
                fontSize = 30.sp,
                fontWeight = if (eink && answer == InkColor.DEFAULT) FontWeight.Light else FontWeight.Normal,
                color = answerColor,
            )
        }
        PreviewCell {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val noteWeight = if (note == InkColor.DEFAULT) FontWeight.Normal else FontWeight.Medium
                for (line in listOf("1   3", "     6", "7     ")) {
                    Text(line, fontSize = 12.sp, lineHeight = 14.sp, color = noteColor, fontWeight = noteWeight)
                }
            }
        }
    }
}

@Composable
private fun PreviewCell(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: InkColor, onSelect: (InkColor) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        for (c in InkColor.entries) {
            val label = stringResource(c.labelRes)
            val fill = resolveInkColor(c)
            val isSelected = c == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(if (isSelected) 3.dp else 1.dp, scheme.onSurface, CircleShape)
                    .padding(if (isSelected) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(fill ?: scheme.surface)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(c) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                // The theme swatch shows "A" for automatic.
                if (fill == null) Text("A", fontWeight = FontWeight.Bold, color = scheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PenaltyChoices(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        Modifier.selectableGroup().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (p in AppSettings.PENALTY_CHOICES) {
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(if (p == 0) stringResource(R.string.penalty_off) else stringResource(R.string.penalty_choice, p)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipChoices(choices: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.selectableGroup().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (c in choices) {
            FilterChip(selected = c == selected, onClick = { onSelect(c) }, label = { Text(label(c)) })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

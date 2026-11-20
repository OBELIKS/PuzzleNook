package com.puzzlenook.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.puzzlenook.R
import com.puzzlenook.sudoku.Variant

@Composable
fun HelpButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.action_how_to_play))
    }
}

private class Section(@StringRes val title: Int, val points: List<String>)

/** One-line summary for the game's menu. */
fun gameSummary(variant: Variant): UiText = UiText(
    when (variant) {
        Variant.CLASSIC -> R.string.summary_classic
        Variant.KILLER -> R.string.summary_killer
        Variant.JIGSAW -> R.string.summary_jigsaw
        Variant.SUGURU -> R.string.summary_suguru
        Variant.KAKURO -> R.string.summary_kakuro
        Variant.NONOGRAM -> R.string.summary_nonogram
        Variant.FUTOSHIKI -> R.string.summary_futoshiki
        Variant.CALCUDOKU -> R.string.summary_calcudoku
        Variant.SKYSCRAPERS -> R.string.summary_skyscrapers
        Variant.STAR_BATTLE -> R.string.summary_star_battle
        Variant.HITORI -> R.string.summary_hitori
        Variant.NURIKABE -> R.string.summary_nurikabe
        Variant.SLITHERLINK -> R.string.summary_slitherlink
        Variant.HIDATO -> R.string.summary_hidato
        Variant.TAKUZU -> R.string.summary_takuzu
        Variant.MOSAIC -> R.string.summary_mosaic
        Variant.LIGHT_UP -> R.string.summary_light_up
        Variant.KROPKI -> R.string.summary_kropki
        Variant.TENTS -> R.string.summary_tents
        Variant.KURODOKO -> R.string.summary_kurodoko
        Variant.MASYU -> R.string.summary_masyu
        Variant.SHIKAKU -> R.string.summary_shikaku
        Variant.NORINORI -> R.string.summary_norinori
        Variant.BATTLESHIPS -> R.string.summary_battleships
        Variant.AQUARIUM -> R.string.summary_aquarium
        Variant.HEYAWAKE -> R.string.summary_heyawake
        Variant.LITS -> R.string.summary_lits
        Variant.FILLOMINO -> R.string.summary_fillomino
        Variant.GALAXIES -> R.string.summary_galaxies
        Variant.YAJILIN -> R.string.summary_yajilin
        Variant.BRIDGES -> R.string.summary_bridges
    },
)

/** A game's rules in one line, shown at the top of the game screen (n = grid size; Star Battle uses [stars]). */
fun ruleText(variant: Variant, size: Int, stars: Int = 1): UiText = when (variant) {
    Variant.CLASSIC -> UiText(R.string.rule_classic)
    Variant.KILLER -> UiText(R.string.rule_killer)
    Variant.JIGSAW -> UiText(R.string.rule_jigsaw)
    Variant.SUGURU -> UiText(R.string.rule_suguru)
    Variant.KAKURO -> UiText(R.string.rule_kakuro)
    Variant.FUTOSHIKI -> UiText(R.string.rule_futoshiki, listOf(size))
    Variant.CALCUDOKU -> UiText(R.string.rule_calcudoku, listOf(size))
    Variant.SKYSCRAPERS -> UiText(R.string.rule_skyscrapers, listOf(size))
    Variant.NONOGRAM -> UiText(R.string.rule_nonogram)
    Variant.STAR_BATTLE -> UiText.plural(R.plurals.rule_star_battle, stars, stars)
    Variant.TAKUZU -> UiText(R.string.rule_takuzu)
    Variant.MOSAIC -> UiText(R.string.rule_mosaic)
    Variant.LIGHT_UP -> UiText(R.string.rule_light_up)
    Variant.KROPKI -> UiText(R.string.rule_kropki, listOf(size))
    Variant.TENTS -> UiText(R.string.rule_tents)
    Variant.KURODOKO -> UiText(R.string.rule_kurodoko)
    Variant.MASYU -> UiText(R.string.rule_masyu)
    Variant.SHIKAKU -> UiText(R.string.rule_shikaku)
    Variant.NORINORI -> UiText(R.string.rule_norinori)
    Variant.BATTLESHIPS -> UiText(R.string.rule_battleships)
    Variant.AQUARIUM -> UiText(R.string.rule_aquarium)
    Variant.HEYAWAKE -> UiText(R.string.rule_heyawake)
    Variant.LITS -> UiText(R.string.rule_lits)
    Variant.FILLOMINO -> UiText(R.string.rule_fillomino)
    Variant.GALAXIES -> UiText(R.string.rule_galaxies)
    Variant.YAJILIN -> UiText(R.string.rule_yajilin)
    Variant.BRIDGES -> UiText(R.string.rule_bridges)
    Variant.HITORI -> UiText(R.string.rule_hitori)
    Variant.NURIKABE -> UiText(R.string.rule_nurikabe)
    Variant.SLITHERLINK -> UiText(R.string.rule_slitherlink)
    Variant.HIDATO -> UiText(R.string.rule_hidato)
}

@Composable
private fun rules(variant: Variant): Section = Section(
    R.string.how_section_rules,
    stringArrayResource(
        when (variant) {
            Variant.HIDATO -> R.array.rules_hidato
            Variant.SLITHERLINK -> R.array.rules_slitherlink
            Variant.MOSAIC -> R.array.rules_mosaic
            Variant.LIGHT_UP -> R.array.rules_light_up
            Variant.KROPKI -> R.array.rules_kropki
            Variant.TENTS -> R.array.rules_tents
            Variant.KURODOKO -> R.array.rules_kurodoko
            Variant.MASYU -> R.array.rules_masyu
            Variant.SHIKAKU -> R.array.rules_shikaku
            Variant.NORINORI -> R.array.rules_norinori
            Variant.BATTLESHIPS -> R.array.rules_battleships
            Variant.AQUARIUM -> R.array.rules_aquarium
            Variant.HEYAWAKE -> R.array.rules_heyawake
            Variant.LITS -> R.array.rules_lits
            Variant.FILLOMINO -> R.array.rules_fillomino
            Variant.GALAXIES -> R.array.rules_galaxies
            Variant.YAJILIN -> R.array.rules_yajilin
            Variant.BRIDGES -> R.array.rules_bridges
            Variant.TAKUZU -> R.array.rules_takuzu
            Variant.STAR_BATTLE -> R.array.rules_star_battle
            Variant.HITORI -> R.array.rules_hitori
            Variant.NURIKABE -> R.array.rules_nurikabe
            Variant.CLASSIC -> R.array.rules_classic
            Variant.JIGSAW -> R.array.rules_jigsaw
            Variant.SUGURU -> R.array.rules_suguru
            Variant.KILLER -> R.array.rules_killer
            Variant.KAKURO -> R.array.rules_kakuro
            Variant.NONOGRAM -> R.array.rules_nonogram
            Variant.FUTOSHIKI -> R.array.rules_futoshiki
            Variant.CALCUDOKU -> R.array.rules_calcudoku
            Variant.SKYSCRAPERS -> R.array.rules_skyscrapers
        },
    ).toList(),
)

@Composable
private fun controls(variant: Variant): Section {
    val points: List<String> = when (variant) {
        Variant.HIDATO -> stringArrayResource(R.array.controls_hidato).toList()
        Variant.SLITHERLINK -> stringArrayResource(R.array.controls_slitherlink).toList()
        Variant.MASYU -> stringArrayResource(R.array.controls_masyu).toList()
        Variant.SHIKAKU -> stringArrayResource(R.array.controls_shikaku).toList()
        Variant.GALAXIES -> stringArrayResource(R.array.controls_galaxies).toList()
        Variant.YAJILIN -> stringArrayResource(R.array.controls_yajilin).toList()
        Variant.BRIDGES -> stringArrayResource(R.array.controls_bridges).toList()
        Variant.STAR_BATTLE, Variant.HITORI, Variant.NURIKABE, Variant.TAKUZU, Variant.MOSAIC, Variant.LIGHT_UP, Variant.TENTS, Variant.KURODOKO, Variant.NORINORI, Variant.BATTLESHIPS, Variant.AQUARIUM, Variant.HEYAWAKE, Variant.LITS -> listOf(
            stringResource(
                when (variant) {
                    Variant.STAR_BATTLE -> R.string.controls_shade_tap_star_battle
                    Variant.HITORI -> R.string.controls_shade_tap_hitori
                    Variant.TAKUZU -> R.string.controls_shade_tap_takuzu
                    Variant.MOSAIC -> R.string.controls_shade_tap_mosaic
                    Variant.LIGHT_UP -> R.string.controls_shade_tap_light_up
                    Variant.TENTS -> R.string.controls_shade_tap_tents
                    Variant.KURODOKO -> R.string.controls_shade_tap_kurodoko
                    Variant.NORINORI -> R.string.controls_shade_tap_norinori
                    Variant.BATTLESHIPS -> R.string.controls_shade_tap_battleships
                    Variant.AQUARIUM -> R.string.controls_shade_tap_aquarium
                    Variant.HEYAWAKE -> R.string.controls_shade_tap_heyawake
                    Variant.LITS -> R.string.controls_shade_tap_lits
                    else -> R.string.controls_shade_tap_nurikabe
                },
            ),
            stringResource(
                when (variant) {
                    Variant.STAR_BATTLE -> R.string.controls_shade_mode_star_battle
                    Variant.HITORI -> R.string.controls_shade_mode_hitori
                    Variant.TAKUZU -> R.string.controls_shade_mode_takuzu
                    Variant.MOSAIC -> R.string.controls_shade_mode_mosaic
                    Variant.LIGHT_UP -> R.string.controls_shade_mode_light_up
                    Variant.TENTS -> R.string.controls_shade_mode_tents
                    Variant.KURODOKO -> R.string.controls_shade_mode_kurodoko
                    Variant.NORINORI -> R.string.controls_shade_mode_norinori
                    Variant.BATTLESHIPS -> R.string.controls_shade_mode_battleships
                    Variant.AQUARIUM -> R.string.controls_shade_mode_aquarium
                    Variant.HEYAWAKE -> R.string.controls_shade_mode_heyawake
                    Variant.LITS -> R.string.controls_shade_mode_lits
                    else -> R.string.controls_shade_mode_nurikabe
                },
            ),
            stringResource(R.string.controls_shade_drag),
            stringResource(
                when (variant) {
                    Variant.TAKUZU -> R.string.controls_shade_last_takuzu
                    Variant.MOSAIC -> R.string.controls_shade_last_mosaic
                    Variant.LIGHT_UP -> R.string.controls_shade_last_light_up
                    Variant.TENTS -> R.string.controls_shade_last_tents
                    Variant.KURODOKO -> R.string.controls_shade_last_kurodoko
                    Variant.NORINORI -> R.string.controls_shade_last_norinori
                    Variant.BATTLESHIPS -> R.string.controls_shade_last_battleships
                    Variant.AQUARIUM -> R.string.controls_shade_last_aquarium
                    Variant.HEYAWAKE -> R.string.controls_shade_last_heyawake
                    Variant.LITS -> R.string.controls_shade_last_lits
                    else -> R.string.controls_shade_last_default
                },
            ),
        )
        Variant.NONOGRAM -> stringArrayResource(R.array.controls_nonogram).toList()
        else -> listOf(
            stringResource(if (variant.isLatin) R.string.controls_digits_first_latin else R.string.controls_digits_first),
        ) + stringArrayResource(R.array.controls_digits)
    }
    return Section(R.string.how_section_playing, points)
}

@Composable
private fun scoring(penaltyPercent: Int) = Section(
    R.string.how_section_scoring,
    stringArrayResource(R.array.scoring_points).toList() + if (penaltyPercent == 0) {
        stringResource(R.string.scoring_penalty_off)
    } else {
        stringResource(R.string.scoring_penalty_on, penaltyPercent)
    },
)

@Composable
fun HowToPlayDialog(variant: Variant, penaltyPercent: Int, onDismiss: () -> Unit) {
    val sections = listOf(rules(variant), controls(variant), scoring(penaltyPercent))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.how_title, stringResource(variant.titleRes))) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (section in sections) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(section.title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        for (p in section.points) {
                            Text("•  $p", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) } },
    )
}

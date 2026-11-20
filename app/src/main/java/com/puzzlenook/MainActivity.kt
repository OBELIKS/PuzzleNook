package com.puzzlenook

import android.content.Context
import android.content.res.Configuration
import com.puzzlenook.settings.AppLanguage
import java.util.Locale
import androidx.compose.runtime.remember
import android.graphics.Color
import android.os.Bundle
import com.puzzlenook.ui.theme.UiZoom
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import com.puzzlenook.nonogram.NonogramGenerator
import com.puzzlenook.nonogram.NonogramViewModel
import com.puzzlenook.sudoku.CalcudokuGenerator
import com.puzzlenook.sudoku.CalcudokuViewModel
import com.puzzlenook.sudoku.FutoshikiViewModel
import com.puzzlenook.sudoku.LatinGenerator
import com.puzzlenook.sudoku.SkyscrapersViewModel
import com.puzzlenook.sudoku.KakuroGenerator
import com.puzzlenook.sudoku.KakuroViewModel
import com.puzzlenook.sudoku.KillerGenerator
import com.puzzlenook.sudoku.JigsawViewModel
import com.puzzlenook.sudoku.FillominoGenerator
import com.puzzlenook.sudoku.FillominoViewModel
import com.puzzlenook.sudoku.SuguruGenerator
import com.puzzlenook.sudoku.SuguruViewModel
import com.puzzlenook.sudoku.KillerSudokuViewModel
import androidx.annotation.StringRes
import com.puzzlenook.ui.UiText
import com.puzzlenook.ui.GameMenuScreen
import com.puzzlenook.ui.HomeGame
import com.puzzlenook.ui.SavedGame
import com.puzzlenook.ui.nonogram.NonogramGameScreen
import com.puzzlenook.ui.shade.ShadeGameScreen
import com.puzzlenook.ui.slither.GalaxiesGameScreen
import com.puzzlenook.ui.slither.MasyuGameScreen
import com.puzzlenook.ui.slither.YajilinGameScreen
import com.puzzlenook.ui.region.ShikakuGameScreen
import com.puzzlenook.region.ShikakuGenerator
import com.puzzlenook.region.ShikakuViewModel
import com.puzzlenook.bridges.BridgesGenerator
import com.puzzlenook.bridges.BridgesViewModel
import com.puzzlenook.ui.bridges.BridgesGameScreen
import androidx.compose.material.icons.outlined.CropSquare
import com.puzzlenook.ui.slither.SlitherGameScreen
import com.puzzlenook.ui.hidato.HidatoGameScreen
import com.puzzlenook.hidato.HidatoGenerator
import com.puzzlenook.hidato.HidatoViewModel
import androidx.compose.material.icons.outlined.Route
import com.puzzlenook.slither.GalaxiesGenerator
import com.puzzlenook.slither.GalaxiesViewModel
import com.puzzlenook.slither.MasyuGenerator
import com.puzzlenook.slither.MasyuViewModel
import com.puzzlenook.slither.YajilinGenerator
import com.puzzlenook.slither.YajilinViewModel
import com.puzzlenook.slither.SlitherGenerator
import com.puzzlenook.slither.SlitherViewModel
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import com.puzzlenook.shade.HitoriGenerator
import com.puzzlenook.shade.HitoriViewModel
import com.puzzlenook.shade.NurikabeGenerator
import com.puzzlenook.shade.NurikabeViewModel
import com.puzzlenook.shade.StarBattleGenerator
import com.puzzlenook.shade.StarBattleViewModel
import com.puzzlenook.shade.MosaicGenerator
import com.puzzlenook.shade.MosaicViewModel
import com.puzzlenook.shade.TakuzuGenerator
import com.puzzlenook.shade.TakuzuViewModel
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import com.puzzlenook.sudoku.KropkiViewModel
import com.puzzlenook.shade.LightUpGenerator
import com.puzzlenook.shade.LightUpViewModel
import com.puzzlenook.shade.TentsGenerator
import com.puzzlenook.shade.TentsViewModel
import com.puzzlenook.shade.KurodokoGenerator
import com.puzzlenook.shade.KurodokoViewModel
import com.puzzlenook.shade.AquariumGenerator
import com.puzzlenook.shade.AquariumViewModel
import com.puzzlenook.shade.HeyawakeGenerator
import com.puzzlenook.shade.HeyawakeViewModel
import com.puzzlenook.shade.LitsGenerator
import com.puzzlenook.shade.LitsViewModel
import androidx.compose.material.icons.outlined.MeetingRoom
import com.puzzlenook.shade.BattleshipsGenerator
import com.puzzlenook.shade.BattleshipsViewModel
import com.puzzlenook.shade.NorinoriGenerator
import com.puzzlenook.shade.NorinoriViewModel
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Park
import com.puzzlenook.ui.theme.resolveInkColor
import com.puzzlenook.sudoku.SudokuViewModel
import com.puzzlenook.sudoku.Variant
import com.puzzlenook.sudoku.formatTime
import com.puzzlenook.ui.HomeScreen
import com.puzzlenook.ui.SettingsScreen
import com.puzzlenook.ui.StatsScreen
import com.puzzlenook.ui.sudoku.SudokuGameScreen
import com.puzzlenook.ui.theme.PuzzleNookTheme
import com.puzzlenook.ui.theme.isDark

class MainActivity : ComponentActivity() {
    // Bigger UI on tablets (Boox); phones are unchanged.
    // The chosen language is applied first, the zoom on top of it (the zoom copies the wrapped configuration).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiZoom.wrap(withLanguage(newBase)))
    }

    private fun withLanguage(base: Context): Context {
        val tag = AppLanguage.saved(base).tag ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PuzzleNookApplication
        setContent {
            // A new language needs a fresh activity (new base context); the screen state is saved and restored.
            val language by app.settings.language.collectAsStateWithLifecycle()
            val startLanguage = remember { language }
            LaunchedEffect(language) { if (language != startLanguage) recreate() }
            val theme by app.settings.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark(isSystemInDarkTheme())
            // Status/navigation bar icons must contrast with the app's theme, not the system's.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PuzzleNookTheme(theme) { PuzzleNookApp(app) }
        }
    }
}

private enum class Screen { HOME, SETTINGS, MENU, GAME, STATS }

private class GameEntry(val variant: Variant, val icon: ImageVector, @StringRes val blurb: Int, @StringRes val section: Int)

@Composable
private fun PuzzleNookApp(app: PuzzleNookApplication) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var variant by rememberSaveable { mutableStateOf(Variant.CLASSIC) }
    // Settings can be opened from any screen; remember where to return to.
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.HOME) }
    // Statistics open for one game (from its menu) or all (from home); remember where to go back.
    var statsVariant by rememberSaveable { mutableStateOf<Variant?>(null) }
    var statsReturn by rememberSaveable { mutableStateOf(Screen.HOME) }
    fun openStats(v: Variant?) {
        statsVariant = v
        statsReturn = screen
        screen = Screen.STATS
    }
    val openSettings = {
        settingsReturn = screen
        screen = Screen.SETTINGS
    }
    val classic: SudokuViewModel = viewModel()
    val killer: KillerSudokuViewModel = viewModel()
    val jigsaw: JigsawViewModel = viewModel()
    val suguru: SuguruViewModel = viewModel()
    val kakuro: KakuroViewModel = viewModel()
    val nonogram: NonogramViewModel = viewModel()
    val futoshiki: FutoshikiViewModel = viewModel()
    val kropki: KropkiViewModel = viewModel()
    val calcudoku: CalcudokuViewModel = viewModel()
    val skyscrapers: SkyscrapersViewModel = viewModel()
    val slither: SlitherViewModel = viewModel()
    val masyu: MasyuViewModel = viewModel()
    val galaxies: GalaxiesViewModel = viewModel()
    val yajilin: YajilinViewModel = viewModel()
    val shikaku: ShikakuViewModel = viewModel()
    val bridges: BridgesViewModel = viewModel()
    val hidato: HidatoViewModel = viewModel()
    val starBattle: StarBattleViewModel = viewModel()
    val hitori: HitoriViewModel = viewModel()
    val nurikabe: NurikabeViewModel = viewModel()
    val takuzu: TakuzuViewModel = viewModel()
    val mosaic: MosaicViewModel = viewModel()
    val lightUp: LightUpViewModel = viewModel()
    val tents: TentsViewModel = viewModel()
    val kurodoko: KurodokoViewModel = viewModel()
    val norinori: NorinoriViewModel = viewModel()
    val battleships: BattleshipsViewModel = viewModel()
    val aquarium: AquariumViewModel = viewModel()
    val heyawake: HeyawakeViewModel = viewModel()
    val lits: LitsViewModel = viewModel()
    val fillomino: FillominoViewModel = viewModel()
    val shadeGames = mapOf(
        Variant.STAR_BATTLE to starBattle, Variant.HITORI to hitori, Variant.NURIKABE to nurikabe,
        Variant.TAKUZU to takuzu, Variant.MOSAIC to mosaic, Variant.LIGHT_UP to lightUp,
        Variant.TENTS to tents, Variant.KURODOKO to kurodoko, Variant.NORINORI to norinori,
        Variant.BATTLESHIPS to battleships, Variant.AQUARIUM to aquarium,
        Variant.HEYAWAKE to heyawake, Variant.LITS to lits,
    )
    val digitGames = mapOf(
        Variant.CLASSIC to classic,
        Variant.KILLER to killer,
        Variant.JIGSAW to jigsaw,
        Variant.SUGURU to suguru,
        Variant.FILLOMINO to fillomino,
        Variant.KAKURO to kakuro,
        Variant.FUTOSHIKI to futoshiki,
        Variant.KROPKI to kropki,
        Variant.CALCUDOKU to calcudoku,
        Variant.SKYSCRAPERS to skyscrapers,
    )
    val handwriting by app.settings.handwriting.collectAsStateWithLifecycle()
    val penalty by app.settings.penaltyPercent.collectAsStateWithLifecycle()

    @Composable
    fun saved(v: Variant): SavedGame? = if (v == Variant.NONOGRAM) {
        val s by nonogram.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v.isShading) {
        val s by shadeGames.getValue(v).state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.SLITHERLINK) {
        val s by slither.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.MASYU) {
        val s by masyu.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.GALAXIES) {
        val s by galaxies.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.YAJILIN) {
        val s by yajilin.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.SHIKAKU) {
        val s by shikaku.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.BRIDGES) {
        val s by bridges.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else if (v == Variant.HIDATO) {
        val s by hidato.state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    } else {
        val s by digitGames.getValue(v).state.collectAsStateWithLifecycle()
        s?.takeIf { !it.completed }?.let { SavedGame(it.difficulty, it.elapsedSeconds) }
    }

    // Outside `when (screen)` so Home keeps its scroll position when you come back to it.
    val homeScroll = rememberScrollState()
    when (screen) {
        Screen.HOME -> {
            fun open(v: Variant) {
                variant = v
                screen = Screen.MENU
            }
            val numbers = R.string.section_numbers
            val pictures = R.string.section_shading
            val lines = R.string.section_lines
            val regions = R.string.section_regions
            val games = listOf(
                GameEntry(Variant.CLASSIC, Icons.Outlined.GridOn, R.string.blurb_classic, numbers),
                GameEntry(Variant.KILLER, Icons.Outlined.Calculate, R.string.blurb_killer, numbers),
                GameEntry(Variant.JIGSAW, Icons.Outlined.Extension, R.string.blurb_jigsaw, numbers),
                GameEntry(Variant.SUGURU, Icons.Outlined.Dashboard, R.string.blurb_suguru, numbers),
                GameEntry(Variant.KAKURO, Icons.Outlined.Functions, R.string.blurb_kakuro, numbers),
                GameEntry(Variant.FUTOSHIKI, Icons.Outlined.Code, R.string.blurb_futoshiki, numbers),
                GameEntry(Variant.KROPKI, Icons.Outlined.MoreHoriz, R.string.blurb_kropki, numbers),
                GameEntry(Variant.CALCUDOKU, Icons.Outlined.Exposure, R.string.blurb_calcudoku, numbers),
                GameEntry(Variant.SKYSCRAPERS, Icons.Outlined.Apartment, R.string.blurb_skyscrapers, numbers),
                GameEntry(Variant.NONOGRAM, Icons.Outlined.Image, R.string.blurb_nonogram, pictures),
                GameEntry(Variant.STAR_BATTLE, Icons.Outlined.StarOutline, R.string.blurb_star_battle, pictures),
                GameEntry(Variant.HITORI, Icons.Outlined.Contrast, R.string.blurb_hitori, pictures),
                GameEntry(Variant.NURIKABE, Icons.Outlined.Water, R.string.blurb_nurikabe, pictures),
                GameEntry(Variant.TAKUZU, Icons.Outlined.Circle, R.string.blurb_takuzu, pictures),
                GameEntry(Variant.MOSAIC, Icons.Outlined.GridView, R.string.blurb_mosaic, pictures),
                GameEntry(Variant.LIGHT_UP, Icons.Outlined.Lightbulb, R.string.blurb_light_up, pictures),
                GameEntry(Variant.TENTS, Icons.Outlined.Park, R.string.blurb_tents, pictures),
                GameEntry(Variant.KURODOKO, Icons.Outlined.Visibility, R.string.blurb_kurodoko, pictures),
                GameEntry(Variant.NORINORI, Icons.Outlined.ViewAgenda, R.string.blurb_norinori, pictures),
                GameEntry(Variant.BATTLESHIPS, Icons.Outlined.DirectionsBoat, R.string.blurb_battleships, pictures),
                GameEntry(Variant.AQUARIUM, Icons.Outlined.WaterDrop, R.string.blurb_aquarium, pictures),
                GameEntry(Variant.HEYAWAKE, Icons.Outlined.MeetingRoom, R.string.blurb_heyawake, pictures),
                GameEntry(Variant.LITS, Icons.Outlined.Extension, R.string.blurb_lits, pictures),
                GameEntry(Variant.SLITHERLINK, Icons.Outlined.Polyline, R.string.blurb_slitherlink, lines),
                GameEntry(Variant.MASYU, Icons.Outlined.RadioButtonUnchecked, R.string.blurb_masyu, lines),
                GameEntry(Variant.YAJILIN, Icons.Outlined.Route, R.string.blurb_yajilin, lines),
                GameEntry(Variant.BRIDGES, Icons.Outlined.Hub, R.string.blurb_bridges, lines),
                GameEntry(Variant.HIDATO, Icons.Outlined.Route, R.string.blurb_hidato, lines),
                GameEntry(Variant.SHIKAKU, Icons.Outlined.CropSquare, R.string.blurb_shikaku, regions),
                GameEntry(Variant.FILLOMINO, Icons.Outlined.Category, R.string.blurb_fillomino, regions),
                GameEntry(Variant.GALAXIES, Icons.Outlined.AutoAwesome, R.string.blurb_galaxies, regions),
            ).map { e ->
                val s = saved(e.variant)
                HomeGame(
                    e.icon,
                    e.variant.titleRes,
                    s?.let { UiText(R.string.home_continue, listOf(UiText(it.difficulty.labelRes), formatTime(it.elapsedSeconds))) } ?: UiText(e.blurb),
                    e.section,
                ) { open(e.variant) }
            }
            HomeScreen(games, homeScroll, onOpenSettings = openSettings, onOpenStats = { openStats(null) })
        }
        Screen.SETTINGS -> {
            BackHandler { screen = settingsReturn }
            SettingsScreen(app.settings, app.handwriting, onBack = { screen = settingsReturn })
        }
        Screen.MENU -> {
            BackHandler { screen = Screen.HOME }
            val stats by (
                when {
                    variant == Variant.NONOGRAM -> nonogram.stats
                    variant.isShading -> shadeGames.getValue(variant).stats
                    variant == Variant.SLITHERLINK -> slither.stats
                    variant == Variant.MASYU -> masyu.stats
                    variant == Variant.GALAXIES -> galaxies.stats
                    variant == Variant.YAJILIN -> yajilin.stats
                    variant == Variant.SHIKAKU -> shikaku.stats
                    variant == Variant.BRIDGES -> bridges.stats
                    variant == Variant.HIDATO -> hidato.stats
                    else -> digitGames.getValue(variant).stats
                }
                )
                .collectAsStateWithLifecycle()
            GameMenuScreen(
                variant = variant,
                saved = saved(variant),
                stats = stats,
                penaltyPercent = penalty,
                difficultyDetail = { d ->
                    when (variant) {
                        Variant.KAKURO -> KakuroGenerator.size(d).let { UiText(R.string.detail_kakuro, listOf(it - 1)) }
                        Variant.NONOGRAM -> NonogramGenerator.size(d).let { UiText(R.string.detail_nonogram, listOf(it)) }
                        Variant.KILLER -> KillerGenerator.maxCageSize(d).let { UiText.plural(R.plurals.detail_killer, it, it) }
                        Variant.CLASSIC, Variant.JIGSAW -> null
                        Variant.SUGURU -> SuguruGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.FILLOMINO -> FillominoGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.STAR_BATTLE -> StarBattleGenerator.stars(d).let { UiText.plural(R.plurals.detail_star_battle, it, StarBattleGenerator.size(d), it) }
                        Variant.HITORI -> HitoriGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.NURIKABE -> NurikabeGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.TAKUZU -> TakuzuGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.MOSAIC -> MosaicGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.LIGHT_UP -> LightUpGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.TENTS -> TentsGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.KURODOKO -> KurodokoGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.NORINORI -> NorinoriGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.BATTLESHIPS -> BattleshipsGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.AQUARIUM -> AquariumGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.HEYAWAKE -> HeyawakeGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.LITS -> LitsGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.SLITHERLINK -> SlitherGenerator.size(d).let { UiText(R.string.detail_squares, listOf(it)) }
                        Variant.MASYU -> MasyuGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.GALAXIES -> GalaxiesGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.YAJILIN -> YajilinGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.SHIKAKU -> ShikakuGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.BRIDGES -> BridgesGenerator.size(d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.HIDATO -> HidatoGenerator.size(d).let { UiText(R.string.detail_hidato, listOf(it, it * it)) }
                        Variant.FUTOSHIKI, Variant.SKYSCRAPERS, Variant.KROPKI -> LatinGenerator.size(variant, d).let { UiText(R.string.detail_grid, listOf(it)) }
                        Variant.CALCUDOKU -> CalcudokuGenerator.maxCage(d).let { UiText.plural(R.plurals.detail_calcudoku, it, LatinGenerator.size(variant, d), it) }
                    }
                },
                onNewGame = { d ->
                    when {
                        variant == Variant.NONOGRAM -> nonogram.newGame(d)
                        variant.isShading -> shadeGames.getValue(variant).newGame(d)
                        variant == Variant.SLITHERLINK -> slither.newGame(d)
                        variant == Variant.MASYU -> masyu.newGame(d)
                        variant == Variant.GALAXIES -> galaxies.newGame(d)
                        variant == Variant.YAJILIN -> yajilin.newGame(d)
                        variant == Variant.SHIKAKU -> shikaku.newGame(d)
                        variant == Variant.BRIDGES -> bridges.newGame(d)
                        variant == Variant.HIDATO -> hidato.newGame(d)
                        else -> digitGames.getValue(variant).newGame(d)
                    }
                },
                onBack = { screen = Screen.HOME },
                onPlay = { screen = Screen.GAME },
                onOpenSettings = openSettings,
                onOpenStats = { openStats(variant) },
            )
        }
        Screen.STATS -> {
            BackHandler { screen = statsReturn }
            StatsScreen(
                initialVariant = statsVariant,
                currentPenalty = penalty,
                onBack = { screen = statsReturn },
                onOpenSettings = openSettings,
                onReset = { vs ->
                    vs.forEach { v ->
                        when {
                            v == Variant.NONOGRAM -> nonogram.reloadStats()
                            v.isShading -> shadeGames.getValue(v).reloadStats()
                            v == Variant.SLITHERLINK -> slither.reloadStats()
                            v == Variant.MASYU -> masyu.reloadStats()
                            v == Variant.GALAXIES -> galaxies.reloadStats()
                            v == Variant.YAJILIN -> yajilin.reloadStats()
                            v == Variant.SHIKAKU -> shikaku.reloadStats()
                            v == Variant.BRIDGES -> bridges.reloadStats()
                            v == Variant.HIDATO -> hidato.reloadStats()
                            else -> digitGames.getValue(v).reloadStats()
                        }
                    }
                },
            )
        }
        Screen.GAME -> {
            BackHandler { screen = Screen.MENU }
            val answerColor by app.settings.answerColor.collectAsStateWithLifecycle()
            val noteColor by app.settings.noteColor.collectAsStateWithLifecycle()
            // key: switching games must not reuse the other game's ink and lifecycle hooks.
            key(variant) {
                if (variant == Variant.HIDATO) {
                    HidatoGameScreen(
                        vm = hidato,
                        handwriting = handwriting,
                        answerColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant == Variant.BRIDGES) {
                    BridgesGameScreen(
                        vm = bridges,
                        lineColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant == Variant.SHIKAKU) {
                    ShikakuGameScreen(
                        vm = shikaku,
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant == Variant.GALAXIES) {
                    GalaxiesGameScreen(
                        vm = galaxies,
                        lineColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant == Variant.YAJILIN) {
                    YajilinGameScreen(vm = yajilin, lineColor = resolveInkColor(answerColor), onBack = { screen = Screen.MENU }, onOpenSettings = openSettings)
                } else if (variant == Variant.MASYU) {
                    MasyuGameScreen(
                        vm = masyu,
                        lineColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant == Variant.SLITHERLINK) {
                    SlitherGameScreen(
                        vm = slither,
                        lineColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant.isShading) {
                    ShadeGameScreen(
                        vm = shadeGames.getValue(variant),
                        fillColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else if (variant == Variant.NONOGRAM) {
                    NonogramGameScreen(
                        vm = nonogram,
                        fillColor = resolveInkColor(answerColor),
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                } else {
                    SudokuGameScreen(
                        vm = digitGames.getValue(variant),
                        handwriting = handwriting,
                        answerColor = answerColor,
                        noteColor = noteColor,
                        onBack = { screen = Screen.MENU },
                        onOpenSettings = openSettings,
                    )
                }
            }
        }
    }
}
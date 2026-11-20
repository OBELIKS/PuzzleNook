package com.puzzlenook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.puzzlenook.settings.InkColor
import com.puzzlenook.settings.ThemeMode

// Brand palette (shared with the launcher icon): indigo, amber, coral on warm cream.
val NookIndigo = Color(0xFF34407A)
val NookAmber = Color(0xFFFFC857)
val NookCoral = Color(0xFFFF7F50)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3A4691),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0F1A4D),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDF9E),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF9C4323),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF390C00),
    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E2D9),
    onSurfaceVariant = Color(0xFF4A4740),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color(0xFFF0ECE3),
    surfaceContainerHigh = Color(0xFFEAE6DD),
    surfaceContainerHighest = Color(0xFFE4E0D7),
    outline = Color(0xFF7B776E),
    outlineVariant = Color(0xFFCBC6BC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF1F2A61),
    primaryContainer = NookIndigo,
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = NookAmber,
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFFFFB59A),
    onTertiary = Color(0xFF5B1B02),
    tertiaryContainer = Color(0xFF7D2D0F),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF14151B),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF14151B),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0F1015),
    surfaceContainerLow = Color(0xFF1C1D24),
    surfaceContainer = Color(0xFF202128),
    surfaceContainerHigh = Color(0xFF2A2B33),
    surfaceContainerHighest = Color(0xFF35363E),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
)

/**
 * E-ink: pure black on white, a few flat greys (e-ink panels show ~16 grey levels),
 * no colour and no translucency, so everything stays crisp without ghosting.
 */
private val EinkColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E6E6),
    onTertiaryContainer = Color.Black,
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color(0xFFE6E6E6),
    onErrorContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color.Black,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    surfaceContainerHighest = Color(0xFFDDDDDD),
    outline = Color.Black,
    outlineVariant = Color(0xFF888888),
    scrim = Color.Black,
)

/** User-chosen digit colours; null means "use the theme's colour". */
data class DigitColors(val answer: Color?, val note: Color?)

/** Picks the light or dark variant to match the active theme (e-ink counts as light). */
@Composable
fun resolveInkColor(color: InkColor): Color? {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return (if (dark) color.dark else color.light)?.let { Color(it) }
}

/** True when the e-ink theme is active; screens use it to drop animation and colour cues. */
val LocalEink = staticCompositionLocalOf { false }

fun ThemeMode.isDark(systemDark: Boolean) = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.DARK -> true
    ThemeMode.LIGHT, ThemeMode.EINK -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleNookTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val eink = mode == ThemeMode.EINK
    val scheme = when {
        eink -> EinkColors
        mode.isDark(isSystemInDarkTheme()) -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalEink provides eink,
            // Ripples animate for ~300 ms, which smears on e-ink.
            LocalRippleConfiguration provides if (eink) null else LocalRippleConfiguration.current,
        ) {
            // A base surface for every screen: without one, text and icons outside a Scaffold
            // (the home screen's title and top icons) default to black, invisible in dark mode.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}

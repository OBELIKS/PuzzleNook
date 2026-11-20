package com.puzzlenook.settings

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.puzzlenook.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    EINK(R.string.theme_eink),
}

/** The app's language: [SYSTEM] follows the phone, the others force one. [tag] is a BCP 47 language tag. */
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    SLOVENE("sl", R.string.language_slovene);

    companion object {
        /** The saved choice, read straight from the settings file (needed before [AppSettings] exists). */
        fun saved(context: Context): AppLanguage =
            context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE).getString(AppSettings.KEY_LANGUAGE, null)
                ?.let { runCatching { valueOf(it) }.getOrNull() } ?: SYSTEM
    }
}

/**
 * Colours for player-entered digits. Tuned for colour e-ink (Kaleido 3, e.g. Boox Note Air 5C):
 * its colour filter washes out light and pastel tones, so the [light] values are deep,
 * saturated and spread far apart in hue. [dark] values are lighter variants for the dark theme.
 * [DEFAULT] uses the theme's own colours.
 */
enum class InkColor(@StringRes val labelRes: Int, val light: Long?, val dark: Long?) {
    DEFAULT(R.string.ink_theme, null, null),
    BLUE(R.string.ink_blue, 0xFF0047CC, 0xFF8AB4FF),
    RED(R.string.ink_red, 0xFFC8102E, 0xFFFF8A8A),
    GREEN(R.string.ink_green, 0xFF00873C, 0xFF7BE0A0),
    ORANGE(R.string.ink_orange, 0xFFD95A00, 0xFFFFB074),
    PURPLE(R.string.ink_purple, 0xFF6A1B9A, 0xFFD1A3FF),
    TEAL(R.string.ink_teal, 0xFF007C80, 0xFF6FE3E6),
    MAGENTA(R.string.ink_magenta, 0xFFB0006D, 0xFFFF8CCB),
    BROWN(R.string.ink_brown, 0xFF7A4A1E, 0xFFE0B48A),
    GREY(R.string.ink_grey, 0xFF5F6368, 0xFFB0B4BA),
}

object Devices {
    /** Onyx Boox e-readers. */
    val isOnyx: Boolean =
        Build.MANUFACTURER.contains("onyx", ignoreCase = true) || Build.BRAND.contains("onyx", ignoreCase = true)
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(AppLanguage.saved(context))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: if (Devices.isOnyx) ThemeMode.EINK else ThemeMode.SYSTEM
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _handwriting = MutableStateFlow(prefs.getBoolean(KEY_HANDWRITING, true))
    val handwriting: StateFlow<Boolean> = _handwriting.asStateFlow()

    private val _smallNotes = MutableStateFlow(prefs.getBoolean(KEY_SMALL_NOTES, true))
    /** Small handwriting becomes a note instead of an answer. */
    val smallWritingNotes: StateFlow<Boolean> = _smallNotes.asStateFlow()

    private val _answerColor = MutableStateFlow(readColor(KEY_ANSWER_COLOR))
    val answerColor: StateFlow<InkColor> = _answerColor.asStateFlow()

    private val _noteColor = MutableStateFlow(readColor(KEY_NOTE_COLOR))
    val noteColor: StateFlow<InkColor> = _noteColor.asStateFlow()

    private val _penaltyPercent = MutableStateFlow(prefs.getInt(KEY_PENALTY, DEFAULT_PENALTY_PERCENT))
    /** How much playing time each penalty point adds at the end, in percent (0 = no penalty). */
    val penaltyPercent: StateFlow<Int> = _penaltyPercent.asStateFlow()

    fun setPenaltyPercent(percent: Int) {
        _penaltyPercent.value = percent
        prefs.edit().putInt(KEY_PENALTY, percent).apply()
    }

    private val _hintLimit = MutableStateFlow(prefs.getInt(KEY_HINT_LIMIT, -1))
    /** Hints allowed per game (-1 = unlimited). */
    val hintLimit: StateFlow<Int> = _hintLimit.asStateFlow()

    private val _risingHintCost = MutableStateFlow(prefs.getBoolean(KEY_RISING_COST, false))
    val risingHintCost: StateFlow<Boolean> = _risingHintCost.asStateFlow()

    private val _hintCooldown = MutableStateFlow(prefs.getInt(KEY_COOLDOWN, 0))
    /** Playing seconds to wait between hints (0 = off). */
    val hintCooldown: StateFlow<Int> = _hintCooldown.asStateFlow()

    val help: StateFlow<HelpSettings> = kotlinx.coroutines.flow.combine(_hintLimit, _risingHintCost, _hintCooldown) { l, r, c ->
        HelpSettings(l, r, c)
    }.stateIn(
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        HelpSettings(_hintLimit.value, _risingHintCost.value, _hintCooldown.value),
    )

    fun setHintLimit(limit: Int) {
        _hintLimit.value = limit
        prefs.edit().putInt(KEY_HINT_LIMIT, limit).apply()
    }

    fun setRisingHintCost(enabled: Boolean) {
        _risingHintCost.value = enabled
        prefs.edit().putBoolean(KEY_RISING_COST, enabled).apply()
    }

    fun setHintCooldown(seconds: Int) {
        _hintCooldown.value = seconds
        prefs.edit().putInt(KEY_COOLDOWN, seconds).apply()
    }

    fun setSmallWritingNotes(enabled: Boolean) {
        _smallNotes.value = enabled
        prefs.edit().putBoolean(KEY_SMALL_NOTES, enabled).apply()
    }

    fun setAnswerColor(color: InkColor) {
        _answerColor.value = color
        prefs.edit().putString(KEY_ANSWER_COLOR, color.name).apply()
    }

    fun setNoteColor(color: InkColor) {
        _noteColor.value = color
        prefs.edit().putString(KEY_NOTE_COLOR, color.name).apply()
    }

    private fun readColor(key: String) =
        prefs.getString(key, null)?.let { runCatching { InkColor.valueOf(it) }.getOrNull() } ?: InkColor.DEFAULT

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setHandwriting(enabled: Boolean) {
        _handwriting.value = enabled
        prefs.edit().putBoolean(KEY_HANDWRITING, enabled).apply()
    }

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_LANGUAGE = "language"
        const val DEFAULT_PENALTY_PERCENT = 10
        val PENALTY_CHOICES = listOf(0, 5, 10, 15, 20, 25)
        val HINT_LIMIT_CHOICES = listOf(-1, 5, 3, 1, 0)
        val COOLDOWN_CHOICES = listOf(0, 30, 60, 120)
        private const val KEY_HINT_LIMIT = "hint_limit"
        private const val KEY_RISING_COST = "rising_hint_cost"
        private const val KEY_COOLDOWN = "hint_cooldown"
        private const val KEY_PENALTY = "penalty_percent"
        private const val KEY_THEME = "theme"
        private const val KEY_HANDWRITING = "handwriting"
        private const val KEY_SMALL_NOTES = "small_writing_notes"
        private const val KEY_ANSWER_COLOR = "answer_color"
        private const val KEY_NOTE_COLOR = "note_color"
    }
}

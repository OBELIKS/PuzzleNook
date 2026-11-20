package com.puzzlenook.ui.theme

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

/**
 * Tablets get everything, dialogs and menus included, drawn bigger, as if the screen were
 * about 640 dp wide. At normal size a 10" tablet like the Boox Note Air shows text and
 * buttons no bigger than a phone does, so they look small on the large screen. Phones
 * (narrower than 640 dp) stay as they are. The activity's context carries the zoom, so it
 * reaches every window it opens.
 */
object UiZoom {
    const val TARGET_WIDTH_DP = 640f
    const val MAX = 1.4f

    fun factor(smallestWidthDp: Int): Float = (smallestWidthDp / TARGET_WIDTH_DP).coerceIn(1f, MAX)

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        val zoom = factor(config.smallestScreenWidthDp)
        if (zoom <= 1f) return base
        config.densityDpi = (config.densityDpi * zoom).roundToInt()
        config.screenWidthDp = (config.screenWidthDp / zoom).toInt()
        config.screenHeightDp = (config.screenHeightDp / zoom).toInt()
        config.smallestScreenWidthDp = (config.smallestScreenWidthDp / zoom).toInt()
        return base.createConfigurationContext(config)
    }
}

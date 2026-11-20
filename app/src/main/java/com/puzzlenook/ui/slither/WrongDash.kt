package com.puzzlenook.ui.slither

import androidx.compose.ui.graphics.PathEffect

/**
 * E-ink has no red: a wrong line or wall (shown only while errors are on) is drawn dashed so it
 * still stands out from the right ones. Null elsewhere: colour does the job. [cs] is the cell size.
 */
fun wrongDash(eink: Boolean, wrong: Boolean, cs: Float): PathEffect? =
    if (eink && wrong) PathEffect.dashPathEffect(floatArrayOf(cs * 0.12f, cs * 0.2f)) else null

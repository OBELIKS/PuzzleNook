package com.puzzlenook.ui.ink

import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.LocalView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.puzzlenook.ink.InkPoint
import com.puzzlenook.ink.InkStroke

/**
 * Low-latency pen input on Onyx Boox devices. The Onyx SDK draws the ink straight to
 * the e-ink panel ("raw drawing"), bypassing the slow Android render path, and reports
 * each finished stroke, which we feed to the same [InkController] as other devices.
 *
 * [boardBounds] is the board's rectangle in the root view's coordinates.
 * Returns true while raw drawing is active (the board then ignores stylus events itself).
 */
@Composable
fun OnyxPenInput(enabled: Boolean, boardBounds: ComposeRect?, controller: InkController, strokeColor: Int): Boolean {
    val view = LocalView.current
    var helper by remember { mutableStateOf<TouchHelper?>(null) }
    val bounds = boardBounds

    DisposableEffect(enabled, bounds != null) {
        if (!enabled || bounds == null) return@DisposableEffect onDispose { }
        val callback = object : RawInputCallback() {
            override fun onBeginRawDrawing(b: Boolean, p: TouchPoint) {
                view.post { controller.holdPending() }
            }

            override fun onEndRawDrawing(b: Boolean, p: TouchPoint) {}
            override fun onRawDrawingTouchPointMoveReceived(p: TouchPoint) {}

            override fun onRawDrawingTouchPointListReceived(list: TouchPointList) {
                val origin = currentOrigin
                val stroke = InkStroke(list.points.map { InkPoint(it.x - origin.first, it.y - origin.second, it.timestamp) })
                view.post { controller.addStroke(stroke) }
            }

            override fun onBeginRawErasing(b: Boolean, p: TouchPoint) {}
            override fun onEndRawErasing(b: Boolean, p: TouchPoint) {}
            override fun onRawErasingTouchPointMoveReceived(p: TouchPoint) {}
            override fun onRawErasingTouchPointListReceived(list: TouchPointList) {
                // Flipping the pen to the eraser end clears the character being written.
                view.post { controller.clear() }
            }
        }
        val created = try {
            TouchHelper.create(view, callback).apply {
                setStrokeWidth(4f)
                setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                setStrokeColor(strokeColor)
                setLimitRect(bounds.toRect(), emptyList())
                openRawDrawing()
                setRawDrawingEnabled(true)
            }
        } catch (t: Throwable) {
            Log.w("OnyxPen", "Onyx raw drawing unavailable", t)
            null
        }
        helper = created
        controller.onCleared = {
            // Toggling raw drawing wipes the SDK's ink layer so the typed digit shows through.
            created?.let {
                it.setRawDrawingEnabled(false)
                it.setRawDrawingEnabled(true)
            }
        }
        onDispose {
            controller.onCleared = null
            runCatching { created?.closeRawDrawing() }
            helper = null
        }
    }

    // Keep the raw-drawing area glued to the board as layout changes.
    LaunchedEffect(helper, bounds) {
        val h = helper ?: return@LaunchedEffect
        if (bounds != null) {
            currentOrigin = bounds.left to bounds.top
            runCatching { h.setLimitRect(bounds.toRect(), emptyList()) }
        }
    }
    if (bounds != null) currentOrigin = bounds.left to bounds.top

    LaunchedEffect(helper, strokeColor) {
        helper?.let { h -> runCatching { h.setStrokeColor(strokeColor) } }
    }

    return helper != null
}

/** Latest board origin in view coordinates (read from the SDK's callback thread). */
@Volatile
private var currentOrigin: Pair<Float, Float> = 0f to 0f

private fun ComposeRect.toRect() = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

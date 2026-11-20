package com.puzzlenook

import android.app.Application
import android.os.Build
import android.util.Log
import com.puzzlenook.ink.HandwritingRecognizer
import com.puzzlenook.settings.AppSettings
import com.puzzlenook.settings.Devices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.lsposed.hiddenapibypass.HiddenApiBypass

class PuzzleNookApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var settings: AppSettings
        private set

    val handwriting: HandwritingRecognizer by lazy { HandwritingRecognizer(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        // The Onyx pen SDK reflects into hidden framework APIs on Android 9+.
        if (Devices.isOnyx && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
                .onFailure { Log.w("PuzzleNook", "Hidden API exemption failed", it) }
        }
        if (settings.handwriting.value) handwriting.prepare()
    }
}

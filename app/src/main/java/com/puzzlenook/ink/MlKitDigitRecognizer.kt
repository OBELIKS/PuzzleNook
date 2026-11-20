package com.puzzlenook.ink

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google ML Kit digital ink recognition (on-device, model downloaded once). */
class MlKitDigitRecognizer {
    private val model: DigitalInkRecognitionModel = DigitalInkRecognitionModel.builder(
        requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US"))
    ).build()

    private var client: DigitalInkRecognizer? = null

    suspend fun isModelDownloaded(): Boolean =
        RemoteModelManager.getInstance().isModelDownloaded(model).await()

    /** Downloads the model if needed and opens the recogniser. Throws on failure. */
    suspend fun prepare() {
        val manager = RemoteModelManager.getInstance()
        if (!manager.isModelDownloaded(model).await()) {
            manager.download(model, DownloadConditions.Builder().build()).await()
        }
        client = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
    }

    val isReady get() = client != null

    /** Returns the first candidate that reads as a digit 0–9 (0 = delete), or null. */
    suspend fun recognize(strokes: List<InkStroke>, areaWidth: Float, areaHeight: Float): Int? =
        candidates(strokes, areaWidth, areaHeight)?.firstNotNullOfOrNull { toDigit(it) }

    /** The first candidate that reads as a number from 0 to [max] (one or two digits), or null. */
    suspend fun recognizeNumber(strokes: List<InkStroke>, areaWidth: Float, areaHeight: Float, max: Int): Int? =
        candidates(strokes, areaWidth, areaHeight)?.firstNotNullOfOrNull { text -> toNumber(text)?.takeIf { it <= max } }

    private suspend fun candidates(strokes: List<InkStroke>, areaWidth: Float, areaHeight: Float): List<String>? {
        val c = client ?: return null
        val ink = Ink.builder().apply {
            for (s in strokes) {
                val b = Ink.Stroke.builder()
                s.points.forEach { b.addPoint(Ink.Point.create(it.x, it.y, it.t)) }
                addStroke(b.build())
            }
        }.build()
        val context = RecognitionContext.builder()
            .setPreContext("")
            .setWritingArea(WritingArea(areaWidth, areaHeight))
            .build()
        val result = c.recognize(ink, context).await()
        return result.candidates.map { it.text }
    }

    fun close() {
        client?.close()
        client = null
    }

    companion object {
        /**
         * Maps a recognised string to a digit, tolerating common letter look-alikes.
         * 0 is the "delete" gesture, so O-like shapes map to it too.
         */
        fun toDigit(text: String): Int? {
            val t = text.trim()
            if (t.length != 1) return null
            return when (val ch = t[0]) {
                in '0'..'9' -> ch - '0'
                'o', 'O', 'D', '°' -> 0
                'l', 'I', '|', 'i', '/', '!' -> 1
                'z', 'Z' -> 2
                's', 'S', '$' -> 5
                'b', 'G' -> 6
                'B', '&' -> 8
                'g', 'q' -> 9
                else -> null
            }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

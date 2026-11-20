package com.puzzlenook.ink

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Recognises a handwritten digit. Order of preference:
 * 1. a very close match to one of the user's own learned samples,
 * 2. Google ML Kit (when its model is downloaded),
 * 3. the built-in $P template recogniser.
 */
class HandwritingRecognizer(context: Context, private val scope: CoroutineScope) {

    enum class MlKitState { NOT_STARTED, DOWNLOADING, READY, UNAVAILABLE }

    private val mlKit = MlKitDigitRecognizer()
    private val builtIn = TemplateDigitRecognizer()
    private val samplesFile = File(context.filesDir, "handwriting_samples.json")
    private val samples = mutableListOf<Pair<Int, List<InkStroke>>>()

    private val _mlKitState = MutableStateFlow(MlKitState.NOT_STARTED)
    val mlKitState: StateFlow<MlKitState> = _mlKitState.asStateFlow()

    private val _learnedCount = MutableStateFlow(0)
    val learnedCount: StateFlow<Int> = _learnedCount.asStateFlow()

    private var prepareJob: Job? = null
    private var loaded = false

    /** Loads learned samples and starts the ML Kit model download. Safe to call repeatedly. */
    fun prepare() {
        if (prepareJob?.isActive == true || _mlKitState.value == MlKitState.READY) return
        prepareJob = scope.launch {
            ensureSamplesLoaded()
            _mlKitState.value = MlKitState.DOWNLOADING
            _mlKitState.value = try {
                mlKit.prepare()
                MlKitState.READY
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit unavailable, using built-in recogniser", e)
                MlKitState.UNAVAILABLE
            }
        }
    }

    suspend fun recognize(strokes: List<InkStroke>, areaWidth: Float, areaHeight: Float): Recognition? =
        recognizeInternal(strokes, areaWidth, areaHeight).also {
            Log.d(TAG, "recognised ${it?.digit} via ${it?.engine} (mlKit=${_mlKitState.value}, strokes=${strokes.size})")
        }

    private suspend fun recognizeInternal(strokes: List<InkStroke>, areaWidth: Float, areaHeight: Float): Recognition? {
        ensureSamplesLoaded()
        // A few ms; kept on the caller's (main) thread so it never races with learn().
        val local = builtIn.recognize(strokes)
        if (local != null && local.learned && local.distance <= TemplateDigitRecognizer.STRONG_LEARNED_DISTANCE) {
            return Recognition(local.digit, Recognition.Engine.LEARNED)
        }
        if (mlKit.isReady) {
            val digit = try {
                withTimeoutOrNull(2_000) { mlKit.recognize(strokes, areaWidth, areaHeight) }
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit recognition failed", e)
                null
            }
            if (digit != null) return Recognition(digit, Recognition.Engine.ML_KIT)
        }
        return local?.let {
            Recognition(it.digit, if (it.learned) Recognition.Engine.LEARNED else Recognition.Engine.BUILT_IN)
        }
    }

    /**
     * Recognises a number from 0 to [max] written as one or two digits (Hidato). ML Kit reads
     * the whole number; without it, the strokes are split into digits left to right and each is
     * read on its own.
     */
    suspend fun recognizeNumber(strokes: List<InkStroke>, areaWidth: Float, areaHeight: Float, max: Int): Int? {
        ensureSamplesLoaded()
        val groups = splitDigits(strokes)
        // A single learned digit the user taught us wins, as for one-digit games.
        if (groups.size == 1) {
            val local = builtIn.recognize(strokes)
            if (local != null && local.learned && local.distance <= TemplateDigitRecognizer.STRONG_LEARNED_DISTANCE) return local.digit
        }
        if (mlKit.isReady) {
            val n = try {
                withTimeoutOrNull(2_000) { mlKit.recognizeNumber(strokes, areaWidth, areaHeight, max) }
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit recognition failed", e)
                null
            }
            if (n != null) return n
        }
        // More than two separate parts isn't a one- or two-digit number we can trust:
        // say so rather than enter a guess that would count as a mistake.
        if (groups.size > 2) return null
        var value = 0
        for (g in groups) value = value * 10 + (builtIn.recognize(g)?.digit ?: return null)
        return value.takeIf { it <= max }
    }

    /** Remembers [strokes] as the user's way of writing [digit] (from a correction). */
    fun learn(strokes: List<InkStroke>, digit: Int) {
        scope.launch {
            ensureSamplesLoaded()
            samples += digit to strokes
            // Keep the 12 most recent samples per digit.
            val perDigit = samples.groupBy { it.first }
            val trimmed = perDigit.values.flatMap { it.takeLast(MAX_PER_DIGIT) }
            samples.retainAll(trimmed.toSet())
            rebuildLearned()
            withContext(Dispatchers.IO) { save() }
        }
    }

    fun resetLearning() {
        scope.launch {
            samples.clear()
            rebuildLearned()
            withContext(Dispatchers.IO) { samplesFile.delete() }
        }
    }

    private suspend fun ensureSamplesLoaded() {
        if (loaded) return
        loaded = true
        val read = withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) }
        samples += read
        rebuildLearned()
    }

    private fun rebuildLearned() {
        builtIn.clearLearned()
        samples.forEach { (d, s) -> builtIn.addLearned(d, s) }
        _learnedCount.value = samples.size
    }

    private fun save() {
        val arr = JSONArray()
        for ((digit, strokes) in samples) {
            val js = JSONArray()
            strokes.forEach { s ->
                val flat = JSONArray()
                s.points.forEach { flat.put(it.x.toDouble()).put(it.y.toDouble()) }
                js.put(flat)
            }
            arr.put(JSONObject().put("digit", digit).put("strokes", js))
        }
        samplesFile.writeText(arr.toString())
    }

    private fun load(): List<Pair<Int, List<InkStroke>>> {
        if (!samplesFile.exists()) return emptyList()
        val arr = JSONArray(samplesFile.readText())
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val js = o.getJSONArray("strokes")
            o.getInt("digit") to List(js.length()) { k ->
                val flat = js.getJSONArray(k)
                InkStroke(List(flat.length() / 2) { p ->
                    InkPoint(flat.getDouble(2 * p).toFloat(), flat.getDouble(2 * p + 1).toFloat())
                })
            }
        }
    }

    private companion object {
        const val TAG = "Handwriting"
        const val MAX_PER_DIGIT = 12
    }
}

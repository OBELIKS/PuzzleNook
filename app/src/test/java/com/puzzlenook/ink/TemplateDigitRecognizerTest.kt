package com.puzzlenook.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TemplateDigitRecognizerTest {

    private val recognizer = TemplateDigitRecognizer()

    /** Simulates a person writing a template: random size, aspect, rotation, slant, jitter. */
    private fun scribble(strokes: List<InkStroke>, r: Random): List<InkStroke> {
        val scale = 40f + r.nextFloat() * 80f
        val sx = scale * (0.8f + r.nextFloat() * 0.4f)
        val angle = Math.toRadians((r.nextFloat() * 16f - 8f).toDouble())
        val slant = r.nextFloat() * 0.2f - 0.1f
        return strokes.map { s ->
            InkStroke(s.points.filterIndexed { i, _ -> i % 2 == 0 || r.nextBoolean() }.map { p ->
                val x = (p.x + slant * (1 - p.y)) * sx
                val y = p.y * scale
                val rx = (x * cos(angle) - y * sin(angle)).toFloat()
                val ry = (x * sin(angle) + y * cos(angle)).toFloat()
                InkPoint(rx + 300 + r.nextFloat() * scale * 0.03f, ry + 200 + r.nextFloat() * scale * 0.03f)
            })
        }
    }

    @Test
    fun recognisesDistortedBuiltInShapes() {
        val r = Random(42)
        var correct = 0
        var total = 0
        val misses = mutableListOf<String>()
        // Only the upright (unslanted) variants: every third template.
        val samples = BuiltInDigits.templates().filterIndexed { i, _ -> i % 3 == 0 }
        for ((digit, strokes) in samples) repeat(20) {
            val m = recognizer.recognize(scribble(strokes, r))
            total++
            if (m?.digit == digit) correct++ else misses += "$digit->${m?.digit}(${m?.distance})"
        }
        val rate = correct.toFloat() / total
        println("accuracy $correct/$total; misses: ${misses.groupingBy { it.substringBefore('(') }.eachCount()}")
        assertTrue("accuracy $rate", rate >= 0.95f)
    }

    @Test
    fun learnedSampleWinsForUnusualShape() {
        // A user's "4" written as a closed triangle with a stem, which looks like a 9.
        val weird4 = listOf(
            InkStroke(listOf(InkPoint(40f, 0f), InkPoint(0f, 60f), InkPoint(60f, 60f), InkPoint(40f, 0f))),
            InkStroke(listOf(InkPoint(40f, 0f), InkPoint(40f, 100f))),
        )
        val r = TemplateDigitRecognizer()
        r.addLearned(4, weird4)
        val m = r.recognize(scribble(weird4.map { s -> InkStroke(s.points.map { InkPoint(it.x / 100f, it.y / 100f) }) }, Random(1)))
        assertEquals(4, m?.digit)
        assertTrue(m!!.learned)
        assertTrue("distance ${m.distance}", m.distance < TemplateDigitRecognizer.STRONG_LEARNED_DISTANCE)
    }

    @Test
    fun digitsMatchCloserThanScribbles() {
        val r = Random(7)
        val samples = BuiltInDigits.templates().filterIndexed { i, _ -> i % 3 == 0 }
        val good = samples.flatMap { (_, s) -> List(10) { recognizer.rank(scribble(s, r)).first().distance } }.sorted()
        val scribbles = List(50) {
            listOf(InkStroke(List(12) { InkPoint(r.nextFloat() * 100, r.nextFloat() * 100) }))
        }.map { recognizer.rank(it).first().distance }.sorted()
        val dot = recognizer.rank(listOf(InkStroke(listOf(InkPoint(5f, 5f), InkPoint(5.5f, 5.2f))))).first().distance
        println("good p50=${good[good.size / 2]} p95=${good[good.size * 95 / 100]} max=${good.last()}")
        println("scribble min=${scribbles.first()} p50=${scribbles[25]}; dot=$dot")
        assertTrue(good.last() < TemplateDigitRecognizer.MAX_DISTANCE)
        assertTrue(scribbles[25] > good[good.size * 95 / 100])
    }

    @Test
    fun smallWritingIsDetectedRelativeToCellSize() {
        fun digit(h: Float) = listOf(InkStroke(listOf(InkPoint(10f, 10f), InkPoint(10f + h * 0.6f, 10f), InkPoint(12f, 10f + h))))
        assertTrue(isSmallWriting(digit(30f), cellSize = 100f))   // a note in a corner
        assertTrue(!isSmallWriting(digit(70f), cellSize = 100f))  // a normal answer
        assertTrue(!isSmallWriting(emptyList(), cellSize = 100f))
    }

    @Test
    fun mlKitTextMapsZeroAndLookAlikes() {
        assertEquals(0, MlKitDigitRecognizer.toDigit("0"))
        assertEquals(0, MlKitDigitRecognizer.toDigit("o"))
        assertEquals(0, MlKitDigitRecognizer.toDigit("O"))
        assertEquals(7, MlKitDigitRecognizer.toDigit("7"))
        assertNull(MlKitDigitRecognizer.toDigit("12"))
    }

    @Test
    fun emptyInkIsRejected() {
        assertNull(recognizer.recognize(emptyList()))
    }
}

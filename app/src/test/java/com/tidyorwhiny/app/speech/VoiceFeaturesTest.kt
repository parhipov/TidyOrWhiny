package com.tidyorwhiny.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** The synthetic voices of tools/whine/synth.py, with the numbers features.py gives for them. */
class VoiceFeaturesTest {
    private val rate = VoiceFeatures.RATE

    private fun voice(f0Start: Double, f0End: Double, seconds: Double, loud: Double = 0.3): DoubleArray {
        val n = (seconds * rate).toInt()
        var phase = 0.0
        return DoubleArray(n) { i ->
            val t = i.toDouble() / rate
            phase += 2 * PI * (f0Start + (f0End - f0Start) * i / (n - 1)) / rate
            val x = (1..5).sumOf { k -> sin(k * phase) / k }
            val ramp = min(1.0, min(t, (n - 1.0) / rate - t) / 0.02)
            loud * x / 2.3 * ramp
        }
    }

    private fun silence(seconds: Double) = DoubleArray((seconds * rate).toInt())

    private fun pcm(vararg parts: DoubleArray): ShortArray {
        val x = parts.reduce { a, b -> a + b }
        val rnd = java.util.Random(0)
        return ShortArray(x.size) { ((x[it] + rnd.nextGaussian() * 0.002) * 32767).coerceIn(-32768.0, 32767.0).toInt().toShort() }
    }

    @Test
    fun calmTalk() {
        val parts = mutableListOf(silence(0.3))
        for (i in 0 until 12) {
            val f = 260 + 10 * sin(i.toDouble())
            parts += voice(f, f + 5, 0.18); parts += silence(0.08)
        }
        parts += silence(0.3)
        val m = VoiceFeatures.measure(pcm(*parts.toTypedArray()))
        println("calm  $m")
        assertEquals(263.3f, m.pitchMedianHz, 3f)
        assertEquals(0.19f, m.longestVowelS, 0.02f)
        assertEquals(0f, m.longVowelShare, 0.01f)
        assertEquals(5.36f, m.syllablesPerS, 0.4f)
        assertEquals(1.68f, m.glideStPerS, 0.3f)
    }

    @Test
    fun whine() {
        val m = VoiceFeatures.measure(pcm(silence(0.3), voice(420.0, 520.0, 1.0), silence(0.25), voice(520.0, 430.0, 1.0), silence(0.3)))
        println("whine $m")
        assertEquals(472.7f, m.pitchMedianHz, 5f)
        assertEquals(1.02f, m.longestVowelS, 0.03f)
        assertEquals(1f, m.longVowelShare, 0.01f)
        assertEquals(3.39f, m.glideStPerS, 0.3f)
        assertTrue(m.syllablesPerS < 1.5f)
    }
}

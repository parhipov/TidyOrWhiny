package com.tidyorwhiny.app.speech

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How the child sounded, not what they said: pitch, glide, drawn-out vowels, loudness, tempo.
 * All of it is kept with debug recordings; forPrompt() is the part that told whines apart.
 * tools/whine/features.py is the same code in Python, run over AudioSet clips to check it.
 */
data class VoiceFeatures(
    val seconds: Float,          // recording length
    val voicedShare: Float,      // share of the time with a voice in it, 0-1
    val pitchMedianHz: Float,    // typical pitch
    val pitchHighHz: Float,      // 90th percentile
    val pitchSpreadSt: Float,    // std of pitch, semitones
    val pitchRangeSt: Float,     // 10th..90th percentile, semitones
    val glideStPerS: Float,      // how fast pitch slides within a vowel, semitones per second
    val longestVowelS: Float,    // longest unbroken voiced stretch
    val longVowelShare: Float,   // share of voiced time in stretches over 0.5 s
    val loudnessDb: Float,       // mean loudness of voiced frames, dBFS
    val loudnessSpreadDb: Float, // std of loudness over voiced frames
    val syllablesPerS: Float,    // tempo: energy peaks per second of voiced time
) {
    /**
     * What Qwen is told: only the measures that held up on real child voices (tools/whine, Freesound).
     * Pitch is left out: calm children there spoke at 320-460 Hz, as high as any whine threshold.
     * Tempo is left out: the syllable counter takes noise for syllables on real recordings.
     */
    fun forPrompt() = buildString {
        appendLine("recording length: %.1f s".format(seconds))
        appendLine("voice in %.0f%% of the time".format(voicedShare * 100))
        if (pitchMedianHz > 0) {
            append("longest drawn-out vowel: %.2f s; %.0f%% of the voiced time is in vowels held over 0.5 s".format(longestVowelS, longVowelShare * 100))
        } else {
            append("no clear voice found")
        }
    }

    /** Every measure, one per line: the kept recordings' notes, for calibrating. */
    fun describe() = buildString {
        appendLine("recording length: %.1f s".format(seconds))
        appendLine("voiced: %.0f%% of the time".format(voicedShare * 100))
        if (pitchMedianHz > 0) {
            appendLine("pitch: median %.0f Hz, high (90th percentile) %.0f Hz".format(pitchMedianHz, pitchHighHz))
            appendLine("pitch variation: spread %.1f semitones, range %.1f semitones".format(pitchSpreadSt, pitchRangeSt))
            appendLine("pitch glide within vowels: %.1f semitones per second".format(glideStPerS))
            appendLine("longest drawn-out vowel: %.2f s; %.0f%% of voiced time is in stretches over 0.5 s".format(longestVowelS, longVowelShare * 100))
            appendLine("loudness: %.0f dBFS, varies by %.1f dB".format(loudnessDb, loudnessSpreadDb))
            append("tempo: %.1f syllables per second".format(syllablesPerS))
        } else {
            append("no clear voice found")
        }
    }

    companion object {
        const val RATE = 16_000
        private const val FRAME = 640          // 40 ms: two periods of a 50 Hz floor, plenty for a child's voice
        private const val HOP = 160            // 10 ms
        private const val F0_MIN = 120f        // a child's voice, whines and squeals included
        private const val F0_MAX = 800f
        private const val YIN_THRESHOLD = 0.15f

        /** Measures 16 kHz mono PCM. */
        fun measure(pcm: ShortArray, n: Int = pcm.size): VoiceFeatures {
            val x = FloatArray(n) { pcm[it] / 32768f }
            val frames = if (n < FRAME) 0 else (n - FRAME) / HOP + 1
            val db = FloatArray(frames)
            val f0 = FloatArray(frames)          // 0 = unvoiced
            for (i in 0 until frames) {
                val o = i * HOP
                var e = 0.0
                for (k in 0 until FRAME) e += x[o + k] * x[o + k]
                db[i] = (10 * log10(e / FRAME + 1e-10)).toFloat()
            }
            // a voice must stand out of the room: 12 dB over the quiet floor and not near silence
            val floor = percentile(db, 0.1f)
            val gate = max(floor + 12f, -50f)
            for (i in 0 until frames) if (db[i] > gate) f0[i] = yin(x, i * HOP)
            smooth(f0)

            val voiced = (0 until frames).filter { f0[it] > 0 }
            val seconds = n / RATE.toFloat()
            if (voiced.size < 10) return VoiceFeatures(seconds, voiced.size * HOP / RATE.toFloat() / max(seconds, 0.01f),
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

            val st = voiced.map { 12 * log2(f0[it] / 100f) }.toFloatArray()   // semitones re 100 Hz
            val hz = voiced.map { f0[it] }.toFloatArray()
            val vdb = voiced.map { db[it] }.toFloatArray()

            // voiced stretches: runs of voiced frames, gaps of up to 30 ms bridged
            val runs = mutableListOf<IntRange>()
            var start = -1; var last = -10
            for (i in voiced) {
                if (i - last > 3) { if (start >= 0) runs += start..last; start = i }
                last = i
            }
            runs += start..last
            val runS = runs.map { (it.last - it.first + 1) * HOP / RATE.toFloat() }
            val voicedS = voiced.size * HOP / RATE.toFloat()

            // glide: mean |d pitch / dt| inside stretches, frame to frame, jumps over 3 st (octave errors) left out
            var glide = 0.0; var steps = 0
            for (r in runs) for (i in r.first + 1..r.last) {
                if (f0[i] > 0 && f0[i - 1] > 0) {
                    val d = abs(12 * log2(f0[i] / f0[i - 1]))
                    if (d < 3f) { glide += d; steps++ }
                }
            }
            val glidePerS = if (steps > 0) (glide / steps * RATE / HOP).toFloat() else 0f

            return VoiceFeatures(
                seconds = seconds,
                voicedShare = voicedS / max(seconds, 0.01f),
                pitchMedianHz = percentile(hz, 0.5f),
                pitchHighHz = percentile(hz, 0.9f),
                pitchSpreadSt = std(st),
                pitchRangeSt = percentile(st, 0.9f) - percentile(st, 0.1f),
                glideStPerS = glidePerS,
                longestVowelS = runS.max(),
                longVowelShare = runS.filter { it > 0.5f }.sum() / max(voicedS, 0.01f),
                loudnessDb = vdb.average().toFloat(),
                loudnessSpreadDb = std(vdb),
                syllablesPerS = peaks(db, gate) / max(voicedS, 0.01f),
            )
        }

        /** YIN pitch of one frame, or 0 when it is not clearly periodic. */
        private fun yin(x: FloatArray, o: Int): Float {
            val maxLag = (RATE / F0_MIN).toInt()
            val minLag = (RATE / F0_MAX).toInt()
            val w = FRAME - maxLag
            val d = FloatArray(maxLag + 1)
            for (lag in 1..maxLag) {
                var s = 0f
                for (k in 0 until w) { val v = x[o + k] - x[o + k + lag]; s += v * v }
                d[lag] = s
            }
            var run = 0f
            for (lag in 1..maxLag) {       // cumulative mean normalised difference
                run += d[lag]
                d[lag] = if (run > 0) d[lag] * lag / run else 1f
            }
            var lag = minLag
            while (lag < maxLag) {
                if (d[lag] < YIN_THRESHOLD) {
                    while (lag + 1 < maxLag && d[lag + 1] < d[lag]) lag++
                    // parabolic interpolation around the dip
                    val a = d[lag - 1]; val b = d[lag]; val c = d[lag + 1]
                    val den = a - 2 * b + c
                    val shift = if (abs(den) > 1e-9f) 0.5f * (a - c) / den else 0f
                    return RATE / (lag + shift)
                }
                lag++
            }
            return 0f
        }

        /** Median of three over voiced frames: drops single-frame octave jumps and lone voiced blips. */
        private fun smooth(f0: FloatArray) {
            val c = f0.copyOf()
            for (i in 1 until f0.size - 1) {
                if (c[i] > 0 && c[i - 1] == 0f && c[i + 1] == 0f) f0[i] = 0f
                else if (c[i] > 0 && c[i - 1] > 0 && c[i + 1] > 0) {
                    val s = floatArrayOf(c[i - 1], c[i], c[i + 1]).sorted()
                    f0[i] = s[1]
                }
            }
        }

        /** Syllable nuclei: loudness peaks over the gate at least 100 ms apart, 3 dB above their valleys. */
        private fun peaks(db: FloatArray, gate: Float): Float {
            var count = 0; var lastPeak = -100; var valley = Float.MAX_VALUE
            for (i in 1 until db.size - 1) {
                valley = min(valley, db[i])
                if (db[i] > gate && db[i] >= db[i - 1] && db[i] > db[i + 1] && db[i] - valley > 3f && i - lastPeak >= 10) {
                    count++; lastPeak = i; valley = db[i]
                }
            }
            return count.toFloat()
        }

        private fun percentile(a: FloatArray, q: Float): Float {
            if (a.isEmpty()) return 0f
            val s = a.sorted()
            return s[((s.size - 1) * q).toInt()]
        }

        private fun std(a: FloatArray): Float {
            val m = a.average()
            return sqrt(a.sumOf { (it - m) * (it - m) } / a.size).toFloat()
        }
    }
}

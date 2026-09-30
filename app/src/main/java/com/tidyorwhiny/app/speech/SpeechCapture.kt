package com.tidyorwhiny.app.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tidyorwhiny.app.BuildConfig
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import kotlin.math.log10

sealed interface RecState {
    data object Idle : RecState
    data object Listening : RecState
    data object Measuring : RecState      // recording over, the voice being measured
    /** file: the recording kept by a debug build, to check the measures on our own voices later. */
    data class Ready(val transcript: String, val voice: VoiceFeatures?, val file: File? = null) : RecState
    data object Empty : RecState          // no words and no voice
    data object Unavailable : RecState    // no recognizer on the device
}

/**
 * The child's words and how they sounded. On Android 13+ we record the microphone ourselves,
 * keep the samples for VoiceFeatures and hand the same stream to the phone's recognizer
 * (EXTRA_AUDIO_SOURCE, one segmented session). A recognizer that will not take a stream
 * sends us back to the old way for good: it listens to the microphone itself, words only.
 */
class SpeechCapture(private val context: Context) : RecognitionListener {
    var state by mutableStateOf<RecState>(RecState.Idle)
        private set
    var partial by mutableStateOf("")
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    val levels = mutableStateListOf<Float>()

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val heard = StringBuilder()
    private var wanted = false
    private var startedAt = 0L
    private var streaming = false          // this session feeds the recognizer our own samples

    // our own recording
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private val pcm = ShortArray(VoiceFeatures.RATE * (MAX_MS / 1000).toInt() + VoiceFeatures.RATE)
    @Volatile private var n = 0
    @Volatile private var pipe: OutputStream? = null
    private var pipeRead: ParcelFileDescriptor? = null   // kept open: startListening may parcel it later

    private val tick = object : Runnable {
        override fun run() {
            if (state != RecState.Listening) return
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (elapsedMs >= MAX_MS) stop() else main.postDelayed(this, 100)
        }
    }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state = RecState.Unavailable
            return
        }
        heard.clear(); partial = ""; levels.clear(); elapsedMs = 0; n = 0
        wanted = true
        startedAt = SystemClock.elapsedRealtime()
        state = RecState.Listening
        streaming = Build.VERSION.SDK_INT >= 33 && !streamRefused && startRecording()
        if (streaming) listenToStream() else listenToMic()
        main.post(tick)
    }

    /** The parent pressed stop: the recognizer hands over what it has, then we finish. */
    fun stop() {
        if (state != RecState.Listening) return
        wanted = false
        if (streaming) {
            stopRecording()                    // the stream ends: the recognizer gives its last words
        } else {
            recognizer?.stopListening()
        }
        main.postDelayed({ if (state == RecState.Listening) finish() }, 3000)  // some never answer a stop
    }

    fun reset() {
        wanted = false
        release()
        state = RecState.Idle
        partial = ""; levels.clear(); elapsedMs = 0
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        stopRecording()
        recognizer?.destroy()
        recognizer = null
    }

    // -- our recording ---------------------------------------------------------

    @SuppressLint("MissingPermission")    // the screen asks for RECORD_AUDIO before it starts us
    private fun startRecording(): Boolean {
        val min = AudioRecord.getMinBufferSize(VoiceFeatures.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, VoiceFeatures.RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CHUNK * 4))
        } catch (e: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        r.startRecording()
        record = r
        reader = Thread({
            val buf = ShortArray(CHUNK)
            val bytes = ByteArray(CHUNK * 2)
            while (record === r) {
                val got = r.read(buf, 0, CHUNK)
                if (got <= 0) break
                val room = minOf(got, pcm.size - n)
                System.arraycopy(buf, 0, pcm, n, room)
                n += room
                var e = 0.0
                for (i in 0 until got) {
                    e += buf[i] * buf[i].toDouble()
                    bytes[2 * i] = (buf[i].toInt() and 0xff).toByte()
                    bytes[2 * i + 1] = (buf[i].toInt() shr 8).toByte()
                }
                try { pipe?.write(bytes, 0, got * 2) } catch (_: IOException) { }
                val db = 10 * log10(e / got / (32768.0 * 32768.0) + 1e-10)
                val level = ((db + 60) / 50).toFloat().coerceIn(0.06f, 1f)    // about -60..-10 dBFS
                main.post { if (state == RecState.Listening) { levels.add(level); while (levels.size > LEVELS) levels.removeAt(0) } }
            }
        }, "voice").apply { start() }
        return true
    }

    private fun stopRecording() {
        val r = record ?: return
        record = null
        try { r.stop() } catch (_: Exception) { }
        reader?.join(500)
        reader = null
        r.release()
        closePipe()
    }

    private fun closePipe() {
        try { pipe?.close() } catch (_: IOException) { }
        pipe = null
        try { pipeRead?.close() } catch (_: IOException) { }
        pipeRead = null
    }

    // -- the recognizer ----------------------------------------------------------

    private fun baseIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun newRecognizer() = SpeechRecognizer.createSpeechRecognizer(context).also {
        recognizer?.destroy()
        recognizer = it
        it.setRecognitionListener(this)
    }

    /** One segmented session over a pipe we keep writing our samples into. */
    private fun listenToStream() {
        closePipe()
        val (read, write) = ParcelFileDescriptor.createPipe()
        pipe = ParcelFileDescriptor.AutoCloseOutputStream(write)
        pipeRead = read
        newRecognizer().startListening(baseIntent().apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, VoiceFeatures.RATE)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        })
        Log.d(TAG, "listening to our stream")
    }

    /** The old way: the recognizer takes the microphone and stops at the first pause; restarted until stop. */
    private fun listenToMic() {
        newRecognizer().startListening(baseIntent().apply {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        })
    }

    /** One stretch is over: keep listening if the parent still is, else finish. */
    private fun next() {
        if (wanted && SystemClock.elapsedRealtime() - startedAt < MAX_MS) {
            main.post { if (wanted) { if (streaming) listenToStream() else listenToMic() } }
        } else main.post { finish() }
    }

    private fun finish() {
        if (state != RecState.Listening) return
        wanted = false
        recognizer?.destroy()
        recognizer = null
        stopRecording()
        val text = (heard.toString() + " " + partial).trim().replace(Regex("\\s+"), " ")
        partial = ""
        if (!streaming) {
            state = if (text.isBlank()) RecState.Empty else RecState.Ready(text, null)
            return
        }
        state = RecState.Measuring
        val samples = n
        Thread({
            val voice = VoiceFeatures.measure(pcm, samples)
            Log.d(TAG, "voice: " + voice.describe().replace('\n', ';'))
            val file = if (BuildConfig.DEBUG) keep(samples, text, voice) else null
            main.post {
                if (state != RecState.Measuring) return@post
                // no words is fine when there was a voice: a whine needs none
                state = if (text.isBlank() && voice.voicedShare < 0.05f) RecState.Empty else RecState.Ready(text, voice, file)
            }
        }, "measure").start()
    }

    /** Debug builds: the recording as WAV and what was heard and measured beside it, in files/voice/. */
    private fun keep(samples: Int, text: String, voice: VoiceFeatures): File? = try {
        val dir = File(context.getExternalFilesDir(null), "voice").apply { mkdirs() }
        val name = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        File(dir, "$name.wav").outputStream().buffered().use { out ->
            val bytes = samples * 2
            fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.write("RIFF".toByteArray()); int(36 + bytes); out.write("WAVEfmt ".toByteArray())
            int(16); short(1); short(1); int(VoiceFeatures.RATE); int(VoiceFeatures.RATE * 2); short(2); short(16)
            out.write("data".toByteArray()); int(bytes)
            for (i in 0 until samples) short(pcm[i].toInt())
        }
        File(dir, "$name.txt").apply { writeText("transcript: $text\n${voice.describe()}\n") }
    } catch (e: Exception) {
        Log.w(TAG, "could not keep the recording", e)
        null
    }

    override fun onResults(results: Bundle?) {
        take(results)
        next()
    }

    override fun onSegmentResults(segmentResults: Bundle) {
        take(segmentResults)
    }

    override fun onEndOfSegmentedSession() {
        next()
    }

    private fun take(b: Bundle?) {
        val best = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (best.isNotBlank()) heard.append(best).append(' ')
        partial = ""
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let { partial = it }
    }

    override fun onError(error: Int) {
        Log.d(TAG, "recognizer error $error, streaming=$streaming")
        val early = SystemClock.elapsedRealtime() - startedAt < 2000 && heard.isEmpty()
        when {
            // this recognizer will not take a stream: back to the microphone, for good
            streaming && early && error !in setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT) -> {
                streamRefused = true
                Log.w(TAG, "the recognizer refused our stream ($error): words only from now on")
                streaming = false
                stopRecording()
                main.post { if (wanted) listenToMic() }
            }
            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> next()
            else -> main.post { finish() }
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (streaming || state != RecState.Listening) return      // streaming: our own meter drives the bars
        levels.add(((rmsdB + 2f) / 12f).coerceIn(0.06f, 1f))
        while (levels.size > LEVELS) levels.removeAt(0)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        const val MAX_MS = 30_000L
        const val LEVELS = 36
        private const val CHUNK = 1600          // 100 ms of samples
        private const val TAG = "Inspector"
        @Volatile private var streamRefused = false
    }
}

package com.tidyorwhiny.app

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidyorwhiny.app.ai.Inspector
import com.tidyorwhiny.app.ai.MessVerdict
import com.tidyorwhiny.app.ai.NoKeyException
import com.tidyorwhiny.app.ai.WhineVerdict
import com.tidyorwhiny.app.speech.VoiceFeatures
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Check { Mess, Whine }

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data class PhotoPreview(val photo: Bitmap) : Screen
    data object Recorder : Screen
    data class Analyzing(val check: Check, val error: Failure? = null) : Screen
    data class MessResult(val photo: Bitmap, val verdict: MessVerdict) : Screen
    data class WhineResult(val transcript: String, val verdict: WhineVerdict) : Screen
}

enum class Failure { NoKey, Other }

/** Where the app is, and the one ask to the Inspector in flight. */
class AppViewModel : ViewModel() {
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    private val inspector = Inspector()
    private var photo: Bitmap? = null
    private var transcript: String = ""
    private var voice: VoiceFeatures? = null
    private var job: Job? = null

    fun open(check: Check) {
        screen = if (check == Check.Mess) Screen.Camera else Screen.Recorder
    }

    fun onPhoto(bitmap: Bitmap) {
        photo = bitmap
        screen = Screen.PhotoPreview(bitmap)
    }

    fun retake() { screen = Screen.Camera }

    fun sendPhoto() {
        val p = photo ?: return
        ask(Check.Mess) { Screen.MessResult(p, inspector.judgeRoom(p)) }
    }

    fun sendWords(text: String, sound: VoiceFeatures?, kept: java.io.File? = null) {
        transcript = text
        voice = sound
        ask(Check.Whine) {
            val v = inspector.judgeWords(text, sound)
            kept?.appendText("verdict: ${v.kind} ${v.level}/10, ${v.title}\nwhy: ${v.why.joinToString(" | ")}\ntip: ${v.tip}\n")
            Screen.WhineResult(text, v)
        }
    }

    fun retry() {
        when ((screen as? Screen.Analyzing)?.check) {
            Check.Mess -> sendPhoto()
            Check.Whine -> sendWords(transcript, voice)
            null -> Unit
        }
    }

    private fun ask(check: Check, block: suspend () -> Screen) {
        job?.cancel()
        screen = Screen.Analyzing(check)
        job = viewModelScope.launch {
            val next = try {
                block()
            } catch (e: NoKeyException) {
                Screen.Analyzing(check, Failure.NoKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Inspector", "ask failed", e)
                Screen.Analyzing(check, Failure.Other)
            }
            if (screen == Screen.Analyzing(check)) screen = next
        }
    }

    /** Debug builds: a screen as it is, for [Demo]. */
    fun show(s: Screen) {
        job?.cancel()
        screen = s
    }

    fun home() {
        job?.cancel()
        screen = Screen.Home
    }

    /** System back: one step up the flow. */
    fun back() {
        screen = when (val s = screen) {
            Screen.Home -> Screen.Home
            is Screen.PhotoPreview -> Screen.Camera
            is Screen.Analyzing -> {
                job?.cancel()
                if (s.check == Check.Mess && photo != null) Screen.PhotoPreview(photo!!) else if (s.check == Check.Mess) Screen.Camera else Screen.Recorder
            }
            else -> Screen.Home
        }
    }
}

package com.tidyorwhiny.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.tidyorwhiny.app.R
import com.tidyorwhiny.app.speech.RecState
import com.tidyorwhiny.app.speech.SpeechCapture
import com.tidyorwhiny.app.speech.VoiceFeatures
import com.tidyorwhiny.app.ui.components.CenteredText
import com.tidyorwhiny.app.ui.components.Gear
import com.tidyorwhiny.app.ui.components.Mascot
import com.tidyorwhiny.app.ui.components.Pill
import com.tidyorwhiny.app.ui.components.PressableSticker
import com.tidyorwhiny.app.ui.components.StepDots
import com.tidyorwhiny.app.ui.components.StickerButton
import com.tidyorwhiny.app.ui.components.TwIcons
import com.tidyorwhiny.app.ui.components.TwTopBar
import com.tidyorwhiny.app.ui.components.sticker
import com.tidyorwhiny.app.ui.theme.Coral
import com.tidyorwhiny.app.ui.theme.Cream
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Line
import com.tidyorwhiny.app.ui.theme.Mint
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.TwType
import com.tidyorwhiny.app.ui.theme.Violet
import com.tidyorwhiny.app.ui.theme.VioletDeep
import com.tidyorwhiny.app.ui.theme.VioletTint

@Composable
fun RecorderScreen(onBack: () -> Unit, onSend: (String, VoiceFeatures?, java.io.File?) -> Unit) {
    val context = LocalContext.current
    val capture = remember { SpeechCapture(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { capture.release() } }
    var granted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) capture.start()
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }
    val state = capture.state
    val listening = state == RecState.Listening

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        TwTopBar(stringResource(R.string.whine_check), onBack, backLabel = stringResource(R.string.back)) {
            StepDots(if (state is RecState.Ready) 2 else 1, Violet)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            StatusChip(state)
            Spacer(Modifier.height(14.dp))
            val sec = (capture.elapsedMs / 1000).toInt()
            Text("%d:%02d".format(sec / 60, sec % 60), style = TwType.timer)
            Spacer(Modifier.height(10.dp))
            Waveform(capture.levels, listening, Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(26.dp))
            MicButton(
                listening = listening,
                label = stringResource(if (listening) R.string.stop_recording else R.string.start_recording),
            ) {
                when {
                    !granted -> ask.launch(Manifest.permission.RECORD_AUDIO)
                    listening -> capture.stop()
                    state == RecState.Measuring -> Unit
                    else -> capture.start()
                }
            }
            Spacer(Modifier.height(18.dp))
            CenteredText(
                stringResource(
                    when {
                        !granted -> R.string.mic_permission_body
                        listening -> R.string.rec_tap_stop
                        else -> R.string.rec_tap_start
                    }
                ), TwType.bodyStrong,
            )
            Spacer(Modifier.height(22.dp))
            AnimatedContent(state, label = "below", contentKey = { it.javaClass }) { s ->
                when (s) {
                    RecState.Listening -> if (capture.partial.isNotBlank()) HeardSoFar(capture.partial) else Spacer(Modifier.height(1.dp))
                    is RecState.Ready -> HeardSoFar(s.transcript.ifBlank { stringResource(R.string.heard_no_words) })
                    RecState.Measuring -> Note(stringResource(R.string.rec_measuring))
                    RecState.Empty -> Note(stringResource(R.string.rec_heard_nothing))
                    RecState.Unavailable -> Note(stringResource(R.string.speech_unavailable))
                    RecState.Idle -> Tip()
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        (state as? RecState.Ready)?.let { ready ->
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                StickerButton(stringResource(R.string.send_to_ai), { onSend(ready.transcript, ready.voice, ready.file) }, color = Violet, icon = TwIcons.Sparkle)
                Spacer(Modifier.height(16.dp))
                StickerButton(stringResource(R.string.record_again), { capture.reset(); capture.start() }, color = Paper, icon = TwIcons.Retry)
            }
        }
    }
}

@Composable
private fun StatusChip(state: RecState) {
    val (text, dot) = when (state) {
        RecState.Listening -> stringResource(R.string.rec_listening) to Coral
        is RecState.Ready -> stringResource(R.string.rec_ready) to Mint
        else -> stringResource(R.string.rec_idle) to Line
    }
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(Pill).background(Paper).border(2.dp, Ink, Pill).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot.copy(alpha = if (state == RecState.Listening) a else 1f))
            .border(1.5.dp, Ink, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = TwType.small, color = Ink)
    }
}

/** The recent loudness as rounded bars; flat dots while not listening. */
@Composable
private fun Waveform(levels: List<Float>, live: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val n = SpeechCapture.LEVELS
        val gap = size.width / n
        val w = gap * 0.55f
        for (i in 0 until n) {
            val v = levels.getOrNull(levels.size - n + i) ?: 0f
            val h = if (live || levels.isNotEmpty()) maxOf(w, v * size.height) else w
            val x = i * gap + (gap - w) / 2
            drawRoundRect(
                if (levels.isEmpty()) Line else if (live) VioletDeep else Violet,
                Offset(x, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2),
            )
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, label: String, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "rings")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0..1) {
                    val q = (p + k * 0.5f) % 1f
                    drawCircle(Violet.copy(alpha = (1f - q) * 0.55f), size.minDimension / 2 * (0.62f + 0.38f * q), style = Stroke(6.dp.toPx()))
                }
            }
        } else {
            Box(Modifier.size(200.dp).clip(CircleShape).background(VioletTint))
        }
        PressableSticker(onClick, Modifier.size(150.dp).semantics { contentDescription = label }, shape = CircleShape,
            fill = if (listening) Coral else Violet, shadow = 7.dp) {
            if (listening) Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Ink))
            else Icon(TwIcons.Mic, null, tint = Ink, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun HeardSoFar(text: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).sticker(RoundedCornerShape(22.dp), Paper, 4.dp).padding(18.dp)) {
        Text(stringResource(R.string.what_i_heard), style = TwType.small)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.quoted, text), style = TwType.bodyStrong.copy(fontStyle = FontStyle.Italic))
    }
}

@Composable
private fun Tip() {
    Row(
        Modifier.fillMaxWidth().sticker(RoundedCornerShape(22.dp), VioletTint, 0.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mascot(Modifier.size(52.dp), gear = Gear.Headphones)
        Text(stringResource(R.string.rec_tip), style = TwType.bodyStrong, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = TwType.bodyStrong, color = Ink,
        modifier = Modifier.fillMaxWidth().sticker(RoundedCornerShape(22.dp), Paper.copy(alpha = 1f), 0.dp, outline = Coral).padding(16.dp))
}


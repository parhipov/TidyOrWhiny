package com.tidyorwhiny.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tidyorwhiny.app.Check
import com.tidyorwhiny.app.Failure
import com.tidyorwhiny.app.R
import com.tidyorwhiny.app.ui.components.CenteredText
import com.tidyorwhiny.app.ui.components.Gear
import com.tidyorwhiny.app.ui.components.Mascot
import com.tidyorwhiny.app.ui.components.SpinningRing
import com.tidyorwhiny.app.ui.components.StepDots
import com.tidyorwhiny.app.ui.components.StickerButton
import com.tidyorwhiny.app.ui.components.TwIcons
import com.tidyorwhiny.app.ui.components.TwTopBar
import com.tidyorwhiny.app.ui.components.sticker
import com.tidyorwhiny.app.ui.theme.Cream
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Line
import com.tidyorwhiny.app.ui.theme.Mint
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.OrangeTint
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.TwType
import com.tidyorwhiny.app.ui.theme.Violet
import com.tidyorwhiny.app.ui.theme.VioletTint
import kotlinx.coroutines.delay

@Composable
fun AnalyzingScreen(check: Check, error: Failure?, slow: Boolean, onCancel: () -> Unit, onRetry: () -> Unit) {
    val mess = check == Check.Mess
    val accent = if (mess) Orange else Violet
    val tint = if (mess) OrangeTint else VioletTint
    val steps = if (mess) listOf(R.string.step_photo_received, R.string.step_scanning, R.string.step_writing)
    else listOf(R.string.step_audio_received, R.string.step_tone, R.string.step_writing)
    var step by remember { mutableIntStateOf(1) }
    LaunchedEffect(error) {
        if (error == null) {
            step = 1
            delay(1800); step = 2
        }
    }

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        TwTopBar(stringResource(if (mess) R.string.mess_check else R.string.whine_check), onCancel,
            backIcon = TwIcons.Close, backLabel = stringResource(R.string.close)) {
            StepDots(3, accent)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(214.dp).clip(CircleShape).background(tint))
                if (error == null) SpinningRing(accent, Modifier.size(250.dp))
                Mascot(Modifier.size(150.dp), gear = if (mess) Gear.Lens else Gear.Headphones, bob = error == null)
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(error, label = "state") { e ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (e == null) {
                        CenteredText(stringResource(when {
                            slow -> R.string.slow_title
                            mess -> R.string.inspecting_title
                            else -> R.string.listening_title
                        }), TwType.title)
                        Spacer(Modifier.height(6.dp))
                        CenteredText(stringResource(if (slow) R.string.slow_sub else R.string.analyzing_sub), TwType.body)
                        Spacer(Modifier.height(24.dp))
                        Column(
                            Modifier.fillMaxWidth().sticker(RoundedCornerShape(24.dp), Paper, 5.dp).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            steps.forEachIndexed { i, s -> StepRow(stringResource(s), i, step, accent) }
                        }
                    } else {
                        CenteredText(stringResource(R.string.error_title), TwType.title)
                        Spacer(Modifier.height(8.dp))
                        CenteredText(stringResource(if (e == Failure.NoKey) R.string.error_no_key else R.string.error_generic), TwType.body)
                    }
                }
            }
        }
        if (error != null) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                StickerButton(stringResource(R.string.try_again), onRetry, color = accent, icon = TwIcons.Retry)
            }
        }
    }
}

@Composable
private fun StepRow(text: String, index: Int, current: Int, accent: Color) {
    val done = index < current
    val now = index == current
    val t = rememberInfiniteTransition(label = "step")
    val pulse by t.animateFloat(0.75f, 1.1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).graphicsLayer { if (now) { scaleX = pulse; scaleY = pulse } }
                .clip(CircleShape).background(if (done) Mint else if (now) accent else Line)
                .border(2.dp, if (done || now) Ink else Ink.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(TwIcons.Check, null, tint = Ink, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = if (done || now) TwType.bodyStrong else TwType.body,
            color = if (done || now) Ink else Ink.copy(alpha = 0.5f))
    }
}

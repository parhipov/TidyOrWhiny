package com.tidyorwhiny.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Mint
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.Sun
import com.tidyorwhiny.app.ui.theme.TwType
import com.tidyorwhiny.app.ui.theme.Violet
import kotlin.random.Random

/** The possible verdicts side by side, the one given filled in. */
@Composable
fun VerdictChoices(labels: List<String>, chosen: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        labels.forEachIndexed { i, label ->
            val on = i == chosen
            Row(
                Modifier.clip(Pill).background(if (on) color else Paper.copy(alpha = 0.6f))
                    .border(2.dp, if (on) Ink else Ink.copy(alpha = 0.35f), Pill)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(TwIcons.Check, null, tint = Ink, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(label, style = TwType.small, color = if (on) Ink else Ink.copy(alpha = 0.6f))
            }
        }
    }
}

/** A 0-10 meter in ten chunky segments that fill in one by one. */
@Composable
fun Meter(label: String, level: Int, color: Color, modifier: Modifier = Modifier) {
    val fill = remember(level) { Animatable(0f) }
    LaunchedEffect(level) { fill.animateTo(level.toFloat(), tween(900, delayMillis = 350, easing = FastOutSlowInEasing)) }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label $level/10" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TwType.bodyStrong, modifier = Modifier.weight(1f))
            Text("$level/10", style = TwType.bar)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until 10) {
                val on = fill.value >= i + 0.5f
                Box(
                    Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (on) color else Paper)
                        .border(2.dp, Ink, RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

/** A speech bubble from the Inspector. */
@Composable
fun InspectorSays(title: String, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Mascot(Modifier.size(52.dp))
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).sticker(RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp), Paper, 4.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(title, style = TwType.small)
            Spacer(Modifier.height(4.dp))
            Text(text, style = TwType.bodyStrong)
        }
    }
}

private class Bit(val x: Float, val delay: Float, val speed: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val sway: Float)

/** A burst of paper confetti falling once over the screen. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val bits = remember {
        val colors = listOf(Orange, Violet, Sun, Mint, Paper)
        List(46) {
            Bit(Random.nextFloat(), Random.nextFloat() * 0.35f, 0.7f + Random.nextFloat() * 0.6f,
                Random.nextFloat() * 720f - 360f, colors.random(), 8f + Random.nextFloat() * 8f,
                12f + Random.nextFloat() * 10f, Random.nextFloat() * 40f - 20f)
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    Canvas(modifier) {
        if (t.value >= 1f) return@Canvas
        for (b in bits) {
            val p = ((t.value - b.delay) / (1f - b.delay)).coerceIn(0f, 1f) * b.speed
            if (p <= 0f || p > 1.1f) continue
            val x = b.x * size.width + b.sway * density * kotlin.math.sin(p * 9f)
            val y = -40f + p * (size.height + 80f)
            rotate(b.spin * p, Offset(x, y)) {
                drawRect(Ink, Offset(x - b.w / 2 - 2f, y - b.h / 2 - 2f), Size(b.w + 4f, b.h + 4f))
                drawRect(b.color, Offset(x - b.w / 2, y - b.h / 2), Size(b.w, b.h))
            }
        }
    }
}

@Composable
fun CenteredText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())

package com.tidyorwhiny.app.ui.screens

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tidyorwhiny.app.BuildConfig
import com.tidyorwhiny.app.R
import com.tidyorwhiny.app.ui.components.Pill
import com.tidyorwhiny.app.ui.components.Gear
import com.tidyorwhiny.app.ui.components.Mascot
import com.tidyorwhiny.app.ui.components.PressableSticker
import com.tidyorwhiny.app.ui.components.Tag
import com.tidyorwhiny.app.ui.components.TwIcons
import com.tidyorwhiny.app.ui.components.sticker
import com.tidyorwhiny.app.ui.components.svg
import com.tidyorwhiny.app.ui.theme.Cream
import com.tidyorwhiny.app.ui.theme.Display
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Ink2
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.OrangeDeep
import com.tidyorwhiny.app.ui.theme.OrangeMark
import com.tidyorwhiny.app.ui.theme.OrangeTint
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.Sun
import com.tidyorwhiny.app.ui.theme.TwType
import com.tidyorwhiny.app.ui.theme.Violet
import com.tidyorwhiny.app.ui.theme.VioletDeep
import com.tidyorwhiny.app.ui.theme.VioletMark
import com.tidyorwhiny.app.ui.theme.VioletTint

@Composable
fun HomeScreen(onMess: () -> Unit, onWhine: () -> Unit) {
    Box(Modifier.fillMaxSize().drawBehind { backdrop() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 190.dp)
        ) {
            Wordmark()
            Spacer(Modifier.height(30.dp))
            Slogan()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.home_subtitle), style = TwType.body, modifier = Modifier.padding(end = 24.dp))
            Spacer(Modifier.height(26.dp))
            CheckCard(
                title = stringResource(R.string.mess_check), desc = stringResource(R.string.mess_check_desc),
                tag = stringResource(R.string.chip_photo), icon = TwIcons.Camera,
                tint = OrangeTint, accent = Orange, onClick = onMess,
            )
            Spacer(Modifier.height(20.dp))
            CheckCard(
                title = stringResource(R.string.whine_check), desc = stringResource(R.string.whine_check_desc),
                tag = stringResource(R.string.chip_audio), icon = TwIcons.Mic,
                tint = VioletTint, accent = Violet, onClick = onWhine,
            )
        }
        InspectorPeek(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Box(Modifier.size(44.dp).sticker(RoundedCornerShape(14.dp), Sun, 3.dp), contentAlignment = Alignment.Center) {
            Mascot(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = OrangeDeep)) { append(stringResource(R.string.wordmark_tidy)) }
                withStyle(SpanStyle(color = Ink2, fontWeight = FontWeight.Medium, fontSize = 17.sp)) {
                    append("  " + stringResource(R.string.wordmark_or) + "  ")
                }
                withStyle(SpanStyle(color = VioletDeep)) { append(stringResource(R.string.wordmark_whiny)) }
            },
            fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 23.sp,
            modifier = Modifier.weight(1f),
        )
        Version()
    }
}

/** The build, small, for us: a pill in the app's outline style. */
@Composable
private fun Version() {
    Text(
        "v" + BuildConfig.VERSION_NAME, style = TwType.chip.copy(fontSize = 10.sp, letterSpacing = 0.6.sp), color = Ink2,
        modifier = Modifier.clip(Pill).background(Paper.copy(alpha = 0.7f)).border(1.5.dp, Ink2.copy(alpha = 0.5f), Pill)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Snap the mess. Record the whine. AI decides. -- the marked words underlined with a highlighter. */
@Composable
private fun Slogan() {
    val l1 = stringResource(R.string.slogan_1_lead); val m1 = stringResource(R.string.slogan_1_mark)
    val l2 = stringResource(R.string.slogan_2_lead); val m2 = stringResource(R.string.slogan_2_mark)
    val l3 = stringResource(R.string.slogan_3)
    val text = remember(l1, m1, l2, m2, l3) {
        buildAnnotatedString { append(l1); append(m1); append("\n"); append(l2); append(m2); append("\n"); append(l3) }
    }
    val marks = remember(text) {
        val a = l1.length
        val b = a + m1.length + 1 + l2.length
        listOf(Triple(a, a + m1.length, OrangeMark), Triple(b, b + m2.length, VioletMark))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = TwType.hero, onTextLayout = { layout = it },
        modifier = Modifier.drawBehind {
            val l = layout ?: return@drawBehind
            for ((from, to, color) in marks) {
                if (to > l.layoutInput.text.length) continue
                val r = l.getPathForRange(from, to).getBounds()
                val h = r.height * 0.42f
                drawRoundRect(color, Offset(r.left - 3f, r.bottom - h - r.height * 0.06f),
                    Size(r.width + 6f, h), CornerRadius(h / 2))
            }
        },
    )
}

@Composable
private fun CheckCard(title: String, desc: String, tag: String, icon: ImageVector, tint: Color, accent: Color, onClick: () -> Unit) {
    PressableSticker(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), fill = tint, shadow = 6.dp,
        contentAlignment = Alignment.TopStart) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).sticker(RoundedCornerShape(20.dp), accent, 3.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Ink, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Tag(tag)
                    Spacer(Modifier.height(6.dp))
                    Text(title, style = TwType.cardTitle)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desc, style = TwType.body, color = Ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(48.dp).sticker(CircleShape, Ink, 0.dp), contentAlignment = Alignment.Center) {
                    Icon(TwIcons.Arrow, null, tint = Paper, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** The Inspector peeking up from the bottom edge, saying hello. */
@Composable
private fun InspectorPeek(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(170.dp)) {
        Mascot(Modifier.size(150.dp).align(Alignment.BottomStart).offset(x = 16.dp, y = 40.dp), gear = Gear.Lens)
        Text(
            stringResource(R.string.mascot_hello), style = TwType.bodyStrong,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 170.dp, end = 20.dp, top = 14.dp)
                .sticker(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp), Paper, 4.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Confetti-paper shapes on the cream: a big tangerine sun, a violet squiggle, a few dots. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.backdrop() {
    drawRect(Cream)
    drawCircle(OrangeTint, size.width * 0.42f, Offset(size.width * 1.02f, size.height * 0.02f))
    val u = size.width / 400f
    drawPath(svg("M300 150q12-14 24 0t24 0t24 0").also {
        it.transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) })
    }, Violet, style = Stroke(5f * u, cap = StrokeCap.Round))
    drawCircle(Sun, 7f * u, Offset(size.width * 0.9f, size.height * 0.30f))
    drawCircle(Violet.copy(alpha = 0.5f), 5f * u, Offset(size.width * 0.06f, size.height * 0.36f))
    drawCircle(Orange.copy(alpha = 0.6f), 6f * u, Offset(size.width * 0.82f, size.height * 0.95f))
}

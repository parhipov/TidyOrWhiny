package com.tidyorwhiny.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.tidyorwhiny.app.R
import com.tidyorwhiny.app.ai.MessVerdict
import com.tidyorwhiny.app.ai.WhineKind
import com.tidyorwhiny.app.ai.WhineVerdict
import com.tidyorwhiny.app.ui.components.CenteredText
import com.tidyorwhiny.app.ui.components.Confetti
import com.tidyorwhiny.app.ui.components.Face
import com.tidyorwhiny.app.ui.components.FaceBadge
import com.tidyorwhiny.app.ui.components.InspectorSays
import com.tidyorwhiny.app.ui.components.Meter
import com.tidyorwhiny.app.ui.components.StickerButton
import com.tidyorwhiny.app.ui.components.TwIcons
import com.tidyorwhiny.app.ui.components.TwTopBar
import com.tidyorwhiny.app.ui.components.VerdictChoices
import com.tidyorwhiny.app.ui.components.sticker
import com.tidyorwhiny.app.ui.theme.Coral
import com.tidyorwhiny.app.ui.theme.CoralTint
import com.tidyorwhiny.app.ui.theme.Cream
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Mint
import com.tidyorwhiny.app.ui.theme.MintTint
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.OrangeTint
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.Sun
import com.tidyorwhiny.app.ui.theme.SunTint
import com.tidyorwhiny.app.ui.theme.TwType
import com.tidyorwhiny.app.ui.theme.Violet

/** The photo, the verdict on it, and why. */
@Composable
fun MessResultScreen(photo: Bitmap, verdict: MessVerdict, onAgain: () -> Unit, onHome: () -> Unit) {
    val face = if (verdict.tidy) Face.Tidy else Face.Mess
    val color = if (verdict.tidy) Mint else Orange
    val image = remember(photo) { photo.asImageBitmap() }
    VerdictFrame(
        tint = if (verdict.tidy) MintTint else OrangeTint, celebrate = verdict.tidy, onHome = onHome,
        again = stringResource(R.string.check_again), againColor = Orange, againIcon = TwIcons.Camera, onAgain = onAgain,
    ) {
        // the photo, with the verdict's face stamped on its corner
        Box(Modifier.fillMaxWidth().padding(bottom = 30.dp)) {
            Image(
                image, stringResource(R.string.your_photo), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                    .sticker(RoundedCornerShape(28.dp), Paper, 6.dp).clip(RoundedCornerShape(28.dp))
                    .border(2.5.dp, Ink, RoundedCornerShape(28.dp)),
            )
            PopIn(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 34.dp)) {
                FaceBadge(face, Modifier.size(104.dp))
            }
        }
        Headline(verdict.title.ifBlank { stringResource(if (verdict.tidy) R.string.tidy else R.string.mess) })
        Spacer(Modifier.height(14.dp))
        VerdictChoices(listOf(stringResource(R.string.tidy), stringResource(R.string.mess)),
            if (verdict.tidy) 0 else 1, color, Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Meter(stringResource(R.string.mess_meter), verdict.level, if (verdict.tidy) Mint else Coral,
            Modifier.sticker(RoundedCornerShape(22.dp), Paper, 4.dp).padding(16.dp))
        Why(verdict.why, color)
        Tip(verdict.tip)
    }
}

/** The verdict on the words, what was heard, and why. */
@Composable
fun WhineResultScreen(transcript: String, verdict: WhineVerdict, onAgain: () -> Unit, onHome: () -> Unit) {
    val (face, color, tint) = when (verdict.kind) {
        WhineKind.WHINING -> Triple(Face.Whining, Coral, CoralTint)
        WhineKind.BEGGING -> Triple(Face.Begging, Sun, SunTint)
        WhineKind.CHAMP -> Triple(Face.Champ, Mint, MintTint)
    }
    val name = when (verdict.kind) {
        WhineKind.WHINING -> R.string.whining
        WhineKind.BEGGING -> R.string.begging
        WhineKind.CHAMP -> R.string.champ
    }
    VerdictFrame(
        tint = tint, celebrate = verdict.kind == WhineKind.CHAMP, onHome = onHome,
        again = stringResource(R.string.record_again), againColor = Violet, againIcon = TwIcons.Mic, onAgain = onAgain,
    ) {
        PopIn(Modifier.align(Alignment.CenterHorizontally)) { FaceBadge(face, Modifier.size(150.dp)) }
        Spacer(Modifier.height(10.dp))
        Headline(verdict.title.ifBlank { stringResource(name) })
        Spacer(Modifier.height(14.dp))
        VerdictChoices(
            listOf(stringResource(R.string.whining), stringResource(R.string.begging), stringResource(R.string.champ)),
            verdict.kind.ordinal, color, Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Meter(stringResource(if (verdict.kind == WhineKind.BEGGING) R.string.beg_meter else R.string.whine_meter),
            verdict.level, if (verdict.kind == WhineKind.CHAMP) Mint else color,
            Modifier.sticker(RoundedCornerShape(22.dp), Paper, 4.dp).padding(16.dp))
        Spacer(Modifier.height(18.dp))
        Heard(transcript)
        Why(verdict.why, color)
        Tip(verdict.tip)
    }
}

@Composable
private fun VerdictFrame(
    tint: Color, celebrate: Boolean, onHome: () -> Unit,
    again: String, againColor: Color, againIcon: androidx.compose.ui.graphics.vector.ImageVector, onAgain: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Cream)) {
        // the verdict's colour washes the top of the screen
        Box(Modifier.fillMaxWidth().height(300.dp).background(tint, RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TwTopBar(stringResource(R.string.verdict), onHome, backIcon = TwIcons.Close, backLabel = stringResource(R.string.close))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(28.dp))
                StickerButton(again, onAgain, color = againColor, icon = againIcon)
                Spacer(Modifier.height(16.dp))
                StickerButton(stringResource(R.string.home), onHome, color = Paper, icon = TwIcons.Home)
                Spacer(Modifier.height(24.dp))
            }
        }
        if (celebrate) Confetti(Modifier.fillMaxSize())
    }
}

@Composable
private fun Headline(text: String) =
    CenteredText(text, TwType.verdict, Modifier.semantics { heading() })

/** Why the Inspector decided so: numbered, one reason a line. */
@Composable
private fun Why(reasons: List<String>, color: Color) {
    if (reasons.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Column(Modifier.fillMaxWidth().sticker(RoundedCornerShape(22.dp), Paper, 4.dp).padding(18.dp)) {
        Text(stringResource(R.string.why), style = TwType.bar, modifier = Modifier.semantics { heading() })
        reasons.forEachIndexed { i, r ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(color).border(2.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = TwType.chip) }
                Spacer(Modifier.width(12.dp))
                Text(r, style = TwType.bodyStrong, modifier = Modifier.weight(1f).padding(top = 1.dp))
            }
        }
    }
}

@Composable
private fun Heard(transcript: String) {
    Column(Modifier.fillMaxWidth().sticker(RoundedCornerShape(22.dp), Paper, 4.dp).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TwIcons.Quote, null, tint = Violet, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.what_i_heard), style = TwType.small)
        }
        Spacer(Modifier.height(8.dp))
        Text(if (transcript.isBlank()) stringResource(R.string.heard_no_words) else stringResource(R.string.quoted, transcript),
            style = TwType.bodyStrong.copy(fontStyle = FontStyle.Italic))
    }
}

@Composable
private fun Tip(tip: String) {
    if (tip.isBlank()) return
    Spacer(Modifier.height(20.dp))
    InspectorSays(stringResource(R.string.inspector_says), tip)
}

/** Pops in with a bounce, a beat after the screen opens. */
@Composable
private fun PopIn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Box(modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = (1f - s.value) * -30f }) { content() }
}

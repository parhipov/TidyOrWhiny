package com.tidyorwhiny.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.TwType

val Pill = RoundedCornerShape(percent = 50)
val StrokeW = 2.5.dp

/** The app's look: a flat fill, a thick ink outline and a hard ink shadow straight below. */
fun Modifier.sticker(shape: Shape, fill: Color, shadow: Dp = 5.dp, outline: Color = Ink, shadowColor: Color = Ink) =
    drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        if (shadow > 0.dp) translate(top = shadow.toPx()) { drawOutline(o, shadowColor) }
        drawOutline(o, fill)
        drawOutline(o, outline, style = Stroke(StrokeW.toPx()))
    }

/** A sticker that presses down into its shadow. */
@Composable
fun PressableSticker(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Pill,
    fill: Color = Paper,
    shadow: Dp = 5.dp,
    enabled: Boolean = true,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed) shadow - 1.dp else 0.dp, label = "press")
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .offset(y = press)
            .sticker(shape, fill, shadow - press)
            .clip(shape)
            .clickable(source, ripple(color = Ink), enabled = enabled, role = role, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}

@Composable
fun StickerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Orange,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    PressableSticker(onClick, modifier.fillMaxWidth().height(62.dp), fill = color, enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)) {
            if (icon != null) {
                Icon(icon, null, tint = Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = TwType.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val tint = if (dark) Paper else Ink
    if (dark) {
        Box(
            modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize)) }
    } else {
        PressableSticker(onClick, modifier.size(size), shape = CircleShape, shadow = 3.dp) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/** Where in a three-step flow the screen stands: done dots, the current one long. */
@Composable
fun StepDots(current: Int, color: Color, modifier: Modifier = Modifier, total: Int = 3, dark: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            val w by animateDpAsState(if (i == current) 24.dp else 10.dp, label = "dot")
            val c by animateColorAsState(
                when {
                    i <= current -> color
                    dark -> Color.White.copy(alpha = 0.25f)
                    else -> Paper
                }, label = "dotColor")
            Box(
                Modifier.width(w).height(10.dp).clip(Pill).background(c)
                    .then(if (dark) Modifier else Modifier.border(2.dp, Ink, Pill))
            )
        }
    }
}

@Composable
fun TwTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = TwIcons.Back,
    backLabel: String,
    dark: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(backIcon, backLabel, onBack, dark = dark)
        Spacer(Modifier.width(14.dp))
        Text(title, style = TwType.bar, color = if (dark) Paper else Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A small outlined label: PHOTO, AUDIO. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, fill: Color = Paper) {
    Box(modifier.clip(Pill).background(fill).border(2.dp, Ink, Pill).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = TwType.chip)
    }
}

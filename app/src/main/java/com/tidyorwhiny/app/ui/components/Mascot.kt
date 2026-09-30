package com.tidyorwhiny.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import com.tidyorwhiny.app.ui.theme.Cheek
import com.tidyorwhiny.app.ui.theme.Coral
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Mint
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.Sun
import com.tidyorwhiny.app.ui.theme.Tear
import com.tidyorwhiny.app.ui.theme.Violet

enum class Gear { Lens, Headphones }

internal fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

/** Draws in a box of `unit` x `unit` whatever size the canvas is. */
internal inline fun DrawScope.inUnits(unit: Float, block: DrawScope.() -> Unit) {
    val s = size.minDimension / unit
    withTransform({ scale(s, s, Offset.Zero) }, block)
}

private fun DrawScope.line(d: String, w: Float, c: Color = Ink) =
    drawPath(svg(d), c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

/**
 * The Inspector: a sunny blob with a magnifying glass (photos) or headphones (voices).
 * It blinks; with bob it floats up and down.
 */
@Composable
fun Mascot(modifier: Modifier = Modifier, gear: Gear = Gear.Lens, bob: Boolean = false) {
    val t = rememberInfiniteTransition(label = "mascot")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 3800
        1f at 3500; 0.1f at 3620; 1f at 3760
    }), label = "blink")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bob")
    Canvas(
        modifier.aspectRatio(1f).graphicsLayer {
            if (bob) {
                translationY = -size.height * 0.06f * float
                rotationZ = -4f + 8f * float
            }
        }
    ) {
        inUnits(160f) {
            line("M78 30C76 16 90 10 98 16", 5f)
            drawRoundRect(Sun, Offset(18f, 28f), Size(124f, 120f), CornerRadius(58f))
            drawRoundRect(Ink, Offset(18f, 28f), Size(124f, 120f), CornerRadius(58f), style = Stroke(5f))
            drawCircle(Cheek.copy(alpha = 0.7f), 8f, Offset(44f, 106f))
            drawCircle(Cheek.copy(alpha = 0.7f), 8f, Offset(118f, 110f))
            eye(Offset(58f, 80f), 9f, blink)
            when (gear) {
                Gear.Lens -> {
                    drawCircle(Paper.copy(alpha = 0.6f), 25f, Offset(100f, 78f))
                    drawCircle(Ink, 25f, Offset(100f, 78f), style = Stroke(5f))
                    eye(Offset(100f, 80f), 13f, blink)
                    line("M118 96L138 118", 10f)
                }
                Gear.Headphones -> {
                    eye(Offset(102f, 80f), 9f, blink)
                    drawArc(Ink, 180f, 180f, false, Offset(10f, 14f), Size(140f, 140f),
                        style = Stroke(7f, cap = StrokeCap.Round))
                    for (x in listOf(2f, 138f)) {
                        drawRoundRect(Violet, Offset(x, 64f), Size(20f, 40f), CornerRadius(9f))
                        drawRoundRect(Ink, Offset(x, 64f), Size(20f, 40f), CornerRadius(9f), style = Stroke(4.5f))
                    }
                }
            }
            line("M62 112Q80 126 98 112", 5f)
        }
    }
}

private fun DrawScope.eye(c: Offset, r: Float, open: Float) {
    withTransform({ scale(1f, open, c) }) {
        drawCircle(Ink, r, c)
        drawCircle(Paper, r * 0.34f, Offset(c.x + r * 0.35f, c.y - r * 0.38f))
    }
}

enum class Face(val fill: Color) { Tidy(Mint), Mess(Orange), Whining(Coral), Begging(Sun), Champ(Mint) }

/** A verdict's face: round, bold, one mood each. */
@Composable
fun FaceBadge(face: Face, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        inUnits(140f) {
            val c = Offset(70f, 76f)
            drawCircle(Ink, 52f, c.copy(y = c.y + 5f))
            drawCircle(face.fill, 52f, c)
            drawCircle(Ink, 52f, c, style = Stroke(4.5f))
            when (face) {
                Face.Mess -> {
                    line("M44 58l14 14M58 58L44 72", 6f)
                    line("M82 58l14 14M96 58L82 72", 6f)
                    line("M44 98q6.5-8 13 0t13 0t13 0t13 0", 5.5f)
                    val drop = svg("M108 40q-8 11 0 15q8-4 0-15z")
                    drawPath(drop, Tear); drawPath(drop, Ink, style = Stroke(3f))
                }
                Face.Tidy, Face.Champ -> {
                    line("M42 66q9-12 18 0", 6f)
                    line("M80 66q9-12 18 0", 6f)
                    drawPath(svg("M44 84q26 30 52 0z"), Ink, style = Stroke(5f, join = StrokeJoin.Round))
                    drawPath(svg("M44 84q26 30 52 0z"), Ink)
                    drawPath(svg("M58 96q12 7 24 0q-12-6-24 0z"), Cheek)
                    drawCircle(Cheek.copy(alpha = 0.8f), 7f, Offset(36f, 84f))
                    drawCircle(Cheek.copy(alpha = 0.8f), 7f, Offset(104f, 84f))
                    if (face == Face.Champ) {
                        val crown = svg("M46 34L41 8L57 21L70 4L83 21L99 8L94 34Z")
                        drawPath(crown, Sun)
                        drawPath(crown, Ink, style = Stroke(4f, join = StrokeJoin.Round))
                    } else {
                        sparkle(Offset(120f, 26f), 11f)
                        sparkle(Offset(18f, 118f), 8f)
                        sparkle(Offset(24f, 28f), 6f)
                    }
                }
                Face.Whining -> {
                    line("M40 54l18-7", 5f)
                    line("M100 54l-18-7", 5f)
                    drawCircle(Ink, 6.5f, Offset(50f, 68f))
                    drawCircle(Ink, 6.5f, Offset(90f, 68f))
                    drawPath(svg("M48 106q22-24 44 0z"), Ink)
                    line("M48 106q22-24 44 0z", 4f)
                    val tear = svg("M94 78q-8 11 0 16q8-5 0-16z")
                    drawPath(tear, Tear); drawPath(tear, Ink, style = Stroke(3f))
                }
                Face.Begging -> {
                    line("M38 46l16 5", 5f)
                    line("M102 46l-16 5", 5f)
                    for (x in listOf(50f, 90f)) {
                        drawCircle(Paper, 15f, Offset(x, 70f))
                        drawCircle(Ink, 15f, Offset(x, 70f), style = Stroke(4f))
                        drawCircle(Ink, 10f, Offset(x + 1f, 73f))
                        drawCircle(Paper, 3.8f, Offset(x + 4.5f, 68.5f))
                        drawCircle(Paper, 1.8f, Offset(x - 2.5f, 77f))
                    }
                    drawCircle(Ink, 6.5f, Offset(70f, 104f))
                    drawCircle(Cheek.copy(alpha = 0.8f), 7f, Offset(32f, 92f))
                    drawCircle(Cheek.copy(alpha = 0.8f), 7f, Offset(108f, 92f))
                }
            }
        }
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, Sun)
    drawPath(p, Ink, style = Stroke(2.5f, join = StrokeJoin.Round))
}

/** A dashed ring turning around whatever sits in it. */
@Composable
fun SpinningRing(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "ring")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "turn")
    Canvas(modifier.aspectRatio(1f).graphicsLayer { rotationZ = turn }) {
        val w = size.minDimension * 0.035f
        drawCircle(color, size.minDimension / 2 - w, style = Stroke(w, cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.1f, w * 2.4f))))
    }
}

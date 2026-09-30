package com.tidyorwhiny.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tidyorwhiny.app.R

// Ground and ink
val Cream = Color(0xFFFFF7EC)
val Paper = Color(0xFFFFFFFF)
val Ink = Color(0xFF221C35)
val Ink2 = Color(0xFF5B5470)
val Line = Color(0xFFEDE3D3)
val Night = Color(0xFF16121F)
val NightSurface = Color(0xFF2A2338)

// Mess check: tangerine
val Orange = Color(0xFFFF7A45)
val OrangeDeep = Color(0xFFC2410C)
val OrangeTint = Color(0xFFFFE4D6)
val OrangeMark = Color(0xFFFFB38F)

// Whine check: periwinkle
val Violet = Color(0xFF8B7CFF)
val VioletDeep = Color(0xFF5B3FD9)
val VioletTint = Color(0xFFECE8FF)
val VioletMark = Color(0xFFC9BFFF)

// Verdicts
val Mint = Color(0xFF4FD1A1)
val MintTint = Color(0xFFD8F5E6)
val Sun = Color(0xFFFFC53D)
val SunTint = Color(0xFFFFF1CC)
val Coral = Color(0xFFFF7A6B)
val CoralTint = Color(0xFFFFE1DC)
val Cheek = Color(0xFFFF8FA3)
val Tear = Color(0xFF5AA9FF)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun rubik(w: Int) = Font(R.font.rubik, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(w: Int) = Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Display: Rubik, soft and chunky, with Cyrillic. Body: Nunito, rounded. */
val Display = FontFamily(rubik(500), rubik(600), rubik(700), rubik(800))
val Body = FontFamily(nunito(500), nunito(600), nunito(700), nunito(800))

object TwType {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp, color = Ink)
    val verdict = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp, color = Ink)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, color = Ink)
    val cardTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = Ink)
    val bar = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, color = Ink)
    val button = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp, color = Ink)
    val timer = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, color = Ink)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp, color = Ink2)
    val bodyStrong = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 23.sp, color = Ink)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, color = Ink2)
    val chip = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp, color = Ink)
}

@Composable
fun TwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper, background = Cream, onBackground = Ink,
            surface = Paper, onSurface = Ink, secondary = Orange, tertiary = Violet,
        ),
        content = content,
    )
}

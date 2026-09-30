package com.tidyorwhiny.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The app's stroke icons, on a 24 grid; tinted by Icon like any other. */
object TwIcons {
    val Back = icon("M15 5l-7 7 7 7")
    val Close = icon("M6 6l12 12", "M18 6L6 18")
    val Arrow = icon("M5 12h14", "M13 6l6 6-6 6")
    val Camera = icon(
        "M4 8.5A2.5 2.5 0 0 1 6.5 6h1.7l1.4-2h4.8l1.4 2h1.7A2.5 2.5 0 0 1 20 8.5v9A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5z",
        "M15.5 13a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0")
    val Mic = icon(
        "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z",
        "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21", "M8.5 21h7")
    val Sparkle = icon("M11 3.5l1.9 5.1 5.1 1.9-5.1 1.9L11 17.5l-1.9-5.1L4 10.5l5.1-1.9z", "M19 15.5v5", "M16.5 18h5")
    val Retry = icon("M4 12a8 8 0 0 1 13.7-5.7L20 8.5", "M20 4v4.5h-4.5", "M20 12a8 8 0 0 1-13.7 5.7L4 15.5", "M4 20v-4.5h4.5")
    val Gallery = icon(
        "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z",
        "M10.6 9a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0", "M20 15l-5-5L5 20")
    val Flash = icon("M13 3L5 14h6l-1 7 8-11h-6z")
    val FlashOff = icon("M13 3L5 14h6l-1 7 8-11h-6z", "M4 4l16 16")
    val Home = icon("M4 11l8-7 8 7v8.5a1.5 1.5 0 0 1-1.5 1.5H15v-6H9v6H5.5A1.5 1.5 0 0 1 4 19.5z")
    val Check = icon("M5 12.5l4.5 4.5L19 7.5", width = 3f)
    val Quote = icon("M5 17c0-4 1.5-7 5-8.5", "M14 17c0-4 1.5-7 5-8.5")

    private fun icon(vararg d: String, width: Float = 2.4f): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                d.forEach {
                    addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
                }
            }.build()
}

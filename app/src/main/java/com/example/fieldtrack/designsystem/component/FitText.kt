package com.example.fieldtrack.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * A figure that stays on one line, shrinking a little rather than breaking.
 *
 * For amounts in narrow tiles. With bigger text a figure like ₹3,800 no longer
 * fits a third of a phone's width, and Compose would break it as "₹3,80" over
 * "0" — a number that reads as a different number. This steps the size down a
 * tenth at a time, to no less than [minScale], and draws nothing until it fits,
 * so the broken version is never on screen even for a frame.
 */
@Composable
fun FtFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.6f,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var fits by remember(text, style) { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (fits) drawContent() },
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && scale > minScale) {
                scale = (scale * 0.9f).coerceAtLeast(minScale)
            } else {
                fits = true
            }
        },
    )
}

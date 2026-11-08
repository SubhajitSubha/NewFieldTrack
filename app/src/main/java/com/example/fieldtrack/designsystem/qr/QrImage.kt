package com.example.fieldtrack.designsystem.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A QR code on screen. Always black on white with the quiet margin the standard asks for,
 * whatever the theme: a scanner reads dark squares on light, and a dark theme's inverted code
 * is one many phones cannot read.
 */
@Composable
fun QrImage(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val code = remember(text) { QrCode.encode(text) }
    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val quiet = 4
        val cells = code.size + quiet * 2
        val cell = size.minDimension / cells
        drawRect(Color.White)
        for (y in 0 until code.size) for (x in 0 until code.size) {
            if (code.modules[y][x]) {
                drawRect(
                    Color.Black,
                    topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                    // A hair over a cell, so neighbouring squares meet without a seam.
                    size = Size(cell + 0.5f, cell + 0.5f),
                )
            }
        }
    }
}

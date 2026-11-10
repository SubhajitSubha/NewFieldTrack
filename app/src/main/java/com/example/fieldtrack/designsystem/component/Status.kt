package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * The states a job or a machine can be in.
 *
 * Each one differs from the others in lightness as well as hue, so the pills stay
 * distinguishable in direct sun and to colour-blind users. That is why Completed is
 * blue rather than a second green, and why Due and Breakdown — which never appear in
 * the same list — are allowed to share red.
 */
enum class FtStatus { Running, Paused, Completed, Due, Idle, Breakdown }

@Composable
fun FtStatusPill(
    status: FtStatus,
    label: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val colors = FieldTrackTheme.colors
    val container: Color
    val content: Color
    val dot: Color

    if (onDark) {
        container = colors.onDarkFaint
        content = colors.onDark
        dot = colors.liveDot
    } else when (status) {
        FtStatus.Running -> { container = colors.primaryWash; content = colors.primaryPressed; dot = colors.primary }
        FtStatus.Paused -> { container = colors.moneyWash; content = colors.moneyInk; dot = colors.money }
        FtStatus.Completed -> { container = colors.infoWash; content = colors.infoInk; dot = colors.info }
        FtStatus.Due -> { container = colors.dueWash; content = colors.dueInk; dot = colors.due }
        FtStatus.Breakdown -> { container = colors.dueWash; content = colors.dueInk; dot = colors.due }
        FtStatus.Idle -> { container = colors.surfaceMuted; content = colors.inkSecondary; dot = colors.inkTertiary }
    }

    Row(
        modifier = modifier
            .clip(FieldTrackTheme.shapes.chip)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            text = label,
            style = FieldTrackTheme.type.caption.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.W700
            ),
            color = content,
        )
    }
}

/**
 * The coloured stripe down the left edge of a job row. Carries the same meaning as the
 * pill, so a list scans by colour alone before any text is read.
 */
@Composable
fun FtStatus.stripeColor(): Color {
    val colors = FieldTrackTheme.colors
    return when (this) {
        FtStatus.Running -> colors.primary
        FtStatus.Paused -> colors.money
        FtStatus.Completed -> colors.info
        FtStatus.Due, FtStatus.Breakdown -> colors.due
        FtStatus.Idle -> colors.inkTertiary
    }
}

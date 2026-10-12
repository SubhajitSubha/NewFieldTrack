package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** How loud a button is, and what it means. */
enum class FtButtonTone {
    /** The one thing this screen is for. Solid green. */
    Primary,

    /** A real alternative, not a cancel. Outlined. */
    Secondary,

    /** Sends something outward to a customer — WhatsApp, SMS. Brighter green. */
    Send,

    /** Pause, hold, something reversible. Amber on a wash. */
    Caution,

    /** Breakdown, discard, remove. Red on a wash — never a solid red fill, which
     *  reads as the primary action and gets tapped by mistake. */
    Danger,

    /** Almost invisible. "Not now", "Skip". */
    Quiet,
}

@Composable
fun FtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: FtButtonTone = FtButtonTone.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = FieldTrackTheme.dimens.buttonHeight,
) {
    val colors = FieldTrackTheme.colors
    val shapes = FieldTrackTheme.shapes

    val container: Color
    val content: Color
    var border: BorderStroke? = null

    when (tone) {
        FtButtonTone.Primary -> { container = colors.primary; content = colors.onPrimary }
        FtButtonTone.Send -> { container = colors.send; content = colors.onPrimary }
        FtButtonTone.Caution -> { container = colors.moneyWash; content = colors.moneyInk }
        FtButtonTone.Danger -> { container = colors.dueWash; content = colors.dueInk }
        FtButtonTone.Quiet -> { container = Color.Transparent; content = colors.inkSecondary }
        FtButtonTone.Secondary -> {
            container = colors.surface
            content = colors.primary
            border = BorderStroke(FieldTrackTheme.dimens.border, colors.primary)
        }
    }

    val alpha = if (enabled) 1f else 0.4f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shapes.medium)
            .background(container.copy(alpha = container.alpha * alpha))
            .then(border?.let { Modifier.border(it, shapes.medium) } ?: Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null, // the label beside it already says this
                tint = content.copy(alpha = alpha),
                modifier = Modifier.size(FieldTrackTheme.dimens.icon),
            )
            FtGap(10.dp)
        }
        Text(
            text = text,
            style = if (height >= 58.dp) FieldTrackTheme.type.buttonLarge
            else FieldTrackTheme.type.button,
            color = content.copy(alpha = alpha),
        )
    }
}

/**
 * Icon-only button. [contentDescription] is required, not nullable: an unlabelled icon
 * button is invisible to anyone using TalkBack, and this app has users who rely on it.
 */
@Composable
fun FtIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FieldTrackTheme.colors.ink,
    background: Color = Color.Transparent,
    bordered: Boolean = false,
    shape: RoundedCornerShape = FieldTrackTheme.shapes.medium,
) {
    val dimens = FieldTrackTheme.dimens
    Box(
        modifier = modifier
            .defaultMinSize(dimens.minTouchTarget, dimens.minTouchTarget)
            .size(dimens.iconButtonSize)
            .clip(shape)
            .background(background)
            .then(
                if (bordered) Modifier.border(
                    dimens.hairline, FieldTrackTheme.colors.line, shape
                ) else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(dimens.icon),
        )
    }
}

/**
 * The large circular +/− used to adjust the billable quantity directly on the tracking
 * screen. Sized well past the touch minimum because it is pressed with gloves on.
 */
@Composable
fun FtStepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onDark: Boolean = false,
) {
    val colors = FieldTrackTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val container = when {
        filled && onDark -> colors.onDark
        filled -> colors.primary
        else -> Color.Transparent
    }
    val content = when {
        filled && onDark -> colors.primaryPressed
        filled -> colors.onPrimary
        onDark -> colors.onDark
        else -> colors.ink
    }

    Box(
        modifier = modifier
            .size(62.dp)
            .clip(shape)
            .background(container)
            .then(
                if (!filled) Modifier.border(
                    FieldTrackTheme.dimens.border,
                    if (onDark) colors.onDarkFaint else colors.line,
                    shape,
                ) else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
internal fun FtGap(size: Dp) {
    Box(Modifier.size(size))
}

/**
 * A small square action under the main buttons on a running job.
 *
 * Icon over a short word, because a driver glancing down with the engine running
 * recognises the shape first and reads the label only to confirm it. Sized to the
 * 44dp touch minimum even though the tile looks smaller than a real button — these
 * are tapped with a thumb, often with gloves on.
 */
@Composable
fun FtQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier
            .clip(FieldTrackTheme.shapes.medium)
            .background(colors.surface)
            .border(
                width = FieldTrackTheme.dimens.hairline,
                color = colors.line,
                shape = FieldTrackTheme.shapes.medium,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.inkSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = FieldTrackTheme.type.caption,
            color = colors.inkSecondary,
        )
    }
}

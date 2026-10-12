package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * A labelled text field.
 *
 * [onVoiceInput] adds a microphone beside the field. Offer it on anything a person has
 * to spell — names, villages, notes — because typing a Punjabi name on a phone keyboard
 * is the single most common place these users give up.
 */
@Composable
fun FtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Omit for a self-evident field such as search, where a label is just noise. */
    label: String? = null,
    leadingIcon: ImageVector? = null,
    placeholder: String = "",
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    suffix: String? = null,
    focused: Boolean = false,
    onVoiceInput: (() -> Unit)? = null,
    voiceContentDescription: String = "",
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val shape = FieldTrackTheme.shapes.medium

    Column(modifier) {
        if (label != null) FtSectionLabel(label, Modifier.padding(bottom = 9.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.fieldHeight)
                .clip(shape)
                .background(colors.surface)
                .border(
                    width = dimens.border,
                    color = if (focused) colors.primary else colors.line,
                    shape = shape,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = colors.inkTertiary,
                    modifier = Modifier.size(FieldTrackTheme.dimens.icon),
                )
            }
            if (prefix != null) {
                Text(prefix, style = FieldTrackTheme.type.body, color = colors.inkTertiary)
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = FieldTrackTheme.type.body, color = colors.inkTertiary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = FieldTrackTheme.type.body.copy(
                        color = colors.ink,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (suffix != null) {
                Text(suffix, style = FieldTrackTheme.type.bodySmall, color = colors.inkTertiary)
            }
            if (onVoiceInput != null) {
                FtIconButton(
                    icon = FieldTrackIcons.Mic,
                    contentDescription = voiceContentDescription,
                    onClick = onVoiceInput,
                    tint = colors.primary,
                    background = colors.primaryWash,
                    shape = FieldTrackTheme.shapes.small,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        if (helper != null) {
            Text(
                text = helper,
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 9.dp, start = 2.dp),
            )
        }
    }
}

/**
 * What a chip is saying about its own option.
 *
 * [Busy] and [Warning] still select — they report a state rather than forbidding a
 * choice, because the machine on screen may well have finished and nobody told the
 * app yet.
 */
enum class FtChipTone { Normal, Busy, Warning }

/** A pill-shaped single choice — area units, quick rate amounts, machines. */
@Composable
fun FtChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: FtChipTone = FtChipTone.Normal,
) {
    val colors = FieldTrackTheme.colors
    val shape = FieldTrackTheme.shapes.chip
    val resting = when (tone) {
        FtChipTone.Normal -> colors.surface
        FtChipTone.Busy -> colors.moneyWash
        FtChipTone.Warning -> colors.dueWash
    }
    val restingLine = when (tone) {
        FtChipTone.Normal -> colors.line
        FtChipTone.Busy -> colors.money
        FtChipTone.Warning -> colors.due
    }
    val restingInk = when (tone) {
        FtChipTone.Normal -> colors.ink
        FtChipTone.Busy -> colors.moneyInk
        FtChipTone.Warning -> colors.dueInk
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primaryWash else resting)
            .border(
                width = if (selected) FieldTrackTheme.dimens.border else FieldTrackTheme.dimens.hairline,
                color = if (selected) colors.primary else restingLine,
                shape = shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            style = FieldTrackTheme.type.label,
            color = if (selected) colors.primaryPressed else restingInk,
        )
    }
}

/**
 * A large tappable card used where the choice is consequential — the role fork, the
 * billing mode, the machine type. Big enough to carry an explanation, because these
 * are the choices people get wrong when a screen only shows a label.
 */
@Composable
fun FtChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color = FieldTrackTheme.colors.primary,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val shape = FieldTrackTheme.shapes.large

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(
                width = if (selected) dimens.borderSelected else dimens.border,
                color = if (selected) colors.primary else colors.line,
                shape = shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = if (description == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (icon != null) {
            FtIconTile(
                icon = icon,
                size = 56.dp,
                background = if (selected) colors.primaryWash else colors.surfaceMuted,
                tint = if (selected) iconTint else colors.inkSecondary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(FieldTrackTheme.shapes.chip)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FieldTrackIcons.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

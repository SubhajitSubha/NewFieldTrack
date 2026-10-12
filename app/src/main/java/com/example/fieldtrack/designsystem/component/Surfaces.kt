package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** The default container: white, hairline border, generous radius. No drop shadow. */
@Composable
fun FtCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = FieldTrackTheme.shapes.card,
    background: Color = FieldTrackTheme.colors.surface,
    borderColor: Color = FieldTrackTheme.colors.line,
    borderWidth: Dp = FieldTrackTheme.dimens.hairline,
    padding: Dp = FieldTrackTheme.dimens.cardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(padding),
        content = content,
    )
}

/**
 * Ink-coloured card used for the single most important number on a screen — today's
 * take, the total owed. Dark ground makes a figure read as a headline without needing
 * to be any larger.
 */
@Composable
fun FtEmphasisCard(
    modifier: Modifier = Modifier,
    background: Color = FieldTrackTheme.colors.ink,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = FtCard(
    modifier = modifier,
    shape = FieldTrackTheme.shapes.large,
    background = background,
    borderColor = Color.Transparent,
    borderWidth = 0.dp,
    padding = 18.dp,
    onClick = onClick,
    content = content,
)

/** Small, wide, uppercase heading that groups what follows. */
@Composable
fun FtSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FieldTrackTheme.colors.inkTertiary,
) {
    Text(
        text = text.uppercase(),
        style = FieldTrackTheme.type.overline,
        color = color,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}

/**
 * Initials in a circle. Photos are better and used where we have one, but most
 * customers are entered by a contractor who will never take their picture.
 */
@Composable
fun FtAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = FieldTrackTheme.dimens.avatar,
    background: Color = FieldTrackTheme.colors.surfaceMuted,
    contentColor: Color = FieldTrackTheme.colors.inkSecondary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            style = FieldTrackTheme.type.rowTitle.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.W800
            ),
            color = contentColor,
        )
    }
}

/** Square-ish tinted tile holding a machine or category glyph. */
@Composable
fun FtIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = FieldTrackTheme.dimens.avatar,
    background: Color = FieldTrackTheme.colors.surfaceMuted,
    tint: Color = FieldTrackTheme.colors.inkSecondary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(FieldTrackTheme.shapes.small)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // the row's title names the thing
            tint = tint,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/**
 * The workhorse row: something on the left, a title and subtitle, something on the
 * right. Used for customers, machines, jobs, settings and ledger lines.
 *
 * [statusStripe] paints the left edge so a list of jobs can be read by colour first.
 */
@Composable
fun FtListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showChevron: Boolean = false,
    statusStripe: Color? = null,
    onClick: (() -> Unit)? = null,
    /** Red for a row that destroys something, so the colour warns before the words. */
    titleColor: Color = FieldTrackTheme.colors.ink,
) {
    val dimens = FieldTrackTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .height(IntrinsicSize.Min)
            .padding(end = dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (statusStripe != null) {
            Box(
                Modifier
                    .width(dimens.statusStripe)
                    .fillMaxHeight()
                    .background(statusStripe)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimens.cardPadding, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = FieldTrackTheme.type.rowTitle,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = FieldTrackTheme.type.caption,
                        color = FieldTrackTheme.colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke(this)
            if (showChevron) {
                Icon(
                    imageVector = FieldTrackIcons.Forward,
                    contentDescription = null,
                    tint = FieldTrackTheme.colors.inkTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Hairline between rows, inset so it does not touch the card edge. */
@Composable
fun FtRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = FieldTrackTheme.dimens.cardPadding)
            .height(FieldTrackTheme.dimens.hairline)
            .background(FieldTrackTheme.colors.line)
    )
}


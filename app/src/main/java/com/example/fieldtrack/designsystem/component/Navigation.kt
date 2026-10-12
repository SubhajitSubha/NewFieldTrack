package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Screen header. A back affordance, a title, and at most one action — anything more and
 * the screen is doing too much.
 *
 * [subtitle] carries the context a farmer needs to trust what they are looking at:
 * which customer, which machine, which business.
 */
@Composable
fun FtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "",
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val dimens = FieldTrackTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) 12.dp else dimens.screenPadding,
                end = 12.dp,
                top = 16.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            FtIconButton(
                icon = FieldTrackIcons.Back,
                contentDescription = backContentDescription,
                onClick = onBack,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (subtitle == null) FieldTrackTheme.type.screenTitle
                else FieldTrackTheme.type.screenTitleCompact,
                color = FieldTrackTheme.colors.ink,
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
                )
            }
        }
        action?.invoke(this)
    }
}

/** One destination in the bottom bar. */
data class FtNavDestination(
    val icon: ImageVector,
    val label: String,
    val badge: Boolean = false,
)

/**
 * Bottom navigation. Four or five destinations, never more: a sixth is a sign the role
 * split is wrong rather than a sign we need a scrolling bar.
 */
@Composable
fun FtBottomBar(
    destinations: List<FtNavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(FieldTrackTheme.dimens.hairline)
                .background(colors.line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FieldTrackTheme.dimens.bottomNavHeight)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                val tint = if (selected) colors.primary else colors.inkTertiary
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null, // the label below names it
                            tint = tint,
                            modifier = Modifier.size(25.dp),
                        )
                        if (destination.badge) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.due)
                            )
                        }
                    }
                    Text(
                        text = destination.label,
                        style = FieldTrackTheme.type.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.W700 else FontWeight.W600,
                        ),
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

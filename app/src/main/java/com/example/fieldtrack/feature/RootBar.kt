package com.example.fieldtrack.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtTextField
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * The strip along the top of the four main screens, the same on every one of them and for
 * every role: your picture, which opens the menu with your profile and settings, the bell,
 * and the magnifier, which searches everything. The things people reach for from anywhere
 * sit where people have learned to look for them in other apps.
 *
 * Searching happens in the bar itself: the magnifier turns the whole width into the box,
 * with a back arrow to put the bar back, and the results cover the screen underneath.
 */
@Composable
fun RootBar(
    initials: String,
    menuContentDescription: String,
    searchContentDescription: String,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the phone was told and has not been shown yet: a dot on the bell when above nought. */
    unread: Int = 0,
    notificationsContentDescription: String = "",
    onNotifications: () -> Unit = {},
    /** True while the bar is the search box. */
    searching: Boolean = false,
    query: String = "",
    onQuery: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    val colors = FieldTrackTheme.colors
    if (searching) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = FieldTrackTheme.dimens.screenPadding - 4.dp)
                .padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FtIconButton(
                icon = FieldTrackIcons.Back,
                contentDescription = stringResource(R.string.search_close_cd),
                onClick = onCloseSearch,
            )
            FtTextField(
                value = query,
                onValueChange = onQuery,
                leadingIcon = FieldTrackIcons.Search,
                placeholder = stringResource(R.string.search_title),
                focused = true,
                takeFocus = true,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (query.isNotEmpty()) FtIconButton(
                icon = FieldTrackIcons.Close,
                contentDescription = stringResource(R.string.search_clear_cd),
                onClick = { onQuery("") },
            )
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = FieldTrackTheme.dimens.screenPadding - 4.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .defaultMinSize(48.dp, 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMenu)
                .semantics { contentDescription = menuContentDescription },
            contentAlignment = Alignment.Center,
        ) {
            FtAvatar(
                initials = initials,
                size = 38.dp,
                background = colors.primary,
                contentColor = colors.onPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                FtIconButton(
                    icon = FieldTrackIcons.Bell,
                    contentDescription = notificationsContentDescription,
                    onClick = onNotifications,
                )
                if (unread > 0) Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.due)
                )
            }
            FtIconButton(
                icon = FieldTrackIcons.Search,
                contentDescription = searchContentDescription,
                onClick = onSearch,
            )
        }
    }
}

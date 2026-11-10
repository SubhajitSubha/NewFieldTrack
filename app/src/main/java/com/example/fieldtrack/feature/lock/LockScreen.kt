package com.example.fieldtrack.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * What stands in front of the app until the phone's own lock has been passed.
 *
 * Opaque and full-window, so nothing behind it can be read or touched, and it says
 * nothing about the business: a locked screen that named the farm or showed a figure
 * would be giving away the very thing it is there to keep.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FieldTrackTheme.dimens.screenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FtIconTile(FieldTrackIcons.Lock, size = 64.dp)
        Text(
            text = stringResource(R.string.lock_title),
            style = FieldTrackTheme.type.screenTitle,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.lock_body),
            style = FieldTrackTheme.type.body,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        FtButton(
            text = stringResource(R.string.lock_unlock),
            onClick = onUnlock,
            modifier = Modifier.widthIn(min = 220.dp),
        )
    }
}

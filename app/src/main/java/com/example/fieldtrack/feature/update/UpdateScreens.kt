package com.example.fieldtrack.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** Opens this app's page in the store, or the web page for it on a phone with no store. */
fun openStorePage(context: Context) {
    val id = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
}

/**
 * Shown in place of the app while this version is too old to share data. It says why in
 * plain words, and that nothing on the phone is lost: the records are all still here, and
 * everything goes up once the app is updated.
 */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit, modifier: Modifier = Modifier) {
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
        FtIconTile(FieldTrackIcons.Sync, size = 64.dp)
        Text(
            text = stringResource(R.string.update_required_title),
            style = FieldTrackTheme.type.screenTitle,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.update_required_body),
            style = FieldTrackTheme.type.body,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        FtButton(
            text = stringResource(R.string.update_now),
            onClick = onUpdate,
            modifier = Modifier.widthIn(min = 220.dp),
        )
    }
}

/** A note above the app when a newer version is out. Put off, it comes back in a few days. */
@Composable
fun UpdateBar(onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.infoWash)
            .padding(horizontal = FieldTrackTheme.dimens.screenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.update_available),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.infoInk,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            FtButton(text = stringResource(R.string.update_later), onClick = onLater, tone = FtButtonTone.Quiet, height = 44.dp, modifier = Modifier.weight(1f))
            FtButton(text = stringResource(R.string.update_now), onClick = onUpdate, height = 44.dp, modifier = Modifier.weight(1f))
        }
    }
}

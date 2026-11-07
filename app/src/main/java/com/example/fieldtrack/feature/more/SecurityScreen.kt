package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.settings.LockAfter
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Whether the app asks for the phone's own lock before it opens, and after how long out
 * of sight. Both are this phone's own choice; neither is sent anywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityScreen(
    available: Boolean,
    enabled: Boolean,
    after: LockAfter,
    onEnabled: (Boolean) -> Unit,
    onAfter: (LockAfter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val on = enabled && available
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.security_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            FtCard(padding = 0.dp) {
                Row(
                    Modifier
                        .toggleable(value = on, enabled = available, role = Role.Switch, onValueChange = onEnabled)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.security_lock),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.security_lock_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = on,
                        // The whole row toggles; the switch only shows the state.
                        onCheckedChange = null,
                        enabled = available,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.surface,
                            uncheckedTrackColor = colors.line,
                        ),
                    )
                }
            }
            if (!available) {
                Text(
                    text = stringResource(R.string.security_no_lock),
                    style = FieldTrackTheme.type.caption,
                    color = colors.dueInk,
                )
            }
            if (on) {
                FtSectionLabel(stringResource(R.string.security_after), Modifier.padding(top = 8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        LockAfter.Right to R.string.security_after_right,
                        LockAfter.OneMinute to R.string.security_after_minute,
                        LockAfter.FiveMinutes to R.string.security_after_five,
                    ).forEach { (choice, label) ->
                        FtChip(label = stringResource(label), selected = after == choice, onClick = { onAfter(choice) })
                    }
                }
                Text(
                    text = stringResource(R.string.security_after_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

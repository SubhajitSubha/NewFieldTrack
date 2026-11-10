package com.example.fieldtrack.feature.orgs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.workLabel

/**
 * The businesses this person belongs to, opened from the business line at the top of the
 * menu: two taps from anywhere to be working for somebody else. The one they are in has a
 * tick; the others switch on a tap; joining or starting one is at the foot.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BusinessSheet(
    state: OrgSwitcherState,
    onSwitch: (String) -> Unit,
    onJoinAnother: () -> Unit,
    onStartNew: () -> Unit,
    onDismissRefusal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.biz_sheet_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            FtCard(padding = 0.dp) {
                listOfNotNull(state.current).plus(state.others).forEach { org ->
                    val checking = state.checking == org.orgId
                    val name = org.name.ifBlank { stringResource(R.string.biz_unnamed) }
                    FtListRow(
                        title = name,
                        subtitle = if (checking) stringResource(R.string.orgs_checking)
                        else roleWord(org.role),
                        leading = {
                            FtAvatar(
                                initials = initialsOf(name),
                                size = 42.dp,
                                background = if (org.isCurrent) colors.primary else colors.surfaceMuted,
                                contentColor = if (org.isCurrent) colors.onPrimary else colors.inkSecondary,
                            )
                        },
                        trailing = when {
                            org.isCurrent -> {
                                {
                                    Icon(
                                        FieldTrackIcons.Check,
                                        contentDescription = stringResource(R.string.biz_sheet_here),
                                        tint = colors.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            !org.active -> {
                                { FtStatusPill(status = FtStatus.Idle, label = stringResource(R.string.orgs_removed)) }
                            }
                            else -> null
                        },
                        // Only another business they still work for is something to tap.
                        onClick = if (!org.isCurrent && org.active && state.checking == null) {
                            { onSwitch(org.orgId) }
                        } else null,
                        titleColor = if (org.isCurrent) colors.primary else colors.ink,
                    )
                    FtRowDivider()
                }
                FtListRow(
                    title = stringResource(R.string.orgs_join),
                    subtitle = stringResource(R.string.orgs_join_note),
                    leading = { AddTile() },
                    onClick = onJoinAnother,
                )
                FtRowDivider()
                FtListRow(
                    title = stringResource(R.string.orgs_start_new),
                    subtitle = stringResource(R.string.orgs_start_new_note),
                    leading = { AddTile() },
                    onClick = onStartNew,
                )
            }
            // The one reassurance a driver needs before moving between employers.
            Text(
                text = stringResource(R.string.orgs_apart_hidden),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
    RefusedDialog(state, onDismissRefusal)
}

@Composable
private fun AddTile() {
    val colors = FieldTrackTheme.colors
    FtIconTile(
        icon = FieldTrackIcons.Add,
        size = 42.dp,
        background = colors.primaryWash,
        tint = colors.primaryPressed,
    )
}

@Composable
private fun roleWord(role: MemberRole): String = stringResource(
    when (role) {
        MemberRole.Owner -> R.string.more_owner
        MemberRole.Viewer -> R.string.more_viewer
        MemberRole.Operator -> R.string.more_operator
    }
)

@Composable
private fun RefusedDialog(state: OrgSwitcherState, onDismissRefusal: () -> Unit) {
    val colors = FieldTrackTheme.colors
    // Shown only after the server has said so, never on a guess. Somebody tapping
    // a business they believe they still work for deserves to be told why nothing
    // happened, rather than watching the row quietly turn into "Ended".
    state.refused?.let { raw ->
        val name = raw.ifBlank { stringResource(R.string.biz_unnamed) }
        AlertDialog(
            onDismissRequest = onDismissRefusal,
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.orgs_refused_title, name),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.orgs_refused_body),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissRefusal) {
                    Text(
                        text = stringResource(R.string.orgs_refused_ok),
                        style = FieldTrackTheme.type.label,
                        color = colors.ink,
                    )
                }
            },
        )
    }
}

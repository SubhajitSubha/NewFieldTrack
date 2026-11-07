package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.timeAgo
import com.example.fieldtrack.notify.Notice
import com.example.fieldtrack.notify.PushEvent

/** What the phone was told lately, newest first, so nothing is lost when a notification is swiped away. */
@Composable
fun NoticesScreen(
    notices: List<Notice>,
    /** Which of these had not been looked at before this screen opened. */
    unreadIds: Set<String>,
    onBack: () -> Unit,
    onOpen: (PushEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.notices_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (notices.isEmpty()) {
                Text(
                    text = stringResource(R.string.notices_empty),
                    style = FieldTrackTheme.type.body,
                    color = colors.inkSecondary,
                )
            } else {
                FtCard(padding = 0.dp) {
                    notices.forEachIndexed { index, notice ->
                        if (index > 0) FtRowDivider()
                        val event = PushEvent.entries.firstOrNull { it.key == notice.event }
                        val urgent = event == PushEvent.SosRaised || event == PushEvent.BreakdownReported
                        FtListRow(
                            title = if (event == null) stringResource(R.string.push_open)
                            else com.example.fieldtrack.notify.PushText.title(
                                androidx.compose.ui.platform.LocalContext.current, event, notice.what,
                            ),
                            subtitle = timeAgo(notice.at),
                            leading = {
                                FtIconTile(
                                    icon = iconOf(event),
                                    size = 40.dp,
                                    background = if (urgent) colors.dueWash else colors.surfaceMuted,
                                    tint = if (urgent) colors.due else colors.inkSecondary,
                                )
                            },
                            trailing = if (notice.id in unreadIds) {
                                {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colors.primary)
                                    )
                                }
                            } else null,
                            showChevron = event != null,
                            onClick = event?.let { { onOpen(it) } },
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
    }
}

private fun iconOf(event: PushEvent?) = when (event) {
    PushEvent.BreakdownReported, PushEvent.BreakdownFixed -> FieldTrackIcons.Breakdown
    PushEvent.SosRaised -> FieldTrackIcons.Call
    PushEvent.PaymentRecorded -> FieldTrackIcons.Banknote
    PushEvent.MemberJoined -> FieldTrackIcons.Team
    PushEvent.RequestNew -> FieldTrackIcons.Calendar
    PushEvent.BookingNew, PushEvent.BookingChanged, PushEvent.BookingCancelled -> FieldTrackIcons.Calendar
    PushEvent.JobHanded -> FieldTrackIcons.Swap
    null -> FieldTrackIcons.Bell
}

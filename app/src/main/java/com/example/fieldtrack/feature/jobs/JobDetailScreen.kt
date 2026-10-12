package com.example.fieldtrack.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.labelRes

/**
 * One job, and how it came to be worth what it is worth.
 *
 * The card at the top is the figure; the timeline underneath is the argument for it.
 * Every one of those lines has been recorded since the work loop was built — when it
 * started, who paused it, when the machine broke, what the rate was changed to and
 * why — and until now none of it was visible to the person who might be asked.
 */
@Composable
fun JobDetailScreen(
    state: JobDetailState,
    onBack: () -> Unit,
    onOpenLive: () -> Unit,
    onAmend: (() -> Unit)?,
    onOpenCustomer: () -> Unit,
    onCall: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = state.billNumber?.let { stringResource(R.string.finish_bill_no, it) }
                ?: stringResource(R.string.job_detail_title),
            subtitle = dayMonthYear(state.workedAtMillis).takeIf { state.workedAtMillis > 0 },
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        if (state.loaded && !state.exists) {
            FtCard(Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(R.string.job_detail_gone),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SummaryCard(state, onOpenCustomer)

            if (state.operatorName != null) {
                FtCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FtAvatar(
                            initials = initialsOf(state.operatorName),
                            size = 44.dp,
                            background = colors.primaryWash,
                            contentColor = colors.primaryPressed,
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 13.dp)
                        ) {
                            Text(
                                text = state.operatorName,
                                style = FieldTrackTheme.type.rowTitle,
                                color = colors.ink,
                            )
                            Text(
                                text = listOfNotNull(
                                    stringResource(R.string.job_detail_drove_it),
                                    state.operatorPhone?.let { "+91 $it" },
                                ).joinToString(" · "),
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkTertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (onCall != null) {
                            FtIconButton(
                                icon = FieldTrackIcons.Call,
                                contentDescription = stringResource(R.string.team_call),
                                onClick = onCall,
                                tint = colors.primary,
                                background = colors.primaryWash,
                            )
                        }
                    }
                }
            }

            FtSectionLabel(stringResource(R.string.job_detail_what_happened))
            FtCard(padding = 16.dp) {
                if (state.events.isEmpty()) {
                    Text(
                        text = stringResource(R.string.job_detail_no_events),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                state.events.forEachIndexed { index, event ->
                    TimelineRow(
                        event = event,
                        state = state,
                        last = index == state.events.lastIndex,
                    )
                }
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }

        if (state.live || (state.finished && onAmend != null)) {
            Row(
                Modifier.padding(dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (state.live) {
                    FtButton(
                        text = stringResource(R.string.job_detail_open),
                        onClick = onOpenLive,
                        height = 54.dp,
                    )
                } else if (onAmend != null) {
                    FtButton(
                        text = stringResource(R.string.amend_title),
                        onClick = onAmend,
                        tone = FtButtonTone.Secondary,
                        height = 54.dp,
                    )
                }
            }
        }
    }
}

/** The figure, and what it is made of. */
@Composable
private fun SummaryCard(state: JobDetailState, onOpenCustomer: () -> Unit) {
    val colors = FieldTrackTheme.colors
    val unitLabel = stringResource(state.unit.labelRes)

    FtCard(
        background = colors.primaryPressed,
        borderColor = colors.primaryPressed,
        padding = 18.dp,
        onClick = onOpenCustomer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtStatusPill(
                status = state.pillStatus(),
                label = stringResource(state.pillLabel()),
                onDark = true,
            )
            Box(Modifier.weight(1f))
            Text(
                text = duration(state.elapsedSeconds),
                style = FieldTrackTheme.type.numeric,
                color = colors.onDark,
            )
        }

        Column(Modifier.padding(top = 14.dp)) {
            Text(
                text = state.customerName,
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.onDark,
            )
            Text(
                text = listOfNotNull(state.workType, state.fieldLabel).joinToString(" · "),
                style = FieldTrackTheme.type.caption,
                color = colors.onDarkMuted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Box(
            Modifier
                .padding(vertical = 14.dp)
                .fillMaxWidth()
                .height(FieldTrackTheme.dimens.hairline)
                .background(colors.onDarkFaint)
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (state.canSeeMoney) {
                    Text(
                        text = when {
                            state.ratesVaried -> stringResource(R.string.finish_rate_varies)
                            state.billingMode == BillingMode.Fixed ->
                                stringResource(R.string.billing_fixed)
                            else -> stringResource(
                                R.string.live_rate_line,
                                quantity(state.quantity),
                                rupees(state.rateRupees),
                            )
                        },
                        style = FieldTrackTheme.type.caption,
                        color = colors.onDarkMuted,
                    )
                    Text(
                        text = rupees(state.amountRupees),
                        style = FieldTrackTheme.type.moneyLarge,
                        color = colors.onDark,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    // A driver who may not see money still needs the measure.
                    Text(
                        text = stringResource(
                            R.string.job_detail_measure,
                            quantity(state.quantity),
                            unitLabel,
                        ),
                        style = FieldTrackTheme.type.moneyLarge,
                        color = colors.onDark,
                    )
                }
            }
            state.machineName?.let {
                Text(
                    text = it,
                    style = FieldTrackTheme.type.caption,
                    color = colors.onDarkMuted,
                )
            }
        }
    }
}

/** One moment on the job, with the thread running down beside it. */
@Composable
private fun TimelineRow(event: JobEventRow, state: JobDetailState, last: Boolean) {
    val colors = FieldTrackTheme.colors
    Row {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(event.dotColour())
            )
            if (!last) {
                Box(
                    Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(colors.line)
                )
            }
        }
        Column(
            Modifier
                .padding(start = 11.dp)
                .padding(bottom = if (last) 0.dp else 16.dp)
        ) {
            Text(
                text = event.title(state),
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
            )
            Text(
                text = event.detail(),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Green for the shape of the day, amber for a hold, red for a stoppage. */
@Composable
private fun JobEventRow.dotColour(): Color = when (type) {
    JobEventType.Started, JobEventType.Completed, JobEventType.Resumed ->
        FieldTrackTheme.colors.primary
    JobEventType.Paused -> FieldTrackTheme.colors.money
    JobEventType.BreakdownReported -> FieldTrackTheme.colors.due
    else -> FieldTrackTheme.colors.inkSecondary
}

/** What happened, in the words a farmer would use for it. */
@Composable
private fun JobEventRow.title(state: JobDetailState): String {
    val unitLabel = stringResource(state.unit.labelRes)
    return when (type) {
        JobEventType.Started -> stringResource(R.string.event_started)
        JobEventType.Paused -> stringResource(R.string.event_paused)
        JobEventType.Resumed -> stringResource(R.string.event_resumed)
        JobEventType.Completed -> stringResource(R.string.event_completed)
        JobEventType.QuantitySet -> stringResource(
            R.string.event_quantity,
            quantity(numericValue ?: 0.0),
            unitLabel,
        )
        JobEventType.TripAdded -> stringResource(
            R.string.event_trip_added,
            rupees((numericValue ?: 0.0).toLong()),
        )
        JobEventType.TripRemoved -> stringResource(R.string.event_trip_removed)
        JobEventType.FuelAdded -> stringResource(
            R.string.event_fuel,
            quantity(numericValue ?: 0.0),
        )
        JobEventType.BreakdownReported -> stringResource(
            R.string.event_breakdown,
            textValue.orEmpty(),
        )
        JobEventType.NoteAdded -> textValue.orEmpty()
            .ifBlank { stringResource(R.string.work_note_title) }
        JobEventType.PhotoAdded -> stringResource(R.string.event_photo)
        JobEventType.HandedOver -> stringResource(
            R.string.event_handed_over,
            textValue.orEmpty(),
        )
        JobEventType.RateChanged -> stringResource(
            R.string.event_rate_changed,
            rupees((numericValue ?: 0.0).toLong()),
        )
    }
}

/** The time it happened, who did it, and how long a pause lasted. */
@Composable
private fun JobEventRow.detail(): String = listOfNotNull(
    clockTime(atMillis),
    // With seconds, because a short stop formatted as h:mm reads "0:00", which
    // looks like the pause never happened.
    pausedSeconds?.takeIf { it > 0 }?.let {
        stringResource(R.string.event_paused_for, duration(it, withSeconds = true))
    },
    byName,
    // A correction carries its reason; it is the whole point of recording one.
    textValue.takeIf { type == JobEventType.QuantitySet && !it.isNullOrBlank() },
).joinToString(" · ")

private fun JobDetailState.pillStatus(): FtStatus = when (status) {
    JobStatus.Running -> FtStatus.Running
    JobStatus.Paused -> FtStatus.Paused
    JobStatus.Completed -> FtStatus.Completed
    else -> FtStatus.Idle
}

private fun JobDetailState.pillLabel(): Int = when (status) {
    JobStatus.Running -> R.string.status_running
    JobStatus.Paused -> R.string.status_paused
    JobStatus.Completed -> R.string.job_detail_billed
    else -> R.string.job_detail_thrown_away
}

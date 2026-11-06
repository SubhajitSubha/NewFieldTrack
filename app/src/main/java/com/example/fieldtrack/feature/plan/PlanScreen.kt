package com.example.fieldtrack.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.layout.AdaptiveBody
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.designsystem.layout.LocalWidthClass
import com.example.fieldtrack.designsystem.layout.WidthClass
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import java.text.DateFormat
import java.util.Date

/**
 * The plan for a day: what is booked, on which machine, with which driver, and what
 * is free.
 *
 * Day by day, not a calendar, because the question a contractor asks is "what is
 * tomorrow", and a month grid has no room to answer it. What was promised for an
 * earlier day and never started is held at the top until somebody decides, so work a
 * farmer is still waiting for cannot scroll off the end of a list.
 */
@Composable
fun PlanScreen(
    state: PlanState,
    onBack: () -> Unit,
    onDay: (Long) -> Unit,
    onStep: (Int) -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = FieldTrackTheme.dimens

    AdaptiveScreen(modifier) {
        FtTopBar(
            title = stringResource(R.string.plan_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (!state.loaded) return@AdaptiveScreen

        // Side by side, the button and the notice move into the right-hand pane. On a
        // phone on its side a pinned button takes a fifth of the height from the day's
        // list, and that pane has the room.
        val twoPane = LocalWidthClass.current == WidthClass.Expanded

        AdaptiveBody(
            primary = {
                if (!twoPane && state.stale) StaleNotice(state.updatedAt)
                DayHeader(state, onDay, onStep)
                if (state.overdue.isNotEmpty()) Overdue(state, onOpen, onStart)
                Day(state, onOpen, onStart)
            },
            secondary = {
                if (twoPane) {
                    if (state.canPlan) PlanNewButton(onNew)
                    if (state.stale) StaleNotice(state.updatedAt)
                }
                Free(state)
            },
        )

        if (state.canPlan && !twoPane) {
            Box(Modifier.padding(dimens.screenPadding)) { PlanNewButton(onNew) }
        }
    }
}

@Composable
private fun PlanNewButton(onNew: () -> Unit) {
    FtButton(
        text = stringResource(R.string.plan_new),
        onClick = onNew,
        icon = FieldTrackIcons.Add,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeader(state: PlanState, onDay: (Long) -> Unit, onStep: (Int) -> Unit) {
    val colors = FieldTrackTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }

    FtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconButton(
                icon = FieldTrackIcons.Back,
                contentDescription = stringResource(R.string.plan_day_before_cd),
                onClick = { onStep(-1) },
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val name = dayName(state.day, state.today)
                Text(
                    text = name,
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                // The date under a name like "Tomorrow"; a day already named by its date says it once.
                if (name != dayDate(state.day)) {
                    Text(
                        text = dayDate(state.day),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            FtIconButton(
                icon = FieldTrackIcons.Forward,
                contentDescription = stringResource(R.string.plan_day_after_cd),
                onClick = { onStep(1) },
            )
        }
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FtChip(
                label = stringResource(R.string.plan_today),
                selected = state.day == state.today,
                onClick = { onDay(state.today) },
            )
            FtChip(
                label = stringResource(R.string.plan_tomorrow),
                selected = state.day == state.today + 1,
                onClick = { onDay(state.today + 1) },
            )
            FtChip(
                label = stringResource(R.string.plan_pick_day),
                selected = false,
                onClick = { picking = true },
            )
        }
    }

    if (picking) {
        PlanDayPicker(
            day = state.day,
            notBefore = null,
            onDismiss = { picking = false },
            onPicked = { picking = false; onDay(it) },
        )
    }
}

@Composable
private fun Overdue(state: PlanState, onOpen: (String) -> Unit, onStart: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    FtSectionLabel(stringResource(R.string.plan_needs_decision), color = colors.dueInk)
    Text(
        text = stringResource(
            if (state.canPlan) R.string.plan_needs_decision_note else R.string.plan_needs_decision_note_driver
        ),
        style = FieldTrackTheme.type.caption,
        color = colors.inkSecondary,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
    BookingCard(state.overdue, state, onOpen, onStart)
}

@Composable
private fun Day(state: PlanState, onOpen: (String) -> Unit, onStart: (String) -> Unit) {
    if (state.items.isEmpty()) {
        FtCard(background = FieldTrackTheme.colors.surfaceMuted, borderColor = FieldTrackTheme.colors.line) {
            Text(
                text = stringResource(
                    if (state.canPlan) R.string.plan_nothing_day else R.string.plan_nothing_day_driver
                ),
                style = FieldTrackTheme.type.bodySmall,
                color = FieldTrackTheme.colors.inkSecondary,
            )
        }
        return
    }
    BookingCard(state.items, state, onOpen, onStart)
}

@Composable
private fun BookingCard(
    items: List<PlanItem>,
    state: PlanState,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
) {
    FtCard(padding = 0.dp) {
        items.forEachIndexed { index, item ->
            if (index > 0) FtRowDivider()
            BookingItem(item, state, onOpen, onStart)
        }
    }
}

@Composable
private fun BookingItem(item: PlanItem, state: PlanState, onOpen: (String) -> Unit, onStart: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    val booking = item.row.booking
    val (status, label) = statusOf(item, state.today)

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (state.canPlan) Modifier.clickable(role = Role.Button) { onOpen(item.id) } else Modifier
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = item.customerName.ifBlank { stringResource(R.string.plan_customer_gone) },
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            FtStatusPill(status, label)
        }
        Text(
            text = buildString {
                append(workName(booking.workType))
                item.fieldName?.let { append(" · ").append(it) }
            },
            style = FieldTrackTheme.type.caption,
            color = colors.inkSecondary,
        )
        // When, and with what: the line a driver reads to know whether it is theirs.
        Text(
            text = listOfNotNull(
                dayName(booking.day, state.today).takeIf { booking.day != state.day },
                partName(booking.part),
                booking.startMinute?.let { stringResource(R.string.plan_at_time, timeOfDay(it)) },
                item.machineName ?: stringResource(R.string.plan_no_machine),
                item.driverName ?: stringResource(R.string.plan_anyone),
            ).joinToString(" · "),
            style = FieldTrackTheme.type.caption,
            color = colors.inkTertiary,
        )
        booking.note?.let {
            Text(text = it, style = FieldTrackTheme.type.caption, color = colors.inkSecondary)
        }
        item.flags.forEach { flag ->
            Text(
                text = stringResource(flagText(flag)),
                style = FieldTrackTheme.type.caption,
                color = colors.dueInk,
            )
        }
        if (item.canStart) {
            FtButton(
                text = stringResource(R.string.plan_start),
                onClick = { onStart(item.id) },
                icon = FieldTrackIcons.Play,
                height = 46.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun statusOf(item: PlanItem, today: Long): Pair<FtStatus, String> {
    val booking = item.row.booking
    return when {
        booking.status == BookingStatus.Cancelled -> FtStatus.Idle to stringResource(R.string.plan_cancelled)
        booking.status == BookingStatus.Done || item.row.jobStatus == JobStatus.Completed ->
            FtStatus.Completed to stringResource(R.string.plan_done)
        item.row.jobStatus == JobStatus.Running -> FtStatus.Running to stringResource(R.string.plan_started)
        item.row.jobStatus == JobStatus.Paused -> FtStatus.Paused to stringResource(R.string.plan_paused)
        booking.day < today -> FtStatus.Due to stringResource(R.string.plan_missed)
        else -> FtStatus.Idle to stringResource(R.string.plan_planned)
    }
}

internal fun flagText(flag: PlanDay.Flag): Int = when (flag) {
    PlanDay.Flag.MachineDown -> R.string.plan_flag_machine_down
    PlanDay.Flag.MachineGone -> R.string.plan_flag_machine_gone
    PlanDay.Flag.MachineBooked -> R.string.plan_flag_machine_booked
    PlanDay.Flag.DriverGone -> R.string.plan_flag_driver_gone
    PlanDay.Flag.DriverBooked -> R.string.plan_flag_driver_booked
}

/** Who and what has nothing booked that day: the answer to "can I take one more job?". */
@Composable
private fun Free(state: PlanState) {
    if (state.freeMachines.isEmpty() && state.freeDrivers.isEmpty()) return
    val colors = FieldTrackTheme.colors
    FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
        FtSectionLabel(stringResource(R.string.plan_free_title))
        if (state.freeMachines.isNotEmpty()) {
            Text(
                text = stringResource(R.string.plan_free_machines, state.freeMachines.joinToString(", ")),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.ink,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.freeDrivers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.plan_free_drivers, state.freeDrivers.joinToString(", ")),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Said when the phone may be behind. An empty plan is only "nothing planned" if this
 * phone has lately been able to hear: out of signal it is just a plan nobody could send.
 */
@Composable
private fun StaleNotice(updatedAt: Long?) {
    val colors = FieldTrackTheme.colors
    // Amber, not red: being out of signal is the ordinary state in a field, and a
    // red card would read as something broken.
    FtCard(background = colors.moneyWash, borderColor = colors.money) {
        Text(
            text = if (updatedAt == null) {
                stringResource(R.string.plan_stale_never)
            } else {
                stringResource(
                    R.string.plan_stale_since,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updatedAt)),
                )
            },
            style = FieldTrackTheme.type.bodySmall,
            color = colors.moneyInk,
        )
    }
}

/**
 * A day, from the calendar.
 *
 * [notBefore] keeps a booking from being made for a day that has gone, which would sit
 * in the list as missed from the moment it was written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDayPicker(
    day: Long,
    notBefore: Long?,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    // The picker deals in the midnight (UTC) that starts the date, which is the
    // epoch-day count times a day: the same number this app stores, so no zone is
    // involved and nothing can slip a day.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = day * DAY_MILLIS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                notBefore == null || Math.floorDiv(utcTimeMillis, DAY_MILLIS) >= notBefore
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(Math.floorDiv(it, DAY_MILLIS)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.finish_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * The plan in a line on a home screen: what is on today and tomorrow, and what was
 * promised and not started. One tap goes to the plan.
 */
@Composable
fun PlanCard(plan: PlanSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (!plan.loaded) return
    val colors = FieldTrackTheme.colors
    FtCard(modifier = modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            FtIconTile(
                icon = FieldTrackIcons.Calendar,
                size = 44.dp,
                background = colors.primaryWash,
                tint = colors.primaryPressed,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plan_title),
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                Text(
                    text = when {
                        plan.today > 0 || plan.tomorrow > 0 ->
                            stringResource(R.string.plan_card_counts, plan.today, plan.tomorrow)
                        plan.canPlan -> stringResource(R.string.plan_card_empty)
                        else -> stringResource(R.string.plan_card_empty_driver)
                    },
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (plan.missed > 0) {
                    Text(
                        text = stringResource(R.string.plan_card_missed, plan.missed),
                        style = FieldTrackTheme.type.caption,
                        color = colors.dueInk,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                imageVector = FieldTrackIcons.Forward,
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

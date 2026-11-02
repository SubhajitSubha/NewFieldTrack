package com.example.fieldtrack.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.repository.ClockCheck
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.dayMonth
import java.util.Calendar

/**
 * "The clock has run since yesterday morning. Is that right?"
 *
 * Asked, never assumed: a harvester crew in March really does work fourteen
 * hours, and only the person holding the phone knows whether this one did. So
 * there are two honest answers and both are one tap — stop it where the work
 * stopped, or say it is right and not be asked again about this stretch.
 *
 * The best guess at where work stopped is the last thing anybody recorded on it:
 * an acre counted, a trip added, fuel put in. Offered first, because it is a
 * fact from the job itself rather than a time somebody has to remember.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockCheckSheet(
    state: LiveWorkState,
    now: Long,
    onStopAt: (Long) -> Unit,
    onStillWorking: () -> Unit,
    onDismiss: () -> Unit,
) {
    val since = state.runningSince ?: return
    val colors = FieldTrackTheme.colors
    val lastEntry = state.lastEntryAt

    // The other-time answer: which day, and minutes past that day's midnight.
    val defaultStop = remember(since, now) { defaultStop(since, now) }
    var useLastEntry by rememberSaveable { mutableStateOf(lastEntry != null) }
    var dayStart by rememberSaveable { mutableStateOf(ClockCheck.midnightOf(defaultStop)) }
    var minutes by rememberSaveable {
        mutableStateOf(((defaultStop - ClockCheck.midnightOf(defaultStop)) / 60_000).toInt())
    }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    val chosen = if (useLastEntry && lastEntry != null) lastEntry else dayStart + minutes * 60_000L
    val valid = chosen in since..now
    val clockWouldRead = state.bankedSeconds + ((chosen - since).coerceAtLeast(0L) / 1000)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.clock_check_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(
                    R.string.clock_check_body,
                    moment(since, now),
                    com.example.fieldtrack.feature.spokenDuration((now - since) / 1000),
                ),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
            )

            Text(
                text = stringResource(R.string.clock_check_when).uppercase(),
                style = FieldTrackTheme.type.overline,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (lastEntry != null) {
                FtChoiceCard(
                    title = stringResource(R.string.clock_check_last_entry, moment(lastEntry, now)),
                    description = stringResource(R.string.clock_check_last_entry_note),
                    selected = useLastEntry,
                    onClick = { useLastEntry = true },
                    icon = FieldTrackIcons.Check,
                )
            }
            FtChoiceCard(
                title = stringResource(
                    R.string.clock_check_other_time,
                    moment(dayStart + minutes * 60_000L, now),
                ),
                description = stringResource(R.string.clock_check_other_time_note),
                selected = !useLastEntry || lastEntry == null,
                onClick = { useLastEntry = false },
                icon = FieldTrackIcons.Clock,
            )
            if (!useLastEntry || lastEntry == null) {
                // Every day the stretch touched, newest last. Three at most: a clock
                // forgotten for longer than that is a job somebody should look at
                // whole, and the time can still be typed against its first day.
                val days = remember(since, now) { daysBetween(since, now).takeLast(3) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { day ->
                        FtChip(
                            label = dayLabel(day, now),
                            selected = day == dayStart,
                            onClick = { dayStart = day },
                        )
                    }
                }
                FtButton(
                    text = stringResource(R.string.clock_check_pick_time, clockTime(dayStart + minutes * 60_000L)),
                    onClick = { pickingTime = true },
                    tone = FtButtonTone.Secondary,
                    icon = FieldTrackIcons.Clock,
                    height = FieldTrackTheme.dimens.buttonHeightCompact,
                )
            }

            Text(
                text = if (valid) {
                    stringResource(R.string.clock_check_would_read, duration(clockWouldRead))
                } else {
                    stringResource(R.string.clock_check_out_of_range, moment(since, now))
                },
                style = FieldTrackTheme.type.bodySmall,
                color = if (valid) colors.inkSecondary else colors.dueInk,
                modifier = Modifier.padding(top = 2.dp),
            )

            FtButton(
                text = stringResource(R.string.clock_check_stop, clockTime(chosen)),
                onClick = { onStopAt(chosen) },
                enabled = valid,
                icon = FieldTrackIcons.Pause,
                modifier = Modifier.padding(top = 4.dp),
            )
            FtButton(
                text = stringResource(R.string.clock_check_still_working),
                onClick = onStillWorking,
                tone = FtButtonTone.Secondary,
            )
        }
    }

    if (pickingTime) {
        ClockSheet(
            minutes = minutes,
            onDismiss = { pickingTime = false },
            onPicked = { picked ->
                minutes = picked
                pickingTime = false
            },
        )
    }
}

/** "8:02 AM" today, "8:02 AM yesterday", or "8:02 AM, 31 Oct". */
@Composable
private fun moment(millis: Long, now: Long): String {
    val today = ClockCheck.midnightOf(now)
    return when (ClockCheck.midnightOf(millis)) {
        today -> clockTime(millis)
        ClockCheck.midnightOf(today - 1) -> stringResource(R.string.clock_moment_yesterday, clockTime(millis))
        else -> stringResource(R.string.event_clock_day_time, dayMonth(millis), clockTime(millis))
    }
}

/** A day chip: "Today", "Yesterday", or the date. */
@Composable
private fun dayLabel(dayStart: Long, now: Long): String {
    val today = ClockCheck.midnightOf(now)
    return when (dayStart) {
        today -> stringResource(R.string.clock_day_today)
        ClockCheck.midnightOf(today - 1) -> stringResource(R.string.clock_day_yesterday)
        else -> dayMonth(dayStart)
    }
}

/** Local midnights from the day [since] falls in through the day [now] falls in. */
private fun daysBetween(since: Long, now: Long): List<Long> {
    val last = ClockCheck.midnightOf(now)
    val days = mutableListOf<Long>()
    val cursor = Calendar.getInstance().apply { timeInMillis = ClockCheck.midnightOf(since) }
    while (cursor.timeInMillis <= last && days.size < 62) {
        days += cursor.timeInMillis
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}

/**
 * Six in the evening of the day the clock started, the end of an ordinary
 * working day — held inside the stretch, so a clock started at night offers a
 * time after it started rather than one before.
 */
private fun defaultStop(since: Long, now: Long): Long {
    val evening = ClockCheck.midnightOf(since) + 18 * 60 * 60 * 1000L
    val proposed = if (evening > since) evening else since + 60 * 60 * 1000L
    // Up to the next whole minute, so the time shown on the picker is never a
    // few seconds before the clock started and refused for it.
    val held = proposed.coerceIn(since, now)
    return ((held + 59_999) / 60_000) * 60_000
}

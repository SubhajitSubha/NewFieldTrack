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
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.notify.PushKind
import com.example.fieldtrack.notify.ReminderKind
import com.example.fieldtrack.notify.ReminderSettings
import java.util.Locale

/** The hour on a clock face the way this phone writes it: "7 pm" or "19:00". */
private fun hourLabel(hour: Int): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(
        java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, 0) }.time
    )

/**
 * What the phone tells you without the app being open: news the moment it happens, and
 * reminders at an hour of your choosing. Which reminders this phone gives, and when. A person is only offered what they could
 * act on: the plan for everybody, the money to chase for those who see money, and
 * machine services for those who look after machines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersScreen(
    on: Map<ReminderKind, Boolean>,
    eveningHour: Int,
    morningHour: Int,
    canDues: Boolean,
    canService: Boolean,
    /** Reminders are on but the phone is not letting the app show anything. */
    blocked: Boolean,
    onToggle: (ReminderKind, Boolean) -> Unit,
    push: Map<PushKind, Boolean>,
    canPayments: Boolean,
    canRequests: Boolean = false,
    onPush: (PushKind, Boolean) -> Unit,
    paperDays: Int,
    onPaperDays: (Int) -> Unit,
    onEvening: (Int) -> Unit,
    onMorning: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val kinds = buildList {
        add(ReminderKind.Plan to (R.string.remind_plan_row to R.string.remind_plan_note))
        if (canDues) add(ReminderKind.Dues to (R.string.remind_dues_row to R.string.remind_dues_note))
        if (canService) add(ReminderKind.Service to (R.string.remind_service_row to R.string.remind_service_note))
        if (canService) add(ReminderKind.Papers to (R.string.remind_papers_row to R.string.remind_papers_note))
    }
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.remind_title),
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
            Text(
                text = stringResource(R.string.remind_intro),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
            )
            FtCard(padding = 0.dp) {
                Column {
                    kinds.forEachIndexed { index, (kind, texts) ->
                        if (index > 0) FtRowDivider()
                        SwitchRow(texts.first, texts.second, on[kind] == true) { onToggle(kind, it) }
                    }
                }
            }
            if (blocked) {
                Text(
                    text = stringResource(R.string.remind_blocked),
                    style = FieldTrackTheme.type.caption,
                    color = colors.dueInk,
                )
            }
            FtSectionLabel(stringResource(R.string.push_section), Modifier.padding(top = 8.dp))
            FtCard(padding = 0.dp) {
                Column {
                    val rows = buildList {
                        add(PushKind.Booking to (R.string.push_booking_row to R.string.push_booking_note))
                        add(PushKind.Job to (R.string.push_job_row to R.string.push_job_note))
                        if (canPayments) add(PushKind.Payment to (R.string.push_payment_row to R.string.push_payment_note))
                        if (canRequests) add(PushKind.Request to (R.string.push_request_row to R.string.push_request_note))
                    }
                    rows.forEachIndexed { index, (kind, texts) ->
                        if (index > 0) FtRowDivider()
                        SwitchRow(texts.first, texts.second, push[kind] != false) { onPush(kind, it) }
                    }
                }
            }
            Text(
                text = stringResource(R.string.push_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
            if (on[ReminderKind.Plan] == true) {
                FtSectionLabel(stringResource(R.string.remind_evening), Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderSettings.EVENING_HOURS.forEach { hour ->
                        FtChip(label = hourLabel(hour), selected = eveningHour == hour, onClick = { onEvening(hour) })
                    }
                }
            }
            if (on[ReminderKind.Papers] == true) {
                FtSectionLabel(stringResource(R.string.remind_papers_window), Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.fieldtrack.data.costing.Papers.WARN_CHOICES.forEach { days ->
                        FtChip(label = stringResource(R.string.remind_papers_days, days), selected = paperDays == days, onClick = { onPaperDays(days) })
                    }
                }
            }
            if (on[ReminderKind.Dues] == true || on[ReminderKind.Service] == true || on[ReminderKind.Papers] == true) {
                FtSectionLabel(stringResource(R.string.remind_morning), Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderSettings.MORNING_HOURS.forEach { hour ->
                        FtChip(label = hourLabel(hour), selected = morningHour == hour, onClick = { onMorning(hour) })
                    }
                }
            }
            Text(
                text = stringResource(R.string.remind_privacy),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

@Composable
private fun SwitchRow(title: Int, note: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = FieldTrackTheme.type.rowTitle, color = colors.ink)
            Text(
                stringResource(note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            // The whole row toggles; the switch only shows the state.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.line,
            ),
        )
    }
}

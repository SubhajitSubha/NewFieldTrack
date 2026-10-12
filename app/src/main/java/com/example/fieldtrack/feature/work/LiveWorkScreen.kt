package com.example.fieldtrack.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldtrack.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtMetricChip
import com.example.fieldtrack.designsystem.component.FtQuantityStepper
import com.example.fieldtrack.designsystem.component.FtQuickAction
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.labelRes

/**
 * Work in progress.
 *
 * The hero number is whatever the job is billed by — acres, trips, hours — never the
 * clock unless time is what is being charged for. A driver adjusting the figure they
 * get paid for should not have to hunt for a smaller control further down the screen.
 */
@Composable
fun LiveWorkScreen(
    state: LiveWorkState,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddTrip: () -> Unit,
    onUndoTrip: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onNote: (String) -> Unit,
    onRate: (Long) -> Unit,
    onFuel: (Double, Long) -> Unit,
    onBreakdown: (BreakdownCategory, String?) -> Unit,
    onHandOver: (String) -> Unit,
    onDiscard: () -> Unit,
    onAmount: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which extra panel is open. Local to the screen because it is where the driver
    // is looking, not something the job itself knows about.
    var sheet by rememberSaveable { mutableStateOf(WorkSheet.None) }
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val unitLabel = stringResource(state.unit.labelRes)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.primaryPressed)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.customerName,
                    style = FieldTrackTheme.type.screenTitleCompact,
                    color = colors.onDark,
                )
                Text(
                    text = listOfNotNull(state.workType, state.machineName).joinToString(" · "),
                    style = FieldTrackTheme.type.caption,
                    color = colors.onDarkMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            FtStatusPill(
                status = if (state.running) FtStatus.Running else FtStatus.Paused,
                label = stringResource(
                    if (state.running) R.string.status_running else R.string.status_paused
                ),
                onDark = true,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Without fillMaxWidth this column shrinks to its widest child and
                // the root column parks it on the left, so everything "centred"
                // inside it lands off-centre. It only looked right in area mode,
                // where the stepper happens to fill the width on its own.
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.billingMode) {
                BillingMode.PerTrip -> TripHero(state)
                BillingMode.PerHour -> HoursHero(state)
                BillingMode.Fixed -> FixedHero(state)
                else -> MeasuredHero(
                    state = state,
                    unitLabel = unitLabel,
                    onIncrease = onIncrease,
                    onDecrease = onDecrease,
                    onTypeAmount = { sheet = WorkSheet.Amount },
                )
            }

            Box(Modifier.padding(top = 18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (state.billingMode != BillingMode.PerHour) {
                        FtMetricChip(
                            label = duration(state.elapsedSeconds, withSeconds = true),
                            icon = FieldTrackIcons.Clock,
                        )
                    }
                    state.startedAt?.let {
                        FtMetricChip(stringResource(R.string.live_started_at, clockTime(it)))
                    }
                }
            }
        }

        Column(
            Modifier
                .clip(FieldTrackTheme.shapes.sheet)
                .background(colors.paper)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.billingMode == BillingMode.PerTrip) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FtButton(
                        text = stringResource(R.string.live_add_one_trip),
                        onClick = onAddTrip,
                        modifier = Modifier.weight(2.4f),
                        icon = FieldTrackIcons.Add,
                        height = dimens.buttonHeightHero,
                    )
                    FtButton(
                        text = stringResource(R.string.work_undo_trip_cd),
                        onClick = onUndoTrip,
                        modifier = Modifier.weight(1f),
                        tone = FtButtonTone.Secondary,
                        height = dimens.buttonHeightHero,
                    )
                }
            }
            // The occasional things, one tap away but out of the way of the two
            // buttons that matter with the engine running.
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (state.hasMachine) {
                    FtQuickAction(
                        icon = FieldTrackIcons.Fuel,
                        label = stringResource(R.string.work_fuel_title),
                        onClick = { sheet = WorkSheet.Fuel },
                        modifier = Modifier.weight(1f),
                    )
                }
                FtQuickAction(
                    icon = FieldTrackIcons.Edit,
                    label = stringResource(R.string.work_note_short),
                    onClick = { sheet = WorkSheet.Note },
                    modifier = Modifier.weight(1f),
                )
                FtQuickAction(
                    icon = FieldTrackIcons.More,
                    label = stringResource(R.string.work_more),
                    contentDescription = stringResource(R.string.work_more_cd),
                    onClick = { sheet = WorkSheet.Menu },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                FtButton(
                    text = stringResource(
                        // "Start work" belongs on the home screen. A job that is
                        // paused mid-field is carried on, not started.
                        if (state.running) R.string.work_pause else R.string.work_resume
                    ),
                    onClick = if (state.running) onPause else onResume,
                    modifier = Modifier.weight(1f),
                    tone = FtButtonTone.Caution,
                    icon = if (state.running) FieldTrackIcons.Pause else FieldTrackIcons.Play,
                    height = 60.dp,
                )
                FtButton(
                    text = stringResource(R.string.work_finish),
                    onClick = onFinish,
                    modifier = Modifier.weight(1.3f),
                    icon = FieldTrackIcons.Check,
                    height = 60.dp,
                )
            }
        }
    }

    WorkSheets(
        open = sheet,
        state = state,
        onOpen = { sheet = it },
        onNote = onNote,
        onRate = onRate,
        onFuel = onFuel,
        onBreakdown = onBreakdown,
        onHandOver = onHandOver,
        onDiscard = onDiscard,
        onAmount = onAmount,
    )
}

@Composable
private fun MeasuredHero(
    state: LiveWorkState,
    unitLabel: String,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onTypeAmount: () -> Unit,
) {
    // Bags are not an area. The same hero serves both, so the label follows the
    // billing mode rather than assuming land.
    HeroLabel(
        stringResource(
            if (state.billingMode == BillingMode.PerAcre) R.string.work_area_done
            else R.string.work_amount_done
        )
    )
    FtQuantityStepper(
        value = quantity(state.quantity),
        unit = unitLabel,
        onDecrement = onDecrease,
        onIncrement = onIncrease,
        decrementContentDescription = stringResource(R.string.live_take_cd, unitLabel),
        incrementContentDescription = stringResource(R.string.live_add_cd, unitLabel),
        onTypeValue = onTypeAmount,
        typeValueContentDescription = stringResource(R.string.work_type_amount_cd),
        modifier = Modifier.padding(vertical = 18.dp),
    )
    MoneyLine(state)
}

@Composable
private fun TripHero(state: LiveWorkState) {
    HeroLabel(stringResource(R.string.work_trips_done))
    Text(
        text = quantity(state.quantity),
        style = FieldTrackTheme.type.moneyHero.copy(fontSize = 104.sp, lineHeight = 108.sp),
        color = FieldTrackTheme.colors.onDark,
        textAlign = TextAlign.Center,
    )
    MoneyLine(state)
}

@Composable
private fun HoursHero(state: LiveWorkState) {
    HeroLabel(stringResource(R.string.work_time_on_job))
    Text(
        text = duration(state.elapsedSeconds, withSeconds = true),
        style = FieldTrackTheme.type.moneyHero.copy(fontSize = 64.sp, lineHeight = 70.sp),
        color = FieldTrackTheme.colors.onDark,
        textAlign = TextAlign.Center,
    )
    MoneyLine(state)
    Text(
        text = stringResource(R.string.live_hourly_note),
        style = FieldTrackTheme.type.caption,
        color = FieldTrackTheme.colors.onDarkMuted,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun FixedHero(state: LiveWorkState) {
    HeroLabel(stringResource(R.string.finish_total))
    Text(
        text = rupees(state.amountRupees),
        style = FieldTrackTheme.type.moneyHero.copy(fontSize = 64.sp, lineHeight = 70.sp),
        color = FieldTrackTheme.colors.moneyOnDark,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.live_fixed_note),
        style = FieldTrackTheme.type.caption,
        color = FieldTrackTheme.colors.onDarkMuted,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun HeroLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = FieldTrackTheme.type.overline,
        color = FieldTrackTheme.colors.onDarkMuted,
    )
}

@Composable
private fun MoneyLine(state: LiveWorkState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            // Hourly work shows the rate per hour rather than "hours × rate": the
            // clock above IS the quantity, and two minutes in it rounds to "0.0",
            // which reads as a broken sum sitting next to a rising amount.
            text = if (state.billingMode == BillingMode.PerHour) {
                stringResource(
                    R.string.statement_rate_note,
                    rupees(state.rateRupees),
                    stringResource(state.unit.labelRes),
                )
            } else if (state.billingMode == BillingMode.PerTrip) {
                // Trips are priced one at a time, so "3 × ₹400" would be a lie the
                // moment the rate moves. The rate shown is what the NEXT one costs.
                stringResource(R.string.work_next_trip_rate, rupees(state.rateRupees))
            } else {
                stringResource(
                    R.string.live_rate_line,
                    quantity(state.quantity),
                    rupees(state.rateRupees),
                )
            },
            style = FieldTrackTheme.type.bodySmall,
            color = FieldTrackTheme.colors.onDarkMuted,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = rupees(state.amountRupees),
            style = FieldTrackTheme.type.moneyLarge,
            color = FieldTrackTheme.colors.moneyOnDark,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

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
import com.example.fieldtrack.designsystem.layout.AdaptiveBody
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.measure
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
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.auth.formatPhone
import androidx.compose.foundation.layout.Spacer
import com.example.fieldtrack.designsystem.component.FtRowDivider

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
    onAddCost: (() -> Unit)?,
    onPayCost: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Sends the farmer a short summary of this job. Null where there is nothing to say yet. */
    onShareSummary: (() -> Unit)? = null,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    AdaptiveScreen(modifier) {
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
            return@AdaptiveScreen
        }

        // The figure and who earned it on one side; what it cost and what
        // happened on the other — on a tablet both read at once.
        AdaptiveBody(primary = {
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
                                    state.operatorPhone?.let { formatPhone(it) },
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

        }, secondary = {
            if (state.finished) {
                CostCard(state = state, onAddCost = onAddCost, onPayCost = onPayCost)
            }

            ProofCard(state)

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
        })

        if (state.live || (state.finished && (onAmend != null || onShareSummary != null))) {
            Row(
                Modifier.padding(dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (state.finished && onShareSummary != null) {
                    FtButton(
                        text = stringResource(R.string.job_share_summary),
                        onClick = onShareSummary,
                        height = 54.dp,
                    )
                }
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
    val unitLabel = com.example.fieldtrack.feature.unitName(state.unit)

    FtCard(
        background = colors.deepGreen,
        borderColor = colors.deepGreen,
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
            if (!state.sent) {
                // The question behind "did the owner get it?", answered on the
                // job itself rather than only on the Backup screen.
                Text(
                    text = stringResource(R.string.job_not_sent),
                    style = FieldTrackTheme.type.caption,
                    color = colors.moneyOnDark,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = listOfNotNull(com.example.fieldtrack.feature.workLabel(state.workType), state.fieldLabel).joinToString(" · "),
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
                                if (state.unit == MeasureUnit.Hour) measure(state.quantity, state.unit)
                                else quantity(state.quantity),
                                money(state.rateMinor),
                            )
                        },
                        style = FieldTrackTheme.type.caption,
                        color = colors.onDarkMuted,
                    )
                    Text(
                        text = money(state.amountMinor),
                        style = FieldTrackTheme.type.moneyLarge,
                        color = colors.onDark,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    state.quote?.takeIf { it.hasExtras }?.let { q ->
                        // Where the figure comes from, when it is more than quantity times rate.
                        val parts = listOfNotNull(
                            stringResource(R.string.job_quote_work, money(q.workMinor)),
                            q.minimumTopUpMinor.takeIf { it > 0 }?.let { stringResource(R.string.job_quote_minimum, money(it)) },
                            q.arrivalMinor.takeIf { it > 0 }?.let { stringResource(R.string.job_quote_arrival, money(it)) },
                            q.waitingMinor.takeIf { it > 0 }?.let { stringResource(R.string.job_quote_waiting, money(it)) },
                            q.fuelMinor.takeIf { it > 0 }?.let { stringResource(R.string.job_quote_diesel, money(it)) },
                            q.discountMinor.takeIf { it > 0 }?.let { stringResource(R.string.job_quote_discount, money(it)) },
                        )
                        Text(
                            text = parts.joinToString(" · "),
                            style = FieldTrackTheme.type.caption,
                            color = colors.onDarkMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    // A driver who may not see money still needs the measure.
                    Text(
                        text = measure(state.quantity, state.unit),
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
                text = event.detail(jobDay = state.workedAtMillis),
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
    JobEventType.Paused, JobEventType.ClockCorrected -> FieldTrackTheme.colors.money
    JobEventType.BreakdownReported, JobEventType.HelpAsked -> FieldTrackTheme.colors.due
    else -> FieldTrackTheme.colors.inkSecondary
}

/** What happened, in the words a farmer would use for it. */
@Composable
private fun JobEventRow.title(state: JobDetailState): String {
    val unitLabel = com.example.fieldtrack.feature.unitName(state.unit)
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
        // The trip happened either way; what it was charged at is the owner's.
        JobEventType.TripAdded -> if (state.canSeeMoney) {
            stringResource(R.string.event_trip_added, money((numericValue ?: 0.0).toLong()))
        } else {
            stringResource(R.string.event_trip_added_plain)
        }
        JobEventType.TripRemoved -> stringResource(R.string.event_trip_removed)
        JobEventType.FuelAdded -> stringResource(
            R.string.event_fuel,
            quantity(numericValue ?: 0.0),
        )
        JobEventType.BreakdownReported -> stringResource(
            R.string.event_breakdown,
            // A kind of ours, translated; otherwise the reporter's own words.
            textValue?.let { t -> BreakdownCategory.entries.firstOrNull { it.name == t } }
                ?.let { stringResource(it.labelRes) } ?: textValue.orEmpty(),
        )
        JobEventType.NoteAdded -> textValue.orEmpty()
            .ifBlank { stringResource(R.string.work_note_title) }
        JobEventType.PhotoAdded -> stringResource(R.string.event_photo)
        JobEventType.HandedOver -> stringResource(
            R.string.event_handed_over,
            textValue.orEmpty(),
        )
        // Said in full on the timeline, because this is the line somebody reads
        // when the hours on the bill are questioned: when the clock was stopped,
        // and how much of what it showed was taken off. When it was put right is
        // the time beside it.
        JobEventType.ClockCorrected -> {
            val stoppedAt = textValue?.toLongOrNull()
            val stopped = stoppedAt?.let {
                if (com.example.fieldtrack.feature.isSameDay(it, atMillis)) clockTime(it)
                else stringResource(R.string.event_clock_day_time, dayMonth(it), clockTime(it))
            }.orEmpty()
            stringResource(
                R.string.event_clock_corrected,
                stopped,
                com.example.fieldtrack.feature.spokenDuration((numericValue ?: 0.0).toLong()),
            )
        }
        JobEventType.HelpAsked -> stringResource(R.string.event_help)
        JobEventType.MachineTookOver -> stringResource(R.string.event_took_over, textValue.orEmpty())
        JobEventType.MachineSwappedOut -> stringResource(R.string.event_swapped_out, textValue.orEmpty())
        JobEventType.RateChanged -> if (state.canSeeMoney) {
            stringResource(R.string.event_rate_changed, money((numericValue ?: 0.0).toLong()))
        } else {
            stringResource(R.string.event_rate_changed_plain)
        }
    }
}

/** The time it happened, who did it, and how long a pause lasted. */
@Composable
private fun JobEventRow.detail(jobDay: Long): String = listOfNotNull(
    // The date only when it is not the job's own: a job that ran over two days,
    // or was put right the next morning, otherwise reads as one jumbled day.
    if (com.example.fieldtrack.feature.isSameDay(atMillis, jobDay)) clockTime(atMillis)
    else stringResource(R.string.event_clock_day_time, dayMonth(atMillis), clockTime(atMillis)),
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


/**
 * What the job cost the business, which is never what the farmer is charged.
 *
 * Said out loud on the card, because the two figures sit a thumb apart and
 * confusing them would be expensive in both directions. Nothing here is
 * written to a bill, a statement or a receipt.
 *
 * A total appears only when every part of it is both visible to this reader
 * and actually priced. The alternative — a figure quietly missing a wage or an
 * unpriced line — reads as profit, and somebody acts on it.
 */
@Composable
private fun CostCard(
    state: JobDetailState,
    onAddCost: (() -> Unit)?,
    onPayCost: ((String) -> Unit)?,
) {
    val colors = FieldTrackTheme.colors

    FtSectionLabel(
        stringResource(
            if (state.canSeeMoney) R.string.cost_section else R.string.cost_section_driver
        )
    )

    FtCard {
        if (state.canSeeMoney) {
            CostLine(
                label = stringResource(R.string.cost_earned),
                amount = money(state.amountMinor),
                strong = true,
            )
            Spacer(Modifier.height(11.dp))
            FtRowDivider()
            Spacer(Modifier.height(11.dp))
            CostLine(
                label = stringResource(R.string.cost_diesel),
                amount = "−" + money(state.fuelMinor),
                cost = true,
            )
        } else {
            // No rupees at all for this reader, so the diesel is told in the
            // unit they put in the tank themselves.
            CostLine(
                label = stringResource(R.string.cost_diesel),
                amount = stringResource(
                    R.string.cost_litres, formatLitres(state.fuelLitres)
                ),
            )
        }

        val heads = state.labourHeads
        if (heads != null || state.labourUncounted) {
            Spacer(Modifier.height(9.dp))
            CostLine(
                label = stringResource(R.string.cost_labour),
                detail = heads?.toString() ?: stringResource(R.string.cost_a_gang),
                // The amount slot holds an amount or nothing. A sentence put
                // here wrapped across the label beside it and the line became
                // unreadable; what it means is said in its own card below.
                amount = when {
                    !state.seesPeopleCost -> ""
                    state.labourMinor > 0 -> "−" + money(state.labourMinor)
                    else -> stringResource(R.string.cost_not_priced_yet)
                },
                cost = state.seesPeopleCost && state.labourMinor > 0,
            )
        }

        if (state.seesPeopleCost) {
            state.otherCosts.forEach { (note, amount) ->
                Spacer(Modifier.height(9.dp))
                CostLine(
                    label = note ?: stringResource(R.string.cost_other),
                    amount = "−" + money(amount),
                    cost = true,
                )
            }
        }

        if (state.canTotal) {
            Spacer(Modifier.height(13.dp))
            FtRowDivider()
            Spacer(Modifier.height(13.dp))
            CostLine(
                label = stringResource(R.string.cost_left),
                amount = money(state.leftMinor),
                strong = true,
                good = state.leftMinor >= 0,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = stringResource(R.string.cost_yours_note, money(state.amountMinor)),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
        } else if (state.canSeeMoney) {
            Spacer(Modifier.height(11.dp))
            Text(
                text = stringResource(
                    if (state.seesPeopleCost) R.string.cost_no_total
                    else R.string.cost_no_total_hidden
                ),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
        }
    }

    // Said in words as well as in red: a negative figure is easy to read past, and
    // a job that cost more than it brought in is the thing this card exists to show.
    state.lostMinor?.let { lost ->
        Spacer(Modifier.height(9.dp))
        FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
            Text(
                text = stringResource(R.string.cost_lost, money(lost)),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.dueInk,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.cost_lost_note),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.dueInk,
            )
        }
    }

    // The machine's own wear and loan, for the hours this job ran. An estimate, so
    // it sits apart and says so; the figures above it are not changed by it.
    val share = state.machineShareMinor
    val afterMachine = state.leftAfterMachineMinor
    if (state.canTotal && share != null && afterMachine != null) {
        Spacer(Modifier.height(9.dp))
        FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
            Text(
                text = stringResource(
                    if (afterMachine >= 0) R.string.cost_share_keeps else R.string.cost_share_loses,
                    money(share),
                    money(kotlin.math.abs(afterMachine)),
                ),
                style = FieldTrackTheme.type.bodySmall,
                color = if (afterMachine >= 0) colors.inkSecondary else colors.dueInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cost_share_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
        }
    }

    // A cost somebody recorded in the field and nobody has priced. Without a
    // way in to that row, the only button on this screen adds a second entry
    // beside it and the same crew is counted twice.
    val unpriced = state.costs.firstOrNull { it.cost.amountMinor == null }
    if (state.seesPeopleCost && unpriced != null) {
        Spacer(Modifier.height(9.dp))
        FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
            Text(
                text = stringResource(R.string.cost_unpriced),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.dueInk,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.cost_unpriced_note),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.dueInk,
            )
            if (onPayCost != null) {
                Spacer(Modifier.height(9.dp))
                FtButton(
                    text = stringResource(R.string.cost_put_figure),
                    onClick = { onPayCost(unpriced.cost.id) },
                    tone = FtButtonTone.Secondary,
                    height = 46.dp,
                )
            }
        }
    }

    if (state.seesPeopleCost && state.owedMinor > 0) {
        Spacer(Modifier.height(9.dp))
        FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
            Text(
                text = stringResource(R.string.cost_owed, money(state.owedMinor)),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.dueInk,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.cost_owed_note),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.dueInk,
            )
            val owing = state.costs.firstOrNull { (it.owedMinor ?: 0) > 0 }
            if (owing != null && onPayCost != null) {
                Spacer(Modifier.height(9.dp))
                FtButton(
                    text = stringResource(R.string.cost_pay_now),
                    onClick = { onPayCost(owing.cost.id) },
                    tone = FtButtonTone.Secondary,
                    height = 46.dp,
                )
            }
        }
    }

    if (!state.canSeeMoney) {
        Spacer(Modifier.height(9.dp))
        FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
            Text(
                text = stringResource(R.string.cost_hidden_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cost_hidden_note),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
        }
    }

    if (onAddCost != null) {
        Spacer(Modifier.height(9.dp))
        FtButton(
            text = stringResource(
                if (state.canPriceCost) R.string.cost_add else R.string.cost_say_who
            ),
            onClick = onAddCost,
            tone = FtButtonTone.Secondary,
            height = 50.dp,
        )
    }
}

@Composable
private fun CostLine(
    label: String,
    amount: String,
    detail: String? = null,
    strong: Boolean = false,
    cost: Boolean = false,
    good: Boolean = true,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = label,
                style = if (strong) FieldTrackTheme.type.sectionTitle else FieldTrackTheme.type.body,
                color = if (strong) colors.ink else colors.inkSecondary,
            )
            if (detail != null) {
                Text(
                    text = stringResource(R.string.cost_detail_sep) + detail,
                    style = FieldTrackTheme.type.body,
                    color = colors.inkTertiary,
                )
            }
        }
        if (amount.isNotEmpty()) {
            Text(
                text = amount,
                style = if (strong) FieldTrackTheme.type.moneyMedium else FieldTrackTheme.type.body,
                color = when {
                    cost -> colors.due
                    strong && good -> colors.money
                    strong -> colors.due
                    else -> colors.ink
                },
            )
        }
    }
}

/** Litres without a trailing ".0" on the whole numbers people actually say. */
private fun formatLitres(litres: Double): String =
    if (litres % 1.0 == 0.0) litres.toLong().toString() else "%.1f".format(litres)


/** What was noted to prove the work, if anything: the meter at both ends, where, and the photos. */
@Composable
private fun ProofCard(state: JobDetailState) {
    val p = state.proof
    val any = p.start.meter != null || p.end.meter != null || p.start.photoId != null || p.end.photoId != null ||
        state.startPlace != null || state.endPlace != null
    if (!any) return
    val colors = FieldTrackTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    FtSectionLabel(stringResource(R.string.proof_title))
    FtCard(padding = 16.dp) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            if (p.start.meter != null || p.end.meter != null) {
                val from = p.start.meter?.let { com.example.fieldtrack.designsystem.format.quantity(it) } ?: "—"
                val to = p.end.meter?.let { com.example.fieldtrack.designsystem.format.quantity(it) } ?: "—"
                Text(stringResource(R.string.proof_meter_line, from, to), style = FieldTrackTheme.type.rowTitle, color = colors.ink)
                if (p.start.meter != null && p.end.meter != null && p.end.meter >= p.start.meter) {
                    Text(
                        stringResource(R.string.proof_meter_diff, com.example.fieldtrack.designsystem.format.quantity(p.end.meter - p.start.meter)),
                        style = FieldTrackTheme.type.caption, color = colors.inkSecondary,
                    )
                }
            }
            listOf(
                R.string.proof_at_start to state.startPlace,
                R.string.proof_at_finish to state.endPlace,
            ).forEach { (label, place) ->
                if (place != null) {
                    com.example.fieldtrack.designsystem.component.FtButton(
                        text = stringResource(R.string.proof_open_map, stringResource(label)),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("geo:${place.first},${place.second}?q=${place.first},${place.second}"),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        tone = com.example.fieldtrack.designsystem.component.FtButtonTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            p.start.photoId?.let {
                Text(stringResource(R.string.proof_at_start), style = FieldTrackTheme.type.caption, color = colors.inkSecondary)
                com.example.fieldtrack.feature.work.PhotoImage(state.orgId, it)
            }
            p.end.photoId?.let {
                Text(stringResource(R.string.proof_at_finish), style = FieldTrackTheme.type.caption, color = colors.inkSecondary)
                com.example.fieldtrack.feature.work.PhotoImage(state.orgId, it)
            }
        }
    }
}

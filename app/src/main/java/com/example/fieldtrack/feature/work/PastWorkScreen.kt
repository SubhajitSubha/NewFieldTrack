package com.example.fieldtrack.feature.work

import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.data.region.BusinessClock
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.feature.machine.MachineChoice
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.billingLabel
import com.example.fieldtrack.feature.captionRes
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.unitsFor

/**
 * Recording work that happened before anybody pressed start.
 *
 * Opens on yesterday, because work done today would have been timed. The times are
 * optional and said to be optional: most people genuinely do not remember when they
 * started, and demanding a number they will invent is worse than having none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PastWorkScreen(
    state: PastWorkState,
    onBack: () -> Unit,
    onDay: (Long) -> Unit,
    onPickDate: () -> Unit,
    onPickCustomer: () -> Unit,
    onMachine: (String?) -> Unit,
    onWorkType: (com.example.fieldtrack.feature.WorkTypeOption) -> Unit,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (com.example.fieldtrack.data.db.MeasureUnit) -> Unit,
    onQuantity: (String) -> Unit,
    onRate: (String) -> Unit,
    onTerms: (TermsDraft) -> Unit = {},
    /** What was said, to be understood and put in the form. Null hides the microphone. */
    onSpoken: ((String) -> Unit)? = null,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onForgetTimes: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onPartner: (String?) -> Unit = {},
    onOtherName: (String) -> Unit = {},
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val unitLabel = stringResource(state.unit.labelRes)
    val today = remember0()
    val yesterday = today - DAY

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(R.string.past_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_close_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onSpoken != null) {
                val voice = com.example.fieldtrack.feature.voice.rememberVoiceInput(
                    prompt = stringResource(R.string.past_say_prompt), onText = onSpoken,
                )
                if (voice.available) {
                    com.example.fieldtrack.designsystem.component.FtButton(
                        text = stringResource(R.string.past_say),
                        onClick = voice.start,
                        tone = com.example.fieldtrack.designsystem.component.FtButtonTone.Secondary,
                        icon = com.example.fieldtrack.designsystem.icon.FieldTrackIcons.Mic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.past_say_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                    )
                }
            }
            FtCard(background = colors.moneyWash, borderColor = colors.moneyWash) {
                Text(
                    text = stringResource(R.string.past_why),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.moneyInk,
                )
            }

            Column {
                FtSectionLabel(
                    stringResource(R.string.past_which_day),
                    Modifier.padding(bottom = 9.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FtChip(
                        label = stringResource(R.string.past_yesterday),
                        selected = state.dayMillis == yesterday,
                        onClick = { onDay(yesterday) },
                    )
                    FtChip(
                        label = stringResource(R.string.past_today),
                        selected = state.dayMillis == today,
                        onClick = { onDay(today) },
                    )
                    FtChip(
                        label = stringResource(R.string.past_pick_day),
                        selected = state.dayMillis != today && state.dayMillis != yesterday,
                        onClick = onPickDate,
                    )
                }
                Text(
                    text = dayMonthYear(state.dayMillis),
                    style = FieldTrackTheme.type.label,
                    color = colors.ink,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            FtCard(padding = 0.dp) {
                FtListRow(
                    title = stringResource(R.string.past_customer),
                    trailing = {
                        Text(
                            text = state.customer?.name
                                ?: stringResource(R.string.past_choose),
                            style = FieldTrackTheme.type.label,
                            color = if (state.customer == null) colors.inkTertiary else colors.ink,
                        )
                    },
                    showChevron = true,
                    onClick = onPickCustomer,
                )
            }

            // The machine first: what it is decides which work comes first, and what went with it.
            if (state.machines.isNotEmpty()) {
                MachineChoice(
                    machines = state.machines,
                    selectedId = state.machineId,
                    noneLabel = stringResource(R.string.start_no_machine),
                    onPick = onMachine,
                    label = stringResource(R.string.start_machine),
                )
            }

            PartnerChoice(state.pick, onPartner, showBusy = false)

            WorkChoice(
                menu = state.pick.menu,
                selected = state.workType,
                otherName = state.pick.otherName,
                onPick = onWorkType,
                onOtherName = onOtherName,
            )

            Column {
                FtSectionLabel(
                    stringResource(R.string.past_how_charged),
                    Modifier.padding(bottom = 9.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    state.workType.billingModes.forEach { mode ->
                        FtChoiceCard(
                            title = billingLabel(mode, state.unit),
                            description = stringResource(mode.captionRes),
                            selected = state.billingMode == mode,
                            onClick = { onBillingMode(mode) },
                        )
                    }
                }
            }

            if (state.billingMode != BillingMode.Fixed) {
                FtTextField(
                    label = stringResource(R.string.past_how_much),
                    value = state.quantityText,
                    onValueChange = onQuantity,
                    suffix = unitLabel,
                    keyboardType = KeyboardType.Decimal,
                )
                val units = unitsFor(state.billingMode)
                if (units.size > 1) {
                    FtPick(
                        options = units,
                        selected = units.firstOrNull { it == state.unit },
                        text = { stringResource(it.labelRes) },
                        onPick = onUnit,
                        searchHint = stringResource(R.string.pick_search),
                    )
                }
            }

            FtTextField(
                label = stringResource(R.string.start_your_rate),
                value = state.rateText,
                onValueChange = onRate,
                prefix = Money.style.symbol,
                suffix = if (state.billingMode == BillingMode.Fixed) null
                else stringResource(R.string.start_rate_per, unitLabel),
                keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
            )

            TermsSection(
                draft = state.terms,
                onChange = onTerms,
            )

            TimesSection(state, onPickFrom, onPickTo, onForgetTimes)

            if (state.overlaps.isNotEmpty()) {
                // A warning, not a refusal: a machine genuinely can be handed over
                // mid-field, and the person typing this knows what happened.
                FtCard(background = colors.moneyWash, borderColor = colors.money) {
                    Text(
                        text = stringResource(R.string.past_overlap),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.moneyInk,
                    )
                }
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }

        Column(
            Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(colors.surfaceMuted, FieldTrackTheme.shapes.chip)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.past_by_hand),
                        style = FieldTrackTheme.type.overline,
                        color = colors.inkSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.past_by_hand_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            FtButton(
                text = if (state.amountMinor > 0) {
                    stringResource(R.string.past_save_amount, money(state.amountMinor))
                } else {
                    stringResource(R.string.past_save)
                },
                onClick = onSave,
                enabled = state.canSave,
                height = 58.dp,
            )
        }
    }
}

/** From and to, said to be optional because most people do not remember. */
@Composable
private fun TimesSection(
    state: PastWorkState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onForgetTimes: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            FtSectionLabel(
                stringResource(R.string.past_how_long),
                Modifier.weight(1f),
            )
            if (state.hasTimes) {
                Text(
                    text = stringResource(R.string.past_forget_times),
                    style = FieldTrackTheme.type.label,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onForgetTimes)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        Row(
            Modifier.padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeBox(
                label = stringResource(R.string.past_from),
                minutes = state.fromMinutes,
                onClick = onPickFrom,
                modifier = Modifier.weight(1f),
            )
            TimeBox(
                label = stringResource(R.string.past_to),
                minutes = state.toMinutes,
                onClick = onPickTo,
                modifier = Modifier.weight(1f),
            )
            state.workedMinutes?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = duration(it * 60L),
                        style = FieldTrackTheme.type.numeric,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.past_hours),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                    )
                }
            }
        }
        if (state.hoursDisagreeWithTimes) {
            Text(
                text = stringResource(R.string.past_hours_differ),
                style = FieldTrackTheme.type.caption,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.timesBackwards) {
            Text(
                text = stringResource(R.string.past_times_backwards),
                style = FieldTrackTheme.type.caption,
                color = colors.dueInk,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TimeBox(
    label: String,
    minutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    Column(modifier) {
        Text(
            text = label,
            style = FieldTrackTheme.type.caption,
            color = colors.inkTertiary,
            modifier = Modifier.padding(bottom = 5.dp, start = 3.dp),
        )
        FtCard(
            padding = 0.dp,
            shape = FieldTrackTheme.shapes.medium,
            onClick = onClick,
        ) {
            Text(
                text = minutes?.let { clockOf(it) }
                    ?: stringResource(R.string.past_time_unset),
                style = FieldTrackTheme.type.rowTitle,
                color = if (minutes == null) colors.inkTertiary else colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

/** "10:00 am" from minutes past midnight. */
private fun clockOf(minutes: Int): String {
    val hour24 = minutes / 60
    val minute = minutes % 60
    val hour = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val suffix = if (hour24 < 12) "am" else "pm"
    return "%d:%02d %s".format(hour, minute, suffix)
}

/** Midnight today, computed once per composition. */
@Composable
private fun remember0(): Long = androidx.compose.runtime.remember {
    java.util.Calendar.getInstance(BusinessClock.timeZone).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private const val DAY = 24 * 60 * 60 * 1000L

package com.example.fieldtrack.feature.work

import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChipTone
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.billingLabel
import com.example.fieldtrack.feature.captionRes
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.plan.dayName
import com.example.fieldtrack.feature.plan.partName
import com.example.fieldtrack.feature.plan.workName
import com.example.fieldtrack.feature.unitInPhrase
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.workTypeOptions

@Composable
fun StartWorkScreen(
    state: StartWorkState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onNewCustomer: () -> Unit,
    onNewMachine: () -> Unit,
    onSelectCustomer: (com.example.fieldtrack.data.db.entity.CustomerEntity) -> Unit,
    onSelectMachine: (String?) -> Unit,
    onSelectField: (String?) -> Unit,
    onSelectWorkType: (com.example.fieldtrack.feature.WorkTypeOption) -> Unit,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (com.example.fieldtrack.data.db.MeasureUnit) -> Unit,
    onRate: (String) -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    onUseBooking: (BookingRow) -> Unit = {},
    onDropBooking: () -> Unit = {},
    onTerms: (TermsDraft) -> Unit = {},
    onSuggestion: (RateSuggestion) -> Unit = {},
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = when (state.step) {
                0 -> stringResource(R.string.start_who_for)
                1 -> stringResource(R.string.start_what_doing)
                else -> stringResource(R.string.start_how_charging)
            },
            subtitle = stringResource(R.string.start_step, state.step + 1),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        StepBar(state.step, Modifier.padding(horizontal = dimens.screenPadding, vertical = 10.dp))

        Box(Modifier.weight(1f)) {
            when (state.step) {
                0 -> CustomerStep(state, onQuery, onNewCustomer, onSelectCustomer)
                1 -> WorkStep(
                    state, onSelectMachine, onSelectField, onSelectWorkType, onNewMachine,
                    onUseBooking, onDropBooking,
                )
                else -> RateStep(state, onBillingMode, onUnit, onRate, onTerms, onSuggestion)
            }
        }

        // Step 0 commits by picking a customer, so it needs no footer button.
        if (state.step > 0) {
            Box(Modifier.padding(dimens.screenPadding)) {
                if (state.step == 1) {
                    FtButton(stringResource(R.string.start_next), onNext, enabled = state.canGoNext)
                } else {
                    FtButton(
                        text = stringResource(R.string.start_begin),
                        onClick = onStart,
                        icon = FieldTrackIcons.Play,
                        enabled = state.canGoNext && !state.starting,
                        height = 62.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBar(step: Int, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(FieldTrackTheme.shapes.chip)
                    .background(if (index <= step) colors.primary else colors.lineStrong)
            )
        }
    }
}

@Composable
private fun CustomerStep(
    state: StartWorkState,
    onQuery: (String) -> Unit,
    onNewCustomer: () -> Unit,
    onSelect: (com.example.fieldtrack.data.db.entity.CustomerEntity) -> Unit,
) {
    val dimens = FieldTrackTheme.dimens
    // A LazyColumn all the way up rather than a list nested in a scrolling Column:
    // a contractor with two hundred customers should not pay to compose all of them.
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = dimens.space6,
        ),
    ) {
        item {
            FtTextField(
                label = stringResource(R.string.start_search_customers),
                value = state.query,
                onValueChange = onQuery,
            )
            Box(Modifier.height(14.dp))
            FtButton(
                text = stringResource(R.string.start_new_customer),
                onClick = onNewCustomer,
                tone = FtButtonTone.Secondary,
                icon = FieldTrackIcons.Add,
            )
            Box(Modifier.height(16.dp))
        }

        if (state.customers.isEmpty()) {
            item {
                FtCard {
                    Text(
                        text = stringResource(R.string.start_no_customers_title),
                        style = FieldTrackTheme.type.rowTitle,
                        color = FieldTrackTheme.colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.start_no_customers_body),
                        style = FieldTrackTheme.type.bodySmall,
                        color = FieldTrackTheme.colors.inkTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            item {
                FtSectionLabel(stringResource(R.string.start_recent))
                Box(Modifier.height(10.dp))
            }
            items(state.customers, key = { it.id }) { customer ->
                FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
                    FtListRow(
                        title = customer.name,
                        subtitle = customer.village,
                        leading = { FtAvatar(initialsOf(customer.name)) },
                        showChevron = true,
                        onClick = { onSelect(customer) },
                    )
                }
                Box(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Whether this is work that was booked.
 *
 * Asked rather than guessed: a farmer may have two jobs on the plan, or this may be
 * something else entirely. Answering is what keeps the booking from being left on the
 * list as a promise nobody kept.
 */
@Composable
private fun BookingHint(state: StartWorkState, onUse: (BookingRow) -> Unit, onDrop: () -> Unit) {
    val colors = FieldTrackTheme.colors
    if (state.bookingId != null) {
        FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
            Text(
                text = stringResource(R.string.plan_start_from_booking),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
            FtButton(
                text = stringResource(R.string.plan_start_not_this),
                onClick = onDrop,
                tone = FtButtonTone.Quiet,
                height = 44.dp,
            )
        }
        return
    }
    state.openBookings.forEach { row ->
        FtCard(background = colors.primaryWash, borderColor = colors.primaryWash) {
            Text(
                text = stringResource(
                    R.string.plan_start_booked,
                    dayName(row.booking.day, state.planToday),
                    workName(row.booking.workType),
                    partName(row.booking.part).lowercase(),
                ),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.primaryPressed,
            )
            FtButton(
                text = stringResource(R.string.plan_start_use),
                onClick = { onUse(row) },
                tone = FtButtonTone.Secondary,
                height = 46.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkStep(
    state: StartWorkState,
    onSelectMachine: (String?) -> Unit,
    onSelectField: (String?) -> Unit,
    onSelectWorkType: (com.example.fieldtrack.feature.WorkTypeOption) -> Unit,
    onNewMachine: () -> Unit,
    onUseBooking: (BookingRow) -> Unit,
    onDropBooking: () -> Unit,
) {
    val dimens = FieldTrackTheme.dimens
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.customer?.let { customer ->
            FtCard(
                background = FieldTrackTheme.colors.primaryWash,
                borderColor = FieldTrackTheme.colors.primaryWash,
                padding = 12.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FtAvatar(
                        initialsOf(customer.name),
                        size = 38.dp,
                        background = FieldTrackTheme.colors.primary,
                        contentColor = FieldTrackTheme.colors.onPrimary,
                    )
                    Text(
                        text = customer.name,
                        style = FieldTrackTheme.type.rowTitle,
                        color = FieldTrackTheme.colors.primaryPressed,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        BookingHint(state, onUseBooking, onDropBooking)

        // Asked only when there is something to ask. A farmer nobody has
        // written plots down for gets no question and loses no tap.
        if (state.fields.isNotEmpty()) {
            FtSectionLabel(stringResource(R.string.field_which))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Chips, like the machines below. Full-width buttons made two
                // visual languages for the same kind of choice on one screen.
                state.fields.forEach { field ->
                    FtChip(
                        label = field.name,
                        selected = state.fieldId == field.id,
                        onClick = { onSelectField(field.id) },
                    )
                }
                FtChip(
                    label = stringResource(R.string.field_not_said),
                    selected = state.fieldId == null,
                    onClick = { onSelectField(null) },
                )
            }
            Text(
                text = stringResource(R.string.field_not_said_note),
                style = FieldTrackTheme.type.caption,
                color = FieldTrackTheme.colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        FtSectionLabel(stringResource(R.string.start_machine))
        // Wraps: a contractor with five machines had the last of them off the edge
        // of a Row that could not wrap.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FtChip(
                label = stringResource(R.string.start_no_machine),
                selected = state.machineId == null,
                onClick = { onSelectMachine(null) },
            )
            state.machines.forEach { machine ->
                // Says what the machine is doing. Picking the harvester that is
                // already out on someone else's field is the kind of mistake that is
                // only noticed at billing time, when two jobs claim one machine.
                FtChip(
                    label = when (machine.status) {
                        MachineStatus.Working ->
                            stringResource(R.string.start_machine_busy, machine.name)
                        MachineStatus.Down ->
                            stringResource(R.string.start_machine_down, machine.name)
                        MachineStatus.Idle -> machine.name
                    },
                    selected = state.machineId == machine.id,
                    onClick = { onSelectMachine(machine.id) },
                    tone = when (machine.status) {
                        MachineStatus.Working -> FtChipTone.Busy
                        MachineStatus.Down -> FtChipTone.Warning
                        MachineStatus.Idle -> FtChipTone.Normal
                    },
                )
            }
            // A machine bought last week should not mean abandoning the job you are
            // standing in a field trying to start.
            if (state.canAddMachines) {
                FtChip(
                    label = stringResource(R.string.start_new_machine),
                    selected = false,
                    onClick = onNewMachine,
                )
            }
        }
        if (state.machines.any { it.status != MachineStatus.Idle }) {
            Text(
                text = stringResource(R.string.start_machine_busy_note),
                style = FieldTrackTheme.type.caption,
                color = FieldTrackTheme.colors.inkTertiary,
            )
        }

        FtSectionLabel(stringResource(R.string.start_work_type), Modifier.padding(top = 4.dp))
        workTypeOptions.forEach { option ->
            FtChoiceCard(
                title = stringResource(option.labelRes),
                selected = state.workType.key == option.key,
                onClick = { onSelectWorkType(option) },
                icon = option.icon,
            )
        }
        Box(Modifier.height(dimens.space4))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateStep(
    state: StartWorkState,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (com.example.fieldtrack.data.db.MeasureUnit) -> Unit,
    onRate: (String) -> Unit,
    onTerms: (TermsDraft) -> Unit,
    onSuggestion: (RateSuggestion) -> Unit,
) {
    val dimens = FieldTrackTheme.dimens
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.workType.billingModes.forEach { mode ->
            FtChoiceCard(
                title = billingLabel(mode, state.landUnit),
                description = stringResource(mode.captionRes),
                selected = state.billingMode == mode,
                onClick = { onBillingMode(mode) },
            )
        }

        FtTextField(
            label = stringResource(R.string.start_your_rate),
            value = state.rateText,
            onValueChange = onRate,
            prefix = Money.style.symbol,
            suffix = stringResource(R.string.start_rate_per, unitInPhrase(state.unit)),
            keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
            focused = true,
        )

        if (state.suggestedRates.isNotEmpty()) {
            // Wraps, because a contractor can have several rates for one kind of work
            // and four chips plus the label do not fit across a phone.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.start_usual_rate),
                    style = FieldTrackTheme.type.caption,
                    color = FieldTrackTheme.colors.inkTertiary,
                )
                state.suggestedRates.forEach { suggestion ->
                    FtChip(
                        label = suggestion.label?.let {
                            stringResource(
                                R.string.start_usual_rate_labelled,
                                money(suggestion.amountMinor),
                                it,
                            )
                        } ?: money(suggestion.amountMinor),
                        selected = state.rateMinor == suggestion.amountMinor,
                        onClick = { onSuggestion(suggestion) },
                    )
                }
            }
        }

        CostHintCard(state)

        TermsSection(
            draft = state.terms,
            onChange = onTerms,
            implements = state.machines.filter { it.id != state.machineId },
            crew = state.crew,
        )

        val units = unitsFor(state.billingMode)
        if (units.size > 1) {
            FtSectionLabel(stringResource(R.string.start_counted_in))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                units.forEach { unit ->
                    FtChip(
                        label = stringResource(unit.labelRes),
                        selected = state.unit == unit,
                        onClick = { onUnit(unit) },
                    )
                }
            }
        }
        Box(Modifier.height(dimens.space4))
    }
}

/**
 * "On this machine a bigha costs you about ₹640 — at ₹1,000 you keep about ₹360."
 *
 * Shown under the rate while it is being typed, because that is the one moment a
 * contractor can still change it. Says "before workers' pay" every time: the
 * figure is the machine's diesel, repairs and loan, and a farmer's rate has to
 * cover the crew as well.
 *
 * Absent rather than approximate when there is not enough behind it. It does not
 * tell anybody what to charge — only what it costs them, which they did not have.
 */
@Composable
private fun CostHintCard(state: StartWorkState) {
    val hint = state.costHint ?: return
    val colors = FieldTrackTheme.colors
    val machine = state.machines.firstOrNull { it.id == state.machineId }?.name.orEmpty()
    val unit = stringResource(R.string.start_rate_per, unitInPhrase(state.unit))

    FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
        when (hint) {
            is CostHint.OnlyHourly -> {
                Text(
                    text = stringResource(R.string.start_cost_hour, machine, money(hint.perHourMinor)),
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.start_cost_hour_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            is CostHint.PerUnit -> {
                Text(
                    text = stringResource(R.string.start_cost_unit, machine, money(hint.costMinor), unit),
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                if (state.rateMinor > 0) {
                    val left = state.rateMinor - hint.costMinor
                    Text(
                        text = if (left >= 0) {
                            stringResource(R.string.start_cost_keep, money(state.rateMinor), money(left), unit)
                        } else {
                            stringResource(R.string.start_cost_lose, money(state.rateMinor), money(-left), unit)
                        },
                        style = FieldTrackTheme.type.bodySmall,
                        color = if (left >= 0) colors.primaryPressed else colors.dueInk,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.start_cost_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

package com.example.fieldtrack.feature.work

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
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.billingLabel
import com.example.fieldtrack.feature.captionRes
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.labelRes
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
    onSelectWorkType: (com.example.fieldtrack.feature.WorkTypeOption) -> Unit,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (com.example.fieldtrack.data.db.MeasureUnit) -> Unit,
    onRate: (String) -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
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
                1 -> WorkStep(state, onSelectMachine, onSelectWorkType, onNewMachine)
                else -> RateStep(state, onBillingMode, onUnit, onRate)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkStep(
    state: StartWorkState,
    onSelectMachine: (String?) -> Unit,
    onSelectWorkType: (com.example.fieldtrack.feature.WorkTypeOption) -> Unit,
    onNewMachine: () -> Unit,
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
            prefix = "₹",
            suffix = stringResource(R.string.start_rate_per, unitInPhrase(state.unit)),
            keyboardType = KeyboardType.Number,
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
                                rupees(suggestion.amountRupees),
                                it,
                            )
                        } ?: rupees(suggestion.amountRupees),
                        selected = state.rateRupees == suggestion.amountRupees,
                        onClick = { onRate(suggestion.amountRupees.toString()) },
                    )
                }
            }
        }

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

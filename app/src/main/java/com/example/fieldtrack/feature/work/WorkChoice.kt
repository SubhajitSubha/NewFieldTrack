package com.example.fieldtrack.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChipTone
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.WorkCatalog
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.title

/**
 * The work, asked the same way wherever it is asked.
 *
 * What this machine usually does first; everything else one tap away, so nothing anybody does
 * is ever missing, only further down. "Other" opens a box for the work's own name, which may
 * be left empty.
 */
@Composable
fun WorkChoice(
    menu: WorkCatalog.Menu,
    selected: WorkTypeOption,
    otherName: String,
    onPick: (WorkTypeOption) -> Unit,
    onOtherName: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    // A work chosen from the rest, by a booking or by voice, is never hidden behind "More work".
    val open = showAll || menu.usual.isEmpty() || menu.rest.any { it.key == selected.key }
    Column(modifier) {
        FtSectionLabel(stringResource(R.string.start_work_type), Modifier.padding(bottom = 9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            menu.usual.forEach { WorkCard(it, selected, otherName, onPick, onOtherName) }
            if (open) {
                menu.rest.forEach { WorkCard(it, selected, otherName, onPick, onOtherName) }
            } else if (menu.rest.isNotEmpty()) {
                val names = menu.rest.map { it.title() }
                FtCard(padding = 0.dp) {
                    FtListRow(
                        title = stringResource(R.string.work_more_kinds),
                        subtitle = names.joinToString(", "),
                        subtitleMaxLines = 2,
                        showChevron = true,
                        onClick = { showAll = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkCard(
    option: WorkTypeOption,
    selected: WorkTypeOption,
    otherName: String,
    onPick: (WorkTypeOption) -> Unit,
    onOtherName: (String) -> Unit,
) {
    val chosen = selected.key == option.key
    FtChoiceCard(
        title = option.title(),
        selected = chosen,
        onClick = { onPick(option) },
        icon = option.icon,
    )
    if (chosen && option.key == WorkCatalog.OTHER) {
        OtherWorkName(otherName, onOtherName)
    }
}

/** The box under "Other": the work's own name, or nothing, which keeps "Other". */
@Composable
fun OtherWorkName(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    FtTextField(
        label = stringResource(R.string.work_other_name),
        value = value,
        onValueChange = { onChange(it.take(WorkCatalog.MAX_NAME)) },
        placeholder = stringResource(R.string.work_type_other),
        helper = stringResource(R.string.work_other_name_note),
        modifier = modifier,
    )
}

/**
 * What goes out with the machine: the tool on the back of a tractor, or the tractor in front
 * of a tool. Asked only when the business has one to name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartnerChoice(
    pick: WorkPick,
    onPick: (String?) -> Unit,
    showBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val choices = pick.partners
    if (choices.isEmpty()) return
    Column(modifier) {
        FtSectionLabel(
            stringResource(if (pick.asksPuller) R.string.work_pulled_by else R.string.work_attached),
            Modifier.padding(bottom = 9.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FtChip(
                label = stringResource(if (pick.asksPuller) R.string.work_pulled_none else R.string.terms_no_implement),
                selected = pick.partnerId == null,
                onClick = { onPick(null) },
            )
            choices.forEach { machine ->
                FtChip(
                    label = when {
                        machine.status == MachineStatus.Down -> stringResource(R.string.start_machine_down, machine.name)
                        showBusy && machine.status == MachineStatus.Working ->
                            stringResource(R.string.start_machine_busy, machine.name)
                        else -> machine.name
                    },
                    selected = pick.partnerId == machine.id,
                    onClick = { onPick(machine.id) },
                    tone = when (machine.status) {
                        MachineStatus.Working -> if (showBusy) FtChipTone.Busy else FtChipTone.Normal
                        MachineStatus.Down -> FtChipTone.Warning
                        MachineStatus.Idle -> FtChipTone.Normal
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.terms_implement_note),
            style = FieldTrackTheme.type.caption,
            color = FieldTrackTheme.colors.inkTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

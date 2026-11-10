package com.example.fieldtrack.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChipTone
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.WorkCatalog
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.title

/**
 * The work, asked the same way wherever it is asked: one line, opening a list like the
 * machine type does.
 *
 * What this machine usually does comes first in the list; everything else follows, so nothing
 * anybody does is ever missing, only further down. "Other" opens a box for the work's own
 * name, which may be left empty.
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
    val options = menu.usual + menu.rest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        FtPick(
            options = options,
            selected = options.firstOrNull { it.key == selected.key } ?: selected,
            text = { it.title() },
            icon = { it.icon },
            onPick = onPick,
            label = stringResource(R.string.start_work_type),
            searchHint = stringResource(R.string.pick_search),
        )
        if (selected.key == WorkCatalog.OTHER) {
            OtherWorkName(otherName, onOtherName)
        }
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

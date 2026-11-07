package com.example.fieldtrack.feature.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChipTone
import com.example.fieldtrack.designsystem.component.FtDropdown
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.feature.iconFor

/** A fleet of this many or fewer is shown as pills; a bigger one would fill the screen, so it opens as a list. */
private const val MACHINES_AS_PILLS = 5

/**
 * Which machine, asked the same way on every screen that asks it.
 *
 * Says what each machine is doing: choosing the harvester that is already out on somebody
 * else's field is a mistake that otherwise shows up at billing, when two jobs claim one machine.
 * [showBusy] is for starting work now; a booking for another day only cares whether it is down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MachineChoice(
    machines: List<MachineEntity>,
    selectedId: String?,
    noneLabel: String,
    onPick: (String?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    showBusy: Boolean = false,
    /** A booking that names a machine since removed: shown as chosen, so it is not silently "none". */
    removedLabel: String? = null,
    /** "Add a machine" without leaving the form, where the person may. */
    onAddNew: (() -> Unit)? = null,
    addNewLabel: String = "",
) {
    @Composable
    fun nameOf(machine: MachineEntity): String = when {
        machine.status == MachineStatus.Down -> stringResource(R.string.start_machine_down, machine.name)
        showBusy && machine.status == MachineStatus.Working ->
            stringResource(R.string.start_machine_busy, machine.name)
        else -> machine.name
    }

    if (machines.size > MACHINES_AS_PILLS) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val chosen = machines.firstOrNull { it.id == selectedId }
            FtDropdown<MachineEntity?>(
                options = listOf<MachineEntity?>(null) + machines,
                selected = chosen,
                text = { it?.let { m -> nameOf(m) } ?: noneLabel },
                icon = { it?.let { m -> iconFor(m.kind) } },
                onPick = { onPick(it?.id) },
                label = label,
                placeholder = if (selectedId != null && removedLabel != null) removedLabel else noneLabel,
                searchHint = stringResource(R.string.pick_search),
            )
            if (onAddNew != null) {
                FtChip(label = addNewLabel, selected = false, onClick = onAddNew)
            }
        }
        return
    }
    Column(modifier) {
        FtSectionLabel(label, Modifier.padding(bottom = 9.dp))
        // Wraps: a contractor with five machines had the last of them off the edge of a Row.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FtChip(label = noneLabel, selected = selectedId == null, onClick = { onPick(null) })
            if (removedLabel != null && selectedId != null && machines.none { it.id == selectedId }) {
                FtChip(label = removedLabel, selected = true, onClick = {}, tone = FtChipTone.Warning)
            }
            machines.forEach { machine ->
                FtChip(
                    label = nameOf(machine),
                    selected = selectedId == machine.id,
                    onClick = { onPick(machine.id) },
                    tone = when (machine.status) {
                        MachineStatus.Working -> if (showBusy) FtChipTone.Busy else FtChipTone.Normal
                        MachineStatus.Down -> FtChipTone.Warning
                        MachineStatus.Idle -> FtChipTone.Normal
                    },
                )
            }
            if (onAddNew != null) {
                FtChip(label = addNewLabel, selected = false, onClick = onAddNew)
            }
        }
    }
}

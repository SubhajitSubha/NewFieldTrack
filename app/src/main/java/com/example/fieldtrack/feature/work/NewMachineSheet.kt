package com.example.fieldtrack.feature.work

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtDropdown
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.machine.MachineKinds
import com.example.fieldtrack.feature.machine.labelRes
import com.example.fieldtrack.feature.machine.nameHintRes

/**
 * Adding a machine without leaving the job you are starting.
 *
 * A tractor bought last week should not cost a trip out to the Machines tab and back
 * while the customer waits in the field. Two things only — what it is and what you
 * call it; the hour meter and the service interval can wait for a quieter moment.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMachineSheet(
    onDismiss: () -> Unit,
    onAdd: (String, MachineKind) -> Unit,
) {
    val colors = FieldTrackTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MachineKind.Tractor) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp)
                // A phone on its side gives a sheet barely 400dp; what does not
                // fit has to scroll rather than be cut off below the buttons.
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.start_new_machine_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )

            FtDropdown(
                options = MachineKinds,
                selected = kind,
                text = { stringResource(it.labelRes()) },
                icon = { iconFor(it) },
                onPick = { kind = it },
                label = stringResource(R.string.machines_kind),
                searchHint = stringResource(R.string.machines_kind_search),
            )

            FtTextField(
                label = stringResource(R.string.machines_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(kind.nameHintRes()),
                focused = true,
            )

            FtButton(
                text = stringResource(R.string.start_new_machine_save),
                onClick = { onAdd(name, kind) },
                enabled = name.isNotBlank(),
            )
        }
    }
}

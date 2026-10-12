package com.example.fieldtrack.feature.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Putting a driver on the books.
 *
 * Name and number only. What they are allowed to do is set afterwards on their own
 * screen, so adding somebody in a hurry on a working morning is two fields and a
 * button rather than a form of switches nobody reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberSheet(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val colors = FieldTrackTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

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
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.team_add_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.team_add_note),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkTertiary,
            )
            FtTextField(
                label = stringResource(R.string.onboard_your_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.team_add_name_hint),
                focused = true,
            )
            FtTextField(
                label = stringResource(R.string.edit_business_phone),
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() } },
                prefix = "+91",
                keyboardType = KeyboardType.Phone,
            )
            FtButton(
                text = stringResource(R.string.team_add_save),
                onClick = { onAdd(name, phone) },
                enabled = name.isNotBlank(),
            )
        }
    }
}

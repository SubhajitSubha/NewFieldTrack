package com.example.fieldtrack.feature.work

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Which day the work was done.
 *
 * Future days are not selectable: this screen exists for work that has already
 * happened, and a job dated next Tuesday would sit at the top of every list until
 * next Tuesday came.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerSheet(
    selectedMillis: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val today = System.currentTimeMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selectedMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onPicked) },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.finish_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** A clock face for a start or finish time, returning minutes past midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockSheet(
    minutes: Int?,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (minutes ?: 9 * 60) / 60,
        initialMinute = (minutes ?: 0) % 60,
        is24Hour = false,
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(
                    FieldTrackTheme.colors.paper,
                    FieldTrackTheme.shapes.card,
                )
                .padding(20.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            TimePicker(state = state)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) {
                    Text(stringResource(R.string.finish_done))
                }
            }
        }
    }
}

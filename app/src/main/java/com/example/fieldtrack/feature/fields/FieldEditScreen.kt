package com.example.fieldtrack.feature.fields

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes

/** The crops offered as one tap. Anything else is typed. */
private val CROP_CHIPS = listOf(
    R.string.field_crop_wheat,
    R.string.field_crop_paddy,
    R.string.field_crop_cotton,
    R.string.field_crop_mustard,
    R.string.field_crop_sugarcane,
    R.string.field_crop_fallow,
)

/**
 * Writing a plot down.
 *
 * The name comes first and in the farmer's own words, because that is what
 * everybody will say on the phone — "the canal-side plot", never a survey
 * number. Size and crop follow. The pin is last and optional: it needs the
 * person to be standing in the field, which they very often are not.
 */
@Composable
fun FieldEditScreen(
    state: FieldEditState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onArea: (String) -> Unit,
    onUnit: (MeasureUnit) -> Unit,
    onCrop: (String) -> Unit,
    onDropPin: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    // Asked for only when the pin is tapped, never on opening the screen. A
    // permission prompt nobody asked for is the fastest way to get a no.
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Asked for either way, refused or not. currentPin() checks the
        // permission itself and answers NotAllowed, which is how the card gets
        // to say so — swallowing the refusal here left the tap doing visibly
        // nothing, which is the one outcome worse than a no.
        onDropPin()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(
                if (state.fieldId == null) R.string.field_add_title
                else R.string.field_edit_title
            ),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {

            FtSectionLabel(stringResource(R.string.field_name_label))
            FtTextField(
                label = stringResource(R.string.field_name_hint),
                value = state.name,
                onValueChange = onName,
            )
            Text(
                text = stringResource(R.string.field_name_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            FtSectionLabel(stringResource(R.string.field_size_label))
            FtTextField(
                label = stringResource(R.string.field_size_hint),
                value = state.areaText,
                onValueChange = onArea,
                keyboardType = KeyboardType.Decimal,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Land only. A plot cannot be five hours or three quintals,
                // and offering it invites a field whose size means nothing.
                MeasureUnit.entries.filter { it.isLand }.forEach { unit ->
                    FtChip(
                        label = stringResource(unit.labelRes),
                        selected = unit == state.unit,
                        onClick = { onUnit(unit) },
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.field_crop_label))
            FtTextField(
                label = stringResource(R.string.field_crop_hint),
                value = state.crop,
                onValueChange = onCrop,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Chips fill the box rather than replacing it, so a crop
                // nobody listed can still be typed.
                CROP_CHIPS.forEach { res ->
                    val label = stringResource(res)
                    FtChip(
                        label = label,
                        selected = state.crop == label,
                        onClick = { onCrop(label) },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            FtCard {
                Text(
                    text = stringResource(R.string.field_pin_title),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (state.pin) {
                        PinState.Getting -> stringResource(R.string.field_pin_getting)
                        PinState.Refused -> stringResource(R.string.field_pin_refused)
                        PinState.Nowhere -> stringResource(R.string.field_pin_nowhere)
                        PinState.Dropped -> stringResource(
                            R.string.field_pin_saved, coordinates(state),
                        )
                        PinState.None -> stringResource(R.string.field_pin_note)
                    },
                    style = FieldTrackTheme.type.bodySmall,
                    color = if (state.pin == PinState.Refused || state.pin == PinState.Nowhere) {
                        colors.dueInk
                    } else {
                        colors.inkSecondary
                    },
                )
                Spacer(Modifier.height(9.dp))
                FtButton(
                    text = stringResource(
                        if (state.hasPin) R.string.field_pin_again
                        else R.string.field_pin_title
                    ),
                    onClick = {
                        askLocation.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                        )
                    },
                    tone = FtButtonTone.Secondary,
                    height = 48.dp,
                    enabled = state.pin != PinState.Getting,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(R.string.field_pin_stand),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }

            if (state.fieldId != null) {
                FtButton(
                    text = stringResource(R.string.field_remove),
                    onClick = { confirmRemove = true },
                    tone = FtButtonTone.Danger,
                    height = 48.dp,
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
            FtButton(
                text = stringResource(R.string.field_save),
                onClick = onSave,
                enabled = state.canSave,
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.field_remove_title, state.name),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.field_remove_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text(
                        text = stringResource(R.string.field_remove_yes),
                        style = FieldTrackTheme.type.label,
                        color = colors.due,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = FieldTrackTheme.type.label,
                        color = colors.ink,
                    )
                }
            },
        )
    }
}

/** The pin as a farmer would never read it, but an engineer checking it would. */
@Composable
private fun coordinates(state: FieldEditState): String {
    val lat = state.latitude ?: return ""
    val lng = state.longitude ?: return ""
    return "%.4f, %.4f".format(lat, lng)
}

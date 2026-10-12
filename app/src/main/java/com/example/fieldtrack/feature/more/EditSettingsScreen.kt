package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditSettingsState(
    val name: String = "",
    /** The letterhead. Each one prints only when it is filled in. */
    val phone: String = "",
    val addressLine: String = "",
    val gstin: String = "",
    val upiId: String = "",
    /** Null on the profile form, which has no land unit to set. */
    val landUnit: MeasureUnit? = null,
    val allowed: Boolean = true,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving && allowed
}

/** Correcting your own name. Everyone may do this; it is their own record. */
class EditProfileViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(EditSettingsState())
    val state: StateFlow<EditSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            _state.update { it.copy(name = s.displayName) }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        app.sessionStore.updateProfile(current.name)
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/**
 * Renaming the business and changing the unit it measures land in.
 *
 * The unit is the riskier of the two: it is what every new job is quoted in, so
 * changing it here changes tomorrow's bills. Work already recorded keeps the unit it
 * was measured in, which is why the khata is never retold in the new unit.
 */
class EditBusinessViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(EditSettingsState(landUnit = MeasureUnit.Acre))
    val state: StateFlow<EditSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val org = app.db.orgDao().observeOrg(s.orgId).first()
            _state.update {
                it.copy(
                    name = s.businessName,
                    phone = org?.phone.orEmpty(),
                    addressLine = org?.addressLine.orEmpty(),
                    gstin = org?.gstin.orEmpty(),
                    upiId = org?.upiId.orEmpty(),
                    landUnit = s.landUnit,
                    allowed = Capability.EditBusiness in s.can,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() }) }
    fun setAddress(v: String) = _state.update { it.copy(addressLine = v) }
    fun setGstin(v: String) = _state.update { it.copy(gstin = v.uppercase()) }
    fun setUpiId(v: String) = _state.update { it.copy(upiId = v) }
    fun setLandUnit(unit: MeasureUnit) = _state.update { it.copy(landUnit = unit) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        runCatching {
            app.sessionStore.updateBusiness(
                name = current.name,
                landUnit = current.landUnit ?: MeasureUnit.Acre,
                phone = current.phone,
                addressLine = current.addressLine,
                gstin = current.gstin,
                upiId = current.upiId,
            )
        }.onFailure { _state.update { s -> s.copy(allowed = false) } }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/** The letterhead editors, grouped so they travel as one argument. */
class LetterheadFields(
    val onPhone: (String) -> Unit,
    val onAddress: (String) -> Unit,
    val onGstin: (String) -> Unit,
    val onUpiId: (String) -> Unit,
)

/**
 * One short form behind both "Your details" and "Your business".
 *
 * They differ by a single field, so sharing the screen keeps the two from drifting
 * into two different ideas of what a settings form looks like.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSettingsScreen(
    state: EditSettingsState,
    titleRes: Int,
    nameLabelRes: Int,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onLandUnit: ((MeasureUnit) -> Unit)?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    nameHintRes: Int? = null,
    helperRes: Int? = null,
    /** Null on the profile form, which has no letterhead to edit. */
    onLetterhead: LetterheadFields? = null,
) {
    val dimens = FieldTrackTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(FieldTrackTheme.colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(titleRes),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_close_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FtTextField(
                label = stringResource(nameLabelRes),
                value = state.name,
                onValueChange = onName,
                placeholder = nameHintRes?.let { stringResource(it) }.orEmpty(),
                helper = helperRes?.let { stringResource(it) },
                focused = true,
            )

            if (onLetterhead != null) {
                // What a customer reads at the top of a bill. Optional throughout:
                // a farmer trading under his own name has a village and a phone, and
                // printing empty labels for the rest would look like an unfinished
                // form rather than a bill.
                Column {
                    FtSectionLabel(
                        stringResource(R.string.edit_bill_details),
                        Modifier.padding(bottom = 10.dp),
                    )
                    Text(
                        text = stringResource(R.string.edit_bill_details_note),
                        style = FieldTrackTheme.type.bodySmall,
                        color = FieldTrackTheme.colors.inkTertiary,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FtTextField(
                            label = stringResource(R.string.edit_business_phone),
                            value = state.phone,
                            onValueChange = onLetterhead.onPhone,
                            prefix = "+91",
                            keyboardType = KeyboardType.Phone,
                        )
                        FtTextField(
                            label = stringResource(R.string.edit_business_address),
                            value = state.addressLine,
                            onValueChange = onLetterhead.onAddress,
                            placeholder = stringResource(R.string.edit_business_address_hint),
                        )
                        FtTextField(
                            label = stringResource(R.string.edit_business_upi),
                            value = state.upiId,
                            onValueChange = onLetterhead.onUpiId,
                            placeholder = stringResource(R.string.edit_business_upi_hint),
                            helper = stringResource(R.string.edit_business_upi_note),
                        )
                        FtTextField(
                            label = stringResource(R.string.edit_business_gstin),
                            value = state.gstin,
                            onValueChange = onLetterhead.onGstin,
                            helper = stringResource(R.string.edit_business_gstin_note),
                        )
                    }
                }
            }

            if (onLandUnit != null && state.landUnit != null) {
                Column {
                    FtSectionLabel(
                        stringResource(R.string.onboard_land_unit),
                        Modifier.padding(bottom = 10.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            MeasureUnit.Acre, MeasureUnit.Bigha,
                            MeasureUnit.Kanal, MeasureUnit.Guntha,
                        ).forEach { unit ->
                            FtChip(
                                label = stringResource(unit.labelRes),
                                selected = state.landUnit == unit,
                                onClick = { onLandUnit(unit) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.edit_unit_note),
                        style = FieldTrackTheme.type.caption,
                        color = FieldTrackTheme.colors.inkTertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (!state.allowed) {
                Text(
                    text = stringResource(R.string.edit_not_allowed),
                    style = FieldTrackTheme.type.bodySmall,
                    color = FieldTrackTheme.colors.dueInk,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(
                text = stringResource(R.string.edit_save),
                onClick = onSave,
                enabled = state.canSave,
            )
        }
    }
}

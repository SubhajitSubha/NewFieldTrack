package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.region.PhoneField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditDetailsState(
    /** The person's own name, which everyone may correct. */
    val yourName: String = "",
    val businessName: String = "",
    /** The letterhead. Each one prints only when it is filled in. */
    val phone: String = "",
    val addressLine: String = "",
    val gstin: String = "",
    val upiId: String = "",
    /** The business's currency, which decides whether the form asks for a UPI id. */
    val currencyCode: String = "INR",
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** False for a driver, who sees only their own name: the business is not theirs to change. */
    val canEditBusiness: Boolean = false,
    /** A save the server or the rules turned down. */
    val refused: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
) {
    val canSave: Boolean
        get() = loaded && yourName.isNotBlank() && !saving &&
            (!canEditBusiness || businessName.isNotBlank())
}

/**
 * Everything about you and your business on one form with one Save.
 *
 * Name, bill details and land unit used to be six rows leading to two screens, which
 * made correcting a village and a phone number a round trip each. A person who is
 * editing is editing; they should not have to decide which door the field is behind.
 *
 * The unit is the riskier of the fields: it is what every new job is quoted in, so
 * changing it changes tomorrow's bills. Work already recorded keeps the unit it was
 * measured in, which is why the khata is never retold in the new unit.
 */
class EditDetailsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(EditDetailsState())
    val state: StateFlow<EditDetailsState> = _state.asStateFlow()
    private var originalName = ""

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val org = app.db.orgDao().observeOrg(s.orgId).first()
            originalName = s.displayName
            _state.update {
                it.copy(
                    yourName = s.displayName,
                    businessName = s.businessName,
                    phone = org?.phone.orEmpty(),
                    addressLine = org?.addressLine.orEmpty(),
                    gstin = org?.gstin.orEmpty(),
                    upiId = org?.upiId.orEmpty(),
                    landUnit = s.landUnit,
                    currencyCode = s.currencyCode,
                    canEditBusiness = Capability.EditBusiness in s.can,
                    loaded = true,
                )
            }
        }
    }

    fun setYourName(v: String) = _state.update { it.copy(yourName = v) }
    fun setBusinessName(v: String) = _state.update { it.copy(businessName = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v) }
    fun setAddress(v: String) = _state.update { it.copy(addressLine = v) }
    fun setGstin(v: String) = _state.update { it.copy(gstin = v.uppercase()) }
    fun setUpiId(v: String) = _state.update { it.copy(upiId = v) }
    fun setLandUnit(unit: MeasureUnit) = _state.update { it.copy(landUnit = unit) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true, refused = false) }
        if (current.yourName.trim() != originalName) app.sessionStore.updateProfile(current.yourName)
        if (current.canEditBusiness) {
            val failed = runCatching {
                app.sessionStore.updateBusiness(
                    name = current.businessName,
                    landUnit = current.landUnit,
                    phone = current.phone,
                    addressLine = current.addressLine,
                    gstin = current.gstin,
                    upiId = current.upiId,
                )
            }.isFailure
            if (failed) {
                _state.update { it.copy(saving = false, refused = true) }
                return@launch
            }
        }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/** The editors of the form, grouped so they travel as one argument. */
class EditDetailsActions(
    val onYourName: (String) -> Unit,
    val onBusinessName: (String) -> Unit,
    val onPhone: (String) -> Unit,
    val onAddress: (String) -> Unit,
    val onGstin: (String) -> Unit,
    val onUpiId: (String) -> Unit,
    val onLandUnit: (MeasureUnit) -> Unit,
)

private val LandUnitsOffered = listOf(
    MeasureUnit.Acre, MeasureUnit.Hectare, MeasureUnit.Bigha, MeasureUnit.Kanal, MeasureUnit.Guntha,
)

@Composable
fun EditDetailsScreen(
    state: EditDetailsState,
    actions: EditDetailsActions,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = FieldTrackTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(FieldTrackTheme.colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(R.string.edit_profile_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_close_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FtTextField(
                label = stringResource(R.string.onboard_your_name),
                value = state.yourName,
                onValueChange = actions.onYourName,
                placeholder = stringResource(R.string.onboard_your_name_hint),
            )

            if (state.canEditBusiness) {
                FtSectionLabel(stringResource(R.string.profile_business), Modifier.padding(top = 6.dp))
                FtTextField(
                    label = stringResource(R.string.onboard_business_name),
                    value = state.businessName,
                    onValueChange = actions.onBusinessName,
                    placeholder = stringResource(R.string.onboard_business_name_hint),
                    helper = stringResource(R.string.edit_business_name_note),
                )

                // What a customer reads at the top of a bill. Optional throughout:
                // a farmer trading under his own name has a village and a phone, and
                // printing empty labels for the rest would look like an unfinished
                // form rather than a bill.
                Text(
                    text = stringResource(R.string.edit_bill_details_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = FieldTrackTheme.colors.inkTertiary,
                )
                PhoneField(
                    label = stringResource(R.string.edit_business_phone),
                    value = state.phone,
                    onValueChange = actions.onPhone,
                )
                FtTextField(
                    label = stringResource(R.string.edit_business_address),
                    value = state.addressLine,
                    onValueChange = actions.onAddress,
                    placeholder = stringResource(R.string.edit_business_address_hint),
                )
                // UPI is how people pay in India; elsewhere the bank or mobile-money
                // details live under Money and region.
                if (state.currencyCode == "INR") FtTextField(
                    label = stringResource(R.string.edit_business_upi),
                    value = state.upiId,
                    onValueChange = actions.onUpiId,
                    placeholder = stringResource(R.string.edit_business_upi_hint),
                    helper = stringResource(R.string.edit_business_upi_note),
                )
                FtTextField(
                    label = stringResource(R.string.edit_business_gstin),
                    value = state.gstin,
                    onValueChange = actions.onGstin,
                    helper = stringResource(R.string.edit_business_gstin_note),
                )

                FtPick(
                    options = LandUnitsOffered,
                    selected = state.landUnit,
                    text = { stringResource(it.labelRes) },
                    onPick = actions.onLandUnit,
                    label = stringResource(R.string.onboard_land_unit),
                    searchHint = stringResource(R.string.pick_search),
                )
                Text(
                    text = stringResource(R.string.edit_unit_note),
                    style = FieldTrackTheme.type.caption,
                    color = FieldTrackTheme.colors.inkTertiary,
                )
            }

            if (state.refused) {
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

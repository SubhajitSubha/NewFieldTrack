package com.example.fieldtrack.feature.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.auth.formatPhone
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.region.PhoneField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields of the form, as typed. Compared with what was loaded to know whether anything changed. */
data class ProfileFields(
    /** The person's own name, which everyone may correct. */
    val yourName: String = "",
    val businessName: String = "",
    /** The letterhead. Each one prints only when it is filled in. */
    val phone: String = "",
    val addressLine: String = "",
    val gstin: String = "",
    val upiId: String = "",
)

data class ProfileState(
    val fields: ProfileFields = ProfileFields(),
    /** What is saved now: the form has changes when [fields] differs from it. */
    val saved: ProfileFields = ProfileFields(),
    val role: MemberRole = MemberRole.Owner,
    /** The number this person signs in with: shown, never edited here. */
    val signedInPhone: String? = null,
    /** The business's currency, which decides whether the form asks for a UPI id. */
    val currencyCode: String = "INR",
    /** False for a driver, who sees only their own name: the business is not theirs to change. */
    val canEditBusiness: Boolean = false,
    /** A save the server or the rules turned down. */
    val refused: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    /** True right after a save, until the next change: the page says it worked. */
    val justSaved: Boolean = false,
) {
    val changed: Boolean get() = loaded && fields != saved
    val canSave: Boolean
        get() = changed && fields.yourName.isNotBlank() && !saving &&
            (!canEditBusiness || fields.businessName.isNotBlank())
}

/**
 * You, and the business you trade as, on one page that is itself the form.
 *
 * The same details the person gave when they started, shown filled in and changed in
 * place. Nothing on it is a row leading somewhere else; a Save appears once something
 * has actually changed, and leaving with changes asks first. Preferences are not here:
 * they live in Settings.
 */
class ProfileViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val org = app.db.orgDao().observeOrg(s.orgId).first()
            val loaded = ProfileFields(
                yourName = s.displayName,
                businessName = org?.name?.ifBlank { null } ?: s.businessName,
                phone = org?.phone.orEmpty(),
                addressLine = org?.addressLine.orEmpty(),
                gstin = org?.gstin.orEmpty(),
                upiId = org?.upiId.orEmpty(),
            )
            _state.update {
                it.copy(
                    fields = loaded,
                    saved = loaded,
                    role = s.role,
                    signedInPhone = app.sessionStore.signedInPhone(),
                    currencyCode = s.currencyCode,
                    canEditBusiness = Capability.EditBusiness in s.can,
                    loaded = true,
                )
            }
        }
    }

    private fun edit(change: (ProfileFields) -> ProfileFields) =
        _state.update { it.copy(fields = change(it.fields), justSaved = false, refused = false) }

    fun setYourName(v: String) = edit { it.copy(yourName = v) }
    fun setBusinessName(v: String) = edit { it.copy(businessName = v) }
    fun setPhone(v: String) = edit { it.copy(phone = v) }
    fun setAddress(v: String) = edit { it.copy(addressLine = v) }
    fun setGstin(v: String) = edit { it.copy(gstin = v.uppercase()) }
    fun setUpiId(v: String) = edit { it.copy(upiId = v) }

    fun save() = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        val f = current.fields
        _state.update { it.copy(saving = true, refused = false) }
        if (f.yourName.trim() != current.saved.yourName.trim()) app.sessionStore.updateProfile(f.yourName)
        if (current.canEditBusiness && f.copy(yourName = "") != current.saved.copy(yourName = "")) {
            val failed = runCatching {
                app.sessionStore.updateBusiness(
                    name = f.businessName,
                    // The unit is set in Settings; read it fresh so this form never undoes it.
                    landUnit = app.sessionStore.current()?.landUnit ?: return@runCatching,
                    phone = f.phone,
                    addressLine = f.addressLine,
                    gstin = f.gstin,
                    upiId = f.upiId,
                )
            }.isFailure
            if (failed) {
                _state.update { it.copy(saving = false, refused = true) }
                return@launch
            }
        }
        _state.update { it.copy(saving = false, saved = f, justSaved = true) }
    }

    /** Puts the form back to what is saved, for somebody leaving without saving. */
    fun discard() = _state.update { it.copy(fields = it.saved, refused = false) }
}

/** The editors of the form, grouped so they travel as one argument. */
class ProfileActions(
    val onYourName: (String) -> Unit,
    val onBusinessName: (String) -> Unit,
    val onPhone: (String) -> Unit,
    val onAddress: (String) -> Unit,
    val onGstin: (String) -> Unit,
    val onUpiId: (String) -> Unit,
    val onSave: () -> Unit,
    val onDiscard: () -> Unit,
)

@Composable
fun ProfileScreen(
    state: ProfileState,
    actions: ProfileActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val f = state.fields
    var askLeave by rememberSaveable { mutableStateOf(false) }
    val back = { if (state.changed) askLeave = true else onBack() }
    BackHandler(enabled = state.changed) { askLeave = true }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(R.string.profile_title),
            onBack = back,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Who this is: shown, not offered for editing. The phone number is how they
            // sign in, and the role is the owner's to give.
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FtAvatar(
                    initials = initialsOf(f.yourName),
                    size = 76.dp,
                    background = colors.primary,
                    contentColor = colors.onPrimary,
                )
                Text(
                    text = listOfNotNull(
                        stringResource(
                            when (state.role) {
                                MemberRole.Owner -> R.string.more_owner
                                MemberRole.Viewer -> R.string.more_viewer
                                MemberRole.Operator -> R.string.more_operator
                            }
                        ),
                        state.signedInPhone?.let { formatPhone(it) },
                    ).joinToString(" · "),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            FtTextField(
                label = stringResource(R.string.onboard_your_name),
                value = f.yourName,
                onValueChange = actions.onYourName,
                placeholder = stringResource(R.string.onboard_your_name_hint),
            )

            if (state.canEditBusiness) {
                FtSectionLabel(stringResource(R.string.profile_business), Modifier.padding(top = 6.dp))
                FtTextField(
                    label = stringResource(R.string.onboard_business_name),
                    value = f.businessName,
                    onValueChange = actions.onBusinessName,
                    placeholder = stringResource(R.string.onboard_business_name_hint),
                )
                // What a customer reads at the top of a bill. Optional throughout:
                // a farmer trading under his own name has a village and a phone, and
                // printing empty labels for the rest would look like an unfinished
                // form rather than a bill.
                Text(
                    text = stringResource(R.string.edit_bill_details_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkTertiary,
                )
                PhoneField(
                    label = stringResource(R.string.edit_business_phone),
                    value = f.phone,
                    onValueChange = actions.onPhone,
                )
                FtTextField(
                    label = stringResource(R.string.edit_business_address),
                    value = f.addressLine,
                    onValueChange = actions.onAddress,
                    placeholder = stringResource(R.string.edit_business_address_hint),
                )
                // UPI is how people pay in India; elsewhere the bank or mobile-money
                // details live under Money and region.
                if (state.currencyCode == "INR") FtTextField(
                    label = stringResource(R.string.edit_business_upi),
                    value = f.upiId,
                    onValueChange = actions.onUpiId,
                    placeholder = stringResource(R.string.edit_business_upi_hint),
                    helper = stringResource(R.string.edit_business_upi_note),
                )
                FtTextField(
                    label = stringResource(R.string.edit_business_gstin),
                    value = f.gstin,
                    onValueChange = actions.onGstin,
                    helper = stringResource(R.string.edit_business_gstin_note),
                )
            }

            if (state.refused) {
                Text(
                    text = stringResource(R.string.edit_not_allowed),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.dueInk,
                )
            }
            if (state.justSaved) {
                Text(
                    text = stringResource(R.string.profile_saved),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.money,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        // Only when there is something to save: a button that does nothing is a
        // button that teaches people to stop trusting buttons.
        if (state.changed) {
            Box(Modifier.padding(dimens.screenPadding)) {
                FtButton(
                    text = stringResource(R.string.edit_save),
                    onClick = actions.onSave,
                    enabled = state.canSave,
                )
            }
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.profile_unsaved_title)) },
            text = { Text(stringResource(R.string.profile_unsaved_body)) },
            confirmButton = {
                TextButton(onClick = { askLeave = false; actions.onSave() }) {
                    Text(stringResource(R.string.edit_save), color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { askLeave = false; actions.onDiscard(); onBack() }) {
                    Text(stringResource(R.string.profile_unsaved_leave), color = colors.dueInk)
                }
            },
        )
    }
}

package com.example.fieldtrack.feature.customer

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.voice.rememberVoiceInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddCustomerState(
    val name: String = "",
    val phone: String = "",
    val village: String = "",
    val openingText: String = "",
    val saving: Boolean = false,
) {
    val openingRupees: Long get() = openingText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

class AddCustomerViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(AddCustomerState())
    val state: StateFlow<AddCustomerState> = _state.asStateFlow()

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() }) }
    fun setVillage(v: String) = _state.update { it.copy(village = v) }
    fun setOpening(v: String) = _state.update { it.copy(openingText = v.filter { c -> c.isDigit() }) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        // Null only if this ran before setup, which the startup gate prevents.
        val session = app.sessionStore.load() ?: return@launch
        app.directoryRepository.addCustomer(
            orgId = session.orgId,
            name = current.name,
            phone = current.phone.ifBlank { null },
            village = current.village.ifBlank { null },
            openingBalanceRupees = current.openingRupees,
            byPersonId = session.personId,
        )
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/**
 * The customer form, used both to add and to correct.
 *
 * [onOpening] is null when editing: an opening balance is a dated line on the khata,
 * not a field on the customer, and offering it again here would double-count.
 */
@Composable
fun AddCustomerScreen(
    state: AddCustomerState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onVillage: (String) -> Unit,
    onOpening: ((String) -> Unit)?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    titleRes: Int = R.string.customer_new_title,
    saveLabelRes: Int = R.string.customer_save,
    readOnlyNotice: String? = null,
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
            // Typing a Punjabi or Telugu name on a phone keyboard is the single most
            // common place these users give up, so offer the microphone — and hide it
            // entirely on a handset with no recogniser rather than show a dead button.
            val voice = rememberVoiceInput(
                prompt = stringResource(R.string.customer_name_hint),
                onText = onName,
            )
            FtTextField(
                label = stringResource(R.string.customer_name),
                value = state.name,
                onValueChange = onName,
                placeholder = stringResource(R.string.customer_name_hint),
                focused = true,
                onVoiceInput = if (voice.available) voice.start else null,
                voiceContentDescription = stringResource(R.string.customer_speak_name_cd),
            )
            FtTextField(
                label = stringResource(R.string.customer_mobile),
                value = state.phone,
                onValueChange = onPhone,
                prefix = "+91",
                keyboardType = KeyboardType.Phone,
                helper = stringResource(R.string.customer_whatsapp_note),
            )
            FtTextField(
                label = stringResource(R.string.customer_village),
                value = state.village,
                onValueChange = onVillage,
            )
            if (onOpening != null) {
                FtTextField(
                    label = stringResource(R.string.customer_already_owes),
                    value = state.openingText,
                    onValueChange = onOpening,
                    prefix = "₹",
                    keyboardType = KeyboardType.Number,
                    helper = stringResource(R.string.customer_already_owes_note),
                )
            }
            if (readOnlyNotice != null) {
                Text(
                    text = readOnlyNotice,
                    style = FieldTrackTheme.type.bodySmall,
                    color = FieldTrackTheme.colors.dueInk,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(
                text = stringResource(saveLabelRes),
                onClick = onSave,
                enabled = state.canSave && readOnlyNotice == null,
            )
        }
    }
}

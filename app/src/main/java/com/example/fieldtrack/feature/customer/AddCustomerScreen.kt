package com.example.fieldtrack.feature.customer

import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.feature.region.PhoneField
import androidx.compose.foundation.background
import com.example.fieldtrack.designsystem.format.MoneyInput
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
    /** The most they may owe before work for them is flagged. Empty for no limit. */
    val creditLimitText: String = "",
    /** The language they read, for reminders and bills sent to them. Null follows the app. */
    val language: String? = null,
    val saving: Boolean = false,
) {
    val openingMinor: Long get() = MoneyInput.parse(openingText) ?: 0L
    val creditLimitMinor: Long? get() = MoneyInput.parse(creditLimitText)?.takeIf { it > 0 }
    val terms: com.example.fieldtrack.data.repository.CustomerTerms
        get() = com.example.fieldtrack.data.repository.CustomerTerms(language, creditLimitMinor)
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

class AddCustomerViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(AddCustomerState())
    val state: StateFlow<AddCustomerState> = _state.asStateFlow()

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v) }
    fun setVillage(v: String) = _state.update { it.copy(village = v) }
    fun setOpening(v: String) = _state.update { it.copy(openingText = MoneyInput.clean(v)) }
    fun setCreditLimit(v: String) = _state.update { it.copy(creditLimitText = MoneyInput.clean(v)) }
    fun setLanguage(tag: String?) = _state.update { it.copy(language = tag) }

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
            openingBalanceMinor = current.openingMinor,
            byPersonId = session.personId,
            terms = current.terms,
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
@OptIn(ExperimentalLayoutApi::class)
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
    onCreditLimit: ((String) -> Unit)? = null,
    onLanguage: ((String?) -> Unit)? = null,
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
            PhoneField(
                label = stringResource(R.string.customer_mobile),
                value = state.phone,
                onValueChange = onPhone,
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
                    prefix = Money.style.symbol,
                    keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
                    helper = stringResource(R.string.customer_already_owes_note),
                )
            }
            if (onCreditLimit != null) {
                FtTextField(
                    label = stringResource(R.string.customer_credit_limit),
                    value = state.creditLimitText,
                    onValueChange = onCreditLimit,
                    prefix = Money.style.symbol,
                    keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
                    helper = stringResource(R.string.customer_credit_limit_note),
                )
            }
            // Only when there is more than English to read in.
            if (onLanguage != null && com.example.fieldtrack.data.settings.AppLanguage.SUPPORTED.size > 1) {
                FtSectionLabel(stringResource(R.string.customer_language))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FtChip(
                        label = stringResource(R.string.language_auto),
                        selected = state.language == null,
                        onClick = { onLanguage(null) },
                    )
                    com.example.fieldtrack.data.settings.AppLanguage.SUPPORTED.forEach { tag ->
                        FtChip(
                            label = com.example.fieldtrack.data.settings.AppLanguage.nativeName(tag),
                            selected = state.language == tag,
                            onClick = { onLanguage(tag) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.customer_language_note),
                    style = FieldTrackTheme.type.caption,
                    color = FieldTrackTheme.colors.inkTertiary,
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

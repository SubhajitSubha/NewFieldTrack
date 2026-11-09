package com.example.fieldtrack.feature.orgs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.region.CurrencyPickerSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewBusinessState(
    val name: String = "",
    val currencyCode: String = "INR",
    val guessedCurrency: String? = null,
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** The name of the business's own land unit, when it chose one. */
    val ownLandUnit: String = "",
    val fiscalStartMonth: Int = 4,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

/**
 * A second business of one's own, with its own books and its own currency.
 *
 * Starts from what the phone says, as the first one did, and asks only for a name and for
 * the two things that cost something to get wrong.
 */
class NewBusinessViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(
        Region.detect(app).let { guess ->
            NewBusinessState(
                currencyCode = guess.currencyCode,
                guessedCurrency = guess.currencyCode,
                landUnit = guess.landUnit,
                fiscalStartMonth = guess.fiscalStartMonth,
            )
        }
    )
    val state: StateFlow<NewBusinessState> = _state.asStateFlow()

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setCurrency(code: String) = _state.update { it.copy(currencyCode = code) }
    fun setLandUnit(unit: MeasureUnit) = _state.update { it.copy(landUnit = unit) }
    fun setOwnLandUnit(v: String) = _state.update { it.copy(ownLandUnit = v) }

    fun create(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (!s.canSave) return@launch
        _state.update { it.copy(saving = true) }
        app.sessionStore.startAnotherBusiness(
            businessName = s.name,
            landUnit = s.landUnit,
            currencyCode = s.currencyCode,
            moneyStepMinor = MoneyStyle.defaultStepMinor(s.currencyCode),
            fiscalStartMonth = s.fiscalStartMonth,
            ownLandUnit = s.ownLandUnit.takeIf { s.landUnit == MeasureUnit.OwnLand },
        )
        _state.update { it.copy(saving = false) }
        onDone()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewBusinessScreen(
    state: NewBusinessState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onLandUnit: (MeasureUnit) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    onOwnLandUnit: (String) -> Unit = {},
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(colors.paper).imePadding()) {
        FtTopBar(
            title = stringResource(R.string.newbiz_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.newbiz_caption),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
            FtTextField(
                label = stringResource(R.string.onboard_business_name),
                value = state.name,
                onValueChange = onName,
                placeholder = stringResource(R.string.onboard_business_name_hint),
                focused = true,
            )
            FtSectionLabel(stringResource(R.string.onboard_money_in))
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = Region.nameOf(state.currencyCode),
                    subtitle = "${MoneyStyle.symbolOf(state.currencyCode)} · ${state.currencyCode}",
                    showChevron = true,
                    onClick = { picking = true },
                )
            }
            FtSectionLabel(stringResource(R.string.onboard_land_unit))
            com.example.fieldtrack.feature.region.LandUnitChoice(
                selected = state.landUnit,
                ownName = state.ownLandUnit,
                onUnit = onLandUnit,
                onOwnName = onOwnLandUnit,
                chips = true,
                label = stringResource(R.string.onboard_land_unit),
            )
            Box(Modifier.padding(bottom = dimens.space4))
        }
        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(
                text = stringResource(R.string.newbiz_create),
                onClick = onCreate,
                enabled = state.canSave,
            )
        }
    }
    if (picking) {
        CurrencyPickerSheet(
            current = state.currencyCode,
            guessed = state.guessedCurrency,
            onPick = { onCurrency(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

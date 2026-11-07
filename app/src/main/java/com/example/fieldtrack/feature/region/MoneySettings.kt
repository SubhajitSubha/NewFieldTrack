package com.example.fieldtrack.feature.region

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.feature.season.monthName
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoneySettingsState(
    val loaded: Boolean = false,
    /** False for anybody but whoever may edit the business: it is read here, not changed. */
    val allowed: Boolean = true,
    val currencyCode: String = "INR",
    val guessedCurrency: String? = null,
    /** True once amounts have been written, after which the currency cannot be changed. */
    val locked: Boolean = false,
    val stepMinor: Long = 100,
    val fiscalStartMonth: Int = 4,
    /** Null follows the phone. */
    val timeZoneId: String? = null,
    val saving: Boolean = false,
) {
    val digits: Int get() = MoneyStyle.digitsOf(currencyCode)

    /** Whether this currency has a small coin to choose about at all. */
    val hasSmallCoin: Boolean get() = digits > 0

    val wholeUnits: Boolean get() = stepMinor >= MoneyStyle.pow10(digits)

    val canSave: Boolean get() = loaded && allowed && !saving
}

/**
 * The business's currency and how its bills are rounded.
 *
 * The currency was chosen when the business was set up from what the phone said, and
 * can still be changed here until the books have an amount in them. The rounding is
 * a habit rather than a rule, so it can be changed whenever; a business that never
 * deals in the small coin keeps every bill a whole number.
 */
class MoneySettingsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(MoneySettingsState())
    val state: StateFlow<MoneySettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val org = app.db.orgDao().observeOrg(s.orgId).first()
            val locked = app.db.orgDao().moneyEntryCount(s.orgId) > 0
            _state.update {
                it.copy(
                    loaded = true,
                    allowed = Capability.EditBusiness in s.can,
                    currencyCode = org?.currencyCode ?: s.currencyCode,
                    guessedCurrency = Region.detect(app).currencyCode,
                    locked = locked,
                    stepMinor = org?.moneyStepMinor ?: s.moneyStepMinor,
                    fiscalStartMonth = org?.fiscalStartMonth ?: s.fiscalStartMonth,
                    timeZoneId = org?.timeZoneId ?: s.timeZoneId,
                )
            }
        }
    }

    fun setCurrency(code: String) = _state.update {
        if (it.locked || !it.allowed) it
        else it.copy(currencyCode = code, stepMinor = MoneyStyle.defaultStepMinor(code))
    }

    fun setWholeUnits(whole: Boolean) = _state.update {
        it.copy(stepMinor = if (whole) MoneyStyle.pow10(it.digits) else 1L)
    }

    fun setFiscalStartMonth(month: Int) = _state.update { if (it.allowed) it.copy(fiscalStartMonth = month) else it }

    fun setTimeZone(id: String?) = _state.update { if (it.allowed) it.copy(timeZoneId = id) else it }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val result = runCatching {
            app.sessionStore.updateMoney(current.currencyCode, current.stepMinor)
        }.getOrNull()
        if (result == SessionStore.MoneyChange.Saved) {
            runCatching { app.sessionStore.updateCalendar(current.fiscalStartMonth, current.timeZoneId) }
        }
        _state.update {
            it.copy(
                saving = false,
                locked = it.locked || result == SessionStore.MoneyChange.CurrencyLocked,
            )
        }
        if (result == SessionStore.MoneyChange.Saved) onSaved()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneySettingsScreen(
    state: MoneySettingsState,
    onBack: () -> Unit,
    onCurrency: (String) -> Unit,
    onWholeUnits: (Boolean) -> Unit,
    onFiscalStartMonth: (Int) -> Unit,
    onTimeZone: (String?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickingZone by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.money_title),
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
            FtSectionLabel(stringResource(R.string.money_currency))
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = Region.nameOf(state.currencyCode),
                    subtitle = "${MoneyStyle.symbolOf(state.currencyCode)} · ${state.currencyCode}",
                    showChevron = state.allowed && !state.locked,
                    onClick = { picking = true }.takeIf { state.allowed && !state.locked },
                )
            }
            if (state.locked) {
                Text(
                    text = stringResource(R.string.money_locked_body),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }

            FtSectionLabel(stringResource(R.string.money_round), Modifier.padding(top = 4.dp))
            if (state.hasSmallCoin) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FtChip(
                        label = stringResource(R.string.money_round_whole),
                        selected = state.wholeUnits,
                        onClick = { if (state.allowed) onWholeUnits(true) },
                    )
                    FtChip(
                        label = stringResource(R.string.money_round_exact),
                        selected = !state.wholeUnits,
                        onClick = { if (state.allowed) onWholeUnits(false) },
                    )
                }
                Text(
                    text = stringResource(
                        if (state.wholeUnits) R.string.money_round_note_whole else R.string.money_round_note_exact
                    ),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            } else {
                Text(
                    text = stringResource(R.string.money_round_none),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }

            FtSectionLabel(stringResource(R.string.money_fiscal), Modifier.padding(top = 4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..12).forEach { month ->
                    FtChip(
                        label = monthName(month),
                        selected = state.fiscalStartMonth == month,
                        onClick = { if (state.allowed) onFiscalStartMonth(month) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.money_fiscal_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )

            FtSectionLabel(stringResource(R.string.zone_title), Modifier.padding(top = 4.dp))
            FtCard(padding = 0.dp) {
                val zone = state.timeZoneId
                FtListRow(
                    title = if (zone == null) stringResource(R.string.zone_phone)
                    else zone.substringAfterLast('/').replace('_', ' '),
                    subtitle = (zone ?: java.time.ZoneId.systemDefault().id) + " · " +
                        Region.offsetLabel(com.example.fieldtrack.data.region.BusinessClock.zoneOf(zone)),
                    showChevron = state.allowed,
                    onClick = { pickingZone = true }.takeIf { state.allowed },
                )
            }
            Text(
                text = stringResource(R.string.zone_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )

            if (!state.allowed) {
                Text(
                    text = stringResource(R.string.edit_not_allowed),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.dueInk,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        if (state.allowed) {
            Box(Modifier.padding(dimens.screenPadding)) {
                FtButton(
                    text = stringResource(R.string.edit_save),
                    onClick = onSave,
                    enabled = state.canSave,
                )
            }
        }
    }

    if (pickingZone) {
        TimeZonePickerSheet(
            currentId = state.timeZoneId,
            onPick = { onTimeZone(it); pickingZone = false },
            onDismiss = { pickingZone = false },
        )
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

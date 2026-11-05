package com.example.fieldtrack.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlanSettingsState(
    val loaded: Boolean = false,
    /** False for anybody but the owner: how the plan is read is the business's, not a driver's to change. */
    val allowed: Boolean = true,
    val settings: PlanSettings = PlanSettings(),
    val saving: Boolean = false,
    val nowMillis: Long = 0,
) {
    val canStepEarlier: Boolean get() = settings.dayStartHour > 0
    val canStepLater: Boolean get() = settings.dayStartHour < PlanSettings.MAX_DAY_START_HOUR

    /** The day the plan is on at this moment, so the setting can be checked against the clock. */
    val planDay: Long get() = PlanDay.today(nowMillis, settings.dayStartHour)

    /** True when it is earlier than the day starts, so the plan is still on the day before. */
    val stillYesterday: Boolean
        get() = planDay < LocalDate.now().toEpochDay()
}

/** How this business reads its plan: when the day turns over, and what drivers see. */
class PlanSettingsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(PlanSettingsState())
    val state: StateFlow<PlanSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val settings = app.directoryRepository.observePlanSettings(s.orgId).first()
            _state.update {
                it.copy(
                    loaded = true,
                    allowed = Capability.EditBusiness in s.can,
                    settings = settings,
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    fun stepHour(by: Int) = _state.update {
        it.copy(settings = it.settings.copy(dayStartHour = (it.settings.dayStartHour + by).coerceIn(0, PlanSettings.MAX_DAY_START_HOUR)))
    }

    fun setDriversSeeAll(all: Boolean) = _state.update { it.copy(settings = it.settings.copy(driversSeeAll = all)) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        runCatching { app.sessionStore.updatePlanSettings(_state.value.settings) }
            .onSuccess { _state.update { it.copy(saving = false) }; onSaved() }
            // Refused: the owner's permission was taken away while this was open.
            .onFailure { _state.update { it.copy(saving = false, allowed = false) } }
    }
}

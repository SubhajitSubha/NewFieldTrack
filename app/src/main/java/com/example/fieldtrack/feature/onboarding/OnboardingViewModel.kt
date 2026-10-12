package com.example.fieldtrack.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which track the owner picked. Decides whether we ask for a business name. */
enum class OwnerKind { Solo, Business }

enum class OnboardingStep { Welcome, Role, You, Business, Machine, Done }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val ownerKind: OwnerKind = OwnerKind.Solo,
    val name: String = "",
    val businessName: String = "",
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    val machineKind: MachineKind = MachineKind.Tractor,
    val machineName: String = "",
    val saving: Boolean = false,
) {
    /** A solo owner bills under their own name unless they give the business one. */
    val effectiveBusinessName: String
        get() = businessName.trim().ifBlank { name.trim() }

    val canAdvance: Boolean
        get() = when (step) {
            OnboardingStep.You -> name.isNotBlank()
            OnboardingStep.Business -> effectiveBusinessName.isNotBlank()
            else -> true
        }
}

/**
 * First run.
 *
 * Collects the least that makes the rest of the app honest: a name for the person, a
 * name for the business so a bill has a sender, the land unit this region actually
 * uses, and optionally the first machine. No phone number and no account — see
 * [com.example.fieldtrack.data.session.SessionStore.completeOnboarding] for why.
 */
class OnboardingViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setOwnerKind(kind: OwnerKind) = _state.update { it.copy(ownerKind = kind) }
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setBusinessName(v: String) = _state.update { it.copy(businessName = v) }
    fun setLandUnit(unit: MeasureUnit) = _state.update { it.copy(landUnit = unit) }
    fun setMachineKind(kind: MachineKind) = _state.update { it.copy(machineKind = kind) }
    fun setMachineName(v: String) = _state.update { it.copy(machineName = v) }

    fun next() = _state.update { it.copy(step = it.step.next(it.ownerKind)) }

    /** Returns true when there is nowhere further back to go. */
    fun back(): Boolean {
        val current = _state.value
        val previous = current.step.previous(current.ownerKind) ?: return true
        _state.update { it.copy(step = previous) }
        return false
    }

    /**
     * Writes the business, the membership and the first machine, then shows the
     * confirmation step. The app itself does not take over until [start].
     */
    fun finishSetup() = viewModelScope.launch {
        val current = _state.value
        if (current.saving) return@launch
        _state.update { it.copy(saving = true) }

        val session = app.sessionStore.completeOnboarding(
            displayName = current.name,
            businessName = current.effectiveBusinessName,
            landUnit = current.landUnit,
            isBusiness = current.ownerKind == OwnerKind.Business,
        )

        if (current.machineName.isNotBlank()) {
            app.directoryRepository.addMachine(
                orgId = session.orgId,
                name = current.machineName,
                kind = current.machineKind,
            )
        }

        _state.update { it.copy(saving = false, step = OnboardingStep.Done) }
    }

    /** Leaves onboarding for the app proper. */
    fun start() = viewModelScope.launch { app.sessionStore.activate() }
}

private fun OnboardingStep.next(kind: OwnerKind): OnboardingStep = when (this) {
    OnboardingStep.Welcome -> OnboardingStep.Role
    OnboardingStep.Role -> OnboardingStep.You
    // A solo owner is their own business; asking them to name one twice is friction.
    OnboardingStep.You -> if (kind == OwnerKind.Business) OnboardingStep.Business
    else OnboardingStep.Machine
    OnboardingStep.Business -> OnboardingStep.Machine
    OnboardingStep.Machine -> OnboardingStep.Done
    OnboardingStep.Done -> OnboardingStep.Done
}

private fun OnboardingStep.previous(kind: OwnerKind): OnboardingStep? = when (this) {
    OnboardingStep.Welcome -> null
    OnboardingStep.Role -> OnboardingStep.Welcome
    OnboardingStep.You -> OnboardingStep.Role
    OnboardingStep.Business -> OnboardingStep.You
    OnboardingStep.Machine -> if (kind == OwnerKind.Business) OnboardingStep.Business
    else OnboardingStep.You
    OnboardingStep.Done -> OnboardingStep.Machine
}

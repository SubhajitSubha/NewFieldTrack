package com.example.fieldtrack.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.designsystem.format.MoneyStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which track the owner picked. Decides whether we ask for a business name. */
enum class OwnerKind { Solo, Business }

enum class OnboardingStep { Welcome, Role, You, Business, Region, Machine, Done }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val ownerKind: OwnerKind = OwnerKind.Solo,
    val name: String = "",
    val businessName: String = "",
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** The name of the business's own land unit, when it chose one. */
    val ownLandUnit: String = "",
    /** The currency the business keeps its books in; guessed from the phone, confirmed on the Region step. */
    val currencyCode: String = "INR",
    /** The country the phone guessed, which puts that country's currency first in the picker. */
    val guessedCurrency: String? = null,
    /** From the phone's country, used without asking; changeable in Profile. */
    val fiscalStartMonth: Int = 4,
    val machineKind: MachineKind = MachineKind.Tractor,
    val machineName: String = "",
    /** What the owner calls a kind of machine the list does not have. */
    val machineTypeLabel: String = "",
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

    private val _state = MutableStateFlow(
        Region.detect(app).let { guess ->
            OnboardingState(
                currencyCode = guess.currencyCode,
                guessedCurrency = guess.currencyCode,
                landUnit = guess.landUnit,
                fiscalStartMonth = guess.fiscalStartMonth,
            )
        }
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setOwnerKind(kind: OwnerKind) = _state.update { it.copy(ownerKind = kind) }
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setBusinessName(v: String) = _state.update { it.copy(businessName = v) }
    fun setLandUnit(unit: MeasureUnit) = _state.update { it.copy(landUnit = unit) }
    fun setOwnLandUnit(v: String) = _state.update { it.copy(ownLandUnit = v) }
    fun setCurrency(code: String) = _state.update { it.copy(currencyCode = code) }
    fun setMachineKind(kind: MachineKind) = _state.update { it.copy(machineKind = kind) }
    fun setMachineName(v: String) = _state.update { it.copy(machineName = v) }
    fun setMachineTypeLabel(v: String) = _state.update { it.copy(machineTypeLabel = v.take(MAX_TYPE_LABEL)) }

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
            currencyCode = current.currencyCode,
            moneyStepMinor = MoneyStyle.defaultStepMinor(current.currencyCode),
            fiscalStartMonth = current.fiscalStartMonth,
            ownLandUnit = current.ownLandUnit.takeIf { current.landUnit == MeasureUnit.OwnLand },
        )

        if (current.machineName.isNotBlank()) {
            app.directoryRepository.addMachine(
                orgId = session.orgId,
                name = current.machineName,
                kind = current.machineKind,
                typeLabel = current.machineTypeLabel.trim().ifBlank { null }.takeIf { current.machineKind == MachineKind.Other },
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
    else OnboardingStep.Region
    OnboardingStep.Business -> OnboardingStep.Region
    OnboardingStep.Region -> OnboardingStep.Machine
    OnboardingStep.Machine -> OnboardingStep.Done
    OnboardingStep.Done -> OnboardingStep.Done
}

private fun OnboardingStep.previous(kind: OwnerKind): OnboardingStep? = when (this) {
    OnboardingStep.Welcome -> null
    OnboardingStep.Role -> OnboardingStep.Welcome
    OnboardingStep.You -> OnboardingStep.Role
    OnboardingStep.Business -> OnboardingStep.You
    OnboardingStep.Region -> if (kind == OwnerKind.Business) OnboardingStep.Business
    else OnboardingStep.You
    OnboardingStep.Machine -> OnboardingStep.Region
    OnboardingStep.Done -> OnboardingStep.Machine
}

/** As on the full machine form. */
private const val MAX_TYPE_LABEL = 40

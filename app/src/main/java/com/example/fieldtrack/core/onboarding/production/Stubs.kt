package com.example.fieldtrack.core.onboarding.production

import com.example.fieldtrack.core.session.identityfoundation.SessionManager
import com.example.fieldtrack.data.repository.OrganizationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ProductionOnboardingStep {
    OTP, WELCOME, ROLE_SELECTION, PROFILE, OWNER_ORGANIZATION, JOIN_OPTIONS, OPERATOR_INVITE, JOIN_PREVIEW, SUCCESS, COMPLETED
}

data class ProductionOnboardingState(
    val step: ProductionOnboardingStep = ProductionOnboardingStep.OTP,
    val userId: String? = null,
    val displayName: String = "",
    val mobileNumber: String = "",
    val organizationName: String = "",
    val role: String? = null
)

object ProductionOnboardingPersistence {
    val state = MutableStateFlow(ProductionOnboardingState(step = ProductionOnboardingStep.COMPLETED))
    
    fun init(app: Any) {}
    fun persist(newState: ProductionOnboardingState) {
        state.value = newState
    }
}

class ProductionOnboardingEngine(
    val sessionManager: SessionManager,
    val organizationRepository: OrganizationRepository
) {
    fun resetForLogin() {
        ProductionOnboardingPersistence.persist(ProductionOnboardingState(step = ProductionOnboardingStep.OTP))
    }
    fun restoreMembershipContext(userId: String, fallbackDisplayName: String, fallbackMobile: String): Boolean = true
    
    fun routeAuthenticatedUserWithoutMembership(userId: String, displayName: String, mobile: String) {
        ProductionOnboardingPersistence.persist(ProductionOnboardingState(step = ProductionOnboardingStep.WELCOME, userId = userId, displayName = displayName, mobileNumber = mobile))
    }
    
    fun advanceFromWelcome() {
        ProductionOnboardingPersistence.persist(ProductionOnboardingPersistence.state.value.copy(step = ProductionOnboardingStep.ROLE_SELECTION))
    }
    
    fun selectRole(role: String) {
        ProductionOnboardingPersistence.persist(ProductionOnboardingPersistence.state.value.copy(step = ProductionOnboardingStep.PROFILE, role = role))
    }
    
    suspend fun submitProfile(name: String, path: String?): String? {
        val currentState = ProductionOnboardingPersistence.state.value
        val nextStep = if (currentState.role == "owner") ProductionOnboardingStep.OWNER_ORGANIZATION else ProductionOnboardingStep.JOIN_OPTIONS
        ProductionOnboardingPersistence.persist(currentState.copy(step = nextStep, displayName = name))
        return null
    }

    suspend fun createOrganization(orgName: String): String? {
        val currentState = ProductionOnboardingPersistence.state.value
        ProductionOnboardingPersistence.persist(currentState.copy(step = ProductionOnboardingStep.SUCCESS, organizationName = orgName))
        return null
    }
    
    fun goToInvite() {
        ProductionOnboardingPersistence.persist(ProductionOnboardingPersistence.state.value.copy(step = ProductionOnboardingStep.OPERATOR_INVITE))
    }
    
    fun skipJoin() {
        ProductionOnboardingPersistence.persist(ProductionOnboardingPersistence.state.value.copy(step = ProductionOnboardingStep.COMPLETED))
    }

    fun cancelJoinPreview() {
        ProductionOnboardingPersistence.persist(ProductionOnboardingPersistence.state.value.copy(step = ProductionOnboardingStep.JOIN_OPTIONS))
    }
    fun continueAfterSuccess() {
        ProductionOnboardingPersistence.persist(ProductionOnboardingPersistence.state.value.copy(step = ProductionOnboardingStep.COMPLETED))
    }
    
    suspend fun verifyOtp(mobile: String, otp: String): String? = null
    suspend fun resendOtp(mobile: String): String? = null
    suspend fun prepareJoinPreview(inviteCode: String): String? = null
    suspend fun confirmJoinOrganization(): String? = null
    suspend fun joinOrganization(inviteCode: String): String? = null
}

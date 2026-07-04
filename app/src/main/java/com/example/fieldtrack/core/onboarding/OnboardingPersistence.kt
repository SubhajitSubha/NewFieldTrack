package com.example.fieldtrack.core.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class OnboardingState(
    val profilePhone: String? = null,
    val profileName: String = "",
    val languageCode: String = "",
    val organizationName: String? = null,
    val onboardingCompleted: Boolean = true,
    val welcomeDismissed: Boolean = true,
    val isFirstLaunch: Boolean = false,
    val hasStartedFirstWork: Boolean = true
)

object OnboardingPersistence {
    val state: StateFlow<OnboardingState> = MutableStateFlow(OnboardingState())
    fun init(app: Any) {}
    fun isProfileComplete(): Boolean = true
    fun setProfileName(name: String) {}
    fun setRolePosture(role: Any) {}
    fun setProfilePhone(phone: String?) {}
    fun setLanguage(language: String) {}
}

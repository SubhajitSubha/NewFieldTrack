package com.example.fieldtrack.core.startup

sealed class AppStartupState {
    object AuthenticatedWithMembership : AppStartupState()
    object AuthenticatedWithoutMembership : AppStartupState()
    object Unauthenticated : AppStartupState()
    data class StartupError(val error: Throwable) : AppStartupState()
    object Loading : AppStartupState()
    object Initializing : AppStartupState()
}

object StartupStateResolver {
    val state = kotlinx.coroutines.flow.MutableStateFlow<AppStartupState>(AppStartupState.Unauthenticated)
    fun resolve(isAuthenticated: Boolean, hasMembership: Boolean): AppStartupState {
        if (isAuthenticated) {
            if (hasMembership) {
                return AppStartupState.AuthenticatedWithMembership
            } else {
                return AppStartupState.AuthenticatedWithoutMembership
            }
        }
        return AppStartupState.Unauthenticated
    }
}

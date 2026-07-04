package com.example.fieldtrack.core.session.identityfoundation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class IdentitySessionState {
    AUTHENTICATED, UNAUTHENTICATED
}

data class IdentitySession(
    val role: Role = Role(),
    val userId: String = "",
    val displayName: String = "",
    val language: String = ""
)

data class Role(
    val isTestUser: Boolean = false
)

class SessionManager(
    val sessionRepository: Any? = null,
    val userRepository: Any? = null
) {
    val state: StateFlow<IdentitySessionState> = MutableStateFlow(IdentitySessionState.UNAUTHENTICATED)
    val currentSession: StateFlow<IdentitySession?> = MutableStateFlow(null)
    val restoreCompleted: StateFlow<Boolean> = MutableStateFlow(true)
}

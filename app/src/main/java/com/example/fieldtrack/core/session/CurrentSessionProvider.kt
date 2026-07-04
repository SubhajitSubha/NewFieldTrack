package com.example.fieldtrack.core.session

import com.example.fieldtrack.core.session.identityfoundation.IdentitySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CurrentSessionProvider {
    val session: StateFlow<IdentitySession?> = MutableStateFlow(null)

    fun installFallbackSessionIfAbsent() {}
    fun updateSession(session: IdentitySession, source: Any) {}
}

enum class SessionSource {
    TEST_LOCAL
}

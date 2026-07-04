package com.example.fieldtrack.core.visibility

enum class RoleVisibilityProfile {
    OPERATOR;

    val displayLabel: String = ""
    fun canSee(surface: VisibilitySurface): Boolean = true
}

enum class VisibilitySurface {
    ACTIVE_WORK
}

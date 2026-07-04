package com.example.fieldtrack.core.config

import com.example.fieldtrack.BuildConfig

/**
 * Single source of truth for environment behavior.
 *
 * All environment-specific decisions MUST go through this enum.
 * No scattered BuildConfig.DEBUG checks elsewhere in the codebase.
 */
enum class AppEnvironment(
    val environmentLabel: String,
    val isDebugLike: Boolean,
    val allowsVerboseLogging: Boolean,
    val allowsOperationalSimulation: Boolean,
    val allowsFakeData: Boolean,
    val allowsInvariantOverlays: Boolean,
    val enablesCrashReporting: Boolean,
    val enablesAnalytics: Boolean
) {
    DEV(
        environmentLabel = "DEV",
        isDebugLike = true,
        allowsVerboseLogging = true,
        allowsOperationalSimulation = true,
        allowsFakeData = true,
        allowsInvariantOverlays = true,
        enablesCrashReporting = false,
        enablesAnalytics = false
    ),
    STAGING(
        environmentLabel = "STAGING",
        isDebugLike = true,
        allowsVerboseLogging = true,
        allowsOperationalSimulation = true,
        allowsFakeData = false,
        allowsInvariantOverlays = true,
        enablesCrashReporting = true,
        enablesAnalytics = false
    ),
    PROD(
        environmentLabel = "PROD",
        isDebugLike = false,
        allowsVerboseLogging = false,
        allowsOperationalSimulation = false,
        allowsFakeData = false,
        allowsInvariantOverlays = false,
        enablesCrashReporting = true,
        enablesAnalytics = true
    );

    companion object {
        /** Resolve current environment from BuildConfig.ENVIRONMENT (set by product flavors). */
        fun current(): AppEnvironment {
            return when (BuildConfig.ENVIRONMENT) {
                "dev" -> DEV
                "staging" -> STAGING
                "prod" -> PROD
                else -> DEV // Safe fallback — never silently run as prod
            }
        }
    }
}


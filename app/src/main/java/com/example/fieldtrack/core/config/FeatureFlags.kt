package com.example.fieldtrack.core.config

/**
 * Centralized operational feature gating.
 *
 * All flags derive from [AppEnvironment] — no hardcoded booleans.
 * This is the single place to check "should feature X be available?"
 */
object FeatureFlags {

    private val env: AppEnvironment
        get() = AppEnvironment.current()

    /** Allows triggering simulated field scenarios (rapid switching, long sessions, etc.) */
    val enableOperationalSimulator: Boolean
        get() = env.allowsOperationalSimulation

    /** Verbose invariant logging (assertion-level checks logged to console) */
    val enableVerboseInvariantLogging: Boolean
        get() = env.allowsVerboseLogging

    /** Fake sync responses for testing offline/online transitions */
    val enableFakeSync: Boolean
        get() = env.allowsFakeData

    /** Debug menu access in app */
    val enableDebugMenu: Boolean
        get() = env.isDebugLike

    /** Experimental sync strategies (new batching, compression, etc.) */
    val enableExperimentalSync: Boolean
        get() = env == AppEnvironment.DEV

    /** Crash overlay showing last crash inline (dev diagnostics) */
    val enableCrashOverlay: Boolean
        get() = env.allowsInvariantOverlays

    /** Advanced testing tools (DB inspector, fake data injection, etc.) */
    val enableAdvancedTestingTools: Boolean
        get() = env.allowsFakeData
}


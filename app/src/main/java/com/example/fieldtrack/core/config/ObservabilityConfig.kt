package com.example.fieldtrack.core.config

/**
 * Observability configuration derived from [AppEnvironment].
 *
 * Controls crash reporting, analytics, diagnostics, and invariant capture.
 * No SDK integration yet — this is the configuration layer only.
 *
 * Future integrations:
 * - Firebase Crashlytics (crash reporting)
 * - Firebase Analytics (usage analytics)
 * - Custom sync diagnostics pipeline
 */
object ObservabilityConfig {

    private val env: AppEnvironment
        get() = AppEnvironment.current()

    /** Whether crashes should be reported to external service. */
    val crashReportingEnabled: Boolean
        get() = env.enablesCrashReporting

    /** Whether usage analytics events should be sent. */
    val analyticsEnabled: Boolean
        get() = env.enablesAnalytics

    /**
     * Whether invariant violations should be captured with full context.
     * In DEV/STAGING: captures stack trace, state snapshot, timing.
     * In PROD: minimal metadata only (performance-safe).
     */
    val verboseInvariantCapture: Boolean
        get() = env.allowsVerboseLogging

    /**
     * Whether sync operations should emit detailed diagnostics.
     * Useful for debugging sync failures and timing issues.
     */
    val syncDiagnosticsEnabled: Boolean
        get() = env.isDebugLike
}


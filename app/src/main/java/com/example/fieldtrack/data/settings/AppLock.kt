package com.example.fieldtrack.data.settings

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long the app may stay out of sight before it asks again. */
enum class LockAfter(val millis: Long) {
    Right(0L),
    OneMinute(60_000L),
    FiveMinutes(5 * 60_000L),
}

/**
 * Asking for the phone's own screen lock (fingerprint, face, PIN or pattern) before the
 * app opens.
 *
 * Per phone, like the display settings: it protects what this handset shows, and says
 * nothing about anyone else's. It uses the lock the person already has, so there is no
 * second PIN of ours to forget and nothing of the sort to store.
 *
 * It is a lock on the screen, not on the data: the signed-in session and the database
 * are exactly as they were, which is why a forgotten lock can never cost anybody their
 * work. A phone with no screen lock cannot be asked anything, so the setting is treated
 * as off there, whatever was saved; otherwise a backup restored onto such a phone could
 * lock its owner out of the app.
 */
class AppLock(
    private val appContext: Context,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val deviceHasLock: () -> Boolean = {
        (appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
    },
) {
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _enabled by lazy { MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false)) }
    val enabled: StateFlow<Boolean> get() = _enabled.asStateFlow()

    private val _after by lazy {
        MutableStateFlow(
            prefs.getString(KEY_AFTER, null)?.let { saved -> LockAfter.entries.firstOrNull { it.name == saved } }
                ?: LockAfter.OneMinute
        )
    }
    val after: StateFlow<LockAfter> get() = _after.asStateFlow()

    /** Whether the phone has a screen lock this could use. */
    fun available(): Boolean = deviceHasLock()

    /** Starts shut when it is on: a fresh launch is somebody who has not yet shown who they are. */
    private val _locked by lazy { MutableStateFlow(active()) }
    val locked: StateFlow<Boolean> get() = _locked.asStateFlow()

    private var leftAt: Long? = null

    private fun active() = _enabled.value && available()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        // Turning it on is done by somebody who is looking at the app; locking them
        // out of the screen they just used would only be a surprise.
        _locked.value = false
        leftAt = null
    }

    fun setAfter(choice: LockAfter) {
        prefs.edit().putString(KEY_AFTER, choice.name).apply()
        _after.value = choice
    }

    /** The app went out of sight. */
    fun onBackground() {
        leftAt = now()
    }

    /** The app came back; shut it if it has been away longer than the person allowed. */
    fun onForeground() {
        val left = leftAt ?: return
        leftAt = null
        if (active() && now() - left >= _after.value.millis) _locked.value = true
    }

    /** The phone's lock was passed. */
    fun unlock() {
        _locked.value = false
        leftAt = null
    }

    companion object {
        const val PREFS = "fieldtrack.lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_AFTER = "after"
    }
}

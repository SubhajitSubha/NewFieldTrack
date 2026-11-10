package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.settings.AppLock
import com.example.fieldtrack.data.settings.LockAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The screen lock in front of the app: when it shuts, when it does not, and that it can never trap anyone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockTest {

    private lateinit var context: Context
    private var clock = 1_000L
    private var phoneHasLock = true

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppLock.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        clock = 1_000L
        phoneHasLock = true
    }

    private fun lock() = AppLock(context, now = { clock }, deviceHasLock = { phoneHasLock })

    @Test fun `off until somebody turns it on`() {
        val lock = lock()
        assertFalse(lock.enabled.value)
        assertFalse(lock.locked.value)
        lock.onBackground(); clock += 10 * 60_000; lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test fun `turning it on does not lock the person out of the screen they are on`() {
        val lock = lock()
        lock.setEnabled(true)
        assertFalse(lock.locked.value)
    }

    @Test fun `it asks again once the app has been out of sight for as long as was chosen`() {
        val lock = lock()
        lock.setEnabled(true)
        lock.setAfter(LockAfter.OneMinute)
        lock.onBackground(); clock += 59_000; lock.onForeground()
        assertFalse("a quick look at another app", lock.locked.value)
        lock.onBackground(); clock += 60_000; lock.onForeground()
        assertTrue(lock.locked.value)
        lock.unlock()
        assertFalse(lock.locked.value)
    }

    @Test fun `every time means every time`() {
        val lock = lock()
        lock.setEnabled(true)
        lock.setAfter(LockAfter.Right)
        lock.onBackground(); lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test fun `a fresh launch starts shut`() {
        lock().setEnabled(true)
        assertTrue("the next process is somebody who has not yet shown who they are", lock().locked.value)
    }

    @Test fun `the choice is remembered`() {
        lock().apply { setEnabled(true); setAfter(LockAfter.FiveMinutes) }
        val again = lock()
        assertTrue(again.enabled.value)
        assertEquals(LockAfter.FiveMinutes, again.after.value)
    }

    @Test fun `a phone with no screen lock is never held shut, whatever was saved`() {
        lock().setEnabled(true)
        phoneHasLock = false
        val restored = lock()
        assertTrue(restored.enabled.value)
        assertFalse(restored.available())
        assertFalse("a backup restored onto such a phone must not lock its owner out", restored.locked.value)
        restored.onBackground(); clock += 10 * 60_000; restored.onForeground()
        assertFalse(restored.locked.value)
    }

    @Test fun `coming back while the prompt is up does not start it over`() {
        val lock = lock()
        lock.setEnabled(true)
        lock.setAfter(LockAfter.Right)
        lock.onBackground(); lock.onForeground()
        assertTrue(lock.locked.value)
        // The phone's own prompt covers the app and uncovers it: that is not leaving.
        lock.onForeground()
        assertTrue(lock.locked.value)
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.locked.value)
    }
}

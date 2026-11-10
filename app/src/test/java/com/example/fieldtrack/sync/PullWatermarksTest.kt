package com.example.fieldtrack.sync

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.sync.PullWatermarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What lets a screen say how old the last read is, the moment it changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PullWatermarksTest {

    private fun marks(): PullWatermarks {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fieldtrack.pull", Context.MODE_PRIVATE).edit().clear().commit()
        return PullWatermarks(context)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `a screen watching is told when a pull lands, without waiting for anything`() = runBlocking {
        val m = marks()
        val seen = mutableListOf<Long>()
        val watching = launch(Dispatchers.Default) { m.observeSuccessAt("org-a").collect { synchronized(seen) { seen += it } } }
        repeat(50) { idle(); Thread.sleep(10) }
        assertEquals("starts from what is already there: never", listOf(0L), synchronized(seen) { seen.toList() })

        m.recordSuccess("org-a", 1_000)
        repeat(50) { idle(); Thread.sleep(10) }
        m.recordSuccess("org-b", 9_999)   // another business says nothing about this one
        repeat(20) { idle(); Thread.sleep(10) }
        m.recordSuccess("org-a", 2_000)
        repeat(50) { idle(); Thread.sleep(10) }

        watching.cancel()
        assertEquals(2_000L, synchronized(seen) { seen.last() })
        assertEquals("only its own business's pulls", true, synchronized(seen) { seen.none { it == 9_999L } })
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Smoke test: resources resolve under Robolectric, so the rest of the suite can run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("FieldTrack", context.getString(R.string.app_name))
    }
}

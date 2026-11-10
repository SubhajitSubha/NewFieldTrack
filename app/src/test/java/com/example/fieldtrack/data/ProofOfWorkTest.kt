package com.example.fieldtrack.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.photo.PhotoStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a crew notes to prove the work, and the photo that goes with it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProofOfWorkTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() = runTest {
        h = TestHarness(); h.seed()
        context.getSharedPreferences(PhotoStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun tearDown() = h.close()

    private suspend fun job() = h.work.startJob(
        org, TestHarness.CUSTOMER_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 100_000, operatorPersonId = TestHarness.DRIVER,
    )

    @Test fun `each note is written on the job and the others are left as they were`() = runTest {
        val id = job()
        h.work.recordProof(org, id, atFinish = false, meter = 1204.5)
        h.work.recordProof(org, id, atFinish = false, photoId = "photo-1")
        h.work.recordProof(org, id, atFinish = false, latitude = 30.9, longitude = 75.8)
        h.work.recordProof(org, id, atFinish = true, meter = 1207.0)
        val j = h.db.jobDao().find(org, id)!!
        assertEquals(1204.5, j.meterStart!!, 0.0)
        assertEquals("photo-1", j.startPhotoId)
        assertEquals(30.9, j.startLat!!, 0.0); assertEquals(75.8, j.startLng!!, 0.0)
        assertEquals(1207.0, j.meterEnd!!, 0.0)
        assertNull(j.endPhotoId); assertNull(j.endLat)
    }

    @Test fun `a photo added is an event on the job's own timeline, and the job goes up to be shared`() = runTest {
        val id = job()
        h.work.recordProof(org, id, atFinish = false, photoId = "photo-9")
        val events = h.db.jobDao().eventsOnce(id)
        assertTrue(events.any { it.type == JobEventType.PhotoAdded && it.textValue == "photo-9" })
        assertTrue(h.db.outboxDao().nextBatch(100).any { it.tableName == "jobs" && it.entityId == id })
    }

    // ------------------------------------------------------------------ photos

    private fun capture(width: Int, height: Int): File {
        val file = File.createTempFile("cap", ".jpg", context.cacheDir)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file
    }

    @Test fun `a photo is shrunk, filed under the business, and queued to be sent`() {
        val store = PhotoStore(context) { throw IllegalStateException("no network in a test") }
        val source = capture(4000, 3000)
        val id = store.ingest(org, source)
        assertNotNull(id)
        val kept = store.localFile(id!!)!!
        val shape = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(kept.path, shape)
        assertEquals("long side brought down to a size a phone can send", 1280, maxOf(shape.outWidth, shape.outHeight))
        assertFalse("the camera's own file is not kept", source.exists())
        assertEquals(setOf("$org/$id"), store.pending())
    }

    @Test fun `where a photo goes on the server is under its business, and nowhere else`() {
        assertEquals("orgs/o1/photos/abc.jpg", PhotoStore.remotePath("o1", "abc"))
        val rules = File("../storage.rules").readText()
        assertTrue(rules.contains("match /orgs/{orgId}/photos/{photoId}"))
        assertTrue("members only", rules.contains("memberOf(orgId)"))
        assertTrue("small pictures only", rules.contains("request.resource.size <"))
        assertTrue("nothing else is readable", rules.substringAfter("match /{allPaths=**}").contains("if false"))
    }
}

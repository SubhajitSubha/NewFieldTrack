package com.example.fieldtrack.feature

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.measure.Fix
import com.example.fieldtrack.data.measure.WalkStore
import com.example.fieldtrack.feature.fields.GpsAccess
import com.example.fieldtrack.feature.fields.MeasureViewModel
import com.example.fieldtrack.feature.fields.Signal
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.cos

/**
 * Measuring a plot, reading by reading.
 *
 * The phone's GPS is replaced by readings the test hands over one at a time, on a
 * clock the test moves, so every state the screen can be in — no permission, no
 * signal, a weak signal, a walk, a walk cut off — is reached on purpose rather than
 * waited for in a field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeasureTest {

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val org = "org-measure"
    private val lat0 = 30.9
    private val lon0 = 75.85
    private val metreLat = 1.0 / 111_194.9266
    private val metreLon = metreLat / cos(Math.toRadians(lat0))

    private var clock = 1_800_000_000_000L
    private var gps = GpsAccess.Allowed

    @Before fun setUp() {
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("AIzaScreenshotsOnly000000000000000000")
                    .setProjectId("fieldtrack-tests")
                    .build(),
            )
        }
        runBlocking {
            val t = System.currentTimeMillis()
            app.db.orgDao().upsertOrg(OrgEntity(id = org, name = "Walk Works", createdAt = t, updatedAt = t))
            app.db.orgDao().upsertMembership(
                MembershipEntity(
                    id = membershipId(org, "p"), orgId = org, personId = "p", displayName = "P",
                    role = MemberRole.Owner, joinedAt = t, updatedAt = t,
                )
            )
            app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit()
                .putString("personId", "p").putString("orgId", org).commit()
            app.sessionStore.load()
        }
        app.getSharedPreferences("field_walk", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun model(unit: MeasureUnit = MeasureUnit.Acre): MeasureViewModel {
        val vm = MeasureViewModel(app, unit, access = { gps }, now = { clock })
        repeat(100) { shadowOf(Looper.getMainLooper()).idle(); if (vm.state.value.loaded) return vm; Thread.sleep(10) }
        error("never loaded")
    }

    private fun MeasureViewModel.now() = state.value

    /** A reading [east] and [north] metres from the start, taken now. */
    private fun MeasureViewModel.fix(east: Double, north: Double, accuracy: Float = 5f) {
        onFix(Fix(lat0 + north * metreLat, lon0 + east * metreLon, accuracy, clock))
    }

    /** Walks a path, a step every ten seconds. */
    private fun MeasureViewModel.walk(vararg metres: Pair<Int, Int>) = metres.forEach { (e, n) ->
        clock += 10_000
        fix(e.toDouble(), n.toDouble())
    }

    private fun MeasureViewModel.startAtCorner() {
        fix(0.0, 0.0); start()
    }

    private fun rectangleWalked(vm: MeasureViewModel) {
        vm.startAtCorner()
        vm.walk(100 to 0, 100 to 40, 0 to 40)
        vm.finish()
    }

    // ------------------------------------------------------------- getting ready

    @Test
    fun `without the permission, or with location off, it says so and cannot start`() {
        gps = GpsAccess.NotPermitted
        val vm = model()
        vm.refresh()
        assertEquals(GpsAccess.NotPermitted, vm.now().access)
        assertEquals(Signal.None, vm.now().signal)
        gps = GpsAccess.NotPrecise
        vm.refresh()
        assertEquals("only the approximate position was agreed to", GpsAccess.NotPrecise, vm.now().access)
        vm.fix(0.0, 0.0); vm.start()
        assertFalse(vm.now().walking)
        gps = GpsAccess.SwitchedOff
        vm.refresh()
        assertEquals(GpsAccess.SwitchedOff, vm.now().access)
        vm.fix(0.0, 0.0); vm.start()
        assertFalse("no walk without a signal that can be trusted", vm.now().walking)
    }

    @Test
    fun `it looks for a signal, then a weak one, then a good one`() {
        val vm = model()
        vm.refresh()
        assertEquals("nothing yet", Signal.None, vm.now().signal)
        vm.fix(0.0, 0.0, accuracy = 35f)
        assertEquals(Signal.Weak, vm.now().signal)
        assertEquals(35, vm.now().accuracyMetres)
        vm.fix(0.0, 0.0, accuracy = 6f)
        assertEquals(Signal.Good, vm.now().signal)
        assertEquals(6, vm.now().accuracyMetres)
    }

    @Test
    fun `a signal that goes quiet is lost, not remembered as good`() {
        val vm = model()
        vm.fix(0.0, 0.0)
        assertEquals(Signal.Good, vm.now().signal)
        clock += 11_000
        vm.refresh()
        assertEquals(Signal.None, vm.now().signal)
    }

    @Test
    fun `an old reading handed over as soon as the GPS starts is not taken for now`() {
        val vm = model()
        vm.onFix(Fix(lat0, lon0, 5f, clock - 60_000))
        vm.refresh()
        assertEquals(Signal.None, vm.now().signal)
        assertNull(vm.now().here)
    }

    // ------------------------------------------------------------------ walking

    @Test
    fun `a walk is started from a corner, and only on a good signal`() {
        val vm = model()
        vm.fix(0.0, 0.0, accuracy = 30f)
        vm.start()
        assertFalse(vm.now().walking)
        vm.fix(0.0, 0.0, accuracy = 5f)
        vm.start()
        assertTrue(vm.now().walking)
        assertEquals(1, vm.now().points.size)
    }

    @Test
    fun `only readings worth keeping go on the path`() {
        val vm = model()
        vm.startAtCorner()
        clock += 10_000; vm.fix(2.0, 1.0)                    // jitter
        clock += 10_000; vm.fix(30.0, 20.0, accuracy = 40f)  // too poor, and off the line
        clock += 10_000; vm.fix(60.0, 0.0)                   // a real step
        assertEquals(2, vm.now().points.size)
        assertEquals("the poor reading did not bend the path", 60.0, vm.now().walkedMetres, 0.5)
    }

    @Test
    fun `the dot follows the phone even where the path is not extended`() {
        val vm = model()
        vm.startAtCorner()
        vm.fix(1.0, 1.0)
        assertEquals(1, vm.now().points.size)
        assertNotNull(vm.now().here)
    }

    // ------------------------------------------------------------------- result

    @Test
    fun `a walk round a rectangle is its area in the unit chosen`() {
        val vm = model(MeasureUnit.Acre)
        rectangleWalked(vm)
        assertTrue(vm.now().finished)
        // 4,000 square metres is 0.99 of an acre.
        assertEquals(0.99, vm.now().area!!, 0.01)
        assertEquals(280.0, vm.now().shape!!.perimeterMetres, 1.0)
        vm.setUnit(MeasureUnit.Kanal)
        assertEquals(7.91, vm.now().area!!, 0.05)
        vm.setUnit(MeasureUnit.Guntha)
        assertEquals(39.54, vm.now().area!!, 0.2)
    }

    @Test
    fun `a bigha has no size until the farmer says what it is here`() {
        val vm = model(MeasureUnit.Bigha)
        rectangleWalked(vm)
        assertNull("nothing is invented", vm.now().area)
        assertFalse(vm.now().canUse)
        vm.setPerAcre("0")
        assertNull("not a real number", vm.now().area)
        vm.setPerAcre("1.6")
        assertEquals(1.58, vm.now().area!!, 0.02)
        assertTrue(vm.now().canUse)
    }

    @Test
    fun `what a bigha is here is remembered, for this business and this unit only`() {
        val first = model(MeasureUnit.Bigha)
        first.setPerAcre("1.6")
        val later = model(MeasureUnit.Bigha)
        assertEquals("1.6", later.now().perAcreText)
        val katha = model(MeasureUnit.Katha)
        assertEquals("a katha is not a bigha", "", katha.now().perAcreText)
        later.setUnit(MeasureUnit.Katha)
        assertEquals("", later.now().perAcreText)
        later.setUnit(MeasureUnit.Bigha)
        assertEquals("1.6", later.now().perAcreText)
        assertNull("another business", WalkStore(app).perAcre("some-other-org", MeasureUnit.Bigha))
    }

    @Test
    fun `an absurd figure is not remembered`() {
        val vm = model(MeasureUnit.Bigha)
        vm.setPerAcre("9999")
        assertNull(WalkStore(app).perAcre(org, MeasureUnit.Bigha))
        vm.setPerAcre("1..6.")
        assertEquals("1.6", vm.now().perAcreText)
    }

    @Test
    fun `a few steps is not a plot`() {
        val vm = model()
        vm.startAtCorner()
        vm.walk(5 to 0, 5 to 5)
        vm.finish()
        assertNull(vm.now().area)
        assertFalse(vm.now().canUse)
    }

    @Test
    fun `finishing too soon can be undone, and carries on from the end of the path`() {
        val vm = model()
        vm.startAtCorner()
        vm.walk(100 to 0)
        vm.finish()
        assertTrue(vm.now().finished)
        vm.keepWalking()
        assertTrue(vm.now().walking)
        assertFalse(vm.now().finished)
        vm.walk(100 to 40, 0 to 40)
        vm.finish()
        assertTrue(vm.now().canUse)
    }

    @Test
    fun `using the size hands it over and forgets the walk`() {
        val vm = model(MeasureUnit.Kanal)
        rectangleWalked(vm)
        val (area, unit) = vm.use()!!
        assertEquals(7.91, area, 0.05)
        assertEquals(MeasureUnit.Kanal, unit)
        assertTrue("nothing kept", WalkStore(app).track(org).isEmpty())
    }

    @Test
    fun `there is nothing to use before there is a size`() {
        val vm = model()
        assertNull(vm.use())
        vm.startAtCorner()
        assertNull("mid-walk", vm.use())
    }

    @Test
    fun `starting over throws the walk away`() {
        val vm = model()
        rectangleWalked(vm)
        vm.startOver()
        assertTrue(vm.now().points.isEmpty())
        assertFalse(vm.now().walking)
        assertFalse(vm.now().finished)
        assertNull(vm.now().shape)
        assertTrue(WalkStore(app).track(org).isEmpty())
    }

    // ------------------------------------------------------------ being cut off

    @Test
    fun `readings stopping mid-walk is a gap, and the walk says so`() {
        val vm = model()
        vm.startAtCorner()
        vm.listen(kotlinx.coroutines.flow.emptyFlow())
        vm.stop()
        assertTrue(vm.now().interrupted)
        assertTrue("the walk is kept", vm.now().walking)
    }

    @Test
    fun `stopping when there was no walk is nothing`() {
        val vm = model()
        vm.listen(kotlinx.coroutines.flow.emptyFlow())
        vm.stop()
        assertFalse(vm.now().interrupted)
    }

    @Test
    fun `starting over after a gap clears it`() {
        val vm = model()
        vm.startAtCorner()
        vm.listen(kotlinx.coroutines.flow.emptyFlow()); vm.stop()
        vm.startOver()
        assertFalse(vm.now().interrupted)
        vm.fix(0.0, 0.0); vm.start()
        assertFalse("a new walk has no gap", vm.now().interrupted)
    }

    @Test
    fun `a walk the phone was holding when the app was closed comes back, with its gap`() {
        val closed = model()
        closed.startAtCorner()
        closed.walk(100 to 0, 100 to 40)
        val reopened = model()
        assertEquals(3, reopened.now().points.size)
        assertTrue(reopened.now().walking)
        assertTrue(reopened.now().interrupted)
        reopened.fix(0.0, 40.0)
        reopened.walk(0 to 40)
        reopened.finish()
        assertTrue("and can be finished", reopened.now().shape!!.enough)
    }

    @Test
    fun `a saved walk after the phone was restarted still takes new readings`() {
        val closed = model()
        closed.startAtCorner()
        closed.walk(100 to 0)
        clock += 3_600_000                                   // an hour, and a reboot, later
        val reopened = model()
        reopened.fix(100.0, 40.0)
        assertEquals("the reading is not refused for coming after a long gap", 3, reopened.now().points.size)
    }

    @Test
    fun `a corrupt saved walk is ignored rather than crashing`() {
        app.getSharedPreferences("field_walk", Context.MODE_PRIVATE).edit()
            .putString("track.$org", "garbage;1,2;x,y,z;1.5,2.5,10").commit()
        val vm = model()
        assertEquals("the one good row", 1, vm.now().points.size)
    }

    // ------------------------------------------------------ back into the field form

    @Test
    fun `a measured size fills the form, in the unit it was measured in`() {
        val form = com.example.fieldtrack.feature.fields.FieldEditViewModel(app, "customer", null)
        form.applyMeasured(7.91, MeasureUnit.Kanal)
        assertEquals("7.91", form.state.value.areaText)
        assertEquals(MeasureUnit.Kanal, form.state.value.unit)
        assertEquals(7.91, form.state.value.area!!, 0.0)
        form.applyMeasured(2.0, MeasureUnit.Acre)
        assertEquals("a whole number carries no .0", "2", form.state.value.areaText)
    }

    // ------------------------------------------------------------- the warnings

    @Test
    fun `finishing far from where it began, or crossing itself, is on the shape`() {
        val open = model()
        open.startAtCorner(); open.walk(100 to 0, 100 to 40); open.finish()
        assertTrue(open.now().shape!!.closingGapMetres > 30)

        val eight = model()
        eight.startOver()
        eight.startAtCorner(); eight.walk(100 to 40, 100 to 0, 0 to 40); eight.finish()
        assertTrue(eight.now().shape!!.crossesItself)
    }

}

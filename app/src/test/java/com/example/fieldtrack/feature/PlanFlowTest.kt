package com.example.fieldtrack.feature

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.CancelReason
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.data.session.Startup
import com.example.fieldtrack.feature.plan.BookingFormState
import com.example.fieldtrack.feature.plan.BookingFormViewModel
import com.example.fieldtrack.feature.plan.PlanSettingsViewModel
import com.example.fieldtrack.feature.plan.PlanState
import com.example.fieldtrack.feature.plan.PlanSummary
import com.example.fieldtrack.feature.plan.PlanViewModel
import com.example.fieldtrack.feature.plan.observePlanSummary
import com.example.fieldtrack.feature.work.StartWorkViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

/**
 * Planning a day, as the people in a business meet it.
 *
 * The owner books; a driver opens the app in the morning to see what is theirs and
 * starts it from there; the owner's phone shows it started without anybody having
 * written the plan. The tests go through the screens' own view models, so what is
 * checked is what a person would be shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanFlowTest {

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val owner = "person-owner"
    private val boss = "person-boss"
    private val driver = "person-driver"
    private val driver2 = "person-driver2"
    private val org = "org-plan"

    private var tractor = ""
    private var harvester = ""
    private var ramesh = ""
    private var harjit = ""

    /** The plan's own today, as a phone with the default four o'clock turnover reads it. */
    private val today get() = PlanDay.today(System.currentTimeMillis(), PlanSettings.DEFAULT_DAY_START_HOUR)

    @Before fun firebase() {
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
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun member(person: String, name: String, role: MemberRole = MemberRole.Operator, plan: Boolean = false, active: Boolean = true) =
        MembershipEntity(
            id = membershipId(org, person), orgId = org, personId = person, displayName = name, role = role,
            canPlanWork = plan, canSeeMoney = role == MemberRole.Owner, active = active,
            joinedAt = 1, updatedAt = 1,
        )

    /** One business with a crew, two machines and two farmers; signed in as [who]. */
    private fun business(who: String = owner, driversSeeAll: Boolean = false) = runBlocking {
        val now = System.currentTimeMillis()
        app.db.orgDao().upsertOrg(
            OrgEntity(
                id = org, name = "Plan Works", defaultLandUnit = MeasureUnit.Acre,
                driversSeeAll = driversSeeAll, createdAt = now, updatedAt = now,
            )
        )
        app.db.orgDao().upsertMembership(member(owner, "Owner", MemberRole.Owner))
        app.db.orgDao().upsertMembership(member(boss, "Boss", plan = true))
        app.db.orgDao().upsertMembership(member(driver, "Jaswant"))
        app.db.orgDao().upsertMembership(member(driver2, "Gurmeet"))
        tractor = app.directoryRepository.addMachine(org, "Tractor 1", MachineKind.Tractor)
        harvester = app.directoryRepository.addMachine(org, "Harvester", MachineKind.Harvester)
        ramesh = app.directoryRepository.addCustomer(org, "Ramesh Singh", null, "Kheri")
        harjit = app.directoryRepository.addCustomer(org, "Harjit Kaur", null, "Rampur")
        app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().putString("orgId", org).commit()
        signInAs(who)
    }

    /**
     * Somebody else picks up the phone. The store keeps the session it has until told
     * otherwise, so it is told the way a fresh launch would be: nothing loaded yet.
     */
    private fun signInAs(who: String) = runBlocking {
        app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().putString("personId", who).commit()
        @Suppress("UNCHECKED_CAST")
        val startup = SessionStore::class.java.getDeclaredField("_startup")
            .apply { isAccessible = true }.get(app.sessionStore) as MutableStateFlow<Startup>
        startup.value = Startup.Loading
        app.sessionStore.load()
    }

    private suspend fun <T> settled(read: () -> T, ok: (T) -> Boolean): T {
        repeat(200) {
            idle()
            val v = read()
            if (ok(v)) return v
            Thread.sleep(20)
        }
        error("never settled: ${read()}")
    }

    private suspend fun until(done: () -> Boolean) { settled(done) { it } }

    /** The plan screen as [business] was signed in, once it has loaded. */
    private fun planScreen(block: suspend (PlanViewModel, suspend (PlanState.() -> Boolean) -> PlanState) -> Unit) = runBlocking {
        val vm = PlanViewModel(app)
        val job = launch(Dispatchers.Default) { vm.state.collect { } }
        block(vm) { cond -> settled({ vm.state.value }) { it.loaded && it.cond() } }
        job.cancel()
    }

    private fun form(id: String? = null, day: Long? = null, block: suspend (BookingFormViewModel, suspend (BookingFormState.() -> Boolean) -> BookingFormState) -> Unit) =
        runBlocking {
            val vm = BookingFormViewModel(app, id, day)
            // Nobody can touch a form before it has loaded, so neither does a test.
            settled({ vm.state.value }) { it.loaded }
            block(vm) { cond -> settled({ vm.state.value }) { it.loaded && it.cond() } }
        }

    /** Books Ramesh's rotavator work through the screen's own view model. */
    private suspend fun BookingFormViewModel.fill(
        day: Long = today + 1,
        part: DayPart = DayPart.Morning,
        machine: String? = null,
        driver: String? = null,
        farmer: String = ramesh,
    ) {
        val c = app.directoryRepository.findCustomer(org, farmer)!!
        selectCustomer(c)
        setDay(day)
        setPart(part)
        selectMachine(machine)
        selectDriver(driver)
    }

    private suspend fun BookingFormViewModel.saved(): Boolean {
        var done = false
        save { done = true }
        until { done }
        return done
    }

    // ----------------------------------------------------------- booking and seeing it

    @Test
    fun `the owner books work and sees it on the day with its names and what is free`() {
        business()
        form { f, ready ->
            ready { true }
            assertTrue(ready { canPlan }.editable)
            f.fill(machine = tractor, driver = driver)
            f.selectWorkType(workTypeOptions.first { it.key == "Harvesting" })
            f.setStartMinute(6 * 60 + 30)
            f.setNote("  gate by the canal ")
            assertTrue(ready { canSave }.canSave)
            assertTrue(f.saved())
        }
        planScreen { vm, ready ->
            vm.pick(today + 1)
            val s = ready { day == today + 1 && items.isNotEmpty() }
            val item = s.items.single()
            assertEquals("Ramesh Singh", item.customerName)
            assertEquals("Tractor 1", item.machineName)
            assertEquals("Jaswant", item.driverName)
            assertEquals("Harvesting", item.row.booking.workType)
            assertEquals(6 * 60 + 30, item.row.booking.startMinute)
            assertEquals("gate by the canal", item.row.booking.note)
            assertTrue("nothing to warn about", item.flags.isEmpty())
            assertFalse("not startable before its day", item.canStart)
            assertEquals(listOf("Harvester"), s.freeMachines)
            assertEquals(listOf("Owner", "Boss", "Gurmeet"), s.freeDrivers.sortedBy { listOf("Owner", "Boss", "Gurmeet").indexOf(it) })
            assertTrue(s.canPlan)
        }
    }

    @Test
    fun `a farmer is needed, and a day that has passed cannot be saved`() {
        business()
        form { f, ready ->
            assertFalse("no farmer chosen", ready { true }.canSave)
            f.fill(day = today - 1)
            assertFalse("yesterday is not a day to plan", ready { true }.canSave)
            f.setDay(today)
            assertTrue(ready { true }.canSave)
        }
    }

    @Test
    fun `the form says what a booking would double-book and still lets it be saved`() {
        business()
        form { f, ready ->
            f.fill(machine = tractor, driver = driver)
            assertTrue(f.saved())
        }
        form { f, ready ->
            f.fill(farmer = harjit, machine = tractor, driver = driver)
            val s = ready { machineClash != null }
            assertNotNull(s.driverClash)
            assertEquals(ramesh, s.machineClash!!.booking.customerId)
            assertTrue("a warning, never a block", s.canSave)
            f.setPart(DayPart.Afternoon)
            val after = ready { machineClash == null }
            assertNull(after.driverClash)
            assertTrue(f.saved())
        }
    }

    @Test
    fun `a machine that is down is said so on the form and on the plan`() {
        business()
        runBlocking {
            app.db.machineDao().find(org, tractor)!!.let {
                app.db.machineDao().upsert(it.copy(status = MachineStatus.Down))
            }
        }
        form { f, ready ->
            f.fill(machine = tractor)
            assertTrue(ready { machineDown }.canSave)
            assertTrue(f.saved())
        }
        planScreen { vm, ready ->
            vm.pick(today + 1)
            val item = ready { items.isNotEmpty() }.items.single()
            assertEquals(listOf(PlanDay.Flag.MachineDown), item.flags)
        }
    }

    @Test
    fun `a driver who has left is said so and the booking is kept`() {
        business()
        form { f, _ -> f.fill(driver = driver2); assertTrue(f.saved()) }
        runBlocking { app.db.orgDao().upsertMembership(member(driver2, "Gurmeet", active = false)) }
        planScreen { vm, ready ->
            vm.pick(today + 1)
            val item = ready { items.isNotEmpty() }.items.single()
            assertEquals(listOf(PlanDay.Flag.DriverGone), item.flags)
            assertEquals("still named, not 'anyone'", "Gurmeet", item.driverName)
        }
        val id = runBlocking { app.db.bookingDao().observeDays(org, today + 1, today + 1).first().single().booking.id }
        form(id) { _, ready ->
            val s = ready { customer != null }
            assertTrue(s.driverGone)
            assertEquals("the person is still shown, as having left", driver2, s.driverId)
            assertTrue(s.team.any { it.personId == driver2 && !it.active })
        }
    }

    // ------------------------------------------------------- who sees what, who may do what

    private fun bookTwo() {
        business()
        form { f, _ -> f.fill(day = today, driver = driver); assertTrue(f.saved()) }
        form { f, _ -> f.fill(day = today, driver = driver2, farmer = harjit); assertTrue(f.saved()) }
        form { f, _ -> f.fill(day = today, farmer = harjit); assertTrue(f.saved()) }
    }

    @Test
    fun `a driver sees what is theirs and what nobody has been given, not the rest`() {
        bookTwo()
        signInAs(driver)
        planScreen { _, ready ->
            val s = ready { items.isNotEmpty() }
            assertEquals(listOf("Jaswant", null), s.items.map { it.driverName }.sortedBy { it ?: "~" })
            assertFalse(s.canPlan)
            assertTrue("no warnings or free lists for somebody who cannot change the plan", s.items.all { it.flags.isEmpty() })
            assertTrue(s.freeMachines.isEmpty() && s.freeDrivers.isEmpty())
            assertTrue("it is today and unstarted, so theirs to start", s.items.all { it.canStart })
        }
    }

    @Test
    fun `drivers see everyone's work when the business says so`() {
        business(who = driver, driversSeeAll = true)
        runBlocking {
            app.planRepository.save(
                app.sessionStore.current()!!.can.let { com.example.fieldtrack.data.session.Capabilities.ofOwner() },
                org, null, ramesh, null, "Rotavator", today, DayPart.Morning, null, null, driver2, null,
            )
        }
        planScreen { _, ready ->
            assertEquals(1, ready { items.isNotEmpty() }.items.size)
        }
    }

    @Test
    fun `a driver cannot plan, and an owner's permission can be given to a manager`() {
        business(who = driver)
        assertFalse(Capability.PlanWork in app.sessionStore.current()!!.can)
        form { _, ready -> assertFalse(ready { true }.canPlan); assertFalse(ready { true }.editable) }
        signInAs(boss)
        assertTrue(Capability.PlanWork in app.sessionStore.current()!!.can)
        form { _, ready -> val s = ready { true }; assertTrue(s.toString(), s.editable) }
    }

    // --------------------------------------------- missed, started, cancelled, moved, done

    @Test
    fun `work promised for a day gone and never started waits for a decision`() {
        business()
        val id = runBlocking {
            app.planRepository.save(
                com.example.fieldtrack.data.session.Capabilities.ofOwner(),
                org, null, ramesh, null, "Rotavator", today - 2, DayPart.Morning, null, tractor, driver, null,
            )
        }
        planScreen { vm, ready ->
            vm.pick(today + 3)
            val s = ready { overdue.isNotEmpty() }
            assertEquals("it is shown whichever day is being looked at", id, s.overdue.single().id)
            assertTrue("and can be started from there", s.overdue.single().canStart)
        }
        runBlocking {
            val summary = app.observePlanSummary(app.sessionStore.current()!!).first()
            assertEquals(1, summary.missed)
            assertFalse(summary.isEmpty)
        }
        // Decided: moved to a new day it is out of the list and counted as moved.
        form(id) { f, ready ->
            assertFalse("still on its old day, which has passed", ready { customer != null }.canSave)
            f.setDay(today + 1)
            assertTrue(ready { true }.canSave)
            assertTrue(f.saved())
        }
        planScreen { _, ready -> assertTrue(ready { overdue.isEmpty() }.overdue.isEmpty()) }
        assertEquals(1, runBlocking { app.planRepository.find(org, id)!!.movedCount })
    }

    @Test
    fun `starting from the plan fills the job in, links it, and the booking reads as started`() {
        business()
        val id = runBlocking {
            app.planRepository.save(
                com.example.fieldtrack.data.session.Capabilities.ofOwner(),
                org, null, ramesh, null, "Harvesting", today, DayPart.Morning, null, harvester, driver, null,
            )
        }
        signInAs(driver)
        runBlocking {
            val start = StartWorkViewModel(app, forBookingId = id)
            val s = settled({ start.state.value }) { it.customer != null && it.machineId != null }
            assertEquals("Ramesh Singh", s.customer!!.name)
            assertEquals(1, s.step)
            assertEquals("Harvesting", s.workType.key)
            assertEquals(harvester, s.machineId)
            assertEquals(id, s.bookingId)
            assertTrue("backing out leaves, as from a customer's page", start.back())
            start.setRate("900")
            val jobId = start.start()!!
            assertEquals(id, app.workRepository.observeJob(org, jobId).first()!!.bookingId)
        }
        planScreen { _, ready ->
            val item = ready { items.firstOrNull()?.row?.jobStatus == JobStatus.Running }.items.single()
            assertFalse("a started booking is not offered to start again", item.canStart)
        }
        signInAs(owner)
        form(id) { _, ready ->
            val s = ready { customer != null }
            assertTrue(s.started)
            assertFalse("no longer edited once work began", s.editable)
        }
    }

    @Test
    fun `starting work for a customer asks whether it is their booking, and answering links or lets go`() {
        business()
        val id = runBlocking {
            app.planRepository.save(
                com.example.fieldtrack.data.session.Capabilities.ofOwner(),
                org, null, ramesh, null, "Ploughing", today, DayPart.Afternoon, null, tractor, null, null,
            )
        }
        runBlocking {
            val start = StartWorkViewModel(app)
            start.selectCustomer(app.directoryRepository.findCustomer(org, ramesh)!!)
            val s = settled({ start.state.value }) { it.openBookings.isNotEmpty() }
            assertEquals(id, s.openBookings.single().booking.id)
            assertNull("offered, not assumed", s.bookingId)

            start.useBooking(s.openBookings.single())
            val used = start.state.value
            assertEquals(id, used.bookingId)
            assertEquals("Ploughing", used.workType.key)
            assertEquals(tractor, used.machineId)

            start.dropBooking()
            assertNull(start.state.value.bookingId)
            assertEquals("what was filled in stays", "Ploughing", start.state.value.workType.key)

            start.useBooking(s.openBookings.single())
            start.selectCustomer(app.directoryRepository.findCustomer(org, harjit)!!)
            assertNull("a booking made for somebody else does not follow a changed farmer", start.state.value.bookingId)
        }
    }

    @Test
    fun `a driver starting work is not offered what other drivers were booked for`() {
        business()
        val owner = com.example.fieldtrack.data.session.Capabilities.ofOwner()
        val mine = runBlocking {
            app.planRepository.save(owner, org, null, ramesh, null, "Ploughing", today, DayPart.Morning, null, tractor, driver, null)
        }
        runBlocking {
            app.planRepository.save(owner, org, null, ramesh, null, "Rotavator", today, DayPart.Afternoon, null, harvester, driver2, null)
        }
        signInAs(driver)
        runBlocking {
            val start = StartWorkViewModel(app)
            start.selectCustomer(app.directoryRepository.findCustomer(org, ramesh)!!)
            val s = settled({ start.state.value }) { it.openBookings.isNotEmpty() }
            assertEquals(
                "Gurmeet's booking, and its machine, are not Jaswant's to see or adopt",
                listOf(mine), s.openBookings.map { it.booking.id },
            )
        }
    }

    @Test
    fun `a booking for a machine since removed is not carried into the job`() {
        business()
        val id = runBlocking {
            app.planRepository.save(
                com.example.fieldtrack.data.session.Capabilities.ofOwner(),
                org, null, ramesh, null, "Ploughing", today, DayPart.Morning, null, "machine-that-went", null, null,
            )
        }
        runBlocking {
            val start = StartWorkViewModel(app, forBookingId = id)
            val s = settled({ start.state.value }) { it.bookingId != null }
            assertNull(s.machineId)
        }
    }

    @Test
    fun `cancelling keeps the reason and the booking, and it can be put back`() {
        business()
        val id = runBlocking {
            app.planRepository.save(
                com.example.fieldtrack.data.session.Capabilities.ofOwner(),
                org, null, ramesh, null, "Ploughing", today + 1, DayPart.Morning, null, tractor, null, null,
            )
        }
        form(id) { f, ready ->
            ready { customer != null }
            var left = false
            f.cancel(CancelReason.Rain) { left = true }
            until { left }
        }
        planScreen { vm, ready ->
            vm.pick(today + 1)
            val item = ready { items.firstOrNull()?.row?.booking?.status == BookingStatus.Cancelled }.items.single()
            assertEquals(CancelReason.Rain, item.row.booking.cancelReason)
            val free = ready { true }
            assertTrue("a cancelled booking frees its machine", "Tractor 1" in free.freeMachines)
        }
        form(id) { f, ready ->
            assertFalse(ready { customer != null }.editable)
            var left = false
            f.reopen { left = true }
            until { left }
        }
        assertEquals(BookingStatus.Planned, runBlocking { app.planRepository.find(org, id)!!.status })
    }

    @Test
    fun `saying it was done clears it from the missed list`() {
        business()
        val id = runBlocking {
            app.planRepository.save(
                com.example.fieldtrack.data.session.Capabilities.ofOwner(),
                org, null, ramesh, null, "Ploughing", today - 1, DayPart.Morning, null, null, null, null,
            )
        }
        form(id) { f, ready ->
            ready { customer != null }
            var left = false
            f.markDone { left = true }
            until { left }
        }
        planScreen { _, ready -> assertTrue(ready { overdue.isEmpty() }.overdue.isEmpty()) }
        assertEquals(BookingStatus.Done, runBlocking { app.planRepository.find(org, id)!!.status })
    }

    @Test
    fun `a driver is not shown somebody else's missed work`() {
        business()
        runBlocking {
            val all = com.example.fieldtrack.data.session.Capabilities.ofOwner()
            app.planRepository.save(all, org, null, ramesh, null, "Rotavator", today - 1, DayPart.Morning, null, null, driver2, null)
            app.planRepository.save(all, org, null, harjit, null, "Rotavator", today - 1, DayPart.Morning, null, null, driver, null)
        }
        signInAs(driver)
        planScreen { _, ready ->
            assertEquals(listOf("Harjit Kaur"), ready { overdue.isNotEmpty() }.overdue.map { it.customerName })
        }
        runBlocking { assertEquals(1, app.observePlanSummary(app.sessionStore.current()!!).first().missed) }
        signInAs(owner)
        planScreen { _, ready -> assertEquals(2, ready { overdue.isNotEmpty() }.overdue.size) }
    }

    // ------------------------------------------------------------------ the home card

    @Test
    fun `the home counts today and tomorrow for this person`() {
        bookTwo()
        form { f, _ -> f.fill(day = today + 1, driver = driver); assertTrue(f.saved()) }
        runBlocking {
            val owners = app.observePlanSummary(app.sessionStore.current()!!).first()
            assertEquals(PlanSummary(loaded = true, canPlan = true, today = 3, tomorrow = 1, missed = 0), owners)
        }
        signInAs(driver)
        runBlocking {
            val theirs = app.observePlanSummary(app.sessionStore.current()!!).first()
            assertEquals("theirs and the unassigned one", 2, theirs.today)
            assertEquals(1, theirs.tomorrow)
            assertFalse(theirs.canPlan)
        }
    }

    // ----------------------------------------------------------------------- settings

    @Test
    fun `the owner sets the hour and who sees what, and it is kept`() {
        business()
        runBlocking {
            val vm = PlanSettingsViewModel(app)
            val s = settled({ vm.state.value }) { it.loaded }
            assertEquals(4, s.settings.dayStartHour)
            assertFalse(s.settings.driversSeeAll)
            repeat(3) { vm.stepHour(-1) }
            vm.setDriversSeeAll(true)
            var saved = false
            vm.save { saved = true }
            until { saved }
            val stored = app.directoryRepository.observePlanSettings(org).first()
            assertEquals(PlanSettings(1, true), stored)
        }
    }

    @Test
    fun `the hour stays between midnight and eleven`() {
        business()
        runBlocking {
            val vm = PlanSettingsViewModel(app)
            settled({ vm.state.value }) { it.loaded }
            repeat(20) { vm.stepHour(-1) }
            assertEquals(0, vm.state.value.settings.dayStartHour)
            assertFalse(vm.state.value.canStepEarlier)
            repeat(30) { vm.stepHour(1) }
            assertEquals(11, vm.state.value.settings.dayStartHour)
            assertFalse(vm.state.value.canStepLater)
        }
    }

    @Test
    fun `somebody who is not the owner cannot change how the plan works`() {
        business(who = boss)
        runBlocking {
            val vm = PlanSettingsViewModel(app)
            val s = settled({ vm.state.value }) { it.loaded }
            assertFalse(s.allowed)
            vm.stepHour(2)
            vm.save { }
            settled({ vm.state.value }) { !it.saving }
            assertEquals("nothing was stored", PlanSettings(), app.directoryRepository.observePlanSettings(org).first())
        }
    }

    @Test
    fun `the plan's day follows the hour the business chose`() {
        business()
        val at = java.time.ZonedDateTime.now().withHour(3).withMinute(0).toInstant().toEpochMilli()
        val before = PlanDay.today(at, 4)
        val after = PlanDay.today(at, 2)
        assertEquals("at three it is still yesterday for a four o'clock business", after - 1, before)
    }

    // ------------------------------------------------------------------------ staleness

    @Test
    fun `a business of one is never told its plan may be behind`() {
        business()
        runBlocking { listOf(boss, driver, driver2).forEach { app.db.orgDao().upsertMembership(member(it, it, active = false)) } }
        planScreen { _, ready ->
            assertFalse("nobody else could have changed it", ready { true }.stale)
        }
    }

    @Test
    fun `a phone that has not heard from the others says so, and one that has does not`() {
        business()
        planScreen { _, ready ->
            val s = ready { true }
            assertTrue("a business with a crew, and no pull ever", s.stale)
            assertNull(s.updatedAt)
        }
        app.pullWatermarks.recordSuccess(org, System.currentTimeMillis())
        planScreen { _, ready ->
            val s = ready { true }
            assertFalse(s.stale)
            assertNotNull(s.updatedAt)
        }
        app.pullWatermarks.recordSuccess(org, System.currentTimeMillis() - 7 * 60 * 60 * 1000)
        planScreen { _, ready -> assertTrue("seven hours is long enough to be behind", ready { true }.stale) }
    }
}

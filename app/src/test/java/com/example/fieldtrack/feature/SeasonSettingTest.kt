package com.example.fieldtrack.feature

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.season.SeasonCalendar
import com.example.fieldtrack.data.season.SeasonStart
import com.example.fieldtrack.feature.customer.StatementPeriod
import com.example.fieldtrack.feature.customer.periodStart
import com.example.fieldtrack.feature.home.HomeViewModel
import com.example.fieldtrack.feature.money.SeasonViewModel
import com.example.fieldtrack.feature.money.SeasonWindow
import com.example.fieldtrack.feature.season.SeasonRow
import com.example.fieldtrack.feature.season.SeasonsState
import com.example.fieldtrack.feature.season.SeasonsViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Setting when the seasons begin, and everything that reads it.
 *
 * The business's dates are what "this season" means on every screen that says it,
 * for every person in that business — so the tests are about the dates arriving
 * where they are used, on the owner's phone and on a driver's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeasonSettingTest {

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val owner = "person-owner"
    private val driver = "person-driver"
    private val org = "org-seasons"
    private val zone = ZoneId.systemDefault()

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

    /** One business, signed in as [who]; its seasons stored as [stored]. */
    private fun business(who: String = owner, stored: String? = null) = runBlocking {
        val now = System.currentTimeMillis()
        app.db.orgDao().upsertOrg(
            OrgEntity(
                id = org, name = "Seasons Works", defaultLandUnit = MeasureUnit.Acre,
                seasons = stored, createdAt = now, updatedAt = now,
            )
        )
        app.db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(org, owner), orgId = org, personId = owner, displayName = "Owner",
                role = MemberRole.Owner, canSeeMoney = true, joinedAt = now, updatedAt = now,
            )
        )
        app.db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(org, driver), orgId = org, personId = driver, displayName = "Driver",
                role = MemberRole.Operator, joinedAt = now, updatedAt = now,
            )
        )
        app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit()
            .putString("personId", who).putString("orgId", org).commit()
        app.sessionStore.load()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun withEditor(block: (SeasonsViewModel, () -> SeasonsState) -> Unit) {
        val vm = SeasonsViewModel(app)
        fun state(): SeasonsState {
            repeat(100) {
                idle()
                if (vm.state.value.loaded) return vm.state.value
                Thread.sleep(20)
            }
            error("the editor never loaded")
        }
        state()
        block(vm) { idle(); vm.state.value }
    }

    private fun SeasonsState.dates() = rows.map { it.month to it.day }
    private fun SeasonsState.row(i: Int) = rows[i]

    // ------------------------------------------------------------- the editor

    @Test
    fun `it opens on the dates the business has now`() {
        business()
        withEditor { _, state ->
            assertEquals(listOf(6 to 1, 11 to 1), state().dates())
            assertTrue(state().allowed)
            assertTrue("nothing changed yet, but the usual dates can be saved as chosen", state().canSave)
        }
    }

    @Test
    fun `it opens on what the owner chose`() {
        business(stored = SeasonCalendar.of(listOf(SeasonStart(3, 1, "Zaid"), SeasonStart(7, 15, "Kharif")))!!.encode())
        withEditor { _, state ->
            assertEquals(listOf(3 to 1, 7 to 15), state().dates())
            assertEquals(listOf("Zaid", "Kharif"), state().rows.map { it.name })
        }
    }

    @Test
    fun `adding a season gives it a date no other has, up to four`() {
        business()
        withEditor { vm, state ->
            vm.add()
            assertEquals(3, state().rows.size)
            assertEquals("a date nobody else has", 3, state().dates().distinct().size)
            vm.add()
            assertEquals(4, state().rows.size)
            assertEquals(4, state().dates().distinct().size)
            assertFalse(state().canAdd)
            vm.add()
            assertEquals("a fifth is refused", 4, state().rows.size)
        }
    }

    @Test
    fun `a season can be removed but never the last one`() {
        business()
        withEditor { vm, state ->
            vm.remove(state().row(0).id)
            assertEquals(1, state().rows.size)
            assertFalse(state().canRemove)
            vm.remove(state().row(0).id)
            assertEquals("a calendar with no seasons has no answer", 1, state().rows.size)
        }
    }

    @Test
    fun `a day the month does not have cannot be saved`() {
        business()
        withEditor { vm, state ->
            val id = state().row(0).id
            vm.setMonth(id, 2)
            vm.setDay(id, "30")
            assertFalse(state().row(0).dayValid)
            assertFalse(state().canSave)
            vm.setDay(id, "28")
            assertTrue(state().canSave)
            vm.setDay(id, "0")
            assertFalse(state().canSave)
            vm.setDay(id, "")
            assertFalse("an emptied field is not a date", state().canSave)
            vm.setDay(id, "1x2")
            assertEquals("only digits are taken", "12", state().row(0).dayText)
        }
    }

    @Test
    fun `moving to a shorter month pulls the day in with it`() {
        business()
        withEditor { vm, state ->
            val id = state().row(0).id
            vm.setMonth(id, 1); vm.setDay(id, "31")
            vm.setMonth(id, 2)
            assertEquals("31 January never becomes 31 February", "28", state().row(0).dayText)
            vm.setMonth(id, 4)
            assertEquals("28", state().row(0).dayText)
            vm.setDay(id, "30"); vm.setMonth(id, 5)
            assertEquals("a longer month leaves it alone", "30", state().row(0).dayText)
        }
    }

    @Test
    fun `two seasons on one day cannot be saved`() {
        business()
        withEditor { vm, state ->
            vm.setMonth(state().row(1).id, 6)
            assertTrue(state().duplicate)
            assertFalse("said on the card that repeats the date...", state().repeatsEarlier(0))
            assertTrue("...not the one it repeats", state().repeatsEarlier(1))
            assertFalse(state().canSave)
            vm.setDay(state().row(1).id, "2")
            assertFalse(state().duplicate)
            assertTrue(state().canSave)
        }
    }

    @Test
    fun `the editor says which season today falls in under the dates as typed`() {
        business()
        withEditor { vm, state ->
            val id = state().row(0).id
            vm.setName(id, "Year")
            vm.remove(state().row(1).id)
            vm.setMonth(state().row(0).id, 1); vm.setDay(state().row(0).id, "1")
            val today = state().today!!
            assertEquals("Year", today.name)
            assertEquals(LocalDate.now(zone).year, today.start.year)
            assertEquals(1, today.start.monthValue)
            vm.setDay(state().row(0).id, "")
            assertNull("no claim from dates that are not dates", state().today)
        }
    }

    @Test
    fun `a name is kept to twenty characters`() {
        business()
        withEditor { vm, state ->
            vm.setName(state().row(0).id, "x".repeat(60))
            assertEquals(20, state().row(0).name.length)
        }
    }

    @Test
    fun `usual dates come back, unnamed`() {
        business(stored = SeasonCalendar.of(listOf(SeasonStart(1, 1, "Year")))!!.encode())
        withEditor { vm, state ->
            vm.useUsual()
            assertEquals(listOf(6 to 1, 11 to 1), state().dates())
            assertEquals(listOf("", ""), state().rows.map { it.name })
        }
    }

    // -------------------------------------------------------------- saving

    @Test
    fun `saving stores the dates on the business and queues them to send`() {
        business()
        var saved = false
        withEditor { vm, state ->
            vm.setName(state().row(0).id, "Kharif")
            vm.add()
            vm.save { saved = true }
            repeat(100) { idle(); if (saved) return@repeat; Thread.sleep(20) }
        }
        assertTrue(saved)
        runBlocking {
            val stored = app.db.orgDao().findOrg(org)!!.seasons
            val calendar = SeasonCalendar.parse(stored)
            assertEquals(3, calendar.starts.size)
            assertEquals("Kharif", calendar.starts.single { it.month == 6 }.name)
            val queued = app.db.outboxDao().pendingFor(SyncQueue.TABLE_ORGS, org)
            assertTrue("sent with the rest of the business", queued > 0)
        }
    }

    @Test
    fun `saved dates arrive on every screen already open`() {
        business()
        runBlocking {
            val seen = mutableListOf<SeasonCalendar>()
            val job = launch(Dispatchers.Default) { app.directoryRepository.observeSeasons(org).collect { seen += it } }
            repeat(50) { if (seen.isEmpty()) Thread.sleep(20) }
            assertEquals(SeasonCalendar.Default, seen.first())

            app.sessionStore.updateSeasons(SeasonCalendar.of(listOf(SeasonStart(1, 1, "Year")))!!)
            repeat(100) { if (seen.size < 2) Thread.sleep(20) }
            assertEquals(listOf("Year"), seen.last().starts.map { it.name })
            job.cancel()
        }
    }

    @Test
    fun `only the owner may change them`() {
        business(who = driver)
        runBlocking {
            val refused = runCatching {
                app.sessionStore.updateSeasons(SeasonCalendar.of(listOf(SeasonStart(1, 1)))!!)
            }.exceptionOrNull()
            assertTrue("refused for a driver", refused is com.example.fieldtrack.data.session.Capabilities.NotAllowed)
            assertNull("and nothing was written", app.db.orgDao().findOrg(org)!!.seasons)
        }
        withEditor { _, state -> assertFalse("the editor says so", state().allowed) }
    }

    @Test
    fun `a driver's phone follows the owner's change when it arrives`() {
        business(who = driver)
        runBlocking {
            val before = app.directoryRepository.observeSeasons(org).first()
            assertEquals(SeasonCalendar.Default, before)
            // The owner's change reaches this phone as the business's row, as a sync does.
            val row = app.db.orgDao().findOrg(org)!!
            app.db.orgDao().upsertOrg(
                row.copy(seasons = SeasonCalendar.of(listOf(SeasonStart(9, 1, "Rabi")))!!.encode())
            )
            val after = app.directoryRepository.observeSeasons(org).first()
            assertEquals(listOf(9 to 1), after.starts.map { it.month to it.day })
        }
    }

    // -------------------------------------------------- what reads the dates

    private val oneYear = SeasonCalendar.of(listOf(SeasonStart(1, 1, "Year")))!!
    private fun janFirst() = LocalDate.of(LocalDate.now(zone).year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `the owner's home counts from the business's own season`() {
        business(stored = oneYear.encode())
        runBlocking {
            val vm = HomeViewModel(app)
            val job = launch(Dispatchers.Default) { vm.state.collect { } }
            repeat(150) { idle(); if (vm.state.value.loaded) return@repeat; Thread.sleep(20) }
            idle()
            assertEquals(janFirst(), vm.state.value.seasonFromMillis)
            assertEquals("Year", vm.state.value.seasonName)
            job.cancel()
        }
    }

    @Test
    fun `the season screen shows this season and the one before from the business's dates`() {
        business(stored = oneYear.encode())
        runBlocking {
            val vm = SeasonViewModel(app)
            val job = launch(Dispatchers.Default) { vm.state.collect { } }
            fun settled(): com.example.fieldtrack.feature.money.SeasonState {
                repeat(150) { idle(); if (vm.state.value.loaded) return vm.state.value; Thread.sleep(20) }
                error("never loaded")
            }
            val now = settled()
            assertEquals(janFirst(), now.fromMillis)
            assertEquals("Year", now.seasonName)
            assertEquals(listOf(1 to 1), now.seasonStarts.map { it.month to it.day })
            assertTrue("the owner may change them", now.canChangeSeasons)

            vm.setWindow(SeasonWindow.Last)
            repeat(150) { idle(); if (vm.state.value.window == SeasonWindow.Last) return@repeat; Thread.sleep(20) }
            idle()
            val last = vm.state.value
            val lastJan = LocalDate.of(LocalDate.now(zone).year - 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            assertEquals(lastJan, last.fromMillis)
            assertEquals("ends where this one starts, a millisecond short of it", janFirst() - 1, last.toMillis)
            job.cancel()
        }
    }

    @Test
    fun `a manager who is not the owner sees the dates but cannot change them`() {
        business(who = driver, stored = oneYear.encode())
        runBlocking {
            val vm = SeasonViewModel(app)
            val job = launch(Dispatchers.Default) { vm.state.collect { } }
            repeat(150) { idle(); if (vm.state.value.loaded) return@repeat; Thread.sleep(20) }
            idle()
            assertEquals(listOf("Year"), vm.state.value.seasonStarts.map { it.name })
            assertFalse(vm.state.value.canChangeSeasons)
            job.cancel()
        }
    }

    @Test
    fun `a statement's season is the business's season`() {
        val now = System.currentTimeMillis()
        assertEquals(
            janFirst(),
            periodStart(StatementPeriod.Season, now, emptyList(), seasons = oneYear, zone = zone),
        )
        assertNotEquals(
            "and not the usual dates",
            periodStart(StatementPeriod.Season, now, emptyList(), seasons = SeasonCalendar.Default, zone = zone),
            janFirst(),
        )
    }

    @Test
    fun `a row of the editor knows what it is worth`() {
        val ok = SeasonRow(id = 1, month = 6, dayText = "30")
        assertTrue(ok.dayValid)
        assertEquals(SeasonStart(6, 30, null), ok.asStart())
        assertFalse(SeasonRow(id = 1, month = 6, dayText = "31").dayValid)
        assertNull(SeasonRow(id = 1, month = 6, dayText = "31").asStart())
        assertEquals("Kharif", SeasonRow(id = 1, name = " Kharif ", month = 6, dayText = "1").asStart()!!.name)
    }
}

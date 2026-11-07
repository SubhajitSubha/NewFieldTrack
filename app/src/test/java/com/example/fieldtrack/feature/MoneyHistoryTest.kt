package com.example.fieldtrack.feature

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.entity.FuelEntryEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.ServiceRecordEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.data.session.Startup
import com.example.fieldtrack.feature.money.HistoryFilter
import com.example.fieldtrack.feature.money.HistoryKind
import com.example.fieldtrack.feature.money.HistoryPeriod
import com.example.fieldtrack.feature.money.MoneyHistoryState
import com.example.fieldtrack.feature.money.MoneyHistoryViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** One list of every movement of money, and who is shown which part of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyHistoryTest {

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val org = "org-history"
    private val owner = "person-owner"
    private val driver = "person-driver"
    private var ramesh = ""
    private var tractor = ""

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

    private fun business(who: String) = runBlocking {
        val now = System.currentTimeMillis()
        app.db.orgDao().upsertOrg(OrgEntity(id = org, name = "History Works", defaultLandUnit = MeasureUnit.Acre, createdAt = now, updatedAt = now))
        app.db.orgDao().upsertMembership(
            MembershipEntity(id = membershipId(org, owner), orgId = org, personId = owner, displayName = "Owner", role = MemberRole.Owner, canSeeMoney = true, joinedAt = 1, updatedAt = 1)
        )
        app.db.orgDao().upsertMembership(
            MembershipEntity(id = membershipId(org, driver), orgId = org, personId = driver, displayName = "Jaswant", role = MemberRole.Operator, joinedAt = 1, updatedAt = 1)
        )
        tractor = app.directoryRepository.addMachine(org, "Tractor 1", MachineKind.Tractor)
        ramesh = app.directoryRepository.addCustomer(org, "Ramesh Singh", null, "Kheri")
        app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().putString("orgId", org).putString("personId", who).commit()
        @Suppress("UNCHECKED_CAST")
        val startup = SessionStore::class.java.getDeclaredField("_startup").apply { isAccessible = true }.get(app.sessionStore) as MutableStateFlow<Startup>
        startup.value = Startup.Loading
        app.sessionStore.load()

        val at = System.currentTimeMillis() - 3_600_000
        app.ledgerRepository.recordPayment(org, ramesh, 50_000, PaymentMethod.Cash, byPersonId = owner, note = "advance")
        app.ledgerRepository.recordPayout(org, Capabilities.ofOwner(), 30_000, PayoutCovers.Day, at, owner, toPersonId = driver, toName = "Jaswant")
        app.db.machineDao().upsertFuel(FuelEntryEntity("f1", org, tractor, null, 20.0, 2_000, at))
        app.db.machineDao().upsertService(ServiceRecordEntity("s1", org, tractor, "oil change", 1_500, null, at))
    }

    private fun history(block: suspend (MoneyHistoryViewModel, suspend (MoneyHistoryState.() -> Boolean) -> MoneyHistoryState) -> Unit) = runBlocking {
        val vm = MoneyHistoryViewModel(app)
        val job = launch(Dispatchers.Default) { vm.state.collect { } }
        block(vm) { cond ->
            repeat(200) {
                idle()
                val v = vm.state.value
                if (v.loaded && v.cond()) return@block v
                Thread.sleep(20)
            }
            error("never settled: ${vm.state.value}")
        }
        job.cancel()
    }

    @Test fun `the owner sees money in, money out, diesel and repairs together, newest first`() {
        business(owner)
        history { vm, ready ->
            val all = ready { rows.size >= 4 }
            assertEquals(
                setOf(HistoryKind.Received, HistoryKind.PaidOut, HistoryKind.Fuel, HistoryKind.Repairs),
                all.rows.map { it.kind }.toSet(),
            )
            assertEquals(all.rows.sortedByDescending { it.atMillis }, all.rows)
            assertEquals("what came in", 50_000L, all.inMinor)
            assertEquals("wages, diesel and the repair", 33_500L, all.outMinor)

            vm.setFilter(HistoryFilter.PaidOut)
            assertEquals(listOf(HistoryKind.PaidOut), ready { filter == HistoryFilter.PaidOut }.rows.map { it.kind })
            vm.setFilter(HistoryFilter.Costs)
            assertEquals(setOf(HistoryKind.Fuel, HistoryKind.Repairs), ready { filter == HistoryFilter.Costs }.rows.map { it.kind }.toSet())
            vm.setFilter(HistoryFilter.All)
            vm.setQuery("jaswant")
            assertEquals("a name finds the person's line", listOf("Jaswant"), ready { query == "jaswant" && rows.size == 1 }.rows.map { it.who })
        }
    }

    @Test fun `a payment taken back is listed but counts for nothing`() {
        business(owner)
        runBlocking {
            val p = app.db.orgDao().observePayoutsSince(org, 0).first().single()
            app.ledgerRepository.voidPayout(org, Capabilities.ofOwner(), p.id)
        }
        history { _, ready ->
            val s = ready { rows.any { it.voided } }
            assertEquals("still there", 4, s.rows.size)
            assertEquals("but not in what went out", 3_500L, s.outMinor)
        }
    }

    @Test fun `a driver who may see no money is shown nothing at all`() {
        business(driver)
        history { _, ready ->
            val s = ready { loaded }
            assertFalse(s.mayLook)
            assertTrue(s.rows.isEmpty())
        }
    }

    @Test fun `the period narrows what is listed`() {
        business(owner)
        history { vm, ready ->
            ready { rows.size >= 4 }
            vm.setPeriod(HistoryPeriod.Ever)
            assertEquals(HistoryPeriod.Ever, ready { period == HistoryPeriod.Ever && rows.size >= 4 }.period)
        }
    }
}

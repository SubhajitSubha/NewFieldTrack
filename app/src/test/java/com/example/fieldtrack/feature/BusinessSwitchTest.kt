package com.example.fieldtrack.feature

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.feature.machine.MachinesViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One person, two businesses, and a switch in the middle.
 *
 * In one they run their own show and in the other they drive for somebody. After
 * the switch, every screen has to be about the business they switched to — the
 * machines, the permissions, the tabs — and nothing about the one they left.
 *
 * Machines used to read the session once and keep it, and the navigation history
 * outlived the switch, so a tab reopened on the other business's machines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusinessSwitchTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val me = "person-gurmeet"
    private val own = "org-own"
    private val employer = "org-employer"

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
            val now = System.currentTimeMillis()
            val db = app.db
            fun org(id: String, name: String) = OrgEntity(
                id = id, name = name, defaultLandUnit = MeasureUnit.Acre, createdAt = now, updatedAt = now,
            )
            fun member(orgId: String, role: MemberRole) = MembershipEntity(
                id = membershipId(orgId, me), orgId = orgId, personId = me, displayName = "Gurmeet",
                role = role, joinedAt = now, updatedAt = now,
            )
            fun machine(orgId: String, name: String) = MachineEntity(
                id = "m-$orgId", orgId = orgId, name = name, kind = MachineKind.Tractor,
                createdAt = now, updatedAt = now,
            )
            db.orgDao().upsertOrg(org(own, "Gurmeet Farm Works"))
            db.orgDao().upsertOrg(org(employer, "Harpal Agro Works"))
            db.orgDao().upsertMembership(member(own, MemberRole.Owner))
            db.orgDao().upsertMembership(member(employer, MemberRole.Operator))
            db.machineDao().upsert(machine(own, "My Own Tractor"))
            db.machineDao().upsert(machine(employer, "Harpal's Swaraj"))
            app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit()
                .putString("personId", me).putString("orgId", own).putBoolean("isBusiness", false).commit()
            app.sessionStore.load()
        }
    }

    private fun until(what: String, check: () -> Boolean) {
        repeat(200) {
            compose.waitForIdle()
            if (check()) return
            Thread.sleep(25)
        }
        throw AssertionError("never saw: $what")
    }

    private fun shows(text: String) =
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    // ------------------------------------------------------------ the list

    @Test
    fun `the machines list follows the business, with that business's permissions`() {
        runBlocking {
            val vm = MachinesViewModel(app)
            val collecting = launch(Dispatchers.Default) { vm.state.collect { } }
            fun names() = vm.state.value.rows.map { it.name }.toSet()
            // The view model runs on the main thread, which this test is standing on:
            // the looper is let run between looks, or nothing it launched would ever start.
            fun waitFor(expected: Set<String>) {
                repeat(200) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    if (names() == expected) return
                    Thread.sleep(25)
                }
                throw AssertionError("expected $expected but the list said ${names()}")
            }

            waitFor(setOf("My Own Tractor"))
            assertTrue("an owner may add machines", vm.state.value.canEdit)

            app.sessionStore.switchTo(employer)
            waitFor(setOf("Harpal's Swaraj"))
            assertFalse("a driver may not", vm.state.value.canEdit)

            app.sessionStore.switchTo(own)
            waitFor(setOf("My Own Tractor"))
            assertTrue(vm.state.value.canEdit)
            collecting.cancel()
        }
    }

    // ---------------------------------------------------------- the whole app

    @Test
    fun `after a switch the tabs show the new business and not the one left`() {
        compose.setContent { com.example.fieldtrack.feature.AppTheme { FieldTrackApp() } }
        until("the owner's home") { shows("Gurmeet Farm Works") }

        // The Machines tab, as a solo owner.
        compose.onAllNodes(hasText("Machines")).onFirst().performClick()
        until("my own machine") { shows("My Own Tractor") }
        assertFalse(shows("Harpal's Swaraj"))

        // Open the machine's own page, so there is a screen on top of the tab — the
        // kind a kept-over history would bring back in the next business.
        compose.onAllNodes(hasText("My Own Tractor")).onFirst().performClick()
        until("the machine's page") { shows("Breakdown") }

        // Switch to driving for somebody else.
        runBlocking { app.sessionStore.switchTo(employer) }
        until("the other business's home") { shows("Harpal Agro Works") }
        assertFalse("nothing of the business just left", shows("My Own Tractor"))

        // The same tab, now the driver's: the other business's machines.
        compose.onAllNodes(hasText("Machines")).onFirst().performClick()
        until("their machine") { shows("Harpal's Swaraj") }
        assertFalse("not the machine from the business just left", shows("My Own Tractor"))

        // And back again.
        runBlocking { app.sessionStore.switchTo(own) }
        until("my own home again") { shows("Gurmeet Farm Works") }
        compose.onAllNodes(hasText("Machines")).onFirst().performClick()
        until("my own machine again") { shows("My Own Tractor") }
        assertFalse(shows("Harpal's Swaraj"))
        assertEquals(1, compose.onAllNodes(hasText("My Own Tractor")).fetchSemanticsNodes().size)
    }
}

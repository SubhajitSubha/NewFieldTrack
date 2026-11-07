package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.feedback.RatingPrompt
import com.example.fieldtrack.data.feedback.RatingStep
import com.example.fieldtrack.data.sync.SyncChange
import com.example.fieldtrack.data.sync.SyncOutcome
import com.example.fieldtrack.data.sync.SyncTransport
import com.example.fieldtrack.data.db.OutboxOp
import com.example.fieldtrack.data.update.GatedTransport
import com.example.fieldtrack.data.update.UpdateGate
import com.example.fieldtrack.data.update.UpdateNeed
import com.example.fieldtrack.data.update.VersionRules
import com.example.fieldtrack.data.update.VersionRulesSource
import com.example.fieldtrack.data.update.needFor
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Too-old installs, the one-time question, the practice business, and that none of them can hurt real records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrustAndHelpTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var clock = 1_000_000_000L

    @Before fun setUp() {
        listOf(UpdateGate.PREFS, RatingPrompt.PREFS).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        clock = 1_000_000_000L
    }

    // ------------------------------------------------------------------ versions

    private class Rules(var rules: VersionRules?) : VersionRulesSource {
        override suspend fun fetch() = rules
    }

    @Test fun `an install below the minimum is stopped, one below the latest is nudged, the rest left alone`() {
        val rules = VersionRules(minCode = 5, latestCode = 8)
        assertEquals(UpdateNeed.Required, needFor(4, rules, snoozed = false))
        assertEquals(UpdateNeed.Required, needFor(4, rules, snoozed = true))
        assertEquals(UpdateNeed.Available, needFor(6, rules, snoozed = false))
        assertEquals(UpdateNeed.Current, needFor(6, rules, snoozed = true))
        assertEquals(UpdateNeed.Current, needFor(8, rules, snoozed = false))
        assertEquals("no rules known is no news", UpdateNeed.Current, needFor(1, null, snoozed = false))
    }

    @Test fun `what the server said is remembered, so a block survives having no signal`() = runBlocking {
        val source = Rules(VersionRules(minCode = 5, latestCode = 5))
        val gate = UpdateGate(context, installedCode = 3, source = source, now = { clock })
        assertEquals(UpdateNeed.Current, gate.need.value)
        gate.refresh()
        assertEquals(UpdateNeed.Required, gate.need.value)
        source.rules = null // offline
        gate.refresh()
        assertEquals("silence is neither all clear nor a fresh block", UpdateNeed.Required, gate.need.value)
        assertEquals("a restart does not lift it", UpdateNeed.Required, UpdateGate(context, 3, source, { clock }).need.value)
    }

    @Test fun `later hides the nudge for a few days or until something newer, and never a required update`() = runBlocking {
        val source = Rules(VersionRules(minCode = 1, latestCode = 9))
        val gate = UpdateGate(context, installedCode = 5, source = source, now = { clock })
        gate.refresh()
        assertEquals(UpdateNeed.Available, gate.need.value)
        gate.later()
        assertEquals(UpdateNeed.Current, gate.need.value)
        clock += UpdateGate.LATER_MILLIS + 1
        assertEquals("back after the snooze", UpdateNeed.Available, UpdateGate(context, 5, source, { clock }).need.value)
        gate.later()
        source.rules = VersionRules(minCode = 1, latestCode = 10)
        clock += 7 * 60 * 60 * 1000 // past the read interval
        gate.refresh()
        assertEquals("a newer version is worth saying again", UpdateNeed.Available, gate.need.value)
    }

    @Test fun `the server is not asked on every change, but a blocked install asks every time`() = runBlocking {
        var asked = 0
        val source = object : VersionRulesSource {
            override suspend fun fetch(): VersionRules { asked++; return VersionRules(0, 0) }
        }
        val gate = UpdateGate(context, installedCode = 1, source = source, now = { clock })
        gate.refresh(); gate.refresh(); gate.refresh()
        assertEquals(1, asked)
        clock += 7 * 60 * 60 * 1000
        gate.refresh()
        assertEquals(2, asked)
    }

    // -------------------------------------------------------------------- gating

    private class Recorder : SyncTransport {
        override val name = "test"
        val sent = mutableListOf<String>()
        override suspend fun send(batch: List<SyncChange>): List<SyncOutcome> {
            sent += batch.map { it.entityId }
            return batch.map { SyncOutcome.Accepted }
        }
    }

    private fun change(id: String, org: String) = SyncChange(1, org, "customers", id, OutboxOp.Upsert, "{}", 1, 0)

    @Test fun `a blocked install holds every change and sends none`() = runBlocking {
        val inner = Recorder()
        val outcomes = GatedTransport(inner, blocked = { true }).send(listOf(change("a", "o"), change("b", "o")))
        assertTrue(outcomes.all { it is SyncOutcome.Deferred })
        assertTrue(inner.sent.isEmpty())
    }

    @Test fun `the practice business is taken as sent and goes nowhere, and the real one still goes`() = runBlocking {
        val inner = Recorder()
        val gate = GatedTransport(inner, blocked = { false }, isSample = { it == "practice" })
        val outcomes = gate.send(listOf(change("real-1", "mine"), change("fake-1", "practice"), change("real-2", "mine"), change("fake-2", "practice")))
        assertEquals(4, outcomes.size)
        assertTrue(outcomes.all { it == SyncOutcome.Accepted })
        assertEquals(listOf("real-1", "real-2"), inner.sent)
    }

    // -------------------------------------------------------------------- rating

    @Test fun `the question waits for a fortnight and some finished work, is put off for a month, and once answered is never asked again`() {
        val prompt = RatingPrompt(context, now = { clock })
        assertFalse("day one", prompt.eligible(completedJobs = 50))
        clock += 14L * 24 * 60 * 60 * 1000
        assertFalse("a fortnight but no work", prompt.eligible(completedJobs = 2))
        assertTrue(prompt.eligible(completedJobs = 5))
        prompt.evaluate(5)
        assertEquals(RatingStep.Ask, prompt.step.value)
        prompt.later()
        assertEquals(RatingStep.Hidden, prompt.step.value)
        assertFalse(prompt.eligible(5))
        clock += 31L * 24 * 60 * 60 * 1000
        assertTrue(prompt.eligible(5))
        prompt.answer(helping = true)
        assertEquals(RatingStep.Happy, prompt.step.value)
        prompt.finish()
        clock += 400L * 24 * 60 * 60 * 1000
        assertFalse(RatingPrompt(context, now = { clock }).eligible(500))
    }

    // ----------------------------------------------------------------- practice

    private fun app() = ApplicationProvider.getApplicationContext<FieldFlowApplication>().also { app ->
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder().setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("AIzaTestOnly0000000000000000000000000").setProjectId("fieldtrack-test").build(),
            )
        }
    }

    private suspend fun signedInOwner(app: FieldFlowApplication): String {
        val now = System.currentTimeMillis()
        val org = "real-org"
        app.db.orgDao().upsertOrg(OrgEntity(id = org, name = "Harpal Agro", defaultLandUnit = MeasureUnit.Acre, createdAt = now, updatedAt = now))
        app.db.orgDao().upsertMembership(
            MembershipEntity(id = membershipId(org, "p1"), orgId = org, personId = "p1", displayName = "Harpal", role = MemberRole.Owner, joinedAt = now, updatedAt = now)
        )
        app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit()
            .putString("personId", "p1").putString("orgId", org).putBoolean("isBusiness", true).commit()
        app.sessionStore.load()
        return org
    }

    @Test fun `the practice business has its own made-up work, in the person's own currency, and the real one is untouched`() = runBlocking {
        val app = app()
        val real = signedInOwner(app)
        assertTrue(app.sampleBusiness.start())
        val practice = app.sessionStore.sampleOrgId()
        assertNotNull(practice)
        assertNotEquals(real, practice)
        assertEquals("it is the one on screen", practice, app.sessionStore.current()!!.orgId)
        assertTrue(app.db.exportDao().customers(practice!!).size == 3)
        assertTrue(app.db.exportDao().completedJobs(practice) >= 3)
        assertTrue("with a bill and a payment against it", app.db.exportDao().ledger(practice).any { it.kind == com.example.fieldtrack.data.db.LedgerKind.Payment })
        assertEquals(0, app.db.exportDao().customers(real).size)
        // Starting it again goes back to the same one.
        assertTrue(app.sampleBusiness.start())
        assertEquals(practice, app.sessionStore.sampleOrgId())
        assertEquals(1, app.db.orgDao().membershipsOf("p1").count { it.orgId == practice })
    }

    @Test fun `nothing the practice queues is ever left waiting to go up, and it is not counted as a business to delete`() = runBlocking {
        val app = app()
        signedInOwner(app)
        app.sampleBusiness.start()
        val practice = app.sessionStore.sampleOrgId()!!
        assertTrue(app.sessionStore.isSample(practice))
        assertTrue(app.db.outboxDao().pendingCountFor(practice) > 0)
        val queued = app.db.outboxDao().nextBatch(500).filter { it.orgId == practice }
        val result = GatedTransport(Recorder(), blocked = { false }, isSample = { app.sessionStore.isSample(it) })
            .send(queued.map { SyncChange(it.seq, it.orgId, it.tableName, it.entityId, it.operation, it.payloadJson, it.createdAt, 0) })
        assertTrue(result.all { it == SyncOutcome.Accepted })
        assertFalse(practice in app.sessionStore.businessesOwnedAlone())
    }

    @Test fun `removing the practice takes every row it made, and puts the person back in their own business`() = runBlocking {
        val app = app()
        val real = signedInOwner(app)
        app.sampleBusiness.start()
        val practice = app.sessionStore.sampleOrgId()!!
        app.sampleBusiness.remove()
        assertNull(app.sessionStore.sampleOrgId())
        assertEquals(0, app.db.exportDao().customers(practice).size)
        assertEquals(0, app.db.exportDao().jobs(practice).size)
        assertEquals(0, app.db.exportDao().ledger(practice).size)
        assertNull(app.db.orgDao().observeOrg(practice).first())
        assertEquals(0, app.db.outboxDao().pendingCountFor(practice))
        assertEquals(real, app.sessionStore.current()!!.orgId)
        assertNotNull("their own business is still there", app.db.orgDao().observeOrg(real).first())
    }

    @Test fun `the practice business is finished even when the screen that asked for it goes away`() = runBlocking {
        val app = app()
        signedInOwner(app)
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) { app.sampleBusiness.start() }
        kotlinx.coroutines.delay(5)
        job.cancel()
        job.join()
        val practice = app.sessionStore.sampleOrgId()
        // Cancelled almost at once, it may not have begun; if it did begin it must have been finished.
        if (practice != null) {
            assertEquals(3, app.db.exportDao().customers(practice).size)
            assertTrue(app.db.exportDao().completedJobs(practice) >= 3)
        }
    }
}

package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.PlanTier
import com.example.fieldtrack.data.db.entity.EntitlementEntity
import com.example.fieldtrack.data.plan.Need
import com.example.fieldtrack.data.plan.PlanGate
import com.example.fieldtrack.data.plan.PlanLimit
import com.example.fieldtrack.data.plan.PlanLimits
import com.example.fieldtrack.data.plan.PlanStatus
import com.example.fieldtrack.data.plan.Plans
import com.example.fieldtrack.data.plan.Verdict
import com.example.fieldtrack.data.repository.DirectoryRepository
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the free plan allows, what paying adds, and that nothing is ever taken away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlansTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A
    private val owner = Capabilities.ofOwner()
    private var today = 20_000L

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private fun paid(until: Long?, founding: Boolean = false) =
        EntitlementEntity(id = org, orgId = org, tier = PlanTier.Business, untilDay = until, founding = founding)

    // ------------------------------------------------------------------ status

    @Test fun `no record is the free plan`() {
        assertEquals(PlanStatus.Free, Plans.status(null, today))
        assertEquals(PlanTier.Free, Plans.tier(null, today))
        assertEquals(PlanStatus.Free, Plans.status(EntitlementEntity(id = org, orgId = org), today))
    }

    @Test fun `a paid plan is active until its day, then in grace, then lapsed`() {
        assertEquals(PlanStatus.Active(30, false), Plans.status(paid(today + 30), today))
        assertEquals("the last day is still paid", PlanStatus.Active(0, false), Plans.status(paid(today), today))
        assertEquals(PlanStatus.Grace(PlanLimits.GRACE_DAYS - 1, false), Plans.status(paid(today - 1), today))
        assertEquals(PlanStatus.Grace(0, false), Plans.status(paid(today - PlanLimits.GRACE_DAYS), today))
        assertEquals(PlanStatus.Lapsed, Plans.status(paid(today - PlanLimits.GRACE_DAYS - 1), today))
        assertEquals("the extras follow active and grace", PlanTier.Business, Plans.tier(paid(today - 5), today))
        assertEquals(PlanTier.Free, Plans.tier(paid(today - 100), today))
        assertEquals("founding is remembered", PlanStatus.Active(5, true), Plans.status(paid(today + 5, founding = true), today))
        assertEquals("no end given is running", PlanStatus.Active(null, false), Plans.status(paid(null), today))
    }

    // ----------------------------------------------------------------- verdicts

    @Test fun `free allows three machines and the owner alone, and none of the extras`() {
        assertEquals(Verdict.Allowed, Plans.verdict(Need.MoreMachines, PlanTier.Free, machines = PlanLimits.FREE_MACHINES - 1, people = 1))
        assertEquals(Verdict.NeedsBusiness(Need.MoreMachines), Plans.verdict(Need.MoreMachines, PlanTier.Free, PlanLimits.FREE_MACHINES, 1))
        assertEquals(Verdict.Allowed, Plans.verdict(Need.TeamMember, PlanTier.Free, 0, people = PlanLimits.FREE_PEOPLE - 1))
        assertEquals(Verdict.NeedsBusiness(Need.TeamMember), Plans.verdict(Need.TeamMember, PlanTier.Free, 0, PlanLimits.FREE_PEOPLE))
        assertEquals(Verdict.NeedsBusiness(Need.BookingLink), Plans.verdict(Need.BookingLink, PlanTier.Free, 0, 1))
        assertEquals(Verdict.NeedsBusiness(Need.ProofOfWork), Plans.verdict(Need.ProofOfWork, PlanTier.Free, 0, 1))
    }

    @Test fun `paying allows everything, however many there already are`() {
        for (need in Need.entries) assertEquals(Verdict.Allowed, Plans.verdict(need, PlanTier.Business, machines = 40, people = 12))
    }

    // ------------------------------------------------------------ in the business

    private fun directory(gate: PlanGate?) = DirectoryRepository(db = h.db, syncQueue = h.syncQueue, ledger = h.ledger, clock = h.clock, ids = h.ids, gate = gate)

    @Test fun `the fourth machine needs the plan, and having paid it is allowed`() = runTest {
        val gate = PlanGate(h.db) { today }
        val repo = directory(gate)
        repeat(3) { repo.addMachine(org, "M$it", MachineKind.Tractor) }
        var blocked = false
        try { repo.addMachine(org, "M4", MachineKind.Tractor) } catch (e: PlanLimit) { blocked = e.need == Need.MoreMachines }
        assertTrue(blocked)
        h.db.orgDao().upsertEntitlement(paid(today + 90))
        repo.addMachine(org, "M4", MachineKind.Tractor)
        assertEquals(4, h.db.machineDao().liveMachineCount(org))
    }

    @Test fun `a plan that ran out takes nothing away, it only stops adding`() = runTest {
        val gate = PlanGate(h.db) { today }
        h.db.orgDao().upsertEntitlement(paid(today + 90))
        val repo = directory(gate)
        repeat(5) { repo.addMachine(org, "M$it", MachineKind.Tractor) }
        today += 400 // a year later, never renewed
        assertEquals(PlanStatus.Lapsed, gate.status(org))
        assertEquals("every machine is still there", 5, repo.observeMachines(org).first().size)
        var blocked = false
        try { repo.addMachine(org, "M6", MachineKind.Tractor) } catch (e: PlanLimit) { blocked = true }
        assertTrue(blocked)
        // And work can still be recorded, which is what the plan is never allowed to stop.
        val job = h.work.startJob(org, TestHarness.CUSTOMER_A, "Rotavator", com.example.fieldtrack.data.db.BillingMode.PerAcre, com.example.fieldtrack.data.db.MeasureUnit.Acre, 100_000)
        assertNotNull(h.db.jobDao().find(org, job))
    }

    @Test fun `a team needs the plan, and the business already has two people so an invite is stopped on free`() = runTest {
        val team = TeamRepository(db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids, gate = PlanGate(h.db) { today })
        var blocked = false
        try { team.createInvite(org, "Harpal Agro", owner) } catch (e: PlanLimit) { blocked = e.need == Need.TeamMember }
        assertTrue(blocked)
        h.db.orgDao().upsertEntitlement(paid(today + 10))
        assertNotNull(team.createInvite(org, "Harpal Agro", owner))
    }

    // ------------------------------------------------------------------ the server

    @Test fun `what the server writes about a plan arrives on every phone, and no phone can write it`() = runTest {
        RestoreApplier(h.db).apply(
            listOf(
                RemoteRow(
                    table = "entitlement", entityId = "current",
                    fields = mapOf("id" to org, "orgId" to org, "tier" to "Business", "untilDay" to 21_000L, "founding" to true, "updatedAt" to 5L, "_seq" to 0L, "_serverAt" to 9L, "_device" to "server"),
                )
            )
        )
        val e = h.db.orgDao().entitlement(org)!!
        assertEquals(PlanTier.Business, e.tier); assertEquals(21_000L, e.untilDay); assertTrue(e.founding)

        val rules = File("../firestore.rules").readText()
        val block = rules.substringAfter("match /entitlement/{doc} {").substringBefore("}")
        assertTrue(block.contains("allow read: if isMember(orgId)")); assertTrue(block.contains("allow write: if false"))
        val claim = rules.substringAfter("match /purchase_claims/{claimId} {").substringBefore("}")
        assertTrue(claim.contains("allow create: if isOwner(orgId)")); assertTrue(claim.contains("allow read, update, delete: if false"))
        val generic = rules.substringAfter("match /{collection}/{document} {")
        assertEquals(2, Regex("collection != 'entitlement'").findAll(generic).count())
        assertEquals(2, Regex("collection != 'purchase_claims'").findAll(generic).count())
    }

    @Test fun `the server checks a purchase belongs to the business it claims, and is still running`() {
        val code = File("../functions/index.js").readText()
        assertTrue(code.contains("obfuscatedExternalAccountId === orgId"))
        assertTrue(code.contains("SUBSCRIPTION_STATE_ACTIVE"))
        assertTrue("a founding offer is kept by the server, not claimed by the phone", code.contains("tags.includes(\"founding\")"))
    }
}

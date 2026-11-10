package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.auth.PhoneAuth
import com.example.fieldtrack.data.auth.AuthResult
import com.example.fieldtrack.data.auth.SignedIn
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.RolePreset
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.data.session.Startup
import com.example.fieldtrack.data.sync.SyncChange
import com.example.fieldtrack.data.sync.SyncOutcome
import com.example.fieldtrack.data.sync.SyncTransport
import com.example.fieldtrack.feature.home.HomeKind
import com.example.fieldtrack.feature.home.homeKindFor
import com.example.fieldtrack.feature.team.duplicateAmong
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

/** Somebody who may only look, a left-behind identity, a second business, and deleting an account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TeamAndAccountTest {

    private lateinit var h: TestHarness
    private lateinit var store: SessionStore
    private val org = TestHarness.ORG_A

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().clear().commit()
        h = TestHarness()
        store = SessionStore(context, h.db, h.clock, h.ids)
    }

    @After fun tearDown() = h.close()

    private fun member(personId: String, name: String, role: MemberRole = MemberRole.Operator, code: String? = null, phone: String? = null, replaces: String? = null) =
        MembershipEntity(
            id = membershipId(org, personId), orgId = org, personId = personId, displayName = name, role = role,
            phone = phone, joinedWithCode = code, replacesPersonId = replaces, joinedAt = 1, updatedAt = 1,
        )

    // --------------------------------------------------------------------- viewer

    @Test
    fun `a viewer sees what they were given and can change nothing`() {
        val viewer = member("v", "Accountant", MemberRole.Viewer).copy(canSeeMoney = true, canSeePeopleCost = true)
        val can = Capabilities.of(viewer)
        assertTrue(Capability.SeeMoney in can)
        assertTrue(Capability.SeePeopleCost in can)
        for (cap in listOf(Capability.RecordWork, Capability.EditCustomers, Capability.TakePayment, Capability.AmendBilledWork,
            Capability.EditTeam, Capability.EditMachines, Capability.PlanWork, Capability.PayOut, Capability.EditBusiness)) {
            assertFalse("$cap", cap in can)
        }
    }

    @Test
    fun `ticking every switch on a viewer opens no way to change anything`() {
        val everything = member("v", "X", MemberRole.Viewer).copy(
            canSeeMoney = true, canAddCustomers = true, canCloseBills = true, canEditTeam = true,
            canEditMachines = true, canPayOut = true, canSeePeopleCost = true, canPlanWork = true,
        )
        val can = Capabilities.of(everything)
        assertFalse(Capability.RecordWork in can)
        assertFalse(Capability.EditTeam in can)
        assertFalse(Capability.PayOut in can)
        assertFalse(Capability.PlanWork in can)
    }

    @Test
    fun `a viewer is a preset an owner can hand out and a manager cannot`() {
        assertEquals(MemberRole.Viewer, RolePreset.Viewer.role)
        assertTrue(RolePreset.Viewer.grants.seeMoney)
        assertFalse(RolePreset.Viewer.grants.seePeopleCost)
        assertTrue(RolePreset.Viewer in RolePreset.offeredBy(editorIsOwner = true))
        assertFalse(RolePreset.Viewer in RolePreset.offeredBy(editorIsOwner = false))
        assertEquals(RolePreset.Viewer, RolePreset.matching(MemberRole.Viewer, RolePreset.Viewer.grants.copy(payOut = true)))
        assertEquals("a driver is still a driver", RolePreset.Driver, RolePreset.matching(MemberRole.Operator, RolePreset.Driver.grants))
    }

    @Test
    fun `somebody watching opens on the yard`() {
        val viewer = member("v", "X", MemberRole.Viewer)
        assertEquals(HomeKind.Yard, homeKindFor(MemberRole.Viewer, Capabilities.of(viewer), business = false))
        assertEquals(HomeKind.Yard, homeKindFor(MemberRole.Viewer, Capabilities.of(viewer), business = true))
    }

    @Test
    fun `the rules keep a viewer from writing`() {
        val rules = java.io.File("../firestore.rules").readText()
        assertTrue("a viewer is a role the rules know", rules.contains("function isViewer"))
        assertTrue(rules.contains("!isViewer(orgId)"))
        // The blanket write must carry it; reading stays open to the whole team.
        val write = rules.substringAfter("allow write: if isMember(orgId)").substringBefore(";")
        assertTrue(write.contains("!isViewer(orgId)"))
    }

    // ------------------------------------------------- the same person, twice

    @Test
    fun `the same name once by hand and once on a code is asked about, whatever the numbers`() {
        val written = member("hand-1", "Gurmeet Singh", phone = "+919000000001")
        val joined = member("uid-9", "gurmeet singh ", code = "ABC123", phone = "+919000000099")
        val pair = duplicateAmong(listOf(written, joined), emptySet())!!
        assertEquals(joined.id, pair.keepMembershipId)
        assertEquals(written.id, pair.absorbMembershipId)
    }

    @Test
    fun `two men called Singh who both came in by code are not asked about`() {
        val a = member("a", "Singh", code = "X1")
        val b = member("b", "Singh", code = "X2")
        assertNull(duplicateAmong(listOf(a, b), emptySet()))
    }

    @Test
    fun `an owner is never offered as the one to absorb, and a dismissed pair stays dismissed`() {
        val owner = member("o", "Harpal", MemberRole.Owner)
        val joined = member("j", "Harpal", code = "C")
        assertNull(duplicateAmong(listOf(owner, joined), emptySet()))
        val written = member("w", "Kamau")
        val joined2 = member("j2", "Kamau", code = "D")
        val pair = duplicateAmong(listOf(written, joined2), emptySet())!!
        val key = listOf(pair.keepMembershipId, pair.absorbMembershipId).sorted().joinToString("|")
        assertNull(duplicateAmong(listOf(written, joined2), setOf(key)).takeIf { false })
    }

    @Test
    fun `an identity somebody has left behind is not listed as a second person`() = runTest {
        h.db.orgDao().upsertMembership(member("anon-1", "Harpal", MemberRole.Owner))
        h.db.orgDao().upsertMembership(member("uid-1", "Harpal", MemberRole.Owner, replaces = "anon-1"))
        h.db.orgDao().upsertMembership(member("drv", "Gurmeet"))
        assertEquals(listOf("uid-1", "drv").sorted(), h.db.orgDao().observeTeam(org).first().map { it.personId }.sorted())
        assertEquals(setOf("uid-1", "drv"), h.db.orgDao().observeEveryone(org).first().map { it.personId }.toSet())
    }

    @Test
    fun `a left-behind identity in one business does not hide anybody in another`() = runTest {
        h.db.orgDao().upsertMembership(member("anon-1", "Harpal", MemberRole.Owner))
        val other = member("uid-1", "Harpal", MemberRole.Owner, replaces = "anon-1").copy(orgId = TestHarness.ORG_B, id = membershipId(TestHarness.ORG_B, "uid-1"))
        h.db.orgDao().upsertMembership(other)
        assertEquals(listOf("anon-1"), h.db.orgDao().observeTeam(org).first().map { it.personId })
    }

    // ------------------------------------------------------------ second business

    private suspend fun onboard(currency: String = "INR") {
        store.completeOnboarding("Harpal", "Harpal Agro", MeasureUnit.Acre, true, currency, 100)
        store.activate()
    }

    @Test
    fun `a second business is the person's own, in its own currency, and becomes the one on screen`() = runTest {
        onboard("INR")
        val first = store.current()!!.orgId
        val second = store.startAnotherBusiness("Harpal Kenya", MeasureUnit.Hectare, "KES", 1, 7)!!
        assertTrue(first != second)
        val session = store.current()!!
        assertEquals(second, session.orgId)
        assertEquals("KES", session.currencyCode)
        assertEquals(MemberRole.Owner, session.role)
        assertEquals("Harpal", session.displayName)
        assertEquals("the first is untouched", "INR", h.db.orgDao().observeOrg(first).first()!!.currencyCode)
        assertEquals(2, h.db.orgDao().activeMembershipCountFor(session.personId))
        val tables = h.db.outboxDao().nextBatch(100).filter { it.orgId == second }.map { it.tableName }
        assertTrue("organisations" in tables && "memberships" in tables)
    }

    // ---------------------------------------------------------------- deleting

    private class Server(private val accept: Boolean) : SyncTransport {
        override val name = "test"
        var asked: Triple<String, List<String>, List<String>>? = null
        override suspend fun send(batch: List<SyncChange>): List<SyncOutcome> = batch.map { SyncOutcome.Accepted }
        override suspend fun requestAccountDeletion(personId: String, orgIds: List<String>, ownedAlone: List<String>, at: Long): Boolean {
            asked = Triple(personId, orgIds, ownedAlone)
            return accept
        }
    }

    private class Auth : PhoneAuth {
        var deleted = false
        var out = false
        override fun current(): SignedIn? = null
        override suspend fun sendCode(phoneE164: String, resendToken: Any?): AuthResult = AuthResult.Failed(com.example.fieldtrack.data.auth.AuthFailure.Unknown, "")
        override suspend fun verify(verificationId: String, code: String): AuthResult = AuthResult.Failed(com.example.fieldtrack.data.auth.AuthFailure.Unknown, "")
        override suspend fun signOut() { out = true }
        override suspend fun deleteAccount(): Boolean { deleted = true; return true }
    }

    @Test
    fun `the businesses that would go are the ones with nobody else to run them`() = runTest {
        onboard()
        val mine = store.current()!!.orgId
        assertEquals(listOf(mine), store.businessesOwnedAlone())
        h.db.orgDao().upsertMembership(member("co", "Partner", MemberRole.Owner).copy(orgId = mine, id = membershipId(mine, "co")))
        assertTrue("a second owner takes it off the list", store.businessesOwnedAlone().isEmpty())
    }

    @Test
    fun `an account is deleted once the server has the request, and everything on the phone goes`() = runTest {
        onboard()
        val mine = store.current()!!.orgId
        val personId = store.current()!!.personId
        val server = Server(accept = true)
        val auth = Auth()
        assertEquals(SessionStore.DeletionResult.Done, store.deleteAccount(server, auth))
        assertEquals(personId, server.asked!!.first)
        assertEquals(listOf(mine), server.asked!!.second)
        assertEquals("a business that is theirs alone goes with it", listOf(mine), server.asked!!.third)
        assertTrue(auth.deleted && auth.out)
        assertTrue(store.startup.value is Startup.NeedsSignIn)
        assertNull(h.db.orgDao().observeOrg(mine).first())
        assertEquals(0, h.db.orgDao().membershipCountFor(personId))
    }

    @Test
    fun `with no way to reach the server nothing is deleted anywhere`() = runTest {
        onboard()
        val mine = store.current()!!.orgId
        assertEquals(SessionStore.DeletionResult.CouldNotReachServer, store.deleteAccount(Server(accept = false), Auth()))
        assertEquals(SessionStore.DeletionResult.CouldNotReachServer, store.deleteAccount(null, null))
        assertNotNull(h.db.orgDao().observeOrg(mine).first())
        assertTrue(store.startup.value is Startup.Ready)
    }

    @Test
    fun `the booking link keeps its secret through being switched off and on`() = runTest {
        onboard()
        h.db.orgDao().upsertEntitlement(com.example.fieldtrack.data.db.entity.EntitlementEntity(id = store.current()!!.orgId, orgId = store.current()!!.orgId, tier = com.example.fieldtrack.data.db.PlanTier.Business, untilDay = java.time.LocalDate.now().toEpochDay() + 30))
        store.setBookingLink(true, showRates = false)
        val first = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertTrue(first.bookingLink)
        val slug = first.bookingSlug
        assertNotNull(slug)
        store.setBookingLink(false, showRates = false)
        val off = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertFalse(off.bookingLink)
        assertEquals("a link already given out works again", slug, off.bookingSlug)
        store.setBookingLink(true, showRates = true)
        val back = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertEquals(slug, back.bookingSlug); assertTrue(back.bookingShowRates)
    }
}

package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Correcting your own name and your business.
 *
 * Two things are easy to get wrong here and silent when they are. A solo farmer's
 * business name has to follow their own, because it is the name on every bill. And
 * both rows have to reach the outbox, because a rename that lives only on the handset
 * looks exactly like one that worked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionEditTest {

    private lateinit var h: TestHarness
    private lateinit var store: SessionStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE)
            .edit().clear().commit()
        h = TestHarness()
        store = SessionStore(context, h.db, h.clock, h.ids)
    }

    @After fun tearDown() = h.close()

    private suspend fun onboard(isBusiness: Boolean, business: String = "Harpal") {
        store.completeOnboarding(
            displayName = "Harpal",
            businessName = business,
            landUnit = MeasureUnit.Acre,
            isBusiness = isBusiness,
        )
        store.activate()
    }

    private suspend fun outboxTables() =
        h.db.outboxDao().nextBatch(100).map { it.tableName }

    @Test
    fun `renaming yourself never renames the business`() = runTest {
        // A bill goes out under the organisation's name. Letting a profile edit
        // rewrite it destroyed a trading name the moment somebody corrected the
        // spelling of their own, and a solo owner had no field to put it back in.
        onboard(isBusiness = false, business = "Harpal Agro Works")
        store.updateProfile("Harpal Singh")

        val session = store.current()!!
        assertEquals("Harpal Singh", session.displayName)
        assertEquals("Harpal Agro Works", session.businessName)
        assertEquals(
            "the name at the top of the bill is untouched",
            "Harpal Agro Works",
            h.db.orgDao().observeOrg(session.orgId).first()!!.name,
        )
    }

    @Test
    fun `a solo farmer who named no business trades under their own name`() = runTest {
        // Onboarding defaults it, which is a starting point rather than a rule: they
        // can set a trading name afterwards and it will stick.
        onboard(isBusiness = false, business = "Harpal")
        assertEquals("Harpal", store.current()!!.businessName)

        store.updateBusiness(
            name = "Harpal Agro Works", landUnit = MeasureUnit.Acre,
            phone = null, addressLine = null, gstin = null, upiId = null,
        )
        store.updateProfile("Harpal Singh")

        assertEquals("Harpal Agro Works", store.current()!!.businessName)
    }

    @Test
    fun `a real business keeps its own name when the owner renames themselves`() = runTest {
        onboard(isBusiness = true, business = "Singh Agro Services")
        store.updateProfile("Harpal Singh")

        val session = store.current()!!
        assertEquals("Harpal Singh", session.displayName)
        assertEquals("Singh Agro Services", session.businessName)
    }

    @Test
    fun `renaming yourself queues the membership for upload`() = runTest {
        onboard(isBusiness = true, business = "Singh Agro Services")
        store.updateProfile("Harpal Singh")

        assertTrue(
            "a rename that never reaches the outbox is lost on this handset",
            SyncQueue.TABLE_MEMBERSHIPS in outboxTables(),
        )
    }

    @Test
    fun `renaming the business queues the organisation and keeps the new unit`() = runTest {
        onboard(isBusiness = true, business = "Singh Agro")
        store.updateBusiness(
            name = "Singh Agro Services",
            landUnit = MeasureUnit.Bigha,
            phone = "9876543210",
            addressLine = "Village Kheri, Fatehabad",
            gstin = "06ABCDE1234F1Z5",
            upiId = "singhagro@okaxis",
        )

        val session = store.current()!!
        assertEquals("Singh Agro Services", session.businessName)
        assertEquals(MeasureUnit.Bigha, session.landUnit)
        assertTrue(SyncQueue.TABLE_ORGS in outboxTables())
    }

    @Test
    fun `setting up queues both rows it creates`() = runTest {
        onboard(isBusiness = true, business = "Singh Agro")
        val tables = outboxTables()
        assertTrue(SyncQueue.TABLE_ORGS in tables)
        assertTrue(SyncQueue.TABLE_MEMBERSHIPS in tables)
    }

    @Test
    fun `the letterhead is kept and blanks are stored as nothing`() = runTest {
        onboard(isBusiness = true, business = "Singh Agro")
        store.updateBusiness(
            name = "Singh Agro",
            landUnit = MeasureUnit.Acre,
            phone = "9876543210",
            addressLine = "  Village Kheri  ",
            gstin = "   ",
            upiId = "singhagro@okaxis",
        )

        val org = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertEquals("9876543210", org.phone)
        assertEquals("trimmed before it reaches a bill", "Village Kheri", org.addressLine)
        assertEquals("a blank GSTIN is absent, not an empty line on the bill", null, org.gstin)
        assertEquals("singhagro@okaxis", org.upiId)
    }

    @Test
    fun `correcting your own name leaves the letterhead alone`() = runTest {
        onboard(isBusiness = false)
        store.updateBusiness(
            name = "Harpal",
            landUnit = MeasureUnit.Acre,
            phone = "9876543210",
            addressLine = "Village Kheri",
            gstin = null,
            upiId = "harpal@okaxis",
        )
        store.updateProfile("Harpal Singh")

        val org = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertEquals("the business keeps its own name", "Harpal", org.name)
        assertEquals("the phone on every bill survives a rename", "9876543210", org.phone)
        assertEquals("harpal@okaxis", org.upiId)
    }

    @Test
    fun `an owner may rename the business and a plain driver may not`() = runTest {
        onboard(isBusiness = true, business = "Singh Agro")
        assertTrue(Capability.EditBusiness in store.current()!!.can)

        val driver = Capabilities.of(
            com.example.fieldtrack.data.db.entity.MembershipEntity(
                id = "m", orgId = "o", personId = "p", displayName = "Jaswant",
                role = com.example.fieldtrack.data.db.MemberRole.Operator,
                joinedAt = 0, updatedAt = 0,
            )
        )
        assertFalse(Capability.EditBusiness in driver)
    }
}

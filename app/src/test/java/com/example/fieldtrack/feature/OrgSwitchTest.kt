package com.example.fieldtrack.feature

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.SessionStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Moving one person between the businesses they drive for.
 *
 * The switch is a privacy boundary, not a convenience: everything the app reads
 * afterwards is scoped to whichever business the session points at, and this
 * database holds rows belonging to all of them at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrgSwitchTest {

    private lateinit var context: Context
    private lateinit var db: FieldTrackDatabase
    private lateinit var store: SessionStore
    private lateinit var homeOrgId: String

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE)
            .edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, FieldTrackDatabase::class.java)
            .allowMainThreadQueries().build()
        store = SessionStore(context, db)
        homeOrgId = store.completeOnboarding(
            displayName = "Ramesh",
            businessName = "Home business",
            landUnit = MeasureUnit.Acre,
            isBusiness = false,
        ).orgId
        // Onboarding stops short of Ready on purpose, so that its last screen is
        // still reachable; the app calls this when the person taps through.
        store.activate()
    }

    @After
    fun tearDown() = db.close()

    /** Puts this person in another business without going through an invite. */
    private suspend fun joinOrg(orgId: String, name: String, active: Boolean) {
        val personId = requireNotNull(store.personIdOrNull())
        db.orgDao().upsertOrg(
            OrgEntity(
                id = orgId,
                name = name,
                defaultLandUnit = MeasureUnit.Acre,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
        db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(orgId, personId),
                orgId = orgId,
                personId = personId,
                displayName = "Ramesh",
                role = MemberRole.Operator,
                joinedAt = 1L,
                updatedAt = 1L,
                active = active,
            )
        )
    }

    @Test
    fun `switches into a business they belong to`() = runTest {
        joinOrg("org-b", "Singh Harvesting", active = true)

        store.switchTo("org-b")

        assertEquals("org-b", store.current()?.orgId)
        // The whole session follows, not just the id: the name on screen and the
        // role are read from the membership in the business switched into.
        assertEquals("Singh Harvesting", store.current()?.businessName)
        assertEquals(MemberRole.Operator, store.current()?.role)
    }

    @Test
    fun `refuses a business they were removed from`() = runTest {
        joinOrg("org-c", "Old employer", active = false)
        val before = requireNotNull(store.current()?.orgId)

        store.switchTo("org-c")

        assertEquals(before, store.current()?.orgId)
    }

    @Test
    fun `refuses a business they never belonged to`() = runTest {
        val before = requireNotNull(store.current()?.orgId)

        store.switchTo("org-stranger")

        assertEquals(before, store.current()?.orgId)
    }

    @Test
    fun `a removal while switched in does not survive the next switch back`() = runTest {
        joinOrg("org-b", "Singh Harvesting", active = true)
        store.switchTo("org-b")
        // They go back home, and are removed from Singh Harvesting meanwhile.
        store.switchTo(homeOrgId)
        joinOrg("org-b", "Singh Harvesting", active = false)
        store.switchTo("org-b")

        assertEquals(homeOrgId, store.current()?.orgId)
    }
}

package com.example.fieldtrack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.repository.LedgerRepository
import com.example.fieldtrack.data.repository.WorkRepository

/**
 * An in-memory FieldTrack database with a clock and id generator we control.
 *
 * Real Room, real SQL, real transactions — only the storage is in memory and only
 * time is fake. Tests that stub the DAO would pass while the actual query is wrong,
 * which is precisely the failure this project already had.
 */
class TestHarness {

    /** Advances only when a test says so, so elapsed-time assertions are exact. */
    var nowMillis: Long = 1_700_000_000_000L

    private var idCounter = 0

    val clock = Clock { nowMillis }
    val ids = IdGenerator { "id-${++idCounter}" }

    val db: FieldTrackDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        FieldTrackDatabase::class.java,
    ).allowMainThreadQueries().build()

    val syncQueue = SyncQueue(db.outboxDao(), clock)

    val work = WorkRepository(
        db = db,
        syncQueue = syncQueue,
        clock = clock,
        ids = ids,
        deviceId = DEVICE,
    )

    val ledger = LedgerRepository(
        db = db,
        syncQueue = syncQueue,
        clock = clock,
        ids = ids,
        deviceId = DEVICE,
    )

    fun advanceMinutes(minutes: Long) {
        nowMillis += minutes * 60_000
    }

    /** Two organisations and a driver who works for both — the sharing case. */
    suspend fun seed() {
        db.orgDao().upsertOrg(
            OrgEntity(
                id = ORG_A,
                name = "Singh Agro Services",
                defaultLandUnit = MeasureUnit.Acre,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
        db.orgDao().upsertOrg(
            OrgEntity(
                id = ORG_B,
                name = "Dhillon Harvesting",
                defaultLandUnit = MeasureUnit.Bigha,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
        db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(ORG_A, DRIVER), orgId = ORG_A, personId = DRIVER, displayName = "Jaswant Kumar",
                role = MemberRole.Operator, joinedAt = nowMillis, updatedAt = nowMillis,
            )
        )
        db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(ORG_B, DRIVER), orgId = ORG_B, personId = DRIVER, displayName = "Jaswant Kumar",
                role = MemberRole.Operator, joinedAt = nowMillis, updatedAt = nowMillis,
            )
        )
        db.customerDao().upsert(
            CustomerEntity(
                id = CUSTOMER_A, orgId = ORG_A, name = "Ramesh Singh", village = "Kheri",
                createdAt = nowMillis, updatedAt = nowMillis,
            )
        )
        db.customerDao().upsert(
            CustomerEntity(
                id = CUSTOMER_B, orgId = ORG_B, name = "Harjit Kaur", village = "Rampur",
                createdAt = nowMillis, updatedAt = nowMillis,
            )
        )
    }

    fun close() = db.close()

    companion object {
        const val ORG_A = "org-a"
        const val ORG_B = "org-b"
        const val DRIVER = "person-driver"
        const val OWNER = "person-owner"
        const val CUSTOMER_A = "cust-a"
        const val CUSTOMER_B = "cust-b"
        const val DEVICE = "device-test"
    }
}

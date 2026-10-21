package com.example.fieldtrack.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema changes, written out rather than fallen back from.
 *
 * This database holds work that was done and money that was collected. A contractor
 * who loses a season to a version bump has no way to get it back, so every change
 * lands as a migration even while the app is unreleased.
 */

/**
 * Letterhead details on the business, and a bill number on the job.
 *
 * Every column is nullable with no default: existing rows keep working, jobs billed
 * before this version simply have no bill number, and nothing has to be back-filled.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organisations ADD COLUMN phone TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN addressLine TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN gstin TEXT")
        db.execSQL("ALTER TABLE organisations ADD COLUMN upiId TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN billFy TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN billSeq INTEGER")
    }
}


/**
 * Trips priced one at a time.
 *
 * Back-fills the new column for jobs billed before it existed, where every trip was
 * necessarily at the same rate — so their totals come out exactly as they did when
 * they were billed, and nothing already sitting on a khata moves by a rupee.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN tripTotalRupees INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE jobs SET tripTotalRupees = CAST(ROUND(quantity * rateRupees) AS INTEGER) " +
                "WHERE billingMode = 'PerTrip'"
        )
    }
}


/**
 * More than one rate for the same kind of work.
 *
 * Nullable with no default, so every rate already on the card keeps working and keeps
 * being found: a row with no label is the plain rate for that work, exactly as it was
 * before this column existed.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rates ADD COLUMN label TEXT")
    }
}


/**
 * Memberships keyed so a security rule can find them.
 *
 * A rule may fetch a document whose path it can build and may not search a
 * collection, so a membership identified by a random id with the person named
 * inside it cannot be used to answer "is this caller a member here". Rebuilding the
 * id from the two ids it already contains makes it findable, and costs nothing: the
 * row is the same row, and nothing outside this table refers to it by id.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE memberships SET id = orgId || '_' || personId")
    }
}


/**
 * A way past a change the server will never take.
 *
 * Nullable with no default: every change already queued is, by definition, not one
 * the user has set aside.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE outbox ADD COLUMN blockedAt INTEGER")
    }
}


/**
 * The invite a driver came in on, kept so the server can verify it.
 *
 * Nullable with no default: an owner creates their business rather than joining it,
 * and every membership written before this column existed belongs to somebody who
 * is already inside.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN joinedWithCode TEXT")
    }
}


/**
 * What a driver's phone is still carrying.
 *
 * Two columns rather than one, because a count on its own invites being read as
 * current when it may be days old. The owner is shown both.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN unsentCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN unsentAt INTEGER")
    }
}


/**
 * Who was let in, who was let go, and who was given sight of the money.
 *
 * Starts empty on purpose. Nothing in the app wrote these down before now, so
 * there is no history to backfill — the log begins the day it ships.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS team_events (
                id TEXT NOT NULL PRIMARY KEY,
                orgId TEXT NOT NULL,
                atMillis INTEGER NOT NULL,
                kind TEXT NOT NULL,
                byPersonId TEXT,
                byName TEXT NOT NULL DEFAULT '',
                aboutPersonId TEXT,
                aboutName TEXT,
                detail TEXT,
                syncState TEXT NOT NULL DEFAULT 'Pending'
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_team_events_orgId ON team_events (orgId)")
    }
}


/**
 * Money going out, and who is allowed to touch it.
 *
 * The two capability columns sit on the membership so the SERVER can read them.
 * A rule can only enforce what it can see, and what each person is paid is the
 * one figure in this app that cannot be un-seen once a driver has seen it.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN canPayOut INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN canSeePeopleCost INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS payouts (
                id TEXT NOT NULL PRIMARY KEY,
                orgId TEXT NOT NULL,
                amountRupees INTEGER NOT NULL,
                atMillis INTEGER NOT NULL,
                covers TEXT NOT NULL,
                toPersonId TEXT,
                toName TEXT,
                headcount INTEGER NOT NULL DEFAULT 1,
                jobId TEXT,
                method TEXT,
                note TEXT,
                byPersonId TEXT,
                createdAt INTEGER NOT NULL,
                syncState TEXT NOT NULL DEFAULT 'Pending',
                deviceId TEXT
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payouts_orgId ON payouts (orgId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payouts_jobId ON payouts (jobId)")
    }
}


/**
 * A rank between owner and driver.
 *
 * Not a role: the app has always worked in capabilities, and a role enum would
 * make every rule a three-way question for no gain. A manager is somebody with
 * these switched on, and the rules read the switches.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memberships ADD COLUMN canEditTeam INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memberships ADD COLUMN canEditMachines INTEGER NOT NULL DEFAULT 0")
    }
}


/**
 * The job's own cost sheet, and the link that keeps a rupee counted once.
 *
 * Job-attached money used to live only in `payouts`, and a machine's people
 * cost was summed by joining payouts to jobs. Totalling `job_costs` as well
 * would count every settled cost twice, so the two have to be tied together in
 * the same breath they are separated: every existing job-attached payment
 * becomes a cost here, and points back at it.
 *
 * The derived id (`jc_` + the payment's own id) is deliberate. It makes the
 * copy idempotent and lets the UPDATE below find its partner without a join,
 * on a statement that has to be right the first time because there is no
 * second run of a migration.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `job_costs` (
                `id` TEXT NOT NULL,
                `orgId` TEXT NOT NULL,
                `jobId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `headcount` INTEGER,
                `amountRupees` INTEGER,
                `note` TEXT,
                `byPersonId` TEXT,
                `atMillis` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncState` TEXT NOT NULL,
                `deviceId` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_costs_orgId` ON `job_costs` (`orgId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_costs_jobId` ON `job_costs` (`jobId`)")

        db.execSQL("ALTER TABLE payouts ADD COLUMN jobCostId TEXT")

        // Every payment already laid against a job becomes that job's cost.
        // Labour for all of them: paying people is the only thing the screen
        // that wrote these could ever have been used for. A name, where there
        // is one, survives in the note rather than being thrown away.
        db.execSQL(
            """
            INSERT INTO job_costs
                (id, orgId, jobId, kind, headcount, amountRupees, note,
                 byPersonId, atMillis, createdAt, syncState, deviceId)
            SELECT 'jc_' || id, orgId, jobId, 'Labour', headcount, amountRupees,
                   COALESCE(note, toName), byPersonId, atMillis, createdAt,
                   'Pending', deviceId
              FROM payouts
             WHERE jobId IS NOT NULL
            """.trimIndent()
        )
        db.execSQL("UPDATE payouts SET jobCostId = 'jc_' || id WHERE jobId IS NOT NULL")
    }
}


/** Every migration, in order. Add to this list and the builder picks it up. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
)
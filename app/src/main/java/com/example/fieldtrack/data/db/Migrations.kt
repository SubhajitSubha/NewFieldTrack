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


/** Every migration, in order. Add to this list and the builder picks it up. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
)
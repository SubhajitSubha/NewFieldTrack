package com.example.fieldtrack.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps every row's `syncState` true to the outbox.
 *
 * The column existed from the start and was never maintained: rows were written
 * Pending and stayed Pending after the server had them, so it could not be shown
 * to anybody. The outbox is the real answer — a row is on the server exactly when
 * nothing is queued for it — so rather than ask every repository to remember to
 * set the column on every write (sixteen tables, and one forgotten `copy()` would
 * make the app claim something was sent that was not), the database keeps it
 * in step itself:
 *
 *  - a change queued for a row marks it Pending, in the same transaction that
 *    queued it;
 *  - the last queued change for a row leaving the outbox — which only happens
 *    when the server accepted it — marks it Synced.
 *
 * A change the user set aside stays in the outbox, so its row stays Pending:
 * it was never sent, and the app must not say otherwise.
 *
 * Matched on the entity id alone, across tables. Ids are random, so they do not
 * collide, and a job cost's figure travels as a separate queued change under the
 * same id — matching on the id is what keeps that cost Pending until its amount,
 * too, has gone.
 */
object SentState {

    /** Every table with a `syncState` column, which is every table the outbox feeds. */
    val TABLES = listOf(
        "organisations", "memberships", "invites", "customers", "fields", "machines", "machine_finance",
        "fuel_entries", "service_records", "payouts", "job_costs", "team_events",
        "breakdowns", "rates", "jobs", "job_events", "ledger_entries", "bookings",
    )

    /**
     * Installed on every open rather than in a migration, so it reaches a fresh
     * install, an upgraded one and an in-memory test database by the same path —
     * and so a table added later is covered without anybody remembering to write
     * a migration for a trigger.
     */
    val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            install(db)
            reconcile(db)
        }
    }

    fun install(db: SupportSQLiteDatabase) {
        // Dropped first, every time. A trigger names its tables when it is made, so
        // "create if not exists" left a phone that already had the triggers with a
        // list that stopped at whatever tables existed then — the next table added
        // (machine_finance was the first) would have stayed "not sent" for ever,
        // on exactly the installs with the most history. Two small triggers,
        // rebuilt once per open.
        db.execSQL("DROP TRIGGER IF EXISTS sent_state_on_queue")
        db.execSQL("DROP TRIGGER IF EXISTS sent_state_on_accept")
        db.execSQL(
            """
            CREATE TRIGGER sent_state_on_queue AFTER INSERT ON outbox
            BEGIN
            ${TABLES.joinToString("\n") { "UPDATE `$it` SET syncState = 'Pending' WHERE id = NEW.entityId;" }}
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER sent_state_on_accept AFTER DELETE ON outbox
            WHEN NOT EXISTS (SELECT 1 FROM outbox WHERE entityId = OLD.entityId)
            BEGIN
            ${TABLES.joinToString("\n") { "UPDATE `$it` SET syncState = 'Synced' WHERE id = OLD.entityId;" }}
            END
            """.trimIndent()
        )
    }

    /**
     * Puts every row right from the outbox as it stands.
     *
     * Needed once for every install made before the triggers existed — all of
     * whose rows say Pending — and harmless after that: it writes only rows that
     * are wrong, which after the first run is none.
     */
    fun reconcile(db: SupportSQLiteDatabase) {
        TABLES.forEach { table ->
            db.execSQL(
                """
                UPDATE `$table` SET syncState = 'Synced'
                WHERE syncState != 'Synced'
                  AND NOT EXISTS (SELECT 1 FROM outbox WHERE outbox.entityId = `$table`.id)
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE `$table` SET syncState = 'Pending'
                WHERE syncState != 'Pending'
                  AND EXISTS (SELECT 1 FROM outbox WHERE outbox.entityId = `$table`.id)
                """.trimIndent()
            )
        }
    }
}

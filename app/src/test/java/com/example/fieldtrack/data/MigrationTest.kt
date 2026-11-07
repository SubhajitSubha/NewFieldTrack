package com.example.fieldtrack.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.ALL_MIGRATIONS
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.SentState
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The database moving from what a phone holds today to what this build expects.
 *
 * Each old database is built from Room's own exported schema for that version —
 * the real tables, indices and identity hash, not a copy written out by hand —
 * and then opened through Room with every migration in place. Room checks the
 * migrated tables against what this build expects and refuses to open if they
 * differ, so a column that is missing, mistyped or left without its default fails
 * here and not on a farmer's phone. A farmer's season lives in these tables; a
 * migration that is wrong does not fail loudly, it quietly puts a number somewhere
 * else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"
    private val opened = mutableListOf<FieldTrackDatabase>()

    @After fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(name)
    }

    /** A database exactly as version [version] of the app would have left it. */
    private fun create(version: Int, seed: (SQLiteDatabase) -> Unit = {}) {
        context.deleteDatabase(name)
        val schema = File("schemas/com.example.fieldtrack.data.db.FieldTrackDatabase/$version.json")
        assertTrue("no exported schema for version $version at ${schema.absolutePath}", schema.exists())
        val root = JSONObject(schema.readText()).getJSONObject("database")

        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        fun run(sql: String) = db.execSQL(sql.replace("\${TABLE_NAME}", "`room_table_modification_log`"))
        val entities = root.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            for (j in 0 until (indices?.length() ?: 0)) {
                db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = root.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) run(setup.getString(i))
        seed(db)
        db.version = version
        db.close()
    }

    private fun open(): FieldTrackDatabase =
        Room.databaseBuilder(context, FieldTrackDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .addCallback(SentState.callback)
            .build()
            .also { opened += it }

    private fun SQLiteDatabase.seedV13() {
        execSQL(
            "INSERT INTO machines (id, orgId, name, kind, status, hoursUsed, serviceIntervalHours, " +
                "lastServiceAtHours, createdAt, updatedAt, syncState) VALUES " +
                "('m1', 'o1', 'Swraj 855', 'Tractor', 'Idle', 1240.0, 250.0, 1240.0, 1700000000000, 1700000000000, 'Synced')"
        )
        execSQL(
            "INSERT INTO machines (id, orgId, name, kind, status, hoursUsed, createdAt, updatedAt, syncState) " +
                "VALUES ('m2', 'o1', 'No schedule', 'Trolley', 'Idle', 0.0, 1750000000000, 1750000000000, 'Synced')"
        )
        execSQL(
            "INSERT INTO service_records (id, orgId, machineId, description, costRupees, atMachineHours, atMillis, syncState) " +
                "VALUES ('s1', 'o1', 'm1', 'Oil change', 2100, 1240.0, 1710000000000, 'Synced')"
        )
    }

    @Test
    fun `machines keep their readings and are dated from when they were added`() {
        create(13) { it.seedV13() }
        val db = open().openHelper.writableDatabase

        db.query("SELECT id, hoursUsed, lastServiceAtHours, hoursAsOf, createdAt FROM machines ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("m1", c.getString(0))
            assertEquals("the reading is untouched", 1240.0, c.getDouble(1), 0.0)
            assertEquals(1240.0, c.getDouble(2), 0.0)
            assertEquals("counted from when the machine was added", c.getLong(4), c.getLong(3))
            assertTrue(c.moveToNext())
            assertEquals("m2", c.getString(0))
            assertEquals(c.getLong(4), c.getLong(3))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun `existing service records survive and gain an empty parts note`() {
        create(13) { it.seedV13() }
        val db = open().openHelper.writableDatabase
        db.query("SELECT description, costMinor, atMachineHours, partsNote FROM service_records").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Oil change", c.getString(0))
            assertEquals("rupees became paise", 210_000L, c.getLong(1))
            assertEquals(1_240.0, c.getDouble(2), 0.0)
            assertTrue(c.isNull(3))
        }
    }

    @Test
    fun `the private cost record exists, empty, indexed and writable`() {
        create(13)
        val db = open().openHelper.writableDatabase
        db.query("SELECT COUNT(*) FROM machine_finance").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_machine_finance_orgId'").use { c ->
            assertTrue(c.moveToFirst())
        }
        db.execSQL("INSERT INTO machine_finance (id, orgId, monthlyMinor, hoursPerYear, updatedAt, syncState) VALUES ('m1','o1',12000,700,1,'Pending')")
        db.query("SELECT monthlyMinor FROM machine_finance").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(12_000L, c.getLong(0))
        }
    }

    @Test
    fun `the sent-state triggers cover the new table once the database is open`() {
        create(13)
        val db = open().openHelper.writableDatabase
        db.execSQL("INSERT INTO machine_finance (id, orgId, updatedAt, syncState) VALUES ('m1','o1',1,'Pending')")
        db.execSQL(
            "INSERT INTO outbox (orgId, tableName, entityId, operation, payloadJson, createdAt, attempts) " +
                "VALUES ('o1','machine_finance','m1','Upsert','{}',1,0)"
        )
        db.execSQL("DELETE FROM outbox WHERE entityId = 'm1'")
        db.query("SELECT syncState FROM machine_finance WHERE id = 'm1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("accepted by the server, so sent", "Synced", c.getString(0))
        }
    }

    @Test
    fun `a business keeps everything and has no seasons of its own until it sets them`() {
        create(14) {
            it.execSQL(
                "INSERT INTO organisations (id, name, defaultLandUnit, createdAt, updatedAt, syncState) " +
                    "VALUES ('o1', 'Singh Agro', 'Bigha', 1700000000000, 1700000000000, 'Synced')"
            )
        }
        val db = open().openHelper.writableDatabase
        db.query("SELECT name, defaultLandUnit, seasons, syncState FROM organisations").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Singh Agro", c.getString(0))
            assertEquals("Bigha", c.getString(1))
            assertTrue("nothing back-filled: June and November stay the default, not a choice", c.isNull(2))
            assertEquals("Synced", c.getString(3))
        }
        // And what an app of this version stores reads back as what was meant.
        val calendar = com.example.fieldtrack.data.season.SeasonCalendar.of(
            listOf(com.example.fieldtrack.data.season.SeasonStart(4, 1, "Virippu"))
        )!!
        db.execSQL("UPDATE organisations SET seasons = '${calendar.encode().replace("'", "''")}' WHERE id = 'o1'")
        db.query("SELECT seasons FROM organisations").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(calendar, com.example.fieldtrack.data.season.SeasonCalendar.parse(c.getString(0)))
        }
    }

    @Test
    fun `whoever ran the team can plan the work and nobody else gains it`() {
        create(15) {
            it.execSQL(
                "INSERT INTO memberships (id, orgId, personId, displayName, role, canSeeMoney, canAddCustomers, " +
                    "canCloseBills, canEditTeam, canEditMachines, canPayOut, canSeePeopleCost, active, joinedAt, " +
                    "updatedAt, syncState, unsentCount) VALUES " +
                    "('o1_boss', 'o1', 'boss', 'Manager', 'Operator', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 'Synced', 0), " +
                    "('o1_drv', 'o1', 'drv', 'Driver', 'Operator', 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 'Synced', 0)"
            )
            it.execSQL(
                "INSERT INTO organisations (id, name, defaultLandUnit, createdAt, updatedAt, syncState) " +
                    "VALUES ('o1', 'Singh Agro', 'Acre', 1, 1, 'Synced')"
            )
            it.execSQL(
                "INSERT INTO jobs (id, orgId, customerId, workType, billingMode, unit, rateRupees, quantity, " +
                    "tripTotalRupees, elapsedSeconds, status, workedAt, recordedAt, entryMethod, createdAt, updatedAt, " +
                    "syncState, revision) VALUES ('j1', 'o1', 'c1', 'Rotavator', 'PerAcre', 'Acre', 800, 2.0, 0, 0, " +
                    "'Completed', 1, 1, 'Timed', 1, 1, 'Synced', 1)"
            )
        }
        val db = open().openHelper.writableDatabase

        db.query("SELECT personId, canPlanWork FROM memberships ORDER BY personId").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("boss", c.getString(0)); assertEquals(1, c.getInt(1))
            assertTrue(c.moveToNext())
            assertEquals("drv", c.getString(0)); assertEquals(0, c.getInt(1))
        }
        db.query("SELECT planDayStartHour, driversSeeAll FROM organisations").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("nothing back-filled: the default hour is not a choice", c.isNull(0))
            assertEquals(0, c.getInt(1))
        }
        db.query("SELECT status, bookingId FROM jobs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Completed", c.getString(0))
            assertTrue("old work was not planned", c.isNull(1))
        }
        // The new table exists, empty, and takes a booking.
        db.execSQL(
            "INSERT INTO bookings (id, orgId, customerId, workType, day, part, status, movedCount, createdAt, " +
                "updatedAt, syncState) VALUES ('b1', 'o1', 'c1', 'Rotavator', 20000, 'Morning', 'Planned', 0, 1, 1, 'Pending')"
        )
        db.query("SELECT COUNT(*) FROM bookings").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun `a phone that never updated gets to the current schema from the very first version`() {
        create(1)
        // Opening is the assertion: Room validates every table against this build.
        val db = open().openHelper.writableDatabase
        db.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(18, c.getInt(0))
        }
    }

    // ----------------------------------------------------------- 16 to 17: minor units

    private fun SQLiteDatabase.seedV16Money() {
        execSQL(
            "INSERT INTO organisations (id, name, defaultLandUnit, driversSeeAll, createdAt, updatedAt, syncState) " +
                "VALUES ('o1', 'Singh Agro', 'Acre', 0, 1, 1, 'Synced')"
        )
        execSQL(
            "INSERT INTO jobs (id, orgId, customerId, workType, billingMode, unit, rateRupees, quantity, " +
                "tripTotalRupees, elapsedSeconds, status, workedAt, recordedAt, entryMethod, createdAt, updatedAt, " +
                "syncState, revision) VALUES " +
                "('j1', 'o1', 'c1', 'Rotavator', 'PerAcre', 'Acre', 800, 2.5, 0, 0, 'Completed', 1, 1, 'Timed', 1, 1, 'Synced', 1), " +
                "('j2', 'o1', 'c1', 'Hauling', 'PerTrip', 'Trip', 900, 5.0, 4000, 0, 'Completed', 1, 1, 'Timed', 1, 1, 'Synced', 1)"
        )
        execSQL(
            "INSERT INTO ledger_entries (id, orgId, customerId, kind, amountRupees, atMillis, createdAt, syncState) " +
                "VALUES ('l1', 'o1', 'c1', 'Charge', 3000, 1, 1, 'Synced')"
        )
        execSQL(
            "INSERT INTO rates (id, orgId, workType, billingMode, unit, amountRupees, updatedAt, syncState) " +
                "VALUES ('r1', 'o1', 'Rotavator', 'PerAcre', 'Acre', 800, 1, 'Synced')"
        )
        execSQL(
            "INSERT INTO fuel_entries (id, orgId, machineId, litres, costRupees, atMillis, syncState) " +
                "VALUES ('f1', 'o1', 'm1', 20.0, 1900, 1, 'Synced')"
        )
        execSQL(
            "INSERT INTO payouts (id, orgId, amountRupees, atMillis, covers, headcount, createdAt, syncState) " +
                "VALUES ('p1', 'o1', 500, 1, 'Day', 1, 1, 'Synced')"
        )
        // Litres, seconds and acres share the column that holds a trip's price.
        execSQL(
            "INSERT INTO job_events (id, jobId, orgId, type, atMillis, numericValue, syncState) VALUES " +
                "('e1', 'j2', 'o1', 'TripAdded', 1, 900.0, 'Synced'), " +
                "('e2', 'j2', 'o1', 'QuantitySet', 2, 5.0, 'Synced'), " +
                "('e3', 'j2', 'o1', 'RateChanged', 3, 950.0, 'Synced'), " +
                "('e4', 'j2', 'o1', 'FuelAdded', 4, 20.0, 'Synced'), " +
                "('e5', 'j2', 'o1', 'ClockCorrected', 5, 3600.0, 'Synced')"
        )
        // A change queued on this phone and never sent, still in the old shape.
        execSQL(
            "INSERT INTO outbox (orgId, tableName, entityId, operation, payloadJson, createdAt, attempts) VALUES " +
                "('o1', 'ledger_entries', 'l2', 'Upsert', " +
                "'{\"id\":\"l2\",\"orgId\":\"o1\",\"amountRupees\":1500,\"kind\":\"Payment\"}', 1, 0), " +
                "('o1', 'job_events', 'e9', 'Upsert', " +
                "'{\"id\":\"e9\",\"type\":\"TripAdded\",\"numericValue\":900.0}', 2, 0), " +
                "('o1', 'job_events', 'e8', 'Upsert', " +
                "'{\"id\":\"e8\",\"type\":\"FuelAdded\",\"numericValue\":20.0}', 3, 0)"
        )
    }

    @Test
    fun `every amount is scaled to minor units and every business keeps its rupees`() {
        create(16) { it.seedV16Money() }
        val db = open().openHelper.writableDatabase

        db.query("SELECT currencyCode, moneyStepMinor FROM organisations").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("every business so far kept rupees", "INR", c.getString(0))
            assertEquals("and never dealt in paise", 100L, c.getLong(1))
        }
        db.query("SELECT id, rateMinor, tripTotalMinor, stepMinor, quantity FROM jobs ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(80_000L, c.getLong(1)); assertEquals(0L, c.getLong(2))
            assertEquals("each job is stamped with a rupee business's rounding", 100L, c.getLong(3))
            assertEquals("measurements are not money", 2.5, c.getDouble(4), 0.0)
            assertTrue(c.moveToNext())
            assertEquals(90_000L, c.getLong(1)); assertEquals(400_000L, c.getLong(2))
        }
        db.query("SELECT amountMinor FROM ledger_entries").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(300_000L, c.getLong(0))
        }
        db.query("SELECT amountMinor FROM rates").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(80_000L, c.getLong(0))
        }
        db.query("SELECT costMinor FROM fuel_entries").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(190_000L, c.getLong(0))
        }
        db.query("SELECT amountMinor FROM payouts").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(50_000L, c.getLong(0))
        }
    }

    @Test
    fun `a trip price and a rate change are scaled and litres, quantities and seconds are not`() {
        create(16) { it.seedV16Money() }
        val db = open().openHelper.writableDatabase
        val values = buildMap {
            db.query("SELECT id, numericValue FROM job_events").use { c ->
                while (c.moveToNext()) put(c.getString(0), c.getDouble(1))
            }
        }
        assertEquals("trip price", 90_000.0, values.getValue("e1"), 0.0)
        assertEquals("trips counted", 5.0, values.getValue("e2"), 0.0)
        assertEquals("rate change", 95_000.0, values.getValue("e3"), 0.0)
        assertEquals("litres", 20.0, values.getValue("e4"), 0.0)
        assertEquals("seconds taken off a clock", 3_600.0, values.getValue("e5"), 0.0)
    }

    @Test
    fun `changes queued before the update leave in the new shape`() {
        create(16) { it.seedV16Money() }
        val db = open().openHelper.writableDatabase
        val payloads = buildMap {
            db.query("SELECT entityId, payloadJson FROM outbox").use { c ->
                while (c.moveToNext()) put(c.getString(0), JSONObject(c.getString(1)))
            }
        }
        val payment = payloads.getValue("l2")
        assertEquals(150_000L, payment.getLong("amountMinor"))
        assertTrue("the old name must not reach the server", !payment.has("amountRupees"))
        assertEquals(90_000.0, payloads.getValue("e9").getDouble("numericValue"), 0.0)
        assertEquals("a litres event is left alone", 20.0, payloads.getValue("e8").getDouble("numericValue"), 0.0)
    }

    @Test
    fun `the rebuilt tables keep their indexes and take new rows`() {
        create(16) { it.seedV16Money() }
        val db = open().openHelper.writableDatabase
        for (index in listOf("index_jobs_orgId_status", "index_jobs_orgId_workedAt", "index_ledger_entries_orgId_atMillis", "index_rates_orgId_workType", "index_payouts_jobId")) {
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$index'").use { c ->
                assertTrue("$index survived", c.moveToFirst())
            }
        }
        db.execSQL(
            "INSERT INTO ledger_entries (id, orgId, customerId, kind, amountMinor, atMillis, createdAt, syncState) " +
                "VALUES ('l9', 'o1', 'c1', 'Payment', 12345, 1, 1, 'Pending')"
        )
        db.query("SELECT amountMinor FROM ledger_entries WHERE id = 'l9'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(12_345L, c.getLong(0))
        }
    }

    @Test
    fun `every business keeps its April year and follows the phone's zone`() {
        create(17) {
            it.execSQL(
                "INSERT INTO organisations (id, name, defaultLandUnit, currencyCode, moneyStepMinor, driversSeeAll, " +
                    "createdAt, updatedAt, syncState) VALUES ('o1', 'Singh Agro', 'Acre', 'INR', 100, 0, 1, 1, 'Synced')"
            )
        }
        val db = open().openHelper.writableDatabase
        db.query("SELECT fiscalStartMonth, timeZoneId FROM organisations").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("every business so far was Indian", 4, c.getInt(0))
            assertTrue("nothing back-filled: no zone is a choice, it follows the phone", c.isNull(1))
        }
    }
}

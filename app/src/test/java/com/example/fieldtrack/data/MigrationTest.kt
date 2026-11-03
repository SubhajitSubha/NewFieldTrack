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
        db.query("SELECT description, costRupees, atMachineHours, partsNote FROM service_records").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Oil change", c.getString(0))
            assertEquals(2_100L, c.getLong(1))
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
        db.execSQL("INSERT INTO machine_finance (id, orgId, monthlyRupees, hoursPerYear, updatedAt, syncState) VALUES ('m1','o1',12000,700,1,'Pending')")
        db.query("SELECT monthlyRupees FROM machine_finance").use { c ->
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
    fun `a phone that never updated gets to the current schema from the very first version`() {
        create(1)
        // Opening is the assertion: Room validates every table against this build.
        val db = open().openHelper.writableDatabase
        db.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(14, c.getInt(0))
        }
    }
}

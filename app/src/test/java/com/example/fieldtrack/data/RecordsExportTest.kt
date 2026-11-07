package com.example.fieldtrack.data

import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.export.ExportPeriod
import com.example.fieldtrack.data.export.ExportScope
import com.example.fieldtrack.data.export.RecordsExport
import com.example.fieldtrack.data.export.csvCell
import com.example.fieldtrack.data.export.csvLine
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipFile
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

/** Taking everything out: plain tables, the right days, and only what the person may see. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsExportTest {

    private lateinit var h: TestHarness
    private lateinit var dir: File
    private val org = TestHarness.ORG_A
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Before fun setUp() {
        h = TestHarness()
        dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "export-test").apply { deleteRecursively(); mkdirs() }
    }

    @After fun tearDown() { h.close(); dir.deleteRecursively() }

    private suspend fun seed() {
        h.db.orgDao().upsertOrg(OrgEntity(id = org, name = "Harpal Agro", createdAt = 1, updatedAt = 1))
        h.db.customerDao().upsert(CustomerEntity(id = "c1", orgId = org, name = "Balwinder, \"Bali\" Singh", phone = "+919000000001", createdAt = 1, updatedAt = 1))
        h.db.customerDao().upsert(CustomerEntity(id = "c2", orgId = org, name = "=HYPERLINK(\"evil\")", createdAt = 1, updatedAt = 1))
        fun entry(id: String, kind: LedgerKind, minor: Long, on: String) = LedgerEntryEntity(
            id = id, orgId = org, customerId = "c1", kind = kind, amountMinor = minor,
            method = if (kind == LedgerKind.Payment) PaymentMethod.Cash else null,
            atMillis = millis(on), createdAt = millis(on),
        )
        h.db.ledgerDao().insert(entry("l1", LedgerKind.Charge, 500_000, "2025-06-10"))
        h.db.ledgerDao().insert(entry("l2", LedgerKind.Payment, 200_000, "2025-06-20"))
        h.db.ledgerDao().insert(entry("l3", LedgerKind.Charge, 123_456, "2026-05-02"))
    }

    private fun read(zip: File, name: String): String = ZipFile(zip).use { z ->
        z.getInputStream(z.getEntry(name)).readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
    }

    @Test fun `a cell with a comma or a quote is quoted, and nothing a spreadsheet could run is left bare`() {
        assertEquals("plain", csvCell("plain"))
        assertEquals("\"a,b\"", csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvCell("say \"hi\""))
        assertEquals("'=1+1", csvCell("=1+1"))
        assertEquals("'@sum", csvCell("@sum"))
        assertEquals("'-cmd", csvCell("-cmd"))
        assertEquals("a negative amount stays a number", "-500.00", csvCell("-500.00"))
        assertEquals("", csvCell(null))
        assertEquals("a,,\"x,y\"", csvLine(listOf("a", null, "x,y")))
    }

    @Test fun `everything comes out as readable tables, amounts as decimals, dates as year-month-day`() = runTest {
        seed()
        val zip = RecordsExport(h.db).build(org, "Harpal Agro", ExportPeriod.Everything, ExportScope(money = true, peopleCost = true), dir, millis("2026-06-01"), ZoneOffset.UTC, 4)
        val names = ZipFile(zip).use { z -> z.entries().toList().map { it.name } }
        assertTrue(names.containsAll(listOf("README.txt", "customers.csv", "jobs.csv", "money.csv", "machines.csv", "papers.csv", "team.csv", "pay.csv")))
        val money = read(zip, "money.csv").trim().lines()
        assertEquals("Date,Customer,What,Amount,How paid,Note", money[0])
        assertEquals("2025-06-10,\"Balwinder, \"\"Bali\"\" Singh\",Charge,5000.00,,", money[1])
        assertEquals("2025-06-20,\"Balwinder, \"\"Bali\"\" Singh\",Payment,2000.00,Cash,", money[2])
        val customers = read(zip, "customers.csv")
        assertTrue("what is still owed is written beside the name", customers.contains("+919000000001,,,"))
        assertTrue(customers.contains("4234.56"))
        assertTrue("a name that is a formula is written as text", customers.contains("'=HYPERLINK"))
    }

    @Test fun `an accounting year takes the days in it and no others`() = runTest {
        seed()
        // Accounts start in April; "now" is June 2026, so this year began 1 April 2026.
        val now = millis("2026-06-01")
        val thisYear = RecordsExport(h.db).build(org, "Harpal Agro", ExportPeriod.ThisYear, ExportScope(true, true), dir, now, ZoneOffset.UTC, 4)
        val lastYear = RecordsExport(h.db).build(org, "Harpal Agro", ExportPeriod.LastYear, ExportScope(true, true), File(dir, "b"), now, ZoneOffset.UTC, 4)
        val a = read(thisYear, "money.csv").trim().lines()
        val b = read(lastYear, "money.csv").trim().lines()
        assertEquals(2, a.size); assertTrue(a[1].startsWith("2026-05-02"))
        assertEquals(3, b.size); assertTrue(b[1].startsWith("2025-06-10"))
        assertTrue(read(thisYear, "README.txt").contains("2026-04-01"))
    }

    @Test fun `somebody who may not see money gets no money, and nothing about pay`() = runTest {
        seed()
        val zip = RecordsExport(h.db).build(org, "Harpal Agro", ExportPeriod.Everything, ExportScope(money = false, peopleCost = false), dir, millis("2026-06-01"), ZoneOffset.UTC, 4)
        val names = ZipFile(zip).use { z -> z.entries().toList().map { it.name } }
        assertFalse("money.csv" in names)
        assertFalse("pay.csv" in names)
        assertFalse("fuel.csv" in names)
        assertEquals("Name,Phone,Village,Note", read(zip, "customers.csv").lines().first())
        assertEquals("Date,Customer,Field,Machine,Driver,Work,Charged by,Quantity,Unit,Meter at start,Meter at end,Note", read(zip, "jobs.csv").lines().first())
    }
}

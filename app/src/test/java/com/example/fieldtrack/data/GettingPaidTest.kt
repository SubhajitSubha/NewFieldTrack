package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.billing.CreditCheck
import com.example.fieldtrack.data.billing.CreditWarning
import com.example.fieldtrack.data.billing.Quote
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.repository.CustomerTerms
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.designsystem.format.DigitGrouping
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.document.DocumentHtml
import com.example.fieldtrack.document.Documents
import com.example.fieldtrack.document.JobSummaryText
import com.example.fieldtrack.document.ReminderText
import com.example.fieldtrack.feature.Sharing
import com.example.fieldtrack.feature.customer.buildKhata
import com.example.fieldtrack.feature.home.chaseAlerts
import com.example.fieldtrack.feature.home.debtsByAge
import com.example.fieldtrack.feature.money.PayOutState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Chasing money, receipts, summaries, credit warnings, advances and search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GettingPaidTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val inr = MoneyStyle("INR", "₹", 2, 100, DigitGrouping.Indian, '.', ',', false)
    private val usd = MoneyStyle("USD", "$", 2, 1, DigitGrouping.International, '.', ',', false)
    private val day = 86_400_000L

    @Before fun setUp() = runTest { h = TestHarness(); h.seed(); Money.grouping = DigitGrouping.Indian }
    @After fun tearDown() { h.close(); Money.grouping = DigitGrouping.forLocale(java.util.Locale.getDefault()) }

    private fun charge(id: String, amount: Long, at: Long, customer: String = "c1") = LedgerEntryEntity(
        id = id, orgId = org, customerId = customer, kind = LedgerKind.Charge, amountMinor = amount,
        atMillis = at, createdAt = at,
    )

    private fun payment(id: String, amount: Long, at: Long, customer: String = "c1") = LedgerEntryEntity(
        id = id, orgId = org, customerId = customer, kind = LedgerKind.Payment, amountMinor = amount,
        atMillis = at, createdAt = at, method = PaymentMethod.Cash,
    )

    // ------------------------------------------------------------------- reminders

    @Test
    fun `a reminder says who is asking, how much, how old and where to pay`() {
        val text = ReminderText.build(
            context, null, "Singh Agro", 450_000, inr, oldestDays = 40,
            payDetails = "Bank: 1234 5678\nIFSC SBIN000123", upiId = null,
        )
        assertTrue(text.contains("Singh Agro"))
        assertTrue(text.contains("₹4,500"))
        assertTrue(text.contains("40 days"))
        assertTrue(text.contains("Bank: 1234 5678\nIFSC SBIN000123"))
    }

    @Test
    fun `a rupee business with a UPI id sends a link with the amount filled in`() {
        val link = ReminderText.upiLink("singh@okaxis", "Singh Agro", 450_050, inr)!!
        assertEquals("upi://pay?pa=singh%40okaxis&pn=Singh+Agro&am=4500.50&cu=INR", link)
        val text = ReminderText.build(context, null, "Singh Agro", 450_050, inr, null, null, "singh@okaxis")
        assertTrue(text.contains(link))
        assertTrue("the id itself, for a phone that will not open the link", text.contains("singh@okaxis"))
    }

    @Test
    fun `UPI is the rupee's own, so no link in another currency, nothing owed, or no id`() {
        assertNull(ReminderText.upiLink("a@b", "X", 10_000, usd))
        assertNull(ReminderText.upiLink("a@b", "X", 0, inr))
        assertNull(ReminderText.upiLink("a@b", "X", -5, inr))
        assertNull(ReminderText.upiLink(null, "X", 10_000, inr))
        assertNull(ReminderText.upiLink("  ", "X", 10_000, inr))
    }

    @Test
    fun `a fresh debt does not say how old it is`() {
        val text = ReminderText.build(context, null, "Singh Agro", 100_000, usd, oldestDays = 0, payDetails = null, upiId = null)
        assertFalse(text.contains("old"))
        assertFalse(text.contains("Pay to"))
    }

    @Test
    fun `WhatsApp opens a chat with the customer's own number`() {
        assertEquals("https://wa.me/919876543210?text=Hello+there%0AThanks", Sharing.whatsAppLink("9876543210", "Hello there\nThanks"))
        assertEquals("https://wa.me/254712345678?text=x", Sharing.whatsAppLink("+254 712 345 678", "x"))
        assertNull(Sharing.whatsAppLink(null, "x"))
        assertNull(Sharing.whatsAppLink("", "x"))
    }

    // -------------------------------------------------------------- credit warnings

    @Test
    fun `a farmer over the limit is told so, and that wins over late`() {
        val entries = listOf(charge("a", 500_000, 0))
        val now = 100 * day
        val over = CreditCheck.warning(entries, 300_000, now)
        assertEquals(CreditWarning.OverLimit(500_000, 300_000), over)
    }

    @Test
    fun `a farmer who has owed for over a month is told how long`() {
        val entries = listOf(charge("a", 200_000, 0))
        assertEquals(CreditWarning.Late(200_000, 45), CreditCheck.warning(entries, null, 45 * day))
        assertNull("a month is normal credit", CreditCheck.warning(entries, null, 30 * day))
    }

    @Test
    fun `a farmer who owes nothing, or has paid up, is told nothing`() {
        assertNull(CreditCheck.warning(emptyList(), 100, 99 * day))
        val paid = listOf(charge("a", 200_000, 0), payment("p", 200_000, day))
        assertNull(CreditCheck.warning(paid, 100, 99 * day))
    }

    @Test
    fun `what they pay settles the oldest debt, so a fresh charge is not late`() {
        val entries = listOf(charge("old", 100_000, 0), payment("p", 100_000, 10 * day), charge("new", 50_000, 60 * day))
        assertNull(CreditCheck.warning(entries, null, 70 * day))
    }

    // --------------------------------------------------------------------- chasing

    private fun customer(id: String, chase: Long?) =
        CustomerEntity(id = id, orgId = org, name = "Name $id", chaseOnDay = chase, createdAt = 0, updatedAt = 0)

    @Test
    fun `a chase day that has come, for somebody who still owes, is on the home screen`() {
        val today = LocalDate.now(com.example.fieldtrack.data.region.BusinessClock.zone).toEpochDay()
        val customers = listOf(customer("a", today), customer("b", today - 3), customer("c", today + 1), customer("d", null), customer("e", today))
        val debts = debtsByAge(
            listOf(charge("1", 500_000, 0, "a"), charge("2", 300_000, 0, "b"), charge("3", 900_000, 0, "c"), charge("4", 100_000, 0, "d"))
        )
        val alerts = chaseAlerts(customers, debts, 0)
        assertEquals("a and b are due; c is tomorrow, d never asked, e owes nothing", listOf("a", "b"), alerts.map { it.customerId })
        assertEquals("largest debt first", 500_000L, alerts.first().amountMinor)
    }

    @Test
    fun `no more than three are chased at once`() {
        val today = LocalDate.now(com.example.fieldtrack.data.region.BusinessClock.zone).toEpochDay()
        val customers = (1..6).map { customer("c$it", today) }
        val debts = debtsByAge(customers.mapIndexed { i, c -> charge("e$i", (i + 1) * 100_000L, 0, c.id) })
        assertEquals(3, chaseAlerts(customers, debts, 0).size)
    }

    @Test
    fun `a customer remembers a chase day and forgets it when told`() = runTest {
        h.directory.setChaseDay(org, TestHarness.CUSTOMER_A, 20_500)
        assertEquals(20_500L, h.directory.findCustomer(org, TestHarness.CUSTOMER_A)!!.chaseOnDay)
        h.directory.setChaseDay(org, TestHarness.CUSTOMER_A, null)
        assertNull(h.directory.findCustomer(org, TestHarness.CUSTOMER_A)!!.chaseOnDay)
        assertTrue("and it reaches the other phones", h.db.outboxDao().nextBatch(50).any { it.tableName == "customers" })
    }

    // ------------------------------------------------------------------- customers

    @Test
    fun `a customer's language and limit are kept, and left alone when the form does not say`() = runTest {
        val id = h.directory.addCustomer(org, "Kamau", null, null, terms = CustomerTerms("sw", 500_000))
        var c = h.directory.findCustomer(org, id)!!
        assertEquals("sw", c.language); assertEquals(500_000L, c.creditLimitMinor)
        h.directory.updateCustomer(org, Capabilities.ofOwner(), id, "Kamau W", null, null)
        c = h.directory.findCustomer(org, id)!!
        assertEquals("Kamau W", c.name); assertEquals("sw", c.language); assertEquals(500_000L, c.creditLimitMinor)
        h.directory.updateCustomer(org, Capabilities.ofOwner(), id, "Kamau W", null, null, terms = CustomerTerms(null, 0))
        c = h.directory.findCustomer(org, id)!!
        assertNull(c.language); assertNull("a limit of nothing is no limit", c.creditLimitMinor)
    }

    // -------------------------------------------------------------------- receipts

    private val orgRow = OrgEntity(id = org, name = "Singh Agro", createdAt = 0, updatedAt = 0)
    private val farmer = CustomerEntity(id = "c1", orgId = org, name = "Ramesh", createdAt = 0, updatedAt = 0)

    @Test
    fun `a receipt says who paid, how much, how, and what is left`() {
        val entry = payment("p", 300_000, 0).copy(note = null)
        val doc = Documents.receipt(orgRow, farmer, entry, "Cash", "7 Nov 2026", balanceAfterMinor = 150_000)
        val html = DocumentHtml.render(doc)
        assertTrue(html.contains("RECEIPT"))
        assertTrue(html.contains("Ramesh"))
        assertTrue(html.contains("₹3,000"))
        assertTrue(html.contains("Paid by"))
        assertTrue(html.contains("Still due after this"))
        assertTrue(html.contains("₹1,500"))
        assertTrue(html.contains("Rupees three thousand only"))
        assertEquals("Receipt-7-Nov-2026-Ramesh", doc.fileName)
    }

    @Test
    fun `a receipt for a payment ahead says paid ahead, and one that clears the account says settled`() {
        val entry = payment("p", 300_000, 0)
        assertTrue(DocumentHtml.render(Documents.receipt(orgRow, farmer, entry, "UPI", "d", -50_000)).contains("Paid ahead"))
        assertTrue(DocumentHtml.render(Documents.receipt(orgRow, farmer, entry, "UPI", "d", 0)).contains("Account settled"))
    }

    @Test
    fun `grain handed over is on the receipt as what it was`() {
        val entry = payment("p", 600_000, 0).copy(method = PaymentMethod.InKind, note = "20 bags of wheat")
        val html = DocumentHtml.render(Documents.receipt(orgRow, farmer, entry, "In kind (grain or produce)", "d", 0))
        assertTrue(html.contains("20 bags of wheat"))
        assertTrue(html.contains("In kind"))
    }

    @Test
    fun `the balance on a receipt is the one the khata shows at that line`() {
        val entries = listOf(charge("c", 500_000, 10), payment("p1", 200_000, 20), payment("p2", 100_000, 30))
        val rows = buildKhata(entries.sortedByDescending { it.atMillis })
        assertEquals(300_000L, rows.first { it.id == "p1" }.balanceAfterMinor)
        assertEquals(200_000L, rows.first { it.id == "p2" }.balanceAfterMinor)
    }

    // -------------------------------------------------------------- tax invoice

    @Test
    fun `a bill is a tax invoice when the business charges a tax and gives its number`() {
        fun bill(o: OrgEntity) = DocumentHtml.render(
            Documents.bill(
                o, farmer, "2026-27", 1, "d", null, null, "Rotavator", null, null, null, 100_000, 0, emptyList(), 100_000,
            )
        )
        assertTrue(bill(orgRow.copy(taxName = "GST", taxPercentBps = 1800, gstin = "06ABCDE1234F1Z5")).contains("TAX INVOICE"))
        assertTrue(bill(orgRow.copy(taxName = "GST", taxPercentBps = 1800)).contains(">BILL<"))
        assertTrue(bill(orgRow).contains(">BILL<"))
    }

    // ---------------------------------------------------------------- job summary

    @Test
    fun `a job summary says what was done, the lines, the total and where to pay`() {
        val quote = Quote(workMinor = 100_000, minimumTopUpMinor = 0, arrivalMinor = 30_000, waitingMinor = 0, fuelMinor = 20_000, discountMinor = 5_000, totalMinor = 145_000)
        val text = JobSummaryText.build(context, null, "Singh Agro", "Rotavator", "2 acre", "7 Nov 2026", quote, 145_000, inr, "M-Pesa 0712")
        assertTrue(text.contains("Singh Agro"))
        assertTrue(text.contains("Rotavator · 2 acre"))
        assertTrue(text.contains("Arrival: +₹300"))
        assertTrue(text.contains("Diesel: +₹200"))
        assertTrue(text.contains("Discount: −₹50"))
        assertTrue(text.contains("Total: ₹1,450"))
        assertTrue(text.contains("M-Pesa 0712"))
    }

    @Test
    fun `a plain job's summary is just the work and the total`() {
        val text = JobSummaryText.build(context, null, "Singh Agro", "Rotavator", null, "d", null, 100_000, inr, null)
        assertFalse(text.contains("Work:"))
        assertTrue(text.contains("Total: ₹1,000"))
    }

    // --------------------------------------------------------------------- advances

    @Test
    fun `an advance is owed back until pay takes it off`() = runTest {
        val driver = TestHarness.DRIVER
        h.ledger.recordPayout(org, Capabilities.ofOwner(), 500_000, PayoutCovers.Advance, 1, "owner", toPersonId = driver, toName = "Jaswant")
        assertEquals(500_000L, h.ledger.observeAdvanceOutstanding(org, driver).first())
        h.ledger.recordPayout(org, Capabilities.ofOwner(), 700_000, PayoutCovers.Period, 2, "owner", toPersonId = driver, toName = "Jaswant", recoversAdvanceMinor = 300_000)
        assertEquals(200_000L, h.ledger.observeAdvanceOutstanding(org, driver).first())
        h.ledger.recordPayout(org, Capabilities.ofOwner(), 100_000, PayoutCovers.Advance, 3, "owner", toPersonId = driver, toName = "Jaswant")
        assertEquals(300_000L, h.ledger.observeAdvanceOutstanding(org, driver).first())
    }

    @Test
    fun `advances are one person's own`() = runTest {
        h.ledger.recordPayout(org, Capabilities.ofOwner(), 500_000, PayoutCovers.Advance, 1, "owner", toPersonId = TestHarness.DRIVER, toName = "J")
        assertEquals(0L, h.ledger.observeAdvanceOutstanding(org, "someone-else").first())
        assertEquals("another business", 0L, h.ledger.observeAdvanceOutstanding(TestHarness.ORG_B, TestHarness.DRIVER).first())
    }

    @Test
    fun `an advance needs somebody to be given to`() = runTest {
        val refused = runCatching {
            h.ledger.recordPayout(org, Capabilities.ofOwner(), 500_000, PayoutCovers.Advance, 1, "owner")
        }.isFailure
        assertTrue(refused)
    }

    @Test
    fun `more than is owed back cannot be taken off, and an advance takes nothing off itself`() {
        val s = PayOutState(toPersonId = "p", amountText = "5000", advanceOutstandingMinor = 200_000, recoverText = "9999", covers = PayoutCovers.Period, mayPay = true)
        assertEquals(200_000L, s.recoverMinor)
        assertEquals(0L, s.copy(covers = PayoutCovers.Advance).recoverMinor)
        assertEquals(0L, s.copy(recoverText = "").recoverMinor)
        assertTrue(s.canSave)
        assertFalse("an advance to nobody", s.copy(toPersonId = null, covers = PayoutCovers.Advance).canSave)
    }

    // ------------------------------------------------------------------------ search

    @Test
    fun `search finds a farmer by name, village or number`() = runTest {
        h.db.customerDao().upsert(CustomerEntity(id = "k", orgId = org, name = "Kamau Njoroge", village = "Nyeri", phone = "+254712345678", createdAt = 0, updatedAt = 0))
        assertEquals(listOf("k"), h.db.customerDao().findByTerm(org, "%kamau%").map { it.id })
        assertEquals(listOf("k"), h.db.customerDao().findByTerm(org, "%nyeri%").map { it.id })
        assertEquals(listOf("k"), h.db.customerDao().findByTerm(org, "%345678%").map { it.id })
        assertTrue("another business's farmer is never found", h.db.customerDao().findByTerm(TestHarness.ORG_B, "%kamau%").isEmpty())
    }

    @Test
    fun `search finds machines by name, plate or the owner's own kind-name`() = runTest {
        h.directory.addMachine(org, "Swaraj 855", MachineKind.Tractor, plate = "HR 26 AB 1234")
        h.directory.addMachine(org, "Old one", MachineKind.Other, typeLabel = "Laser leveller")
        assertEquals(listOf("Swaraj 855"), h.db.machineDao().findByTerm(org, "%swaraj%").map { it.name })
        assertEquals(listOf("Swaraj 855"), h.db.machineDao().findByTerm(org, "%AB 1234%").map { it.name })
        assertEquals(listOf("Old one"), h.db.machineDao().findByTerm(org, "%laser%").map { it.name })
    }

    @Test
    fun `search finds jobs by the work, the note or whose they are, and never a discarded one`() = runTest {
        val a = h.work.startJob(orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator", billingMode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rateMinor = 1)
        h.work.addNote(org, a, "Canal side")
        val b = h.work.startJob(orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Hauling", billingMode = BillingMode.PerTrip, unit = MeasureUnit.Trip, rateMinor = 1)
        h.work.discard(org, b)
        assertEquals(listOf(a), h.db.jobDao().findByTerm(org, "%rotav%").map { it.id })
        assertEquals(listOf(a), h.db.jobDao().findByTerm(org, "%canal%").map { it.id })
        assertEquals("by the farmer's name", listOf(a), h.db.jobDao().findByTerm(org, "%ramesh%").map { it.id })
        assertTrue(h.db.jobDao().findByTerm(org, "%hauling%").isEmpty())
    }
}

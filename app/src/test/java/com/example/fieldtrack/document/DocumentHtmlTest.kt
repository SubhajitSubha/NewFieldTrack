package com.example.fieldtrack.document

import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.format.DigitGrouping
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a customer actually reads on a printed bill.
 *
 * These assert the content of the page rather than how it looks: that a business
 * which has no GST number is not handed a bill with an empty GSTIN label, that money
 * is grouped the Indian way, and that a customer called "Singh & Sons" does not break
 * the page it is printed on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentHtmlTest {

    @org.junit.Before fun readsInLakhs() { Money.grouping = DigitGrouping.Indian }
    @org.junit.After fun backToThePhone() { Money.grouping = DigitGrouping.forLocale(java.util.Locale.getDefault()) }

    private val fullOrg = OrgEntity(
        id = "org",
        name = "Singh Agro Services",
        addressLine = "Village Kheri, Fatehabad",
        phone = "9876543210",
        gstin = "06ABCDE1234F1Z5",
        upiId = "singhagro@okaxis",
        defaultLandUnit = MeasureUnit.Acre,
        createdAt = 0,
        updatedAt = 0,
    )

    private val soloOrg = OrgEntity(
        id = "org",
        name = "Harpal Singh",
        addressLine = "Village Kheri",
        phone = "9876543210",
        defaultLandUnit = MeasureUnit.Acre,
        createdAt = 0,
        updatedAt = 0,
    )

    private val customer = CustomerEntity(
        id = "cust", orgId = "org", name = "Ramesh Singh", village = "Kheri",
        phone = "9988776655", createdAt = 0, updatedAt = 0,
    )

    /** Whole rupees, in the minor units every amount is kept in. */
    private fun r(rupees: Long) = rupees * 100

    private fun bill(org: OrgEntity?, previous: Long = r(8_900), payments: List<PaymentLine> = emptyList()) =
        Documents.bill(
            org = org,
            customer = customer,
            billFy = "2026-27",
            billSeq = 241,
            dateLabel = "10 October 2026",
            workedOnLabel = "10 October 2026",
            machineLabel = "Mahindra 575",
            workTitle = "Rotavator",
            workDetail = "Canal-side field",
            measureLabel = "3.5 acre",
            rateLabel = "₹1,000 / acre",
            jobAmountMinor = r(3_500),
            previousBalanceMinor = previous,
            payments = payments,
            balanceMinor = r(3_500) + previous - payments.sumOf { it.amountMinor },
        )

    @Test
    fun `the bill carries the business, the customer and the number`() {
        val html = DocumentHtml.render(bill(fullOrg))

        assertTrue(html.contains("Singh Agro Services"))
        assertTrue(html.contains("Village Kheri, Fatehabad"))
        // Grouped the way the number is read aloud, matching every other screen
        // — and routed through the same formatter, so a number stored with its
        // country code already attached does not print with two of them.
        assertTrue(html.contains("+91 98765 43210"))
        assertTrue(html.contains("GSTIN 06ABCDE1234F1Z5"))
        assertTrue(html.contains("2026-27/0241"))
        assertTrue(html.contains("Ramesh Singh"))
    }

    @Test
    fun `a business with no GST number gets no GSTIN line at all`() {
        val html = DocumentHtml.render(bill(soloOrg))

        assertFalse("an empty label reads as an unfinished form", html.contains("GSTIN"))
        assertFalse("no UPI block without a UPI id", html.contains("PAY BY UPI"))
        assertTrue("what they do have is still there", html.contains("Village Kheri"))
    }

    @Test
    fun `money is grouped the Indian way`() {
        val html = DocumentHtml.render(
            bill(fullOrg, previous = r(1_241_000))
        )
        assertTrue("lakh grouping, not thousands", html.contains("12,44,500"))
    }

    @Test
    fun `the balance is written out underneath it`() {
        val html = DocumentHtml.render(
            bill(fullOrg, previous = r(3_900), payments = listOf(PaymentLine("10 Oct 2026", "Cash", r(5_000))))
        )
        // 3,500 + 3,900 - 5,000
        assertTrue(html.contains("Rupees two thousand four hundred only"))
    }

    @Test
    fun `a payment shows as a deduction, not a charge`() {
        val html = DocumentHtml.render(
            bill(fullOrg, payments = listOf(PaymentLine("10 Oct 2026", "Cash", r(5_000))))
        )
        assertTrue(html.contains("RECEIVED"))
        assertTrue(html.contains("&minus; ₹5,000"))
    }

    @Test
    fun `with nothing owed before, the bill does not invent an old balance row`() {
        val html = DocumentHtml.render(bill(fullOrg, previous = 0))
        assertFalse(html.contains("Balance before this bill"))
    }

    @Test
    fun `a customer's name cannot break the page it is printed on`() {
        val awkward = customer.copy(name = "Singh & Sons <Transport>")
        val html = DocumentHtml.render(
            Documents.bill(
                org = fullOrg, customer = awkward, billFy = "2026-27", billSeq = 1,
                dateLabel = "10 Oct 2026", workedOnLabel = null, machineLabel = null,
                workTitle = "Hauling", workDetail = null, measureLabel = "6 trips",
                rateLabel = "₹400 / trip", jobAmountMinor = 2_400,
                previousBalanceMinor = 0, payments = emptyList(), balanceMinor = 2_400,
            )
        )
        assertTrue(html.contains("Singh &amp; Sons &lt;Transport&gt;"))
        assertFalse("an unescaped tag would swallow the rest of the bill", html.contains("<Transport>"))
    }

    @Test
    fun `the footer carries the app's name on every document`() {
        assertTrue(DocumentHtml.render(bill(fullOrg)).contains("Made with FieldTrack"))
    }

    @Test
    fun `nothing on a printed page reaches the network`() {
        val html = DocumentHtml.render(bill(fullOrg))
        assertFalse("a bill is made in a field with no signal", html.contains("http"))
        assertTrue("the typeface comes out of the app itself", html.contains("file:///android_res/font/"))
    }

    @Test
    fun `the file name is safe to write and says what it is`() {
        assertEquals("Bill-2026-27-0241-Ramesh-Singh", bill(fullOrg).fileName)
    }

    @Test
    fun `a dues list credits what has already been paid`() {
        val document = Documents.dues(
            org = fullOrg,
            customer = customer,
            asOnLabel = "10 Oct 2026",
            lines = listOf(
                DueLine("5 Oct 2026", "Levelling · 2 acre", "₹1,950 per acre", "5 days", r(3_900)),
                DueLine("10 Oct 2026", "Rotavator · 3.5 acre", "₹1,000 per acre", "today", r(3_500)),
            ),
            receivedMinor = r(16_500),
            billedMinor = r(23_900),
            sinceLabel = "1 April 2026",
            settledBeforeLabel = "5 October",
        )

        assertEquals(r(7_400), document.totalMinor)
        val html = DocumentHtml.render(document)
        assertTrue(html.contains("₹16,500 received since 1 April 2026"))
        assertTrue(html.contains("paid in full"))
        assertTrue(html.contains("WAITING"))
        assertTrue(html.contains("5 days"))
    }

    @Test
    fun `a statement's running balance walks the khata and lands on the closing figure`() {
        fun entry(kind: com.example.fieldtrack.data.db.LedgerKind, amount: Long, day: Int) =
            com.example.fieldtrack.data.db.entity.LedgerEntryEntity(
                id = "e$day", orgId = "org", customerId = "cust", kind = kind,
                amountMinor = amount, atMillis = day * 86_400_000L, createdAt = 0,
            )

        val charge = com.example.fieldtrack.data.db.LedgerKind.Charge
        val payment = com.example.fieldtrack.data.db.LedgerKind.Payment

        val document = Documents.statement(
            org = fullOrg,
            customer = customer,
            periodLabel = "1 Apr - 10 Oct 2026",
            printedLabel = "10 Oct 2026",
            openingMinor = r(2_400),
            entries = listOf(
                entry(charge, r(7_200), 18),
                entry(payment, r(6_000), 32),
                entry(charge, r(3_000), 72),
            ),
            describe = { "line" },
            dateOf = { "day" },
        )

        assertEquals(r(2_400), document.openingMinor)
        assertEquals(r(10_200), document.billedMinor)
        assertEquals(r(6_000), document.receivedMinor)
        assertEquals("opening plus billed less received", r(6_600), document.closingMinor)
        assertEquals("Rupees six thousand six hundred only", document.closingInWords)

        // The brought-forward row plus one per entry.
        assertEquals(4, document.lines.size)
        assertTrue(document.lines.first().isBroughtForward)
        assertEquals(
            listOf(r(2_400), r(9_600), r(3_600), r(6_600)),
            document.lines.map { it.balanceAfterMinor },
        )
    }

    @Test
    fun `a payments-only page does not summarise an account it cannot see`() {
        fun pay(amount: Long, day: Int) =
            com.example.fieldtrack.data.db.entity.LedgerEntryEntity(
                id = "p$day", orgId = "org", customerId = "cust",
                kind = com.example.fieldtrack.data.db.LedgerKind.Payment,
                amountMinor = amount, atMillis = day * 86_400_000L, createdAt = 0,
            )

        val document = Documents.statement(
            org = fullOrg, customer = customer, periodLabel = "p", printedLabel = "d",
            openingMinor = 0, entries = listOf(pay(2_000, 1), pay(1_000, 2)),
            describe = { "Payment received" }, dateOf = { "day" },
            paymentsOnly = true,
        )
        val html = DocumentHtml.render(document)

        assertEquals("the total is what they paid", 3_000L, document.closingMinor)
        assertEquals("running total, not a balance", listOf(2_000L, 3_000L), document.lines.map { it.balanceAfterMinor })
        // The charges are filtered out, so every one of these would be a lie.
        assertFalse(html.contains("WORK BILLED"))
        assertFalse(html.contains("STILL DUE"))
        assertFalse(html.contains("CHARGED"))
        assertTrue(html.contains("TOTAL RECEIVED"))
        assertTrue(html.contains("PAID SO FAR"))
        assertTrue("and it is not filed as a statement", document.fileName.startsWith("Payments-"))
    }

    @Test
    fun `an account with nothing on it produces no brought-forward row`() {
        val document = Documents.statement(
            org = fullOrg, customer = customer, periodLabel = "p", printedLabel = "d",
            openingMinor = 0, entries = emptyList(),
            describe = { "" }, dateOf = { "" },
        )
        assertTrue(document.lines.isEmpty())
        assertEquals(0L, document.closingMinor)
    }

    @Test
    fun `a customer who has paid nothing gets no thank-you line`() {
        val document = Documents.dues(
            org = fullOrg, customer = customer, asOnLabel = "10 Oct 2026",
            lines = listOf(DueLine("5 Oct 2026", "Levelling", null, "5 days", 3_900)),
            receivedMinor = 0, billedMinor = 3_900,
            sinceLabel = "1 April 2026", settledBeforeLabel = null,
        )
        assertEquals(null, document.receivedNote)
    }
}

package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.billing.PaymentMethods
import com.example.fieldtrack.data.billing.TaxRate
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.designsystem.format.DigitGrouping
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.document.DocumentHtml
import com.example.fieldtrack.document.Documents
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

/** Which ways of paying a business offers, what a bill says about tax, and where it says to pay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentsAndTaxTest {

    private lateinit var h: TestHarness
    private lateinit var store: SessionStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().clear().commit()
        h = TestHarness()
        store = SessionStore(context, h.db, h.clock, h.ids)
        Money.grouping = DigitGrouping.Indian
    }

    @After fun tearDown() {
        h.close()
        Money.grouping = DigitGrouping.forLocale(java.util.Locale.getDefault())
    }

    // ---------------------------------------------------------------- methods

    @Test
    fun `each currency offers what is usual where it is used`() {
        assertEquals(listOf(PaymentMethod.Cash, PaymentMethod.Upi, PaymentMethod.Bank, PaymentMethod.Cheque), PaymentMethods.defaultsFor("INR"))
        assertEquals(listOf(PaymentMethod.Cash, PaymentMethod.MobileMoney, PaymentMethod.Bank), PaymentMethods.defaultsFor("KES"))
        assertTrue(PaymentMethod.Upi !in PaymentMethods.defaultsFor("USD"))
        assertTrue(PaymentMethod.Card in PaymentMethods.defaultsFor("USD"))
    }

    @Test
    fun `what a business chose is what it is offered, with cash always there`() {
        assertEquals(
            listOf(PaymentMethod.Cash, PaymentMethod.MobileMoney),
            PaymentMethods.offered("Cash,MobileMoney", "INR"),
        )
        assertEquals(
            "cash is put back if somebody left it out",
            listOf(PaymentMethod.Cash, PaymentMethod.Bank),
            PaymentMethods.offered("Bank", "USD"),
        )
        assertEquals(PaymentMethods.defaultsFor("KES"), PaymentMethods.offered(null, "KES"))
        assertEquals("nonsense falls back to the usual ones", PaymentMethods.defaultsFor("EUR"), PaymentMethods.offered("Nonsense,", "EUR"))
    }

    @Test
    fun `a business keeps what it chose and the screens follow`() = runTest {
        store.completeOnboarding("Wanjiru", "Wanjiru Farm Hire", MeasureUnit.Hectare, true, "KES", 1)
        store.activate()
        assertEquals(PaymentMethods.defaultsFor("KES"), Money.methods)

        store.updateBilling(listOf(PaymentMethod.Cash, PaymentMethod.MobileMoney, PaymentMethod.InKind), "M-Pesa 0712 345 678", "VAT", 1600)
        val org = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertEquals("Cash,MobileMoney,InKind", org.paymentMethods)
        assertEquals("M-Pesa 0712 345 678", org.payDetails)
        assertEquals("VAT", org.taxName)
        assertEquals(1600, org.taxPercentBps)
        assertEquals(listOf(PaymentMethod.Cash, PaymentMethod.MobileMoney, PaymentMethod.InKind), Money.methods)
    }

    @Test
    fun `choosing the usual ones stores nothing, so the business keeps following its currency`() = runTest {
        store.completeOnboarding("Wanjiru", "WFH", MeasureUnit.Hectare, true, "KES", 1)
        store.activate()
        store.updateBilling(PaymentMethods.defaultsFor("KES"), null, null, null)
        assertNull(h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!.paymentMethods)
    }

    @Test
    fun `a tax with no rate is no tax, and a rate with no name is no tax line`() = runTest {
        store.completeOnboarding("Wanjiru", "WFH", MeasureUnit.Hectare, true, "KES", 1)
        store.activate()
        store.updateBilling(PaymentMethods.defaultsFor("KES"), null, "VAT", null)
        var org = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertNull(org.taxName)
        assertNull(org.taxPercentBps)
        store.updateBilling(PaymentMethods.defaultsFor("KES"), null, "  ", 1600)
        org = h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!
        assertNull("no name to print", org.taxName)
    }

    // -------------------------------------------------------------------- tax

    @Test
    fun `a rate is typed in percent and kept in hundredths`() {
        assertEquals(1600, TaxRate.parse("16"))
        assertEquals(750, TaxRate.parse("7.5"))
        assertEquals(725, TaxRate.parse("7,25"))
        assertEquals(1, TaxRate.parse("0.01"))
        assertNull(TaxRate.parse(""))
        assertNull(TaxRate.parse("0"))
        assertNull("over a hundred percent", TaxRate.parse("101"))
        assertNull(TaxRate.parse("1.2.3"))
        assertNull(TaxRate.parse("abc"))
        assertEquals("16", TaxRate.text(1600))
        assertEquals("7.5", TaxRate.text(750))
        assertEquals("7.25", TaxRate.text(725))
    }

    @Test
    fun `the tax inside a price that includes it`() {
        // 116.00 with 16% in it is 100.00 plus 16.00.
        assertEquals(1_600L, TaxRate.includedIn(11_600, 1600, 1))
        // 1,180 with 18% GST in it carries 180 of it, to the rupee in a rupee business.
        assertEquals(18_000L, TaxRate.includedIn(118_000, 1800, 100))
        assertEquals(0L, TaxRate.includedIn(0, 1600, 1))
        assertEquals(0L, TaxRate.includedIn(5_000, 0, 1))
    }

    // ------------------------------------------------------------------ bills

    private val customer = CustomerEntity(id = "c", orgId = "o", name = "Ramesh", createdAt = 0, updatedAt = 0)

    private fun bill(org: OrgEntity) = Documents.bill(
        org = org, customer = customer, billFy = "2026-27", billSeq = 1, dateLabel = "7 Nov 2026",
        workedOnLabel = null, machineLabel = null, workTitle = "Rotavator", workDetail = null,
        measureLabel = "2 acre", rateLabel = "1,000 / acre", jobAmountMinor = 118_000,
        previousBalanceMinor = 0, payments = emptyList(), balanceMinor = 118_000,
    )

    private val base = OrgEntity(id = "o", name = "Singh Agro", createdAt = 0, updatedAt = 0)

    @Test
    fun `a bill says how much of its total is tax when the business charges one`() {
        val html = DocumentHtml.render(bill(base.copy(taxName = "GST", taxPercentBps = 1800, gstin = "06ABCDE1234F1Z5")))
        assertTrue(html.contains("Includes GST 18%"))
        assertTrue("the tax in 1,180 at 18% is 180", html.contains("₹180"))
        assertTrue("the registration number carries the tax's own name", html.contains("GST 06ABCDE1234F1Z5"))
    }

    @Test
    fun `a business with no tax gets no tax line`() {
        val html = DocumentHtml.render(bill(base))
        assertFalse(html.contains("Includes"))
    }

    @Test
    fun `a bill says where to pay when there are details to say`() {
        val html = DocumentHtml.render(bill(base.copy(payDetails = "M-Pesa 0712 345 678\nTill 123456")))
        assertTrue(html.contains("PAY TO"))
        assertTrue(html.contains("M-Pesa 0712 345 678<br>Till 123456"))
    }

    @Test
    fun `a UPI business keeps its UPI block`() {
        val html = DocumentHtml.render(bill(base.copy(upiId = "singh@okaxis", payDetails = "Bank: 123")))
        assertTrue(html.contains("PAY BY UPI"))
    }
}

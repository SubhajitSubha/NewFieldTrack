package com.example.fieldtrack.designsystem

import com.example.fieldtrack.data.billing.Billing
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.designsystem.format.DigitGrouping
import com.example.fieldtrack.designsystem.format.MoneyInput
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.format.moneyCompact
import com.example.fieldtrack.designsystem.format.moneyPlain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How money is written and typed, for currencies that do and do not have a small coin.
 *
 * Styles are built by hand here, not from the device locale, so the result cannot
 * depend on the machine the tests run on: the whole point of the formatter is that the
 * same amount reads the same on every handset.
 */
class MoneyStyleTest {

    private val inr = MoneyStyle("INR", "₹", 2, 100, DigitGrouping.Indian, '.', ',', false)
    private val inrWithPaise = inr.copy(stepMinor = 1)
    private val usd = MoneyStyle("USD", "$", 2, 1, DigitGrouping.International, '.', ',', false)
    private val jpy = MoneyStyle("JPY", "¥", 0, 1, DigitGrouping.International, '.', ',', false)
    private val kwd = MoneyStyle("KWD", "KD", 3, 1, DigitGrouping.International, '.', ',', false)
    private val eur = MoneyStyle("EUR", "€", 2, 1, DigitGrouping.International, ',', '.', true)

    @Test
    fun `a rupee business sees whole rupees in lakh grouping and no decimals`() {
        assertEquals("₹12,400", money(1_240_000, inr))
        assertEquals("₹12,00,000", money(120_000_000, inr))
        assertEquals("₹0", money(0, inr))
        assertEquals("₹999", money(99_900, inr))
        assertFalse(inr.showsDecimals)
    }

    @Test
    fun `a whole-rupee business rounds a stray paise figure to the rupee`() {
        assertEquals("₹12,400", money(1_240_049, inr))
        assertEquals("₹12,401", money(1_240_050, inr))
        assertEquals("a loss of forty paise is not a minus sign in front of nothing", "₹0", money(-40, inr))
    }

    @Test
    fun `a rupee business that does deal in paise sees them`() {
        assertTrue(inrWithPaise.showsDecimals)
        assertEquals("₹12,400.50", money(1_240_050, inrWithPaise))
        assertEquals("₹0.05", money(5, inrWithPaise))
    }

    @Test
    fun `a dollar business sees cents in threes`() {
        assertEquals("$1,234.56", money(123_456, usd))
        assertEquals("$1,234,567.00", money(123_456_700, usd))
        assertEquals("−$0.50", money(-50, usd))
    }

    fun `currencies with no minor unit or three`() {
        assertEquals("¥1,500", money(1_500, jpy))
        assertEquals("KD1,234.567", money(1_234_567, kwd))
        assertFalse(jpy.showsDecimals)
    }

    @Test
    fun `the symbol and separators follow the reader`() {
        assertEquals("1.234,56 €", money(123_456, eur))
        assertEquals("1.234,56", moneyPlain(123_456, eur))
    }

    @Test
    fun `stat tiles shorten large amounts the way each system speaks them`() {
        assertEquals("₹1.8L", moneyCompact(18_430_000, inr))
        assertEquals("₹2.4Cr", moneyCompact(2_400_000_000, inr))
        assertEquals("₹12.4k", moneyCompact(1_240_000, inr))
        assertEquals("₹900", moneyCompact(90_000, inr))
        assertEquals("$1.8M", moneyCompact(180_000_000, usd))
        assertEquals("$12.4k", moneyCompact(1_240_000, usd))
    }

    @Test
    fun `typing allows only the decimals the business uses`() {
        assertEquals("1500", MoneyInput.clean("1,500", inr))
        assertEquals("1500", MoneyInput.clean("1500.75", inr))
        assertEquals("1500.75", MoneyInput.clean("1500.759", inrWithPaise))
        assertEquals("12.5", MoneyInput.clean("12,5", usd))
        assertEquals("a bare point reads as nought point", "0.5", MoneyInput.clean(".5", usd))
        assertEquals("a pasted amount with grouping", "1500.5", MoneyInput.clean("1,500.5", usd))
        assertEquals("a thousands comma is not a decimal", "1500", MoneyInput.clean("1,500", usd))
        assertEquals("a half-typed point stays so the next digit can follow", "12.", MoneyInput.clean("12.", usd))
        assertEquals("leading noughts go", "5", MoneyInput.clean("005", inr))
        assertEquals("1500", MoneyInput.clean("1500", jpy))
        assertEquals("a slipped thumb stops at eight digits", "12345678", MoneyInput.clean("1234567890", inr))
    }

    @Test
    fun `typed text becomes the stored amount`() {
        assertEquals(150_000L, MoneyInput.parse("1500", inr))
        assertEquals(125L, MoneyInput.parse("1.25", usd))
        assertEquals(120L, MoneyInput.parse("1.2", usd))
        assertEquals(1_500L, MoneyInput.parse("1500", jpy))
        assertEquals(1_234_567L, MoneyInput.parse("1234.567", kwd))
        assertNull(MoneyInput.parse("", usd))
        assertNull(MoneyInput.parse("abc", usd))
    }

    @Test
    fun `an amount goes back into a field as plain editable text`() {
        assertEquals("1500", MoneyInput.text(150_000, inr))
        assertEquals("12.5", MoneyInput.text(1_250, usd))
        assertEquals("12", MoneyInput.text(1_200, usd))
        assertEquals("1.05", MoneyInput.text(105, usd))
        assertEquals("1500", MoneyInput.text(1_500, jpy))
    }

    @Test
    fun `what is typed is what is kept`() {
        for (text in listOf("1", "10", "1500", "99999999")) {
            assertEquals(text, MoneyInput.text(MoneyInput.parse(text, inr)!!, inr))
        }
        for (text in listOf("0.05", "1.05", "12.5", "100")) {
            assertEquals(text, MoneyInput.text(MoneyInput.parse(text, usd)!!, usd))
        }
    }

    @Test
    fun `the usual rounding for a currency follows how it is traded`() {
        assertEquals(100L, MoneyStyle.defaultStepMinor("INR"))
        assertEquals(1L, MoneyStyle.defaultStepMinor("USD"))
        assertEquals(1L, MoneyStyle.defaultStepMinor("EUR"))
        assertEquals(1L, MoneyStyle.defaultStepMinor("JPY"))
        assertEquals(2, MoneyStyle.digitsOf("INR"))
        assertEquals(0, MoneyStyle.digitsOf("JPY"))
        assertEquals(3, MoneyStyle.digitsOf("KWD"))
    }

    @Test
    fun `a bill is rounded to the business's step, and to nothing finer`() {
        // 2.5 acres at 1,234 is a whole number of rupees either way.
        assertEquals(308_500L, Billing.amount(BillingMode.PerAcre, 2.5, 123_400, 100))
        // 0.333 acre at 1,234.00 is 410.922 rupees: a rupee business bills 411, a cent business 410.92.
        assertEquals(41_100L, Billing.amount(BillingMode.PerAcre, 0.333, 123_400, 100))
        assertEquals(41_092L, Billing.amount(BillingMode.PerAcre, 0.333, 123_400, 1))
        // A fixed price is the rate, whatever the quantity or the step.
        assertEquals(500_050L, Billing.amount(BillingMode.Fixed, 7.0, 500_050, 100))
    }
}

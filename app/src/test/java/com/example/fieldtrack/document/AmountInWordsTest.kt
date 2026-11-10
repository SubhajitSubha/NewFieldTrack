package com.example.fieldtrack.document

import com.example.fieldtrack.designsystem.format.MoneyStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The written-out amount at the foot of a bill.
 *
 * It exists to be read aloud when two people disagree about a digit, so it has to be
 * grouped the way it would be spoken here — lakh and crore, not millions.
 */
class AmountInWordsTest {

    /** The words for a whole number of rupees, which is how these cases are written. */
    private fun inRupees(rupees: Long) = amountInWords(rupees * 100)!!

    @Test
    fun `paise are written after the rupees`() {
        assertEquals("Rupees twelve and fifty paise only", amountInWords(1_250))
        assertEquals("Rupees zero and five paise only", amountInWords(5))
    }

    @Test
    fun `no wording is invented for another currency`() {
        assertNull(amountInWords(1_250, MoneyStyle.of("USD", 1)))
        assertNull(amountInWords(1_250, MoneyStyle.of("EUR", 1)))
    }

    @Test
    fun `the amount on the worked example`() {
        assertEquals("Rupees seven thousand four hundred only", inRupees(7_400))
    }

    @Test
    fun `small amounts`() {
        assertEquals("Rupees zero only", inRupees(0))
        assertEquals("Rupees one only", inRupees(1))
        assertEquals("Rupees nineteen only", inRupees(19))
        assertEquals("Rupees twenty only", inRupees(20))
        assertEquals("Rupees ninety nine only", inRupees(99))
        assertEquals("Rupees one hundred only", inRupees(100))
        assertEquals("Rupees one hundred one only", inRupees(101))
    }

    @Test
    fun `grouped in lakh and crore, not millions`() {
        assertEquals("Rupees one lakh only", inRupees(100_000))
        assertEquals(
            "Rupees one lakh twenty three thousand four hundred fifty six only",
            inRupees(123_456),
        )
        assertEquals("Rupees ten lakh only", inRupees(1_000_000))
        assertEquals("Rupees one crore only", inRupees(10_000_000))
        assertEquals(
            "Rupees two crore fifty lakh only",
            inRupees(25_000_000),
        )
    }

    @Test
    fun `a season's turnover reads correctly`() {
        assertEquals(
            "Rupees twelve lakh forty five thousand eight hundred only",
            inRupees(1_245_800),
        )
    }

    @Test
    fun `a credit is said as a credit, never as a negative`() {
        // A customer who has overpaid should not be handed "minus" on a bill.
        assertEquals("Rupees five hundred in credit", inRupees(-500))
    }

    @Test
    fun `very large amounts still group`() {
        assertEquals(
            "Rupees one thousand two hundred thirty four crore only",
            inRupees(12_340_000_000),
        )
    }
}

/**
 * Indian digit grouping.
 *
 * Asserted rather than left to the platform: the en-IN locale groups in threes on a
 * plain JVM, so a figure that reads correctly on one device could read wrong on
 * another, and this is the number the whole app is about.
 */
class IndianGroupingTest {

    @org.junit.Test
    fun `groups three then twos`() {
        assertEquals("₹999", com.example.fieldtrack.designsystem.format.money(999 * 100L))
        assertEquals("₹7,400", com.example.fieldtrack.designsystem.format.money(7_400 * 100L))
        assertEquals("₹1,00,000", com.example.fieldtrack.designsystem.format.money(100_000 * 100L))
        assertEquals("₹12,44,500", com.example.fieldtrack.designsystem.format.money(1_244_500 * 100L))
        assertEquals("₹1,00,00,000", com.example.fieldtrack.designsystem.format.money(10_000_000 * 100L))
    }

    @org.junit.Test
    fun `a negative amount uses a true minus sign`() {
        assertEquals("−₹2,000", com.example.fieldtrack.designsystem.format.money(-2_000 * 100L))
    }

    @org.junit.Test
    fun `zero is just zero`() {
        assertEquals("₹0", com.example.fieldtrack.designsystem.format.money(0 * 100L))
    }
}

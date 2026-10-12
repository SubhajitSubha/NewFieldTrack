package com.example.fieldtrack.document

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The written-out amount at the foot of a bill.
 *
 * It exists to be read aloud when two people disagree about a digit, so it has to be
 * grouped the way it would be spoken here — lakh and crore, not millions.
 */
class AmountInWordsTest {

    @Test
    fun `the amount on the worked example`() {
        assertEquals("Rupees seven thousand four hundred only", rupeesInWords(7_400))
    }

    @Test
    fun `small amounts`() {
        assertEquals("Rupees zero only", rupeesInWords(0))
        assertEquals("Rupees one only", rupeesInWords(1))
        assertEquals("Rupees nineteen only", rupeesInWords(19))
        assertEquals("Rupees twenty only", rupeesInWords(20))
        assertEquals("Rupees ninety nine only", rupeesInWords(99))
        assertEquals("Rupees one hundred only", rupeesInWords(100))
        assertEquals("Rupees one hundred one only", rupeesInWords(101))
    }

    @Test
    fun `grouped in lakh and crore, not millions`() {
        assertEquals("Rupees one lakh only", rupeesInWords(100_000))
        assertEquals(
            "Rupees one lakh twenty three thousand four hundred fifty six only",
            rupeesInWords(123_456),
        )
        assertEquals("Rupees ten lakh only", rupeesInWords(1_000_000))
        assertEquals("Rupees one crore only", rupeesInWords(10_000_000))
        assertEquals(
            "Rupees two crore fifty lakh only",
            rupeesInWords(25_000_000),
        )
    }

    @Test
    fun `a season's turnover reads correctly`() {
        assertEquals(
            "Rupees twelve lakh forty five thousand eight hundred only",
            rupeesInWords(1_245_800),
        )
    }

    @Test
    fun `a credit is said as a credit, never as a negative`() {
        // A customer who has overpaid should not be handed "minus" on a bill.
        assertEquals("Rupees five hundred in credit", rupeesInWords(-500))
    }

    @Test
    fun `very large amounts still group`() {
        assertEquals(
            "Rupees one thousand two hundred thirty four crore only",
            rupeesInWords(12_340_000_000),
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
        assertEquals("₹999", com.example.fieldtrack.designsystem.format.rupees(999))
        assertEquals("₹7,400", com.example.fieldtrack.designsystem.format.rupees(7_400))
        assertEquals("₹1,00,000", com.example.fieldtrack.designsystem.format.rupees(100_000))
        assertEquals("₹12,44,500", com.example.fieldtrack.designsystem.format.rupees(1_244_500))
        assertEquals("₹1,00,00,000", com.example.fieldtrack.designsystem.format.rupees(10_000_000))
    }

    @org.junit.Test
    fun `a negative amount uses a true minus sign`() {
        assertEquals("−₹2,000", com.example.fieldtrack.designsystem.format.rupees(-2_000))
    }

    @org.junit.Test
    fun `zero is just zero`() {
        assertEquals("₹0", com.example.fieldtrack.designsystem.format.rupees(0))
    }
}

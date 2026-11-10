package com.example.fieldtrack.auth

import com.example.fieldtrack.data.auth.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Phone numbers from anywhere, and the old Indian ones that are still out there.
 *
 * What is protected here is that one number has one spelling: a customer typed today with
 * +91 in front and the same customer written last month as ten bare digits are the same
 * person, and an owner must not end up with both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneNumbersTest {

    @Test
    fun `a number written before countries existed reads as Indian`() {
        assertEquals("91" to "9876543210", PhoneNumbers.split("9876543210"))
        assertEquals("+919876543210", PhoneNumbers.normalize("9876543210"))
    }

    @Test
    fun `a number with its code is read off its front, whichever country`() {
        assertEquals("91" to "9876543210", PhoneNumbers.split("+919876543210"))
        assertEquals("254" to "712345678", PhoneNumbers.split("+254 712 345 678"))
        assertEquals("1" to "2025550142", PhoneNumbers.split("+1 (202) 555-0142"))
        assertEquals("44" to "7700900123", PhoneNumbers.split("+447700900123"))
        assertEquals("880" to "1712345678", PhoneNumbers.split("+8801712345678"))
        assertEquals("55" to "11987654321", PhoneNumbers.split("+5511987654321"))
        assertEquals("62" to "81234567890", PhoneNumbers.split("+6281234567890"))
    }

    @Test
    fun `two spellings of one number are one number`() {
        val spellings = listOf("9876543210", "+919876543210", "+91 98765 43210", " +91-98765-43210 ")
        assertEquals(setOf("+919876543210"), spellings.map { PhoneNumbers.normalize(it) }.toSet())
    }

    @Test
    fun `nothing is nothing`() {
        assertEquals("", PhoneNumbers.normalize(null))
        assertEquals("", PhoneNumbers.normalize(""))
        assertEquals("", PhoneNumbers.normalize("  "))
        assertEquals("", PhoneNumbers.e164("91", ""))
    }

    @Test
    fun `a code and its digits make the stored number`() {
        assertEquals("+254712345678", PhoneNumbers.e164("254", "712 345 678"))
        assertEquals("+919876543210", PhoneNumbers.e164("+91", "98765-43210"))
    }

    @Test
    fun `only what cannot be a number is refused`() {
        assertTrue(PhoneNumbers.isPlausible("254", "712345678"))
        assertTrue(PhoneNumbers.isPlausible("1", "2025550142"))
        assertFalse("too short to be anyone's", PhoneNumbers.isPlausible("254", "7123"))
        assertFalse("longer than any number may be", PhoneNumbers.isPlausible("254", "7123456789012345"))
        // India keeps its stricter rule: ten digits, a mobile prefix.
        assertTrue(PhoneNumbers.isPlausible("91", "9876543210"))
        assertFalse(PhoneNumbers.isPlausible("91", "5876543210"))
        assertFalse(PhoneNumbers.isPlausible("91", "987654321"))
    }

    @Test
    fun `typing keeps digits and no more than a number can have`() {
        assertEquals("712345678", PhoneNumbers.nationalDigits("712 345 678", "254"))
        assertEquals("12", PhoneNumbers.nationalDigits("1-2x", "44"))
        assertEquals(12, PhoneNumbers.nationalDigits("1234567890123456789", "254").length)
        // An Indian number pasted with its code is cut to the ten digits, not the first ten of the lot.
        assertEquals("9876543210", PhoneNumbers.nationalDigits("+91 98765 43210", "91"))
        assertEquals("9876543210", PhoneNumbers.nationalDigits("098765 43210", "91"))
    }

    @Test
    fun `a country gives its code and the picker puts the phone's own first`() {
        assertEquals("91", PhoneNumbers.dialOf("IN"))
        assertEquals("91", PhoneNumbers.dialOf("in"))
        assertEquals("254", PhoneNumbers.dialOf("KE"))
        assertEquals("1", PhoneNumbers.dialOf("US"))
        assertNull(PhoneNumbers.dialOf("ZZ"))
        assertNull(PhoneNumbers.dialOf(null))
        val list = PhoneNumbers.countries("KE", Locale.ENGLISH)
        assertEquals("KE", list.first().iso)
        assertEquals("every country once", list.size, list.map { it.iso }.distinct().size)
        assertTrue(list.size > 230)
        assertEquals("Kenya", list.first().name)
    }

    @Test
    fun `every calling code can be read off the front of a number`() {
        // The codes must not start one another, or "+254..." could be read as two countries.
        val codes = PhoneNumbers.countries(null).map { it.dial }.toSet()
        for (a in codes) for (b in codes) {
            if (a != b) assertFalse("$a starts $b", b.startsWith(a))
        }
    }

    @Test
    fun `a number is shown with its code, spaced where the platform knows how`() {
        assertEquals("+91 98765 43210", PhoneNumbers.display("9876543210", Locale("en", "IN")))
        assertEquals("+91 98765 43210", PhoneNumbers.display("+919876543210", Locale("en", "IN")))
        assertNull(PhoneNumbers.display(""))
    }
}

package com.example.fieldtrack.data

import com.example.fieldtrack.data.region.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * What the phone tells us about where its owner works, and what is made of it.
 *
 * These are guesses, and the tests are about the order of trust: the SIM before the
 * network before the language setting, nothing guessed from nothing, and a currency
 * the phone has never heard of falling back to one that exists.
 */
class RegionTest {

    @Test
    fun `the SIM is trusted before the network, and the network before the language`() {
        assertEquals("IN", Region.countryOf("in", "us", Locale("en", "GB")))
        assertEquals("US", Region.countryOf("", "us", Locale("en", "GB")))
        assertEquals("GB", Region.countryOf(null, null, Locale("en", "GB")))
        assertEquals("a code that is not a country is not used", "GB", Region.countryOf("xyz", "", Locale("en", "GB")))
        assertNull("nothing to go on", Region.countryOf(null, null, Locale("en")))
    }

    @Test
    fun `a country gives the money it trades in`() {
        assertEquals("INR", Region.currencyOf("IN"))
        assertEquals("USD", Region.currencyOf("US"))
        assertEquals("KES", Region.currencyOf("KE"))
        assertEquals("BRL", Region.currencyOf("BR"))
        assertEquals("EUR", Region.currencyOf("DE"))
        assertNull(Region.currencyOf(null))
        assertNull("not a country", Region.currencyOf("ZZ"))
    }

    @Test
    fun `a guess always has a currency`() {
        assertEquals("INR", Region.guess("in", null, Locale("hi", "IN")).currencyCode)
        assertEquals("a phone that says nothing gets a currency that exists", "USD", Region.guess(null, null, Locale("en")).currencyCode)
        assertEquals("KES", Region.guess("ke", "ke", Locale("en", "US")).currencyCode)
    }

    @Test
    fun `the picker puts the guessed currency first, then the usual ones, then the rest`() {
        val list = Region.currencies("KES", Locale.ENGLISH)
        assertEquals("KES", list.first().code)
        assertEquals("INR", list[1].code)
        assertTrue("every currency is there once", list.map { it.code }.distinct().size == list.size)
        assertTrue(list.size > 100)
        val rest = list.drop(30).map { it.name }
        assertEquals("the rest are in name order", rest.sortedBy { it.lowercase() }, rest)
    }

    @Test
    fun `a currency is named in the person's language, or by its code when the phone has no name`() {
        assertEquals("Indian Rupee", Region.nameOf("INR", Locale.ENGLISH))
        assertEquals("not a currency", "QQQ", Region.nameOf("QQQ", Locale.ENGLISH))
    }
}

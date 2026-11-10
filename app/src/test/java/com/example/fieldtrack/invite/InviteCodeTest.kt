package com.example.fieldtrack.invite

import com.example.fieldtrack.data.repository.INVITE_CODE_LENGTH
import com.example.fieldtrack.data.repository.generateInviteCode
import com.example.fieldtrack.data.repository.isWellFormedInviteCode
import com.example.fieldtrack.data.repository.normaliseInviteCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class InviteCodeTest {

    @Test
    fun `a code is six characters from the safe alphabet`() {
        val random = SecureRandom()
        repeat(500) {
            val code = generateInviteCode { random.nextInt(it) }
            assertEquals(INVITE_CODE_LENGTH, code.length)
            assertTrue("$code is not well formed", isWellFormedInviteCode(code))
        }
    }

    @Test
    fun `codes never contain a letter that can be misread as a digit`() {
        val random = SecureRandom()
        val produced = buildSet { repeat(2_000) { add(generateInviteCode { random.nextInt(it) }) } }
        // The four Crockford drops. Every remaining pair is distinguishable, which
        // is the property an eyeballed alphabet kept getting wrong.
        val excluded = setOf('I', 'L', 'O', 'U')

        val offenders = produced.flatMap { it.toList() }.filter { it in excluded }.toSet()
        assertTrue(
            "a code read down a bad line must not contain $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `an excluded letter is read as the digit it can only have meant`() {
        // O is never issued, so an O can only be a zero; same for I and L as one.
        assertEquals("0CDEFG", normaliseInviteCode("OCDEFG"))
        assertEquals("1CDEFG", normaliseInviteCode("ICDEFG"))
        assertEquals("1CDEFG", normaliseInviteCode("LCDEFG"))
        assertTrue(isWellFormedInviteCode(normaliseInviteCode("OCDEFG")))
    }

    @Test
    fun `a letter that is genuinely not in the alphabet is refused`() {
        assertFalse("U is dropped and means nothing else", isWellFormedInviteCode("UCDEFG"))
    }

    @Test
    fun `codes are not sequential or predictable from one another`() {
        val random = SecureRandom()
        val produced = buildSet { repeat(1_000) { add(generateInviteCode { random.nextInt(it) }) } }

        assertTrue(
            "a guessable code lets anyone walk into somebody's business",
            produced.size > 990,
        )
    }

    @Test
    fun `case and the spaces a code is read out in do not matter`() {
        assertEquals("ACDEFG", normaliseInviteCode("acd efg"))
        assertEquals("ACDEFG", normaliseInviteCode("ACD-EFG"))
        assertEquals("ACDEFG", normaliseInviteCode("  AcDeFg  "))
    }

    @Test
    fun `a code of the wrong length is not well formed`() {
        assertFalse(isWellFormedInviteCode("ACDEF"))
        assertFalse(isWellFormedInviteCode("ACDEFGH"))
        assertFalse(isWellFormedInviteCode(""))
    }

    @Test
    fun `typing past the end does not run on`() {
        assertEquals("ACDEFG", normaliseInviteCode("ACDEFGHJKL"))
    }

    @Test
    fun `the generator draws from the whole alphabet`() {
        // Pins the alphabet: every index must map to a distinct usable character.
        val everyIndex = (0 until 32).map { i -> generateInviteCode { i }.first() }

        assertEquals("no index collides or falls outside", 32, everyIndex.toSet().size)
        assertTrue(everyIndex.all { it.isLetterOrDigit() })
    }
}

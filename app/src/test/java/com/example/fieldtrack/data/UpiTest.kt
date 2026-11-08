package com.example.fieldtrack.data

import com.example.fieldtrack.data.payments.Upi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The UPI link every UPI app reads, and what they say back. */
class UpiTest {

    @Test fun `a link carries the payee, the amount in rupees and paise, and the note`() {
        assertEquals(
            "upi://pay?pa=harpal@okaxis&pn=Harpal%20Agro%20Works&am=4000.50&cu=INR&tn=Ramesh%20Singh",
            Upi.payLink("harpal@okaxis", "Harpal Agro Works", 400_050, note = "Ramesh Singh"),
        )
        assertEquals("no amount leaves it to the payer", "upi://pay?pa=a@b&pn=a%40b&cu=INR", Upi.payLink("a@b", "", null))
        assertTrue(Upi.payLink("x@ybl", "Jaswant & Sons", 5, null).contains("pn=Jaswant%20%26%20Sons&am=0.05"))
    }

    @Test fun `a UPI id is a name, an at, and a bank's handle`() {
        assertTrue(Upi.looksValid("harpal.singh@okaxis"))
        assertTrue(Upi.looksValid(" 9876543210@ybl "))
        assertFalse(Upi.looksValid("harpal"))
        assertFalse(Upi.looksValid("@okaxis"))
        assertFalse(Upi.looksValid(null))
    }

    @Test fun `what the UPI app said is read whatever its case, and anything else is asked about`() {
        val ok = Upi.parseReply("txnId=ABC123&responseCode=00&Status=SUCCESS&txnRef=r1")
        assertEquals(Upi.Status.Success, ok.status)
        assertEquals("ABC123", ok.transactionId)
        assertEquals(Upi.Status.Failure, Upi.parseReply("status=failure&txnId=null").status)
        assertNull(Upi.parseReply("status=failure&txnId=null").transactionId)
        assertEquals(Upi.Status.Submitted, Upi.parseReply("Status=SUBMITTED").status)
        assertEquals(Upi.Status.Unknown, Upi.parseReply(null).status)
        assertEquals(Upi.Status.Unknown, Upi.parseReply("nonsense").status)
    }
}

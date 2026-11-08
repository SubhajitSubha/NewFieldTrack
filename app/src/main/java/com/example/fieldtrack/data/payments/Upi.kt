package com.example.fieldtrack.data.payments

import java.net.URLEncoder

/**
 * UPI, India's instant bank-to-bank payments, spoken through the link every UPI app understands.
 *
 * No payment company and no fee: the link opens whichever UPI app is on the phone (or is read
 * from a QR code by one) with the payee and the amount filled in, and the person paying
 * confirms it there with their own PIN. Nothing here moves money by itself.
 */
object Upi {

    private val VPA = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$")

    /** Whether [id] looks like a UPI id: a name, an @, and a bank's handle. */
    fun looksValid(id: String?): Boolean = id != null && VPA.matches(id.trim())

    /**
     * The link that asks for [amountMinor] (in paise) to be paid to [id], under [payeeName],
     * with [note] shown to the payer. No amount leaves it for the payer to type.
     */
    fun payLink(id: String, payeeName: String, amountMinor: Long?, note: String? = null, ref: String? = null): String {
        val parts = buildList {
            // The @ stays as it is: the specification writes the address plain, and not every app decodes it.
            add("pa=" + enc(id.trim()).replace("%40", "@"))
            add("pn=" + enc(payeeName.trim().ifBlank { id.trim() }))
            if (amountMinor != null && amountMinor > 0) add("am=" + "%d.%02d".format(amountMinor / 100, amountMinor % 100))
            add("cu=INR")
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { add("tn=" + enc(it.take(50))) }
            ref?.let { add("tr=" + enc(it)) }
        }
        return "upi://pay?" + parts.joinToString("&")
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** What a UPI app said when it handed back to us. */
    enum class Status { Success, Submitted, Failure, Unknown }

    data class Outcome(val status: Status, val transactionId: String?)

    /**
     * Reads the reply a UPI app returns, such as `txnId=…&responseCode=00&Status=SUCCESS&txnRef=…`.
     * Apps differ in case and in which keys they send; anything unreadable is [Status.Unknown],
     * and the person is asked rather than told.
     */
    fun parseReply(reply: String?): Outcome {
        if (reply.isNullOrBlank()) return Outcome(Status.Unknown, null)
        val fields = reply.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim().lowercase() to part.substring(i + 1).trim()
        }.toMap()
        val status = when (fields["status"]?.uppercase()) {
            "SUCCESS" -> Status.Success
            "SUBMITTED", "PENDING" -> Status.Submitted
            "FAILURE", "FAILED" -> Status.Failure
            else -> Status.Unknown
        }
        val txn = fields["txnid"] ?: fields["approvalrefno"] ?: fields["txnref"]
        return Outcome(status, txn?.takeIf { it.isNotBlank() && it != "null" })
    }
}

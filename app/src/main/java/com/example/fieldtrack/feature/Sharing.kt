package com.example.fieldtrack.feature

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.fieldtrack.data.auth.PhoneNumbers
import java.net.URLEncoder

/**
 * Getting a message to a customer with the least fuss.
 *
 * Where the customer's number is known and WhatsApp is on the phone, the message opens
 * in a chat with that customer, ready to send. Otherwise, or when WhatsApp is not there,
 * it goes to the phone's own share sheet, which reaches whatever the owner uses. Both are
 * the platform's own intents; nothing is sent until the person taps send.
 */
object Sharing {

    /** The `wa.me` address of a number: its digits with the country code, no plus. */
    fun whatsAppLink(phone: String?, text: String): String? {
        val digits = PhoneNumbers.normalize(phone).removePrefix("+")
        if (digits.isEmpty()) return null
        return "https://wa.me/$digits?text=${URLEncoder.encode(text, "UTF-8")}"
    }

    fun message(context: Context, phone: String?, text: String) {
        val link = whatsAppLink(phone, text)
        if (link != null) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("com.whatsapp")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // No WhatsApp here: the share sheet below.
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

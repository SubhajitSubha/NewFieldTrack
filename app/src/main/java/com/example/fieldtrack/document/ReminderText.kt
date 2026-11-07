package com.example.fieldtrack.document

import android.content.Context
import com.example.fieldtrack.R
import com.example.fieldtrack.data.settings.AppLanguage
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.designsystem.format.money
import java.net.URLEncoder

/**
 * The message an owner sends a customer who owes money.
 *
 * It says who is writing, how much is due, how old the oldest part is, and where to pay:
 * the bank or mobile-money details the owner wrote down and, for a rupee business with a
 * UPI id, a link that opens the customer's UPI app with the amount already filled in.
 * Written in the customer's own language when it is known and the app has it, which is
 * not necessarily the language the owner reads.
 */
object ReminderText {

    /**
     * A `upi://pay` link for the amount, or null when there is nothing to pay or no UPI id.
     *
     * UPI is only the rupee's: it is not offered for any other currency. The amount is in
     * whole rupees and paise as a decimal, which is what the link wants.
     */
    fun upiLink(upiId: String?, businessName: String, balanceMinor: Long, style: MoneyStyle): String? {
        val id = upiId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (style.currencyCode != "INR" || balanceMinor <= 0) return null
        val amount = "%d.%02d".format(balanceMinor / 100, balanceMinor % 100)
        fun enc(text: String) = URLEncoder.encode(text, "UTF-8")
        return "upi://pay?pa=${enc(id)}&pn=${enc(businessName)}&am=$amount&cu=INR"
    }

    fun build(
        context: Context,
        language: String?,
        businessName: String,
        balanceMinor: Long,
        style: MoneyStyle,
        oldestDays: Int?,
        payDetails: String?,
        upiId: String?,
    ): String {
        val read = AppLanguage.contextFor(context, language)
        return buildString {
            append(read.getString(R.string.reminder_due, businessName, money(balanceMinor, style)))
            if (oldestDays != null && oldestDays >= 1) {
                append("\n").append(read.getString(R.string.reminder_oldest, oldestDays))
            }
            val details = payDetails?.trim()?.takeIf { it.isNotEmpty() }
            if (details != null) append("\n\n").append(read.getString(R.string.reminder_pay_to)).append("\n").append(details)
            upiLink(upiId, businessName, balanceMinor, style)?.let {
                if (details == null) append("\n\n").append(read.getString(R.string.reminder_pay_to)).append("\n").append(upiId?.trim())
                append("\n").append(it)
            }
            append("\n\n").append(read.getString(R.string.reminder_thanks))
        }
    }
}

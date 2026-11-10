package com.example.fieldtrack.document

import android.content.Context
import com.example.fieldtrack.R
import com.example.fieldtrack.data.billing.Quote
import com.example.fieldtrack.data.settings.AppLanguage
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.designsystem.format.money

/**
 * What a farmer is sent about a finished job: what was done, on which day, what it came to
 * line by line, and where to pay. Short on purpose, for a phone screen and a chat window.
 * Written in the customer's language when it is known, which need not be the owner's.
 */
object JobSummaryText {

    fun build(
        context: Context,
        language: String?,
        businessName: String,
        workTitle: String,
        measureLabel: String?,
        dateLabel: String,
        quote: Quote?,
        totalMinor: Long,
        style: MoneyStyle,
        payDetails: String?,
    ): String {
        val read = AppLanguage.contextFor(context, language)
        return buildString {
            append(read.getString(R.string.job_summary_title, businessName))
            append("\n").append(listOfNotNull(workTitle, measureLabel).joinToString(" · "))
            append("\n").append(dateLabel)
            if (quote != null && quote.hasExtras) {
                append("\n\n").append(read.getString(R.string.job_summary_work, money(quote.workMinor, style)))
                if (quote.minimumTopUpMinor > 0) append("\n").append(read.getString(R.string.job_summary_minimum, money(quote.minimumTopUpMinor, style)))
                if (quote.arrivalMinor > 0) append("\n").append(read.getString(R.string.job_summary_arrival, money(quote.arrivalMinor, style)))
                if (quote.waitingMinor > 0) append("\n").append(read.getString(R.string.job_summary_waiting, money(quote.waitingMinor, style)))
                if (quote.fuelMinor > 0) append("\n").append(read.getString(R.string.job_summary_diesel, money(quote.fuelMinor, style)))
                if (quote.discountMinor > 0) append("\n").append(read.getString(R.string.job_summary_discount, money(quote.discountMinor, style)))
            }
            append("\n\n").append(read.getString(R.string.job_summary_total, money(totalMinor, style)))
            payDetails?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("\n\n").append(read.getString(R.string.reminder_pay_to)).append("\n").append(it)
            }
        }
    }
}

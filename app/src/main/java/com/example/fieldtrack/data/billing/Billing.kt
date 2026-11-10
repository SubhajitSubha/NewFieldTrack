package com.example.fieldtrack.data.billing

import com.example.fieldtrack.data.db.BillingMode

/**
 * What work comes to, in one place.
 *
 * A job's bill, the amount a correction would change it to, a half-finished
 * hourly job and the price calculator all ask here, so no two of them can ever
 * round the same work to different rupees. The one that matters is the
 * calculator: it exists to tell somebody what a bill will say before there is a
 * bill, and an answer that differs by a rupee from the bill it predicted is
 * worse than no answer.
 *
 * Trip work is the exception and is not here: each trip carries the price it was
 * given when it was made, so a job's trip total is a sum of what happened, not a
 * product. Asked "what would five trips at ₹800 be?", this is the answer; asked
 * "what is this job's bill?", the job is.
 */
object Billing {

    /**
     * [quantity] of work at [rateMinor] a unit, rounded to the nearest [stepMinor].
     *
     * The step is the business's own: 100 in a rupee business that never deals in
     * paise (so a bill is always whole rupees), 1 where cents count. It travels with
     * the job, never read from the business's current setting, so changing the
     * setting later cannot move a bill that was already made.
     *
     * A fixed price is the rate itself, whatever the quantity: an agreed lump sum
     * does not grow with the acres.
     */
    fun amount(mode: BillingMode, quantity: Double, rateMinor: Long, stepMinor: Long): Long {
        if (mode == BillingMode.Fixed) return rateMinor
        val step = stepMinor.coerceAtLeast(1)
        return Math.round(quantity * rateMinor / step) * step
    }
}

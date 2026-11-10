package com.example.fieldtrack.data.billing

/**
 * A job's bill, line by line, before or after it is finished.
 *
 * [workMinor] is what the work itself comes to. [minimumTopUpMinor] is what a minimum
 * charge adds when the work comes to less. The rest are the extras. [totalMinor] is what the
 * customer is billed, and is the same figure the job reports as its amount: the lines exist
 * so a bill can say where the number came from.
 */
data class Quote(
    val workMinor: Long,
    val minimumTopUpMinor: Long,
    val arrivalMinor: Long,
    val waitingMinor: Long,
    val fuelMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
) {
    /** Whether anything but the work is on this bill. */
    val hasExtras: Boolean
        get() = minimumTopUpMinor != 0L || arrivalMinor != 0L || waitingMinor != 0L ||
            fuelMinor != 0L || discountMinor != 0L
}

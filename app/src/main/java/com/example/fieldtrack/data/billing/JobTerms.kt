package com.example.fieldtrack.data.billing

import com.example.fieldtrack.data.db.FuelTerms
import com.example.fieldtrack.data.db.entity.JobEntity

/**
 * What a job is agreed on beyond its rate, and who and what is on it.
 *
 * Gathered into one thing so the places that make a job (starting work, typing in work
 * done earlier) take it as one argument and cannot forget half of it. Everything defaults
 * to nothing, which bills a job exactly as a plain rate would.
 */
data class JobTerms(
    /** The least the job is billed, whatever the work comes to. */
    val minimumMinor: Long = 0,
    /** A flat charge for getting the machine to the field. */
    val arrivalMinor: Long = 0,
    val fuelTerms: FuelTerms = FuelTerms.Included,
    /** An hour spent waiting for the customer, billed at this; 0 means waiting is not billed. */
    val waitingRateMinor: Long = 0,
    /** A second machine on the job, such as the implement behind a tractor. */
    val implementMachineId: String? = null,
    /** More people on the job than the operator, as person ids joined by commas. */
    val coOperatorIds: String? = null,
) {
    val isPlain: Boolean
        get() = this == JobTerms()

    fun applyTo(job: JobEntity): JobEntity = job.copy(
        minimumMinor = minimumMinor.coerceAtLeast(0),
        arrivalMinor = arrivalMinor.coerceAtLeast(0),
        fuelTerms = fuelTerms,
        waitingRateMinor = waitingRateMinor.coerceAtLeast(0),
        implementMachineId = implementMachineId?.ifBlank { null },
        coOperatorIds = coOperatorIds?.ifBlank { null },
    )
}

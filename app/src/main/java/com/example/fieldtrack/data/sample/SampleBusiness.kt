package com.example.fieldtrack.data.sample

import android.content.Context
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.designsystem.format.Money
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The currencies where a day's tractor work is a few tens of units rather than hundreds. */
private val SMALL_UNITS = setOf("USD", "EUR", "GBP", "AUD", "CAD", "NZD", "CHF", "SGD", "OMR", "KWD", "BHD", "JOD")

/** A rate that looks ordinary in [code]: the same sample, in the currency the person works in. */
internal fun sampleRateMajor(code: String): Long = if (code in SMALL_UNITS) 40L else 800L

/**
 * A practice business, so a person can look around before putting in real records.
 *
 * It is an ordinary business on this phone and nothing else: it is marked before a single
 * row is made, and anything it queues is taken as sent and goes nowhere. It has its own
 * customers, machines, bills and a payment, in the person's own currency, and can be
 * removed in one tap, with every row it owns.
 */
class SampleBusiness(private val app: FieldFlowApplication, private val context: Context) {

    /** Makes the practice business, or goes back to the one already made, and shows it. */
    suspend fun start(): Boolean = withContext(NonCancellable) {
        // Not to be cut short: the moment the practice business becomes the one on screen the
        // screen that asked for it is rebuilt and its coroutine cancelled, which left a business
        // with two customers and nothing else.
        begin()
    }

    private suspend fun begin(): Boolean {
        val session = app.sessionStore.current() ?: return false
        val existing = app.sessionStore.sampleOrgId()
        if (existing != null) {
            app.sessionStore.switchTo(existing)
            return true
        }
        val orgId = app.sessionStore.startAnotherBusiness(
            businessName = context.getString(R.string.sample_business_name),
            landUnit = session.landUnit,
            currencyCode = session.currencyCode,
            moneyStepMinor = session.moneyStepMinor,
            fiscalStartMonth = session.fiscalStartMonth,
            sample = true,
        ) ?: return false
        seed(orgId, session.personId, session.currencyCode, session.landUnit)
        return true
    }

    private suspend fun seed(orgId: String, personId: String, code: String, land: MeasureUnit) {
        val dir = app.directoryRepository
        val work = app.workRepository
        val ledger = app.ledgerRepository
        val unit = Money.style.unit
        val rate = sampleRateMajor(code) * unit
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()

        val first = dir.addCustomer(orgId, context.getString(R.string.sample_customer_1), null, null)
        val second = dir.addCustomer(orgId, context.getString(R.string.sample_customer_2), null, null, openingBalanceMinor = 3 * rate)
        dir.addCustomer(orgId, context.getString(R.string.sample_customer_3), null, null)
        val tractor = dir.addMachine(orgId, context.getString(R.string.sample_tractor), MachineKind.Tractor)
        val harvester = dir.addMachine(orgId, context.getString(R.string.sample_harvester), MachineKind.Harvester)
        dir.setRate(orgId, "Ploughing", BillingMode.PerAcre, land, rate)

        val a = work.recordPastWork(
            orgId, first, "Ploughing", BillingMode.PerAcre, land, rate, 4.0,
            workedAt = now - 6 * day, startedAt = now - 6 * day, completedAt = now - 6 * day + 4 * 3_600_000,
            machineId = tractor, operatorPersonId = personId,
        )
        work.complete(orgId, a, personId)
        ledger.recordPayment(orgId, first, 2 * rate, PaymentMethod.Cash, byPersonId = personId, jobId = a)

        val b = work.recordPastWork(
            orgId, second, "Harvesting", BillingMode.PerAcre, land, rate * 2, 3.0,
            workedAt = now - 3 * day, startedAt = now - 3 * day, completedAt = now - 3 * day + 5 * 3_600_000,
            machineId = harvester, operatorPersonId = personId,
        )
        work.complete(orgId, b, personId)

        val c = work.recordPastWork(
            orgId, first, "Ploughing", BillingMode.PerAcre, land, rate, 2.5,
            workedAt = now - day, startedAt = now - day, completedAt = now - day + 3 * 3_600_000,
            machineId = tractor, operatorPersonId = personId,
        )
        work.complete(orgId, c, personId)
    }

    /** Takes it away, and everything it made. */
    suspend fun remove() = app.sessionStore.removeSample()
}

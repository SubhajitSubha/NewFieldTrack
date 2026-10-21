package com.example.fieldtrack.document

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.formatBillNumber
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.data.auth.formatIndianPhone

/**
 * Turning rows out of the database into something printable.
 *
 * Pure: give it entities and strings and it hands back a [Document]. Everything that
 * needs a Context — dates in the device's locale, the words for a unit, the rendering
 * itself — is passed in or happens elsewhere, so what a customer ends up reading can
 * be asserted in a test rather than inspected on a handset.
 */
object Documents {

    fun letterheadOf(org: OrgEntity?): Letterhead = Letterhead(
        businessName = org?.name.orEmpty(),
        addressLine = org?.addressLine?.ifBlank { null },
        phone = org?.phone?.ifBlank { null },
        gstin = org?.gstin?.ifBlank { null },
        upiId = org?.upiId?.ifBlank { null },
    )

    fun partyOf(customer: CustomerEntity?): Party = Party(
        name = customer?.name.orEmpty(),
        addressLine = customer?.village?.ifBlank { null },
        phone = customer?.phone?.ifBlank { null }?.let { formatIndianPhone(it) },
    )

    /**
     * One job's bill.
     *
     * [previousBalanceRupees] is what was owed before this job was billed, which is
     * only meaningful as a snapshot — read it before completing the job, never after.
     */
    fun bill(
        org: OrgEntity?,
        customer: CustomerEntity?,
        billFy: String?,
        billSeq: Int?,
        dateLabel: String,
        workedOnLabel: String?,
        machineLabel: String?,
        workTitle: String,
        workDetail: String?,
        measureLabel: String?,
        rateLabel: String?,
        jobAmountRupees: Long,
        previousBalanceRupees: Long,
        payments: List<PaymentLine>,
        balanceRupees: Long,
    ): Document.Bill = Document.Bill(
        letterhead = letterheadOf(org),
        customer = partyOf(customer),
        billNumber = billFy?.let { fy -> billSeq?.let { formatBillNumber(fy, it) } },
        dateLabel = dateLabel,
        workedOnLabel = workedOnLabel,
        machineLabel = machineLabel,
        lines = listOf(
            WorkLine(
                title = workTitle,
                detail = workDetail,
                measure = measureLabel,
                rate = rateLabel,
                amountRupees = jobAmountRupees,
            )
        ),
        previousBalanceRupees = previousBalanceRupees,
        payments = payments,
        balanceRupees = balanceRupees,
        balanceInWords = rupeesInWords(balanceRupees),
    )

    /**
     * A period of an account as a running khata.
     *
     * [opening] is the balance carried into the period, so the closing figure on the
     * document reconciles with the one on the customer's screen. Entries arrive
     * oldest first.
     */
    fun statement(
        org: OrgEntity?,
        customer: CustomerEntity?,
        periodLabel: String,
        printedLabel: String,
        openingRupees: Long,
        entries: List<LedgerEntryEntity>,
        describe: (LedgerEntryEntity) -> String,
        dateOf: (Long) -> String,
        openingLabel: String = "Balance brought forward",
        paymentsOnly: Boolean = false,
    ): Document.Statement {
        var running = if (paymentsOnly) 0L else openingRupees
        var runningReceived = 0L
        val lines = mutableListOf<StatementLine>()

        if (!paymentsOnly && (openingRupees != 0L || entries.isNotEmpty())) {
            lines += StatementLine(
                date = entries.firstOrNull()?.let { dateOf(it.atMillis) }.orEmpty(),
                detail = openingLabel,
                chargedRupees = null,
                paidRupees = null,
                balanceAfterRupees = if (paymentsOnly) 0L else openingRupees,
                isBroughtForward = true,
            )
        }

        var billed = 0L
        var received = 0L
        entries.forEach { entry ->
            val isDebit = entry.kind == LedgerKind.Charge || entry.kind == LedgerKind.Opening
            if (isDebit) {
                running += entry.amountRupees
                billed += entry.amountRupees
            } else {
                running -= entry.amountRupees
                received += entry.amountRupees
                runningReceived += entry.amountRupees
            }
            lines += StatementLine(
                date = dateOf(entry.atMillis),
                detail = describe(entry),
                chargedRupees = entry.amountRupees.takeIf { isDebit },
                paidRupees = entry.amountRupees.takeIf { !isDebit },
                balanceAfterRupees = if (paymentsOnly) runningReceived else running,
            )
        }

        return Document.Statement(
            letterhead = letterheadOf(org),
            customer = partyOf(customer),
            periodLabel = periodLabel,
            printedLabel = printedLabel,
            openingRupees = openingRupees,
            billedRupees = billed,
            receivedRupees = received,
            closingRupees = if (paymentsOnly) received else running,
            closingInWords = rupeesInWords(if (paymentsOnly) received else running),
            lines = lines,
            paymentsOnly = paymentsOnly,
        )
    }

    /**
     * What is still owed, and nothing else.
     *
     * Carries a line crediting what has been paid. A demand that ignores the money
     * someone already handed over is how a conversation about ₹7,400 becomes an
     * argument about respect.
     */
    fun dues(
        org: OrgEntity?,
        customer: CustomerEntity?,
        asOnLabel: String,
        lines: List<DueLine>,
        receivedRupees: Long,
        billedRupees: Long,
        sinceLabel: String?,
        settledBeforeLabel: String?,
    ): Document.Dues {
        val total = lines.sumOf { it.amountRupees }
        val note = when {
            receivedRupees <= 0L -> null
            sinceLabel == null -> "${rupees(receivedRupees)} received so far."
            else -> buildString {
                append("${rupees(receivedRupees)} received since $sinceLabel")
                if (billedRupees > 0) append(", against ${rupees(billedRupees)} of work")
                append(".")
                if (settledBeforeLabel != null) {
                    append(" Everything billed before $settledBeforeLabel is paid in full — thank you.")
                }
            }
        }
        return Document.Dues(
            letterhead = letterheadOf(org),
            customer = partyOf(customer),
            asOnLabel = asOnLabel,
            lines = lines,
            totalRupees = total,
            totalInWords = rupeesInWords(total),
            receivedNote = note,
        )
    }
}

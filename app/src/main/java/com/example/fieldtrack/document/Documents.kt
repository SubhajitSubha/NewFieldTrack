package com.example.fieldtrack.document

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.moneyStyle
import com.example.fieldtrack.data.db.formatBillNumber
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.data.auth.formatPhone

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
        payDetails = org?.payDetails?.ifBlank { null },
        taxName = org?.taxName?.ifBlank { null },
        taxPercentBps = org?.taxPercentBps,
        businessName = org?.name.orEmpty(),
        addressLine = org?.addressLine?.ifBlank { null },
        phone = org?.phone?.ifBlank { null },
        gstin = org?.gstin?.ifBlank { null },
        upiId = org?.upiId?.ifBlank { null },
    )

    fun partyOf(customer: CustomerEntity?): Party = Party(
        name = customer?.name.orEmpty(),
        addressLine = customer?.village?.ifBlank { null },
        phone = customer?.phone?.ifBlank { null }?.let { formatPhone(it) },
    )

    /**
     * One job's bill.
     *
     * [previousBalanceMinor] is what was owed before this job was billed, which is
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
        jobAmountMinor: Long,
        previousBalanceMinor: Long,
        payments: List<PaymentLine>,
        balanceMinor: Long,
    ): Document.Bill = Document.Bill(
        letterhead = letterheadOf(org),
        style = org.moneyStyle(),
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
                amountMinor = jobAmountMinor,
            )
        ),
        previousBalanceMinor = previousBalanceMinor,
        payments = payments,
        balanceMinor = balanceMinor,
        balanceInWords = amountInWords(balanceMinor, org.moneyStyle()),
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
        openingMinor: Long,
        entries: List<LedgerEntryEntity>,
        describe: (LedgerEntryEntity) -> String,
        dateOf: (Long) -> String,
        openingLabel: String = "Balance brought forward",
        paymentsOnly: Boolean = false,
    ): Document.Statement {
        var running = if (paymentsOnly) 0L else openingMinor
        var runningReceived = 0L
        val lines = mutableListOf<StatementLine>()

        if (!paymentsOnly && (openingMinor != 0L || entries.isNotEmpty())) {
            lines += StatementLine(
                date = entries.firstOrNull()?.let { dateOf(it.atMillis) }.orEmpty(),
                detail = openingLabel,
                chargedMinor = null,
                paidMinor = null,
                balanceAfterMinor = if (paymentsOnly) 0L else openingMinor,
                isBroughtForward = true,
            )
        }

        var billed = 0L
        var received = 0L
        entries.forEach { entry ->
            val isDebit = entry.kind == LedgerKind.Charge || entry.kind == LedgerKind.Opening
            if (isDebit) {
                running += entry.amountMinor
                billed += entry.amountMinor
            } else {
                running -= entry.amountMinor
                received += entry.amountMinor
                runningReceived += entry.amountMinor
            }
            lines += StatementLine(
                date = dateOf(entry.atMillis),
                detail = describe(entry),
                chargedMinor = entry.amountMinor.takeIf { isDebit },
                paidMinor = entry.amountMinor.takeIf { !isDebit },
                balanceAfterMinor = if (paymentsOnly) runningReceived else running,
            )
        }

        return Document.Statement(
            letterhead = letterheadOf(org),
        style = org.moneyStyle(),
            customer = partyOf(customer),
            periodLabel = periodLabel,
            printedLabel = printedLabel,
            openingMinor = openingMinor,
            billedMinor = billed,
            receivedMinor = received,
            closingMinor = if (paymentsOnly) received else running,
            closingInWords = amountInWords(if (paymentsOnly) received else running, org.moneyStyle()),
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
        receivedMinor: Long,
        billedMinor: Long,
        sinceLabel: String?,
        settledBeforeLabel: String?,
    ): Document.Dues {
        val total = lines.sumOf { it.amountMinor }
        val note = when {
            receivedMinor <= 0L -> null
            sinceLabel == null -> "${money(receivedMinor)} received so far."
            else -> buildString {
                append("${money(receivedMinor)} received since $sinceLabel")
                if (billedMinor > 0) append(", against ${money(billedMinor)} of work")
                append(".")
                if (settledBeforeLabel != null) {
                    append(" Everything billed before $settledBeforeLabel is paid in full — thank you.")
                }
            }
        }
        return Document.Dues(
            letterhead = letterheadOf(org),
        style = org.moneyStyle(),
            customer = partyOf(customer),
            asOnLabel = asOnLabel,
            lines = lines,
            totalMinor = total,
            totalInWords = amountInWords(total, org.moneyStyle()),
            receivedNote = note,
        )
    }
}

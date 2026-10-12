package com.example.fieldtrack.feature.customer

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.document.Document
import com.example.fieldtrack.document.DueLine
import com.example.fieldtrack.document.Documents
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.daysAgo

/**
 * Turns the chosen options into the document they describe.
 *
 * Lives in the UI layer because every word on the page is a resource — the name of a
 * payment method, the date format, the sentence crediting what has been paid. A
 * statement with the wrong word for "cheque" on it is worse than no statement.
 */
@Composable
fun statementDocumentFor(sources: StatementSources): Document {
    val state = sources.state
    val shown = entriesFor(sources.entries, state.content, state.startMillis, state.endMillis)
    val periodLabel = periodLabelOf(state)
    // Every line's wording is resolved here, because the builder below is plain
    // Kotlin and cannot reach a string resource.
    val described = shown.associate { it.id to describe(it) }
    val describeLine: (LedgerEntryEntity) -> String = { described[it.id].orEmpty() }
    val broughtForward = stringResource(R.string.statement_brought_forward)

    return when (state.content) {
        StatementContent.UnpaidOnly -> duesDocument(sources)
        StatementContent.PickedJobs -> pickedDocument(sources)
        StatementContent.PaymentsOnly -> Documents.statement(
            org = sources.org,
            customer = sources.customer,
            periodLabel = periodLabel,
            printedLabel = dayMonthYear(System.currentTimeMillis()),
            openingRupees = 0,
            entries = shown,
            describe = describeLine,
            dateOf = ::dayMonthYear,
            paymentsOnly = true,
        )
        StatementContent.Everything -> Documents.statement(
            org = sources.org,
            customer = sources.customer,
            periodLabel = periodLabel,
            printedLabel = dayMonthYear(System.currentTimeMillis()),
            openingRupees = balanceBefore(sources.entries, state.startMillis),
            entries = shown,
            describe = describeLine,
            dateOf = ::dayMonthYear,
            openingLabel = broughtForward,
        )
    }
}

@Composable
private fun duesDocument(sources: StatementSources): Document.Dues {
    val state = sources.state
    val due = outstandingCharges(sources.entries)
        .filter { state.startMillis == null || it.entry.atMillis >= state.startMillis }
        .filter { state.endMillis == null || it.entry.atMillis <= state.endMillis }

    val received = sources.entries
        .filter { it.kind == LedgerKind.Payment }
        .filter { state.startMillis == null || it.atMillis >= state.startMillis }
        .sumOf { it.amountRupees }
    val billed = sources.entries
        .filter { it.kind == LedgerKind.Charge || it.kind == LedgerKind.Opening }
        .filter { state.startMillis == null || it.atMillis >= state.startMillis }
        .sumOf { it.amountRupees }

    return Documents.dues(
        org = sources.org,
        customer = sources.customer,
        asOnLabel = dayMonthYear(System.currentTimeMillis()),
        lines = due.map { item ->
            DueLine(
                date = dayMonthYear(item.entry.atMillis),
                title = lineTitle(item.entry, sources.jobsById),
                detail = listOfNotNull(
                    rateNote(item.entry, sources.jobsById),
                    partPaidNote(item.entry.amountRupees, item.outstandingRupees),
                ).joinToString(" · ").ifBlank { null },
                age = daysAgo(item.entry.atMillis),
                amountRupees = item.outstandingRupees,
            )
        },
        receivedRupees = received,
        billedRupees = billed,
        sinceLabel = state.startMillis?.let { dayMonthYear(it) },
        // Only claim everything older is settled when something actually is.
        // Only claim older work is settled when there is older work on an earlier
        // DAY. Comparing instants would let a job billed four minutes before the
        // unpaid one satisfy "everything before 11 Oct is paid in full", which is
        // true to the millisecond and reads as a brush-off to the person owed money.
        settledBeforeLabel = due.firstOrNull()?.let { oldest ->
            val oldestDay = dayMonthYear(oldest.entry.atMillis)
            val settledEarlierDay = sources.entries.any {
                (it.kind == LedgerKind.Charge || it.kind == LedgerKind.Opening) &&
                    it.atMillis < oldest.entry.atMillis &&
                    dayMonthYear(it.atMillis) != oldestDay
            }
            oldestDay.takeIf { received > 0 && settledEarlierDay }
        },
    )
}

@Composable
private fun pickedDocument(sources: StatementSources): Document.Dues {
    val state = sources.state
    val picked = state.jobs.filter { it.entryId in state.pickedEntryIds }
    return Documents.dues(
        org = sources.org,
        customer = sources.customer,
        asOnLabel = dayMonthYear(System.currentTimeMillis()),
        lines = picked.sortedBy { it.atMillis }.map { job ->
            DueLine(
                date = dayMonthYear(job.atMillis),
                title = jobTitle(job.title, job.jobId, sources.jobsById),
                detail = listOfNotNull(
                    job.jobId?.let { rateNoteFor(sources.jobsById[it]) },
                    if (job.settled) stringResource(R.string.statement_paid_label) else null,
                ).joinToString(" · ").ifBlank { null },
                age = daysAgo(job.atMillis),
                amountRupees = job.amountRupees,
            )
        },
        receivedRupees = 0,
        billedRupees = 0,
        sinceLabel = null,
        settledBeforeLabel = null,
    )
}

/** "Rotavator · 3.5 Bigha" — the work and how much of it, as the job recorded it. */
@Composable
private fun lineTitle(entry: LedgerEntryEntity, jobs: Map<String, JobEntity>): String =
    jobTitle(entry.note.orEmpty(), entry.jobId, jobs)

@Composable
private fun jobTitle(note: String, jobId: String?, jobs: Map<String, JobEntity>): String {
    val base = note.ifBlank { stringResource(R.string.finish_this_job) }
    val job = jobId?.let { jobs[it] } ?: return base
    if (job.billingMode == BillingMode.Fixed || job.quantity <= 0.0) return base
    return "$base · ${quantity(job.quantity)} ${stringResource(job.unit.labelRes)}"
}

/** "₹1,000 per Bigha" — the agreed rate, which is what gets disputed. */
@Composable
private fun rateNote(entry: LedgerEntryEntity, jobs: Map<String, JobEntity>): String? =
    rateNoteFor(entry.jobId?.let { jobs[it] })

@Composable
private fun rateNoteFor(job: JobEntity?): String? {
    if (job == null || job.billingMode == BillingMode.Fixed) return null
    return stringResource(
        R.string.statement_rate_note,
        rupees(job.rateRupees),
        stringResource(job.unit.labelRes),
    )
}

/** "₹1,100 of ₹3,000 still to pay" — only when the charge is part paid. */
@Composable
private fun partPaidNote(charged: Long, outstanding: Long): String? =
    if (outstanding < charged) {
        stringResource(R.string.statement_part_paid, rupees(charged - outstanding), rupees(charged))
    } else null

/** What a khata line is called on a printed page. */
@Composable
private fun describe(entry: LedgerEntryEntity): String = when (entry.kind) {
    LedgerKind.Opening -> stringResource(R.string.ledger_opening_label)
    LedgerKind.Payment -> stringResource(
        R.string.statement_payment_line,
        stringResource(
            when (entry.method) {
                PaymentMethod.Upi -> R.string.method_upi
                PaymentMethod.Bank -> R.string.method_bank
                PaymentMethod.Cheque -> R.string.method_cheque
                else -> R.string.method_cash
            }
        ),
    )
    LedgerKind.Adjustment -> entry.note.orEmpty().ifBlank {
        stringResource(R.string.ledger_adjustment_label)
    }
    LedgerKind.Charge -> entry.note.orEmpty().ifBlank {
        stringResource(R.string.finish_this_job)
    }
}

@Composable
private fun periodLabelOf(state: StatementState): String {
    val end = state.endMillis ?: System.currentTimeMillis()
    return state.startMillis?.let { stringResource(R.string.statement_range, dayMonthYear(it), dayMonthYear(end)) }
        ?: stringResource(R.string.statement_period_label_all)
}

/**
 * The platform date range picker, for "pick dates".
 *
 * Deliberately the stock one: a farmer who has used any other Android app has already
 * met this calendar, and a hand-built one would be a new thing to learn for no gain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSheet(onDismiss: () -> Unit, onPicked: (Long, Long) -> Unit) {
    val pickerState = rememberDateRangePickerState()
    val start by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { pickerState.selectedStartDateMillis }
    }
    val end by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { pickerState.selectedEndDateMillis }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = start ?: return@TextButton
                    // A single tapped day means that day, not an empty range.
                    onPicked(from, end ?: (from + DAY_MILLIS - 1))
                },
                enabled = start != null,
            ) { Text(stringResource(R.string.finish_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DateRangePicker(state = pickerState)
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

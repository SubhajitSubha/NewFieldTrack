package com.example.fieldtrack.feature.customer

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which days a statement covers. */
enum class StatementPeriod { Season, FinancialYear, ThreeMonths, SinceSettled, Custom, Everything }

/** What goes on it. */
enum class StatementContent { Everything, UnpaidOnly, PaymentsOnly, PickedJobs }

/**
 * The first day of the crop season that is currently running.
 *
 * Kharif is sown from June and Rabi from November, so those are the two moments a
 * contractor's book effectively starts again. Regional and approximate — a farmer in
 * Kerala keeps a different calendar — which is why it sits in one function with a
 * name, rather than scattered through the screen that offers it.
 */
fun seasonStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        today.monthValue >= 11 -> LocalDate.of(today.year, 11, 1)
        today.monthValue >= 6 -> LocalDate.of(today.year, 6, 1)
        // January to May is the back half of the Rabi season that began in November.
        else -> LocalDate.of(today.year - 1, 11, 1)
    }
}

/** The first day of the financial year in progress, which starts in April. */
fun financialYearStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (today.monthValue >= 4) LocalDate.of(today.year, 4, 1)
    else LocalDate.of(today.year - 1, 4, 1)
}

/**
 * When this customer last cleared their account completely.
 *
 * Walks the khata oldest first and remembers the last moment the balance touched
 * zero. That moment is where a farmer would say "we settled up" — everything before
 * it is closed business and nobody wants it reprinted.
 *
 * Null when the account has never been square, in which case there is no such line to
 * draw and the caller should fall back to showing everything.
 */
fun lastSettledAt(entries: List<LedgerEntryEntity>): Long? {
    var running = 0L
    var settledAt: Long? = null
    entries.sortedBy { it.atMillis }.forEach { entry ->
        running += when (entry.kind) {
            LedgerKind.Charge, LedgerKind.Opening -> entry.amountRupees
            LedgerKind.Payment, LedgerKind.Adjustment -> -entry.amountRupees
        }
        if (running <= 0L) settledAt = entry.atMillis
    }
    return settledAt
}

/**
 * The first millisecond a [StatementPeriod] covers.
 *
 * Null means "from the beginning", which is what [StatementPeriod.Everything] asks
 * for and what a never-settled account falls back to.
 */
fun periodStart(
    period: StatementPeriod,
    now: Long,
    entries: List<LedgerEntryEntity>,
    customStart: Long? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? = when (period) {
    StatementPeriod.Season -> seasonStart(now, zone).atStartOfDay(zone).toInstant().toEpochMilli()
    StatementPeriod.FinancialYear ->
        financialYearStart(now, zone).atStartOfDay(zone).toInstant().toEpochMilli()
    StatementPeriod.ThreeMonths ->
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusMonths(3)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    // The settling entry itself belongs to the closed period, so start just after it.
    StatementPeriod.SinceSettled -> lastSettledAt(entries)?.plus(1)
    StatementPeriod.Custom -> customStart
    StatementPeriod.Everything -> null
}

/**
 * What a customer owed at the moment a period began.
 *
 * The opening figure on a statement. Everything dated before the start, netted off.
 */
fun balanceBefore(entries: List<LedgerEntryEntity>, startMillis: Long?): Long {
    if (startMillis == null) return 0L
    return entries.filter { it.atMillis < startMillis }.sumOf {
        when (it.kind) {
            LedgerKind.Charge, LedgerKind.Opening -> it.amountRupees
            LedgerKind.Payment, LedgerKind.Adjustment -> -it.amountRupees
        }
    }
}

/** The entries a period and a content choice between them select, oldest first. */
fun entriesFor(
    entries: List<LedgerEntryEntity>,
    content: StatementContent,
    startMillis: Long?,
    endMillis: Long?,
): List<LedgerEntryEntity> = entries
    .filter { startMillis == null || it.atMillis >= startMillis }
    .filter { endMillis == null || it.atMillis <= endMillis }
    .filter {
        when (content) {
            StatementContent.PaymentsOnly -> it.kind == LedgerKind.Payment
            else -> true
        }
    }
    .sortedBy { it.atMillis }

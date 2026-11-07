package com.example.fieldtrack.feature.customer

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.season.SeasonCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which days a statement covers. */
enum class StatementPeriod { Season, FinancialYear, ThreeMonths, SinceSettled, Custom, Everything }

/** What goes on it. */
enum class StatementContent { Everything, UnpaidOnly, PaymentsOnly, PickedJobs }

/** The first day of the financial year in progress, which starts in the business's own month. */
fun financialYearStart(
    now: Long,
    zone: ZoneId = BusinessClock.zone,
    startMonth: Int = BusinessClock.fiscalStartMonth,
): LocalDate {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (today.monthValue >= startMonth) LocalDate.of(today.year, startMonth, 1)
    else LocalDate.of(today.year - 1, startMonth, 1)
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
            LedgerKind.Charge, LedgerKind.Opening -> entry.amountMinor
            LedgerKind.Payment, LedgerKind.Adjustment -> -entry.amountMinor
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
    /**
     * Required, with no usual-dates default: this is the argument somebody forgets,
     * and a statement quietly starting from June in a business that sows in
     * September is the mistake the calendar exists to prevent.
     */
    seasons: SeasonCalendar,
    customStart: Long? = null,
    zone: ZoneId = BusinessClock.zone,
): Long? = when (period) {
    StatementPeriod.Season -> seasons.current(now, zone).startMillis(zone)
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
            LedgerKind.Charge, LedgerKind.Opening -> it.amountMinor
            LedgerKind.Payment, LedgerKind.Adjustment -> -it.amountMinor
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

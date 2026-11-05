package com.example.fieldtrack.feature.plan

import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val MINUTE = 60_000L

/**
 * The plan's own "today", and how this business reads its plan.
 *
 * Looked at again every minute, because the day turns over while a screen is open: a
 * driver who left the app up at midnight is looking at the same screen at five, and it
 * should be the new day's. Only a change of day is passed on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun FieldFlowApplication.observePlanToday(orgId: String): Flow<Pair<PlanSettings, Long>> =
    directoryRepository.observePlanSettings(orgId).flatMapLatest { settings ->
        flow {
            while (true) {
                emit(PlanDay.today(System.currentTimeMillis(), settings.dayStartHour))
                delay(MINUTE)
            }
        }.distinctUntilChanged().map { settings to it }
    }

/**
 * What this person is shown of the plan.
 *
 * Whoever plans sees all of it. Everybody else sees what is theirs and what nobody has
 * been given — unassigned work belongs to whoever turns up — unless the business has
 * said drivers may see everything.
 */
fun List<BookingRow>.visibleTo(s: Session, settings: PlanSettings): List<BookingRow> =
    if (Capability.PlanWork in s.can || settings.driversSeeAll) this
    else filter { it.booking.operatorPersonId.let { id -> id == null || id == s.personId } }

/** The plan in a line, for the homes: what is on today and tomorrow, and what was missed. */
data class PlanSummary(
    val loaded: Boolean = false,
    val canPlan: Boolean = false,
    val today: Int = 0,
    val tomorrow: Int = 0,
    /** Promised for an earlier day and never started: waiting for somebody to decide. */
    val missed: Int = 0,
) {
    val isEmpty: Boolean get() = today == 0 && tomorrow == 0 && missed == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
fun FieldFlowApplication.observePlanSummary(s: Session): Flow<PlanSummary> =
    observePlanToday(s.orgId).flatMapLatest { (settings, today) ->
        combine(
            planRepository.observeDays(s.orgId, today, today + 1),
            planRepository.observeOverdue(s.orgId, today),
        ) { days, overdue ->
            val coming = days.visibleTo(s, settings).filter(PlanDay::holds)
            PlanSummary(
                loaded = true,
                canPlan = Capability.PlanWork in s.can,
                today = coming.count { it.booking.day == today },
                tomorrow = coming.count { it.booking.day == today + 1 },
                missed = overdue.visibleTo(s, settings).size,
            )
        }
    }

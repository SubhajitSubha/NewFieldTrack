package com.example.fieldtrack.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.EntryMethod
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.customer.seasonStart
import com.example.fieldtrack.feature.startOfToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Calendar

/** How far back the screen is looking. */
enum class DriverPeriod { Week, Month, Season }

/** One job, as the driver's own list shows it. */
data class DriverWorkRow(
    val jobId: String,
    val customerName: String,
    val workType: String,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val workedAtMillis: Long,
    val machineKind: MachineKind,
    val live: Boolean,
    /** Typed in afterwards rather than timed, which the list says out loud. */
    val byHand: Boolean,
)

data class DriverJobsState(
    val loaded: Boolean = false,
    val period: DriverPeriod = DriverPeriod.Week,
    val businessName: String = "",
    val totalSeconds: Long = 0,
    val jobCount: Int = 0,
    val areaCovered: Double = 0.0,
    val areaUnit: MeasureUnit = MeasureUnit.Acre,
    /** Hours per day, Monday first, for the week bars. */
    val weekHours: List<Double> = List(7) { 0.0 },
    val jobs: List<DriverWorkRow> = emptyList(),
)

/**
 * A driver's own record of what they have done.
 *
 * The owner's jobs list is the yard's work, filtered by nothing. This is one
 * person's, and it is the only place they can answer "how many hours did I put in
 * this week" — the question behind being paid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverJobsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val period = MutableStateFlow(DriverPeriod.Week)

    val state: StateFlow<DriverJobsState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            period,
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.workRepository.observeForOperator(s.orgId, s.personId),
        ) { chosen, customers, machines, allJobs ->
            val now = System.currentTimeMillis()
            val from = chosen.startMillis(now)
            val names = customers.associate { it.id to it.name }
            val mine = allJobs
                .filter { it.workedAt >= from && it.status != JobStatus.Discarded }
                .sortedByDescending { it.workedAt }

            val rows = mine.map { job ->
                DriverWorkRow(
                    jobId = job.id,
                    customerName = names[job.customerId].orEmpty(),
                    workType = job.workType,
                    amountDone = job.quantity,
                    unit = job.unit,
                    elapsedSeconds = app.workRepository.elapsedSeconds(job),
                    workedAtMillis = job.workedAt,
                    machineKind = machines.firstOrNull { it.id == job.machineId }?.kind
                        ?: MachineKind.Other,
                    live = job.status == JobStatus.Running || job.status == JobStatus.Paused,
                    byHand = job.entryMethod == EntryMethod.Manual,
                )
            }

            DriverJobsState(
                loaded = true,
                period = chosen,
                businessName = s.businessName,
                totalSeconds = rows.sumOf { it.elapsedSeconds },
                jobCount = rows.count { !it.live },
                // Only ground. Adding acres to trips and quintals makes a number
                // that looks like an answer and is not one.
                areaCovered = mine.filter { it.unit.isLand }.sumOf { it.quantity },
                areaUnit = mine.firstOrNull { it.unit.isLand }?.unit ?: s.landUnit,
                weekHours = weekHours(rows, now),
                jobs = rows,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverJobsState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun setPeriod(value: DriverPeriod) { period.value = value }

    /**
     * Hours per weekday for the current week, Monday first.
     *
     * Always the current week, whatever period is selected. The bars are a shape of
     * a working week, not a second view of the total — stretching them over a season
     * would make seven columns stand for thirteen weeks each.
     */
    private fun weekHours(rows: List<DriverWorkRow>, now: Long): List<Double> {
        val weekStart = startOfWeek(now)
        val perDay = DoubleArray(7)
        rows.filter { it.workedAtMillis >= weekStart }.forEach { row ->
            val index = ((row.workedAtMillis - weekStart) / DAY_MILLIS).toInt()
            if (index in 0..6) perDay[index] += row.elapsedSeconds / 3600.0
        }
        return perDay.toList()
    }

    private fun DriverPeriod.startMillis(now: Long): Long = when (this) {
        DriverPeriod.Week -> startOfWeek(now)
        DriverPeriod.Month -> Calendar.getInstance().apply {
            timeInMillis = startOfToday(now)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        DriverPeriod.Season -> seasonStart(now)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun startOfWeek(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfToday(now)
        // Monday first: the week a contractor is paid by, not the calendar's Sunday.
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }.timeInMillis

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

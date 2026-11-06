package com.example.fieldtrack.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One booking, with the names a person reads it by. */
data class PlanItem(
    val row: BookingRow,
    val customerName: String,
    val fieldName: String?,
    val machineName: String?,
    val driverName: String?,
    val flags: List<PlanDay.Flag>,
    /** This person may start the work from here: it is for today or earlier and nobody has. */
    val canStart: Boolean,
) {
    val id: String get() = row.booking.id
}

data class PlanState(
    val loaded: Boolean = false,
    val canPlan: Boolean = false,
    val today: Long = 0,
    /** The day being looked at. */
    val day: Long = 0,
    /** Promised for an earlier day and never started, until somebody decides. */
    val overdue: List<PlanItem> = emptyList(),
    val items: List<PlanItem> = emptyList(),
    val freeMachines: List<String> = emptyList(),
    val freeDrivers: List<String> = emptyList(),
    /** When this phone last heard the others' plan, or null if it never has. */
    val updatedAt: Long? = null,
    /** Whether the phone has been out of touch long enough that the plan may be behind. */
    val stale: Boolean = false,
)

/**
 * The plan for a day, as this person is allowed to see it.
 *
 * Reads only. Booking, moving and cancelling happen on the booking's own screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val app: FieldFlowApplication) : ViewModel() {

    /** Null until somebody picks a day, which is "today" however the day turns over. */
    private val picked = MutableStateFlow<Long?>(null)

    private data class Names(
        val customers: Map<String, String>,
        val fields: Map<String, String>,
        val machines: List<com.example.fieldtrack.data.db.entity.MachineEntity>,
        val team: List<com.example.fieldtrack.data.db.entity.MembershipEntity>,
        val updatedAt: Long,
        val everyoneNames: Map<String, String>,
    )

    val state: StateFlow<PlanState> = app.sessionStore.session.flatMapLatest { s ->
        val names = combine(
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeAllFields(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            // Everyone ever on the team, so work booked for somebody since let go still
            // says who it was for, instead of reading as "anyone".
            combine(app.db.orgDao().observeTeam(s.orgId), app.db.orgDao().observeEveryone(s.orgId)) { t, e -> t to e },
            // Not distinct: it is read again every half minute so that "now" moves, and a
            // phone that never hears from the others never changes the value but must
            // still find out that six hours have gone by.
            flow {
                while (true) {
                    emit(app.pullWatermarks.lastSuccessAt(s.orgId))
                    delay(HEARD_EVERY_MILLIS)
                }
            },
        ) { customers, fields, machines, (team, everyone), updatedAt ->
            Names(
                customers.associate { it.id to it.name }, fields.associate { it.id to it.name },
                machines, team, updatedAt, everyone.associate { it.personId to it.displayName },
            )
        }

        app.observePlanToday(s.orgId).flatMapLatest { (settings, today) ->
            picked.flatMapLatest { chosen ->
                val day = chosen ?: today
                combine(
                    app.planRepository.observeDays(s.orgId, day, day),
                    app.planRepository.observeOverdue(s.orgId, today),
                    names,
                ) { days, overdue, n ->
                    val canPlan = Capability.PlanWork in s.can
                    val onDay = days.visibleTo(s, settings)
                    val machines = n.machines.associateBy { it.id }
                    val people = n.team.associate { it.personId to it.displayName }

                    fun item(row: BookingRow, sameDay: List<BookingRow>) = PlanItem(
                        row = row,
                        customerName = n.customers[row.booking.customerId].orEmpty(),
                        fieldName = row.booking.fieldId?.let { n.fields[it] },
                        machineName = row.booking.machineId?.let { machines[it]?.name },
                        driverName = row.booking.operatorPersonId?.let { n.everyoneNames[it] ?: people[it] },
                        // Only somebody who can change the plan has any use for a warning.
                        flags = if (canPlan) {
                            PlanDay.flags(row, sameDay, machines, people.keys, today)
                        } else {
                            emptyList()
                        },
                        canStart = row.booking.status == BookingStatus.Planned && row.jobStatus == null &&
                            row.booking.day <= today,
                    )

                    val now = System.currentTimeMillis()
                    PlanState(
                        loaded = true,
                        canPlan = canPlan,
                        today = today,
                        day = day,
                        overdue = PlanDay.inOrder(overdue.visibleTo(s, settings)).map { item(it, overdue) },
                        items = PlanDay.inOrder(onDay).map { item(it, days) },
                        freeMachines = if (canPlan) {
                            PlanDay.freeMachines(n.machines, days, isToday = day == today).map { it.name }
                        } else {
                            emptyList()
                        },
                        freeDrivers = if (canPlan) {
                            PlanDay.freeDrivers(n.team.map { it.personId }, days).mapNotNull { people[it] }
                        } else {
                            emptyList()
                        },
                        updatedAt = n.updatedAt.takeIf { it > 0 },
                        // Only worth saying where somebody else could have changed it.
                        stale = n.team.size > 1 && (n.updatedAt <= 0 || now - n.updatedAt > STALE_AFTER_MILLIS),
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun pick(day: Long) { picked.value = day }

    /** A day on from the one being looked at, or back with a negative [by]. */
    fun step(by: Int) { picked.value = state.value.day + by }

    private companion object {
        const val HEARD_EVERY_MILLIS = 30_000L
        const val STALE_AFTER_MILLIS = 6L * 60 * 60 * 1000
    }
}

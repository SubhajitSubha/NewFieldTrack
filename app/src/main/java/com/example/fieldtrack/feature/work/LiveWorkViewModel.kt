package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Someone else on the team this job could be handed to. */
data class Teammate(val personId: String, val displayName: String)

data class LiveWorkState(
    val loaded: Boolean = false,
    val customerName: String = "",
    val machineName: String? = null,
    val workType: String = "",
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val quantity: Double = 0.0,
    val rateRupees: Long = 0,
    val amountRupees: Long = 0,
    val elapsedSeconds: Long = 0,
    val running: Boolean = true,
    val startedAt: Long? = null,
    val distanceMetres: Long? = null,
    /** Fuel and breakdowns are recorded against a machine, so both need one. */
    val hasMachine: Boolean = false,
    /** A driver who may not see money may not set the rate either. */
    val canSeeMoney: Boolean = true,
    /** Everyone on the team except whoever is driving right now. */
    val teammates: List<Teammate> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LiveWorkViewModel(
    private val app: FieldFlowApplication,
    private val jobId: String,
) : ViewModel() {


    /**
     * Ticks once a second so the clock on screen moves.
     *
     * Elapsed time is derived from the job's `runningSince` rather than counted here,
     * so the figure stays correct across a process death — this only drives redraws.
     */
    private val tick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<LiveWorkState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.workRepository.observeJob(s.orgId, jobId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.db.orgDao().observeTeam(s.orgId),
            tick,
        ) { job, customers, machines, team, now ->
            if (job == null) return@combine LiveWorkState()
            val elapsed = app.workRepository.elapsedSeconds(job, now)
            // Hourly work bills from the clock, so the hero figure and the money
            // shown here must track the timer rather than a stored quantity.
            val quantity = if (job.billingMode == BillingMode.PerHour) elapsed / 3600.0
            else job.quantity
            // Only hourly work is computed here, because its quantity is a clock
            // that is still running. Everything else asks the job what it is worth —
            // recomputing quantity x rate would silently undo per-trip pricing.
            val amount = if (job.billingMode == BillingMode.PerHour) {
                Math.round(quantity * job.rateRupees)
            } else {
                job.amountRupees
            }

            LiveWorkState(
                loaded = true,
                customerName = customers.firstOrNull { it.id == job.customerId }?.name.orEmpty(),
                machineName = machines.firstOrNull { it.id == job.machineId }?.name,
                workType = job.workType,
                billingMode = job.billingMode,
                unit = job.unit,
                quantity = quantity,
                rateRupees = job.rateRupees,
                amountRupees = amount,
                elapsedSeconds = elapsed,
                running = job.status == JobStatus.Running,
                startedAt = job.startedAt,
                distanceMetres = job.distanceMetres,
                hasMachine = job.machineId != null,
                canSeeMoney = s.canSeeMoney,
                teammates = team
                    .filter { it.personId != job.operatorPersonId }
                    .map { Teammate(it.personId, it.displayName) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveWorkState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    // load() returns the session already in memory, so this is a read rather than a
    // round trip — it just also covers the case where a tap beats the first load.
    private fun withSession(block: suspend (Session) -> Unit) = viewModelScope.launch {
        app.sessionStore.load()?.let { block(it) }
    }

    fun increase() = withSession { app.workRepository.adjustQuantity(it.orgId, jobId, up = true) }

    fun decrease() = withSession { app.workRepository.adjustQuantity(it.orgId, jobId, up = false) }

    fun addTrip() = withSession { app.workRepository.addTrip(it.orgId, jobId) }

    fun undoTrip() = withSession { app.workRepository.removeLastTrip(it.orgId, jobId) }

    fun pause() = withSession { app.workRepository.pause(it.orgId, jobId) }

    fun resume() = withSession { app.workRepository.resume(it.orgId, jobId) }

    fun setQuantity(value: Double) = withSession {
        app.workRepository.setQuantity(it.orgId, jobId, value)
    }

    fun setRate(rupees: Long) = withSession { app.workRepository.setRate(it.orgId, jobId, rupees) }

    fun addNote(note: String) = withSession {
        if (note.isNotBlank()) app.workRepository.addNote(it.orgId, jobId, note.trim())
    }

    fun addFuel(litres: Double, costRupees: Long) = withSession {
        app.workRepository.addFuel(it.orgId, jobId, litres, costRupees, it.personId)
    }

    fun reportBreakdown(category: BreakdownCategory, note: String?) = withSession {
        app.workRepository.reportBreakdown(it.orgId, jobId, category, note, it.personId)
    }

    fun handOver(personId: String) = withSession {
        app.workRepository.handOver(it.orgId, jobId, personId)
    }

    /** Leaves the screen once the row is gone, so nothing is left pointing at it. */
    fun discard(onDiscarded: () -> Unit) = withSession {
        app.workRepository.discard(it.orgId, jobId)
        onDiscarded()
    }
}

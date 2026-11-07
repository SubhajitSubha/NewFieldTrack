package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.JobEventEntity
import com.example.fieldtrack.data.repository.ClockCheck
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
    val rateMinor: Long = 0,
    val amountMinor: Long = 0,
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
    /** When the clock last started, while it is running. */
    val runningSince: Long? = null,
    /** Time banked before this stretch, so a proposed stop can show the clock it leaves. */
    val bankedSeconds: Long = 0,
    /**
     * The last thing anybody recorded on this stretch — a trip, an acre, fuel.
     * The best evidence there is of when work actually stopped.
     */
    val lastEntryAt: Long? = null,
    /** The clock has run on past what a working day looks like, and nobody has said why. */
    val askAboutClock: Boolean = false,
    val orgId: String = "",
    /** The business asks its crew for proof of work. */
    val proofOn: Boolean = false,
    val proof: JobProof = JobProof(),
    /** The business's number, for the "call the owner" button of the help sheet. */
    val helpPhone: String? = null,
    /** Machines that could take the work over, the likeliest first. */
    val otherMachines: List<SwapChoice> = emptyList(),
)

/**
 * The machines that could take work over from [currentId], the likeliest first: free ones
 * before busy ones before broken ones, and the same sort of machine first within each.
 */
fun swapChoicesFor(
    machines: List<com.example.fieldtrack.data.db.entity.MachineEntity>,
    currentId: String?,
): List<SwapChoice> {
    val kind = machines.firstOrNull { it.id == currentId }?.kind
    return machines
        .filter { it.id != currentId && it.deletedAt == null }
        .map { SwapChoice(it.id, it.name, it.kind, it.status, sameKind = it.kind == kind) }
        .sortedWith(
            compareBy<SwapChoice> {
                when (it.status) {
                    com.example.fieldtrack.data.db.MachineStatus.Idle -> 0
                    com.example.fieldtrack.data.db.MachineStatus.Working -> 1
                    com.example.fieldtrack.data.db.MachineStatus.Down -> 2
                }
            }.thenBy { !it.sameKind }.thenBy { it.name.lowercase() }
        )
}

/** One machine offered as the one that takes a job over. */
data class SwapChoice(
    val id: String,
    val name: String,
    val kind: com.example.fieldtrack.data.db.MachineKind,
    val status: com.example.fieldtrack.data.db.MachineStatus,
    /** The same sort of machine as the one being replaced: the one most likely wanted. */
    val sameKind: Boolean,
)

/** Kinds of event that only say the clock is going, which prove no work was done. */
private val CLOCK_ONLY = setOf(
    JobEventType.Started, JobEventType.Resumed, JobEventType.Paused,
    JobEventType.HandedOver, JobEventType.ClockCorrected,
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

    private data class Clocked(
        val job: JobEntity?,
        val events: List<JobEventEntity>,
        val confirmed: Int,
        val proofOn: Boolean = false,
        val orgPhone: String? = null,
    )

    val state: StateFlow<LiveWorkState> = app.sessionStore.session.flatMapLatest { s ->
        val clocked = combine(
            app.workRepository.observeJob(s.orgId, jobId),
            app.workRepository.observeEvents(jobId),
            app.clockConfirmations.changes,
            app.db.orgDao().observeOrg(s.orgId),
        ) { job, events, confirmed, org -> Clocked(job, events, confirmed, org?.proofOfWork == true, org?.phone) }

        combine(
            clocked,
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.db.orgDao().observeTeam(s.orgId),
            tick,
        ) { c, customers, machines, team, now ->
            val job = c.job ?: return@combine LiveWorkState()
            val since = job.runningSince.takeIf { job.status == JobStatus.Running }
            val elapsed = app.workRepository.elapsedSeconds(job, now)
            // Hourly work bills from the clock, so the hero figure and the money
            // shown here must track the timer rather than a stored quantity.
            val quantity = app.workRepository.billableQuantity(job, now)
            val amount = app.workRepository.billableMinor(job, now)

            LiveWorkState(
                loaded = true,
                customerName = customers.firstOrNull { it.id == job.customerId }?.name.orEmpty(),
                machineName = machines.firstOrNull { it.id == job.machineId }?.name,
                workType = job.workType,
                billingMode = job.billingMode,
                unit = job.unit,
                quantity = quantity,
                rateMinor = job.rateMinor,
                amountMinor = amount,
                elapsedSeconds = elapsed,
                running = job.status == JobStatus.Running,
                startedAt = job.startedAt,
                distanceMetres = job.distanceMetres,
                hasMachine = job.machineId != null,
                canSeeMoney = s.canSeeMoney,
                teammates = team
                    .filter { it.personId != job.operatorPersonId }
                    .map { Teammate(it.personId, it.displayName) },
                runningSince = since,
                bankedSeconds = job.elapsedSeconds,
                lastEntryAt = since?.let { from ->
                    c.events
                        .filter { it.type !in CLOCK_ONLY && it.atMillis >= from }
                        .maxOfOrNull { it.atMillis }
                },
                askAboutClock = ClockCheck.looksForgotten(since, now) &&
                    !app.clockConfirmations.isConfirmed(job.id, since),
                orgId = s.orgId,
                proofOn = c.proofOn,
                helpPhone = c.orgPhone,
                otherMachines = swapChoicesFor(machines, job.machineId),
                proof = JobProof(
                    start = ProofEnd(job.meterStart, job.startPhotoId, job.startLat != null),
                    end = ProofEnd(job.meterEnd, job.endPhotoId, job.endLat != null),
                ),
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

    fun setRate(amountMinor: Long) = withSession { app.workRepository.setRate(it.orgId, jobId, amountMinor) }

    fun addNote(note: String) = withSession {
        if (note.isNotBlank()) app.workRepository.addNote(it.orgId, jobId, note.trim())
    }

    fun addFuel(litres: Double, costMinor: Long) = withSession {
        app.workRepository.addFuel(it.orgId, jobId, litres, costMinor, it.personId)
    }

    fun reportBreakdown(category: BreakdownCategory, note: String?) = withSession {
        app.workRepository.reportBreakdown(it.orgId, jobId, category, note, it.personId)
    }

    /** Another machine takes the work over. [onSwitched] gets the job the work now carries on in. */
    fun changeMachine(machineId: String, onSwitched: (String) -> Unit) = withSession {
        val next = app.workRepository.changeMachine(it.orgId, jobId, machineId, it.personId)
        onSwitched(next)
    }

    /** "I need help here": the owner and the planners are told at once. */
    fun askForHelp() = withSession { app.workRepository.askForHelp(it.orgId, jobId) }

    /** "Yes, still working": not asked again about this stretch of the clock. */
    fun confirmClock() {
        val since = state.value.runningSince ?: return
        app.clockConfirmations.confirm(jobId, since)
    }

    /** The clock stops where the work did, and the job waits paused to be finished. */
    fun stopClockAt(stoppedAt: Long) = withSession {
        runCatching { app.workRepository.stopClockAt(it.orgId, jobId, stoppedAt, it.personId) }
    }

    /** One note of proof, written on the job the moment it is made. */
    fun noteProof(note: ProofNote) = withSession {
        app.workRepository.recordProof(
            it.orgId, jobId, note.atFinish, note.meter, note.photoId, note.latitude, note.longitude,
        )
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

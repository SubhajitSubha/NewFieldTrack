package com.example.fieldtrack

import android.app.Application
import android.util.Log
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.repository.DirectoryRepository
import com.example.fieldtrack.data.repository.LedgerRepository
import com.example.fieldtrack.data.repository.PlanRepository
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.repository.WorkRepository
import com.example.fieldtrack.data.season.SeasonCalendar
import com.example.fieldtrack.data.session.SessionStore
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.example.fieldtrack.data.sync.FirestoreInviteLookup
import com.example.fieldtrack.data.sync.FirestoreRestore
import com.example.fieldtrack.data.sync.RemoteRestore
import com.example.fieldtrack.data.sync.PullSync
import com.example.fieldtrack.data.sync.PullWatermarks
import com.example.fieldtrack.data.sync.RestoreApplier
import com.example.fieldtrack.data.sync.FirestoreTransport
import com.example.fieldtrack.data.sync.InviteLookup
import com.example.fieldtrack.data.sync.PrefsSyncJournal
import com.example.fieldtrack.data.sync.SyncEngine
import com.example.fieldtrack.data.sync.SyncTransport
import com.example.fieldtrack.data.sync.SyncWorker
import com.example.fieldtrack.data.repository.ClockConfirmations
import com.example.fieldtrack.notify.ClockReminderWorker
import com.example.fieldtrack.notify.WorkClock
import com.example.fieldtrack.notify.WorkClockNotifier
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for FieldFlow.
 *
 * Initializes app-wide singletons:
 * - Database instance
 * - Repository instances
 *
 * Sync strategy:
 * - Push: SyncWorker (via WorkManager) pushes all 4 entity types
 * - Pull: Cold-start full fetch + real-time snapshot listeners for jobs/customers
 * - No separate backup needed — sync covers everything automatically
 */
class FieldFlowApplication : Application() {

    val db: FieldTrackDatabase by lazy { FieldTrackDatabase.build(this) }

    private val syncQueue: SyncQueue by lazy {
        // Every change asks for a send. WorkManager holds the request until there
        // is a network and then runs it, which is the whole point: the waiting is
        // the operating system's job, not the user's. Nobody should have to know
        // this screen exists, let alone press a button on it.
        SyncQueue(db.outboxDao(), onEnqueued = { SyncWorker.runNow(this) })
    }

    /**
     * Identifies this install when merging a job two people edited offline. Stored
     * rather than derived from hardware: Android's device identifiers are either
     * unavailable or user-resettable, and this only has to be stable and unique.
     */
    val deviceId: String by lazy {
        val prefs = getSharedPreferences("fieldtrack.device", MODE_PRIVATE)
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }

    val workRepository: WorkRepository by lazy {
        WorkRepository(db = db, syncQueue = syncQueue, deviceId = deviceId)
    }

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(db = db, syncQueue = syncQueue, deviceId = deviceId)
    }

    val directoryRepository: DirectoryRepository by lazy {
        DirectoryRepository(db = db, syncQueue = syncQueue, ledger = ledgerRepository)
    }

    val planRepository: PlanRepository by lazy {
        PlanRepository(db = db, syncQueue = syncQueue)
    }

    val teamRepository: TeamRepository by lazy {
        TeamRepository(
            db = db,
            syncQueue = syncQueue,
            // Taking somebody off the team stops whatever they were driving.
            pauseLiveJob = { orgId, jobId -> workRepository.pause(orgId, jobId) },
        )
    }

    val sessionStore: SessionStore by lazy { SessionStore(this, db) }

    /**
     * The session together with when its business's seasons begin, each following
     * the other: a switch of business brings that business's calendar, and an
     * owner changing the dates reaches every screen already showing "this season".
     * For anything that asks which season it is.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val seasonedSession: kotlinx.coroutines.flow.Flow<Pair<com.example.fieldtrack.data.session.Session, SeasonCalendar>> by lazy {
        sessionStore.session.flatMapLatest { s ->
            directoryRepository.observeSeasons(s.orgId).map { s to it }
        }
    }

    /**
     * Where local changes go. The only place in the app that names a server.
     *
     * Swapping this for null is all it takes to run with no backend at all: the
     * queue then holds everything and the backup screen says so, which is how this
     * was built and tested before the Firebase project existed.
     */
    val syncTransport: SyncTransport by lazy {
        FirestoreTransport(deviceId = deviceId)
    }

    /**
     * The one read path in the app. See [InviteLookup] for why it is on its own.
     */
    val inviteLookup: InviteLookup by lazy { FirestoreInviteLookup() }

    /** Fetching a person's work back, the counterpart to the outgoing queue. */
    val remoteRestore: RemoteRestore by lazy { FirestoreRestore() }

    val restoreApplier: RestoreApplier by lazy {
        RestoreApplier(db) { personId -> personId in sessionStore.adoptedAway() }
    }

    val pullWatermarks: PullWatermarks by lazy { PullWatermarks(this) }

    /** Brings down what other people changed, the other half of the sync. */
    val pullSync: PullSync by lazy {
        PullSync(
            restore = remoteRestore,
            applier = restoreApplier,
            marks = pullWatermarks,
            clock = Clock.System,
        )
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            outbox = db.outboxDao(),
            transport = syncTransport,
            journal = PrefsSyncJournal(this),
        )
    }

    val workClockNotifier: WorkClockNotifier by lazy { WorkClockNotifier(this) }

    /** The open job, mirrored into the notification shade. */
    val workClock: WorkClock by lazy { WorkClock(this, workClockNotifier) }

    /** For work that has to outlive the screen or receiver that asked for it. */
    fun runInBackground(block: suspend () -> Unit) {
        appScope.launch { block() }
    }

    /** Light or dark, and bigger text — how this phone shows the app. */
    val displaySettings: com.example.fieldtrack.data.settings.DisplaySettings by lazy {
        com.example.fieldtrack.data.settings.DisplaySettings(this)
    }

    /** Which overnight clocks this person has already said were real work. */
    val clockConfirmations: ClockConfirmations by lazy { ClockConfirmations(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Once per business, by somebody allowed to: the job costs version 12 made
     * and never queued. Remembered only once it has actually run, so a person
     * given the right to pay people later still gets it done.
     */
    private suspend fun sendWhatTheCostMigrationKept(session: com.example.fieldtrack.data.session.Session) {
        val key = "migratedCostsQueued_${session.orgId}"
        if (syncPrefs.getBoolean(key, false)) return
        if (com.example.fieldtrack.data.session.Capability.PayOut !in session.can) return
        ledgerRepository.requeueMigratedJobCosts(session.orgId, session.can)
        syncPrefs.edit().putBoolean(key, true).apply()
    }

    private val syncPrefs by lazy {
        getSharedPreferences("field_flow_sync", MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()

        // Work out who is using the app before the first frame is drawn, so the
        // launch window can stay up until there is something real to show instead
        // of a blank frame. Failure is swallowed deliberately: the startup gate in
        // the UI handles an unresolved session, and the splash gives up on a timer
        // rather than waiting on this.
        appScope.launch {
            // An install whose person id predates sign-in cannot satisfy a
            // membership rule, so its queue would sit refused for ever. Carrying the
            // records over to the authenticated identity is what unsticks it.
            runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?.let { sessionStore.adoptAuthenticatedIdentity(it) }
            }
            runCatching { sessionStore.load() }
                // Machines whose stored status disagrees with the facts are put
                // right here. Failure is swallowed: a status that stays wrong is a
                // cosmetic problem, and refusing to start over it is not.
                .getOrNull()
                ?.let { session ->
                    runCatching { workRepository.reconcileMachineStatuses(session.orgId) }
                    runCatching { sendWhatTheCostMigrationKept(session) }
                }

            // What the owner decided while this phone was shut: a permission
            // granted or taken away, or this person removed from the business.
            runCatching { sessionStore.refreshMembership(remoteRestore, restoreApplier) }
        }

        // Wakes on a connection and drains whatever the day's work left queued.
        SyncWorker.start(this, hasBackend = true)

        // The running clock in the notification shade, and the evening question
        // about any clock still going. Channels first: a notification posted to a
        // channel that does not exist yet is silently dropped.
        workClockNotifier.createChannels()
        workClock.watch(appScope)
        ClockReminderWorker.schedule(this)
    }
}


package com.example.fieldtrack

import android.app.Application
import android.util.Log
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.repository.DirectoryRepository
import com.example.fieldtrack.data.repository.LedgerRepository
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.repository.WorkRepository
import com.example.fieldtrack.data.session.SessionStore
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                }

            // What the owner decided while this phone was shut: a permission
            // granted or taken away, or this person removed from the business.
            runCatching { sessionStore.refreshMembership(remoteRestore, restoreApplier) }
        }

        // Wakes on a connection and drains whatever the day's work left queued.
        SyncWorker.start(this, hasBackend = true)
    }
}


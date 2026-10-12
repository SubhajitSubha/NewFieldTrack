package com.example.fieldtrack

import android.app.Application
import android.util.Log
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.local.database.FieldFlowDatabase
import com.example.fieldtrack.data.repository.DirectoryRepository
import com.example.fieldtrack.data.repository.LedgerRepository
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.repository.WorkRepository
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.data.sync.FirestoreInviteLookup
import com.example.fieldtrack.data.sync.FirestoreTransport
import com.example.fieldtrack.data.sync.InviteLookup
import com.example.fieldtrack.data.sync.PrefsSyncJournal
import com.example.fieldtrack.data.sync.SyncEngine
import com.example.fieldtrack.data.sync.SyncTransport
import com.example.fieldtrack.data.sync.SyncWorker
import java.util.UUID
import com.example.fieldtrack.data.repository.ActiveJobRepository
import com.example.fieldtrack.data.repository.CustomBrandModelRepository
import com.example.fieldtrack.data.repository.CustomerRepository
import com.example.fieldtrack.data.repository.FirestoreRepository
import com.example.fieldtrack.data.repository.JobRepository
import com.example.fieldtrack.data.repository.MachineRepository
import com.example.fieldtrack.data.repository.OrganizationRepository
import com.example.fieldtrack.data.repository.SessionRepository
import com.example.fieldtrack.data.repository.SettingsRepository
import com.example.fieldtrack.data.repository.UserRepository
import com.example.fieldtrack.data.local.dao.CustomerFieldDao
import com.example.fieldtrack.core.session.identityfoundation.SessionManager
import com.example.fieldtrack.sync.SyncManager
import com.example.fieldtrack.util.RatePreferences
import com.example.fieldtrack.util.WorkPatternPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.fieldtrack.data.repository.ActiveJobsRepository
import com.example.fieldtrack.data.repository.PaymentRepository

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

    val database: FieldFlowDatabase by lazy {
        FieldFlowDatabase.getInstance(this)
    }

    // ---------------------------------------------------------------------------
    // The real data layer.
    //
    // Added alongside the old `database` above rather than replacing it, so the
    // cut-over happens one screen at a time. `database` is not a Room database at
    // all — it is a plain class whose DAO accessors return null, and every
    // repository hanging off it is an empty shell that swallows its constructor
    // arguments. Nothing it touches has ever persisted anything.
    //
    // Everything below is real: Room, real SQL, covered by tests under
    // app/src/test/java/com/example/fieldtrack/data. As each redesigned screen
    // lands it reads from here, and the stubs above are deleted with the last
    // screen that referenced them.
    // ---------------------------------------------------------------------------

    val db: FieldTrackDatabase by lazy { FieldTrackDatabase.build(this) }

    private val syncQueue: SyncQueue by lazy { SyncQueue(db.outboxDao()) }

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
        TeamRepository(db = db, syncQueue = syncQueue)
    }

    val sessionStore: SessionStore by lazy { SessionStore(this, db) }

    /**
     * Where local changes go. The only place in the app that names a server.
     *
     * Swapping this for null is all it takes to run with no backend at all: the
     * queue then holds everything and the backup screen says so, which is how this
     * was built and tested before the Firebase project existed.
     */
    private val syncTransport: SyncTransport by lazy {
        FirestoreTransport(deviceId = deviceId)
    }

    /**
     * The one read path in the app. See [InviteLookup] for why it is on its own.
     */
    val inviteLookup: InviteLookup by lazy { FirestoreInviteLookup() }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            outbox = db.outboxDao(),
            transport = syncTransport,
            journal = PrefsSyncJournal(this),
        )
    }

    val syncManager: SyncManager by lazy {
        SyncManager(this)
    }

    val firestoreRepository: FirestoreRepository by lazy {
        FirestoreRepository()
    }

    val jobRepository: JobRepository by lazy {
        JobRepository(database.jobDao(), syncManager, firestoreRepository, database.paymentDao(), database)
    }

    val customerRepository: CustomerRepository by lazy {
        CustomerRepository(database.customerDao(), syncManager, firestoreRepository)
    }

    val ratePreferences: RatePreferences by lazy {
        RatePreferences(this)
    }

    val workPatternPreferences: WorkPatternPreferences by lazy {
        WorkPatternPreferences(this)
    }

    val customerFieldDao: CustomerFieldDao by lazy {
        database.customerFieldDao()
    }

    val activeJobRepository: ActiveJobRepository by lazy {
        ActiveJobRepository(database.activeJobDao())
    }

    val activeJobsRepository: ActiveJobsRepository by lazy {
        ActiveJobsRepository(database.activeJobsDao(), database, database.jobDao())
    }

    val machineRepository: MachineRepository by lazy {
        MachineRepository(database.machineDao(), syncManager)
    }

    val customBrandModelRepository: CustomBrandModelRepository by lazy {
        CustomBrandModelRepository(database.customBrandDao(), database.customModelDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(
            database.paymentDao(),
            database.jobDao(),
            syncManager,
            database
        )
    }

    /**
     * R11A — Organization Foundation. Exposed here only so future R11B
     * code (Team UI, capability source, migration engine) can resolve a
     * single repository instance without retrofitting Application. Not
     * wired to any ViewModel or surface yet; user-facing behavior is
     * unchanged.
     */
    val organizationRepository: OrganizationRepository by lazy {
        OrganizationRepository(
            database.organizationDao(),
            database.organizationMemberDao(),
            database.organizationInviteDao()
        )
    }

    /** R36 — identity foundation repository (persistence only, no UI wiring yet). */
    val userRepository: UserRepository by lazy {
        UserRepository(database.userDao())
    }

    /** R36 — identity foundation repository (single active session model). */
    val sessionRepository: SessionRepository by lazy {
        SessionRepository(database.sessionDao())
    }

    /** R36 — identity foundation architecture manager. */
    val sessionManager: SessionManager by lazy {
        SessionManager(userRepository, sessionRepository)
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
        }

        // Wakes on a connection and drains whatever the day's work left queued.
        SyncWorker.start(this, hasBackend = true)

        /*
        // Initialize device identity — must be first (other systems depend on it)
        com.example.fieldtrack.core.identity.DeviceIdentity.initialize(this)

        // Initialize operator identity — after device identity
        com.example.fieldtrack.core.identity.OperatorIdentityManager.initialize(this)

        // Initialize session — after operator identity
        com.example.fieldtrack.core.session.OperationalSessionManager.initialize(this)

        // ROADMAP R1 — Local test identity layer (future auth safe).
        // Loads any persisted CurrentUserSession from disk. If none, the
        // identity-flow resolver leaves the app in ONBOARDING_REQUIRED or
        // TEST_IDENTITY_REQUIRED until the user finishes the existing
        // onboarding card and picks a test role. Real auth later replaces
        // ONLY this initialization.
        //
        // R4.1 — DO NOT eagerly install a fallback session here. The
        // previous `ensureActiveSession()` call made `hasSession` always
        // true at boot, which short-circuited the resolver away from
        // ONBOARDING_REQUIRED → TEST_IDENTITY_REQUIRED and made the
        // fresh-install onboarding+sheet flow unreachable. The fallback
        // is now installed ONLY on explicit sheet dismissal, via
        // [CurrentSessionProvider.installFallbackSessionIfAbsent].
        com.example.fieldtrack.core.session.CurrentSessionProvider.init(this)

        // MULTI-USER FOUNDATION: Initialize auth user identity (nullable — single-user is valid)
        com.example.fieldtrack.core.identity.AuthUserIdentityManager.initialize(this)

        // MULTI-USER FOUNDATION: Bootstrap personal organization (silent, no onboarding)
        com.example.fieldtrack.core.organization.PersonalOrganizationBootstrap.initialize(this)

        // R41 — Mock Identity Provider (testing-only authentication foundation).
        // Loads mock users from assets/mock_users.json.
        // Restores any persisted session on app restart.
        com.example.fieldtrack.core.identity.auth.AuthenticationManager.initialize(this)

        // R11C — Organization Identity Runtime.
        // Promotes bootstrap data into a Room-backed identity snapshot.
        // Synchronous init: reads SharedPrefs snapshot (already in memory from
        // PersonalOrganizationBootstrap) → publishes to StateFlow + in-memory
        // field. Zero observable latency; no loading state.
        com.example.fieldtrack.domain.organization.OrganizationIdentityRuntime.initialize(this)

        // Initialize connectivity awareness
        com.example.fieldtrack.core.sync.OperationalConnectivity.initialize(this)

        // Initialize acknowledgement persistence (Phase C3)
        com.example.fieldtrack.core.sync.AcknowledgementPersistence.initialize(this)

        // PHASE F2 PART 1 — Register the workspace recovery snapshot store.
        // Must run before any UI is composed so the home surface can read
        // the current snapshot synchronously without a "recovering" state.
        com.example.fieldtrack.core.recovery.WorkspaceRecoverySnapshotStore.register(this)
        */

        // PHASE F2 PART 8 — Self-audit release readiness on startup.
        // DEV: silent; STAGING: logs violations; PROD: compact blocker log only.
        /*
        try {
            com.example.fieldtrack.core.release.OperationalReleaseReadiness.verify()
        } catch (_: Exception) { /* readiness check must never crash the app */ }

        // Schedule operational sync worker (Phase C1)
        com.example.fieldtrack.core.sync.OperationalSyncWorker.schedule(this)

        // Operational startup verification — non-blocking, never crashes
        try {
            com.example.fieldtrack.core.verification.OperationalStartupVerifier.verify(this)
        } catch (_: Exception) { /* startup verification must never crash the app */ }

        syncManager.schedulePeriodicSync()

        appScope.launch {
            // R36 — restore persisted identity session state on launch.
            // Architecture-only in this phase: it does not drive navigation yet.
            try {
                sessionManager.restoreSessionOnLaunch()
            } catch (e: Exception) {
                Log.e("FieldFlowApp", "Identity session restore failed (non-fatal): ${e.message}")
            }

            // R11C — Fire-and-forget Room promotion of personal org.
            // Idempotent INSERT OR REPLACE; safe on repeated calls / crash recovery.
            try {
                com.example.fieldtrack.domain.organization.OrganizationIdentityRuntime
                    .promoteToRoom(organizationRepository)
            } catch (e: Exception) {
                Log.e("FieldFlowApp", "Org Room promotion failed (non-fatal): ${e.message}")
            }

            // R11D — Activate organization ownership on all entity rows.
            // Idempotent (WHERE organizationId IS NULL); single transaction.
            try {
                com.example.fieldtrack.domain.organization.OrganizationOwnershipMigration
                    .migrateIfNeeded(this@FieldFlowApplication, database)
            } catch (e: Exception) {
                Log.e("FieldFlowApp", "Org ownership migration failed (non-fatal): ${e.message}")
            }
        }

        appScope.launch {
            try {
                val organizationId = ActorContextProvider.current().organizationId
                val lastFullSync = syncPrefs.getLong("lastFullSyncTime", 0L)
                val threeDays = 3L * 24 * 60 * 60 * 1000
                val needsFullSync = (System.currentTimeMillis() - lastFullSync) > threeDays

                if (needsFullSync) {
                    Log.d("FieldFlowApp", "Full sync needed (last: ${if (lastFullSync == 0L) "never" else "${(System.currentTimeMillis() - lastFullSync) / 86400000}d ago"})")

                    // Jobs & Customers: heavy datasets — pull only every 3 days
                    val remoteJobs = firestoreRepository.fetchAllJobsOnce()
                    val remoteCustomers = firestoreRepository.fetchAllCustomersOnce()

                    if (remoteJobs.isNotEmpty()) jobRepository.mergeRemoteJobsBulk(remoteJobs)
                    if (remoteCustomers.isNotEmpty()) customerRepository.mergeRemoteCustomersBulk(remoteCustomers)

                    syncPrefs.edit().putLong("lastFullSyncTime", System.currentTimeMillis()).apply()
                    Log.d("FieldFlowApp", "Jobs/Customers full sync complete")
                } else {
                    Log.d("FieldFlowApp", "Skipping job/customer full sync (last was recent)")
                }

                // Machines & Payments: small datasets — pull on EVERY app launch
                // They have no real-time listener, so this ensures cross-device freshness
                val remoteMachines = firestoreRepository.fetchAllMachinesOnce()
                val remotePayments = firestoreRepository.fetchAllPaymentsOnce()

                // Machines: safe merge — only insert/update if remote is newer
                if (remoteMachines.isNotEmpty()) {
                    val machineDao = database.machineDao()
                    var inserted = 0; var updated = 0; var skipped = 0
                    for (machine in remoteMachines) {
                        val local = machineDao.getMachineById(machine.machineId, organizationId)
                        if (local == null) {
                            machineDao.insertMachine(machine)
                            inserted++
                        } else if (machine.updatedAt > local.updatedAt) {
                            machineDao.insertMachine(machine)
                            updated++
                        } else {
                            skipped++
                        }
                    }
                    Log.d("FieldFlowApp", "Machine sync: $inserted new, $updated updated, $skipped skipped")
                }

                // Payments: safe merge — only insert/update if remote is newer
                if (remotePayments.isNotEmpty()) {
                    val paymentDao = database.paymentDao()
                    var inserted = 0; var updated = 0; var skipped = 0
                    for (payment in remotePayments) {
                        val local = paymentDao.getPaymentById(payment.paymentId, organizationId)
                        if (local == null) {
                            paymentDao.insert(payment)
                            inserted++
                        } else if (payment.updatedAt > local.updatedAt) {
                            paymentDao.insert(payment)
                            updated++
                        } else {
                            skipped++
                        }
                    }
                    Log.d("FieldFlowApp", "Payment sync: $inserted new, $updated updated, $skipped skipped")
                }

            } catch (e: Exception) {
                Log.e("FieldFlowApp", "Cold start fetch failed: ${e.message}")
            } finally {
                jobRepository.startObservingRemoteJobs()
                customerRepository.startObservingRemoteCustomers()
            }
        }
        */
    }
}


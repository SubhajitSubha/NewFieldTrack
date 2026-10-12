package com.example.fieldtrack.data.session

import android.content.Context
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.MeasureUnit
import androidx.room.withTransaction
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.db.entity.OrgEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString

/**
 * Who is using the app, and which business they are working for right now.
 *
 * A driver can belong to several organisations, so "the current org" is session state
 * rather than a property of the person. Every repository call is scoped by
 * [Session.orgId] — that is the mechanism keeping one business's customers off
 * another's screen on a shared phone.
 */
data class Session(
    val orgId: String,
    val personId: String,
    val displayName: String,
    val businessName: String,
    val role: MemberRole,
    val landUnit: MeasureUnit,
    /** What this person may change here. Computed from their membership on load. */
    val can: Capabilities,
) {
    val canSeeMoney: Boolean get() = Capability.SeeMoney in can
}

/** What the app should show on launch. */
sealed interface Startup {
    data object Loading : Startup

    /** Nobody has proved who they are on this device yet. */
    data object NeedsSignIn : Startup

    /**
     * Signed in, but not in any business yet.
     *
     * A driver and an owner both land here, and the difference between them is which
     * way they go next: one types a code, the other sets up their own business.
     */
    data object NeedsOnboarding : Startup
    data class Ready(val session: Session) : Startup
}

class SessionStore(
    private val context: Context,
    private val db: FieldTrackDatabase,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    // The business and the person are rows like any other: renaming either has to
    // reach the server eventually, so these writes queue the same way a customer's
    // do. Without this a rename survives on the handset and nowhere else.
    private val syncQueue = SyncQueue(db.outboxDao(), clock)

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /**
     * Whether this person runs a business with drivers, rather than working alone.
     *
     * Observable rather than a plain preference read: it decides which tabs the app
     * shows, and someone who turns it on in settings should not have to restart the
     * app to see the fleet appear.
     */
    private val _businessMode by lazy {
        MutableStateFlow(prefs.getBoolean(KEY_IS_BUSINESS, false))
    }

    private val _rememberPerCustomer by lazy {
        MutableStateFlow(prefs.getBoolean(KEY_PER_CUSTOMER_RATES, true))
    }
    val businessMode: StateFlow<Boolean> get() = _businessMode.asStateFlow()

    private val _startup = MutableStateFlow<Startup>(Startup.Loading)
    val startup: StateFlow<Startup> = _startup.asStateFlow()

    /**
     * The live session, re-emitted whenever any part of it changes.
     *
     * Screens observe this instead of taking a snapshot at construction. Someone who
     * corrects their own name or renames their business expects every screen already
     * showing the old one to catch up, not just the ones opened afterwards.
     */
    val session: Flow<Session> = startup.mapNotNull { (it as? Startup.Ready)?.session }

    fun current(): Session? = (_startup.value as? Startup.Ready)?.session

    /**
     * Resolves the session from disk.
     *
     * Returns null when nobody has set the app up yet. Deliberately does NOT invent an
     * organisation: an unnamed business puts a blank sender on every bill, which is
     * exactly the sort of thing nobody notices until a customer asks who sent it.
     */
    suspend fun load(): Session? {
        current()?.let { return it }

        val personId = prefs.getString(KEY_PERSON, null)
        val orgId = prefs.getString(KEY_ORG, null)
        // No identity at all: sign in first. Installs made before sign-in existed
        // already have a person id, so they are never sent back through this.
        if (personId == null) {
            _startup.value = Startup.NeedsSignIn
            return null
        }
        if (orgId == null) {
            _startup.value = Startup.NeedsOnboarding
            return null
        }

        val membership = db.orgDao().membership(orgId, personId)
        if (membership == null) {
            _startup.value = Startup.NeedsOnboarding
            return null
        }

        val org = db.orgDao().observeOrg(orgId).first()
        val session = Session(
            orgId = orgId,
            personId = personId,
            displayName = membership.displayName,
            businessName = org?.name.orEmpty(),
            role = membership.role,
            landUnit = org?.defaultLandUnit ?: MeasureUnit.Acre,
            can = Capabilities.of(membership),
        )
        _startup.value = Startup.Ready(session)
        return session
    }

    /**
     * Creates the person and their business from what onboarding collected.
     *
     * Everything here is local. There is no account and no network: the work loop has
     * to run in a field with no signal, so needing a phone number before recording a
     * single job would put a dead end in front of the people this is for. Signing in
     * becomes necessary when backup or a team is switched on, and not before.
     */
    suspend fun completeOnboarding(
        displayName: String,
        businessName: String,
        landUnit: MeasureUnit,
        isBusiness: Boolean,
    ): Session {
        val now = clock.now()
        val personId = prefs.getString(KEY_PERSON, null) ?: ids.newId()
        val orgId = ids.newId()

        val org = OrgEntity(
            id = orgId,
            name = businessName.trim(),
            defaultLandUnit = landUnit,
            createdAt = now,
            updatedAt = now,
        )
        db.orgDao().upsertOrg(org)
        val membership = MembershipEntity(
            id = membershipId(orgId, personId),
            orgId = orgId,
            personId = personId,
            displayName = displayName.trim(),
            role = MemberRole.Owner,
            canSeeMoney = true,
            canAddCustomers = true,
            canCloseBills = true,
            joinedAt = now,
            updatedAt = now,
        )
        db.orgDao().upsertMembership(membership)
        syncQueue.enqueueUpsert(
            orgId, SyncQueue.TABLE_ORGS, orgId, OutboxJson.encodeToString(org)
        )
        syncQueue.enqueueUpsert(
            orgId, SyncQueue.TABLE_MEMBERSHIPS, membership.id,
            OutboxJson.encodeToString(membership),
        )

        prefs.edit()
            .putString(KEY_PERSON, personId)
            .putString(KEY_ORG, orgId)
            .putBoolean(KEY_IS_BUSINESS, isBusiness)
            .apply()
        _businessMode.value = isBusiness

        // Deliberately does not flip [startup] to Ready. Onboarding still has a
        // confirmation step to show, and swapping the whole UI out from under it the
        // instant the rows are written would make that screen unreachable.
        // Call [activate] when the person taps through.
        return Session(
            orgId = orgId,
            personId = personId,
            displayName = membership.displayName,
            businessName = businessName.trim(),
            role = MemberRole.Owner,
            landUnit = landUnit,
            can = Capabilities.of(membership),
        )
    }

    /** Hands control to the app once onboarding has shown its last screen. */
    suspend fun activate() {
        load()
    }

    /**
     * Correct the name a person goes by. Their customers see it on every bill, so
     * getting it wrong once should not be permanent.
     */
    suspend fun updateProfile(displayName: String) {
        val session = current() ?: return
        val name = displayName.trim()
        val now = clock.now()
        val membership = db.orgDao().membership(session.orgId, session.personId) ?: return
        val renamed = membership.copy(displayName = name, updatedAt = now)
        db.orgDao().upsertMembership(renamed)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_MEMBERSHIPS, renamed.id,
            OutboxJson.encodeToString(renamed),
        )

        // Deliberately does not touch the business. A bill goes out under the
        // organisation's name, and that name is the farmer's to set — onboarding
        // only defaults it to their own. Renaming yourself here used to rename the
        // business too, which quietly destroyed a trading name the moment someone
        // corrected the spelling of their own.
        _startup.value = Startup.Ready(session.copy(displayName = name))
    }

    /**
     * Rename the business, change its land unit, or edit what prints on its bills.
     *
     * Every letterhead field is passed explicitly rather than defaulted, so a caller
     * that forgets one cannot quietly blank a GSTIN somebody typed in once a year.
     */
    suspend fun updateBusiness(
        name: String,
        landUnit: MeasureUnit,
        phone: String?,
        addressLine: String?,
        gstin: String?,
        upiId: String?,
    ) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        val updated = org.copy(
            name = name.trim(),
            defaultLandUnit = landUnit,
            phone = phone?.trim()?.ifBlank { null },
            addressLine = addressLine?.trim()?.ifBlank { null },
            gstin = gstin?.trim()?.ifBlank { null },
            upiId = upiId?.trim()?.ifBlank { null },
            updatedAt = clock.now(),
        )
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
        _startup.value = Startup.Ready(
            session.copy(businessName = name.trim(), landUnit = landUnit)
        )
    }

    /** True when this person set themselves up as a business with drivers. */
    fun isBusiness(): Boolean = _businessMode.value

    /**
     * Turn the fleet and team side of the app on or off.
     *
     * Nothing is created or destroyed by this: a solo farmer who switches on gets
     * the jobs and fleet tabs, and switching back hides them again. The work, the
     * machines and the khata are the same either way.
     */
    fun setBusinessMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_BUSINESS, enabled).apply()
        _businessMode.value = enabled
    }

    /**
     * Whether a rate agreed with one customer is kept for that customer.
     *
     * On by default, because it matches how the trade works: a contractor charges
     * his brother-in-law less and a distant village more, and he should not have to
     * remember which. Switched off, the rate card is one price list for everybody —
     * which is what a contractor who quotes a flat rate actually wants, and who is
     * otherwise slowly confused by a number that changes per customer.
     */
    val rememberPerCustomer: StateFlow<Boolean> get() = _rememberPerCustomer.asStateFlow()

    fun setRememberPerCustomer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PER_CUSTOMER_RATES, enabled).apply()
        _rememberPerCustomer.value = enabled
    }

    /**
     * Records who just signed in, and makes their identity this device's person.
     *
     * The provider's uid becomes the app's [Session.personId] rather than sitting
     * beside it. One identity instead of two means a membership row names the same
     * id the server authenticated, which is what lets a security rule ask "is this
     * caller a member of this organisation" and get a truthful answer. Two ids with
     * a mapping between them would push that question to a table nobody can check.
     */
    suspend fun recordSignIn(uid: String, phone: String?) {
        prefs.edit()
            .putString(KEY_PERSON, uid)
            .apply { phone?.let { putString(KEY_PHONE, it) } }
            .apply()
        _startup.value = Startup.Loading
        load() ?: run { _startup.value = Startup.NeedsOnboarding }
    }

    /**
     * Takes up a membership somebody else created, and works from that business.
     *
     * The membership row is already written by the time this runs; all that is left
     * is to point the session at it, which is what turns a redeemed code into the
     * app opening on their boss's work.
     */
    suspend fun adoptMembership(orgId: String) {
        prefs.edit().putString(KEY_ORG, orgId).apply()
        // A driver is not running a business of their own, whatever tabs the owner
        // of this phone may have switched on before.
        prefs.edit().putBoolean(KEY_IS_BUSINESS, false).apply()
        _businessMode.value = false
        _startup.value = Startup.Loading
        load()
    }

    /**
     * Makes this device's records belong to the person who just proved who they are.
     *
     * Installs made before sign-in existed carry a person id this app invented for
     * itself. That was fine while nothing checked: the id only had to be consistent
     * within one phone. It stops being fine the moment the server asks "are you a
     * member here", because the answer is a membership row naming an id no
     * authentication will ever produce — and that install is then locked out of its
     * own data, politely and permanently.
     *
     * So the local id is retired and the authenticated one takes its place
     * everywhere it appears: the membership, the jobs that name their operator, the
     * events, the payments taken, the fuel and the faults. All in one transaction,
     * because an identity changed in half the tables is worse than one not changed
     * at all — the rows would disagree about who did the work.
     *
     * Nothing is deleted and nothing moves between people. The same work, the same
     * money, under the name that can now be proved.
     */
    suspend fun adoptAuthenticatedIdentity(uid: String): Boolean {
        val stored = prefs.getString(KEY_PERSON, null) ?: return false
        if (stored == uid) return false
        // Only when the old id actually owns something. A device with no history has
        // nothing to carry over and should simply take the new id.
        if (db.orgDao().membershipCountFor(stored) == 0) {
            prefs.edit().putString(KEY_PERSON, uid).apply()
            return false
        }

        db.withTransaction {
            db.orgDao().adoptMemberships(stored, uid)
            db.orgDao().adoptInvites(stored, uid)
            db.jobDao().adoptJobs(stored, uid)
            db.jobDao().adoptJobEvents(stored, uid)
            db.machineDao().adoptFuel(stored, uid)
            db.machineDao().adoptBreakdowns(stored, uid)
            db.ledgerDao().adoptEntries(stored, uid)
        }
        prefs.edit().putString(KEY_PERSON, uid).apply()

        // The server still holds the rows under the old id. Re-queueing the
        // membership is what tells it who this is now; without that, the rules keep
        // refusing and the queue stays stuck behind a change it cannot explain.
        val orgId = prefs.getString(KEY_ORG, null)
        if (orgId != null) {
            db.orgDao().membership(orgId, uid)?.let { membership ->
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_MEMBERSHIPS, membership.id,
                    OutboxJson.encodeToString(membership),
                )
            }
        }
        _startup.value = Startup.Loading
        load()
        return true
    }

    /** The signed-in identity, before any business is chosen. */
    fun personIdOrNull(): String? = prefs.getString(KEY_PERSON, null)

    /** The number this device signed in with, for screens that show it back. */
    fun signedInPhone(): String? = prefs.getString(KEY_PHONE, null)

    /** Move the session to another business this person belongs to. */
    suspend fun switchTo(orgId: String) {
        val personId = current()?.personId ?: return
        // Refuse to switch into an organisation this person is not a member of,
        // rather than trusting the caller — this is the privacy boundary.
        db.orgDao().membership(orgId, personId) ?: return
        prefs.edit().putString(KEY_ORG, orgId).apply()
        _startup.value = Startup.Loading
        load()
    }

    private companion object {
        const val PREFS = "fieldtrack.session"
        const val KEY_PERSON = "personId"
        const val KEY_ORG = "orgId"
        const val KEY_IS_BUSINESS = "isBusiness"
        const val KEY_PER_CUSTOMER_RATES = "rememberPerCustomerRates"
        const val KEY_PHONE = "phone"
    }
}

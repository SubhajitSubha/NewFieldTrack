package com.example.fieldtrack.data.session

import kotlinx.coroutines.withContext
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyStyle
import android.content.Context
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.season.SeasonCalendar
import androidx.room.withTransaction
import com.example.fieldtrack.data.sync.PullWatermarks
import com.example.fieldtrack.data.sync.RemoteRestore
import com.example.fieldtrack.data.sync.RestoreApplier
import com.example.fieldtrack.data.sync.RestoreResult
import com.example.fieldtrack.data.auth.PhoneAuth
import com.example.fieldtrack.data.db.OutboxJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MemberOrgDoc
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.db.entity.OrgEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    /** The business's own land unit's name, when it measures in one of its own. */
    val ownLandUnit: String? = null,
    /** What this person may change here. Computed from their membership on load. */
    val can: Capabilities,
    /** The business's currency, and how its bills are rounded. */
    val currencyCode: String = "INR",
    val moneyStepMinor: Long = 100,
    /** When the business's accounting year begins, and the zone its days are counted in. */
    val fiscalStartMonth: Int = 4,
    val timeZoneId: String? = null,
    /** The ways customers pay, as stored: names joined by commas, or null for the usual ones. */
    val paymentMethods: String? = null,
) {
    val methodsOffered: List<com.example.fieldtrack.data.db.PaymentMethod>
        get() = com.example.fieldtrack.data.billing.PaymentMethods.offered(paymentMethods, currencyCode)

    val canSeeMoney: Boolean get() = Capability.SeeMoney in can

    /** The business's money, written the way this person reads it. */
    val money: MoneyStyle get() = MoneyStyle.of(currencyCode, moneyStepMinor, Money.grouping)
}

/** What the app should show on launch. */
/** What a check against the server turned up. */
sealed interface MembershipRefresh {
    data object Unchanged : MembershipRefresh
    data object PermissionsChanged : MembershipRefresh
    data class Removed(val orgId: String) : MembershipRefresh
}

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
    data class Ready(val session: Session) : Startup {
        init {
            // Whoever the app is ready for is whose money the screens write: a
            // business with its own currency shows in it from the first frame, and
            // switching business changes every figure at once.
            Money.style = session.money
            BusinessClock.use(session.timeZoneId, session.fiscalStartMonth)
            Money.methods = session.methodsOffered
            com.example.fieldtrack.data.region.OwnLandUnit.name = session.ownLandUnit
        }
    }
}

class SessionStore(
    private val context: Context,
    private val db: FieldTrackDatabase,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    /**
     * Cleared when a business is left, so that rejoining reads it from scratch
     * rather than resuming behind a mark set before they were removed.
     */
    private val marks: PullWatermarks = PullWatermarks(context),
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

        // An account turned off outright. Checked before anything else, because
        // it outranks every business they belong to: there is no org to fall back
        // to and no partial state worth showing.
        if (prefs.getBoolean(KEY_DISABLED, false)) {
            _startup.value = Startup.NeedsSignIn
            return null
        }

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
            ownLandUnit = org?.ownLandUnit,
            can = Capabilities.of(membership),
            currencyCode = org?.currencyCode ?: "INR",
            moneyStepMinor = org?.moneyStepMinor ?: 100L,
            fiscalStartMonth = org?.fiscalStartMonth ?: 4,
            timeZoneId = org?.timeZoneId,
            paymentMethods = org?.paymentMethods,
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
        currencyCode: String = "INR",
        moneyStepMinor: Long = 100,
        fiscalStartMonth: Int = 4,
        /** The name of the business's own land unit, when it measures in one. */
        ownLandUnit: String? = null,
    ): Session {
        val now = clock.now()
        val personId = prefs.getString(KEY_PERSON, null) ?: ids.newId()
        val orgId = ids.newId()

        val org = OrgEntity(
            id = orgId,
            name = businessName.trim(),
            defaultLandUnit = landUnit,
            ownLandUnit = ownLandName(ownLandUnit),
            currencyCode = currencyCode,
            moneyStepMinor = moneyStepMinor,
            fiscalStartMonth = fiscalStartMonth,
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
        syncQueue.enqueueMembership(
            orgId = orgId,
            personId = personId,
            membershipId = membership.id,
            payloadJson = OutboxJson.encodeToString(membership),
            indexJson = OutboxJson.encodeToString(
                MemberOrgDoc(personId, orgId, businessName.trim(), now)
            ),
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
            ownLandUnit = org.ownLandUnit,
            can = Capabilities.of(membership),
            currencyCode = currencyCode,
            moneyStepMinor = moneyStepMinor,
            fiscalStartMonth = fiscalStartMonth,
        )
    }

    /** Hands control to the app once onboarding has shown its last screen. */
    suspend fun activate() {
        askForTour()
        load()
    }

    /** The first time somebody gets into a business, the short tour is shown once. */
    private fun askForTour() {
        if (!prefs.getBoolean(KEY_TOUR_SEEN, false)) prefs.edit().putBoolean(KEY_TOUR_PENDING, true).apply()
    }

    fun tourPending(): Boolean = prefs.getBoolean(KEY_TOUR_PENDING, false)

    /** Shown now: not again unless asked for from Help. */
    fun tourShown() {
        prefs.edit().putBoolean(KEY_TOUR_PENDING, false).putBoolean(KEY_TOUR_SEEN, true).apply()
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
        // Only the name goes up. The row on this phone may be behind the owner's
        // latest switches, and sending all of it would undo them.
        syncQueue.enqueueMembershipPatch(
            session.orgId, renamed.id,
            buildJsonObject {
                put("id", renamed.id)
                put("orgId", renamed.orgId)
                put("personId", renamed.personId)
                put("displayName", renamed.displayName)
                put("updatedAt", renamed.updatedAt)
            },
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

    /** What came of asking to change a business's money. */
    enum class MoneyChange { Saved, CurrencyLocked }

    /**
     * Sets the currency the business keeps its books in, and how bills are rounded.
     *
     * The currency is a free choice only until something has been written in it. Past
     * that point every amount in the books would be read in a different money, and
     * nothing is converted behind anybody's back: a ledger is append-only, so the
     * honest way to keep books in another currency is another business. The rounding
     * can change at any time, because every job keeps the rounding it was made with.
     */
    suspend fun updateMoney(currencyCode: String, stepMinor: Long): MoneyChange {
        val session = current() ?: return MoneyChange.Saved
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return MoneyChange.Saved
        if (currencyCode != org.currencyCode && db.orgDao().moneyEntryCount(session.orgId) > 0) {
            return MoneyChange.CurrencyLocked
        }
        val step = stepMinor.coerceAtLeast(1)
        if (currencyCode == org.currencyCode && step == org.moneyStepMinor) return MoneyChange.Saved
        val updated = org.copy(currencyCode = currencyCode, moneyStepMinor = step, updatedAt = clock.now())
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
        _startup.value = Startup.Ready(
            session.copy(currencyCode = currencyCode, moneyStepMinor = step)
        )
        return MoneyChange.Saved
    }

    /**
     * Sets how customers pay, what to print under "Pay to", and the optional tax line.
     *
     * Free to change at any time: the methods only decide what is offered for the next
     * payment, and a payment already recorded keeps its method.
     */
    suspend fun updateBilling(
        methods: List<com.example.fieldtrack.data.db.PaymentMethod>,
        payDetails: String?,
        taxName: String?,
        taxPercentBps: Int?,
    ) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        val usual = com.example.fieldtrack.data.billing.PaymentMethods.defaultsFor(org.currencyCode)
        val chosen = com.example.fieldtrack.data.billing.PaymentMethods.offered(
            com.example.fieldtrack.data.billing.PaymentMethods.encode(methods), org.currencyCode,
        )
        // Stored only when it differs from the usual, so a business that never chose keeps
        // following its currency's usual ways if the currency is changed before any entry.
        val stored = if (chosen == usual) null else com.example.fieldtrack.data.billing.PaymentMethods.encode(chosen)
        val rate = taxPercentBps?.takeIf { it > 0 }
        val updated = org.copy(
            paymentMethods = stored,
            payDetails = payDetails?.trim()?.ifBlank { null },
            taxName = if (rate == null) null else taxName?.trim()?.ifBlank { null },
            taxPercentBps = rate,
            updatedAt = clock.now(),
        )
        if (updated == org.copy(updatedAt = updated.updatedAt)) return
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
        _startup.value = Startup.Ready(session.copy(paymentMethods = stored))
    }

    /**
     * Sets when the business's accounting year begins and which zone its days are counted in.
     *
     * Free to change at any time. Bills already made keep the year they were numbered in;
     * the next one is numbered in the new calendar, from one.
     */
    suspend fun updateCalendar(fiscalStartMonth: Int, timeZoneId: String?) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        val month = fiscalStartMonth.coerceIn(1, 12)
        val zone = timeZoneId?.takeIf { it.isNotBlank() && runCatching { java.time.ZoneId.of(it) }.isSuccess }
        if (month == org.fiscalStartMonth && zone == org.timeZoneId) return
        val updated = org.copy(fiscalStartMonth = month, timeZoneId = zone, updatedAt = clock.now())
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
        _startup.value = Startup.Ready(session.copy(fiscalStartMonth = month, timeZoneId = zone))
    }

    /**
     * Turns the booking link on or off. The secret part of the link is made the first time and
     * kept, so a link already given out works again if it is switched back on.
     */
    suspend fun setBookingLink(on: Boolean, showRates: Boolean) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        // Switching it off is always allowed; switching it on is a paid extra.
        if (on && !org.bookingLink && !com.example.fieldtrack.data.plan.PlanGate(db).allows(session.orgId, com.example.fieldtrack.data.plan.Need.BookingLink)) {
            throw com.example.fieldtrack.data.plan.PlanLimit(com.example.fieldtrack.data.plan.Need.BookingLink)
        }
        val slug = org.bookingSlug ?: ids.newId().replace("-", "").take(14)
        if (org.bookingLink == on && org.bookingShowRates == showRates && org.bookingSlug == slug) return
        val updated = org.copy(bookingLink = on, bookingSlug = slug, bookingShowRates = showRates, updatedAt = clock.now())
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
    }

    /** Whether the crew is asked for proof of work: the meter, a photo and a mark of where they were. */
    suspend fun setProofOfWork(on: Boolean) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        if (org.proofOfWork == on) return
        if (on && !com.example.fieldtrack.data.plan.PlanGate(db).allows(session.orgId, com.example.fieldtrack.data.plan.Need.ProofOfWork)) {
            throw com.example.fieldtrack.data.plan.PlanLimit(com.example.fieldtrack.data.plan.Need.ProofOfWork)
        }
        val updated = org.copy(proofOfWork = on, updatedAt = clock.now())
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
    }

    /**
     * Changes the unit new work is measured in, on its own, from Settings.
     *
     * Work already recorded keeps the unit it was measured in; only what is quoted
     * from now on changes.
     */
    suspend fun setLandUnit(unit: MeasureUnit, ownName: String? = null) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        // The own unit keeps its name when another is chosen, so going back to it does not ask again.
        val name = ownLandName(ownName) ?: org.ownLandUnit
        if (org.defaultLandUnit == unit && org.ownLandUnit == name) return
        val updated = org.copy(defaultLandUnit = unit, ownLandUnit = name, updatedAt = clock.now())
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
        _startup.value = Startup.Ready(session.copy(landUnit = unit, ownLandUnit = name))
    }

    /**
     * Sets when this business's seasons begin.
     *
     * Its own call, not another argument to [updateBusiness]: that one rewrites the
     * letterhead from a form and takes every field explicitly so none can be
     * blanked by accident, and the seasons are edited on a screen of their own.
     * Stored as given, even when it is the usual dates — somebody who chose June
     * and November should not have them shift if the usual dates ever change.
     */
    suspend fun updateSeasons(calendar: SeasonCalendar) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        val updated = org.copy(seasons = calendar.encode(), updatedAt = clock.now())
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
        )
    }

    /**
     * Sets how the business reads its plan: when its day turns over, and whether
     * drivers see everybody's bookings. Its own call for the reason [updateSeasons] is.
     */
    suspend fun updatePlanSettings(settings: PlanSettings) {
        val session = current() ?: return
        session.can.require(Capability.EditBusiness)
        val org = db.orgDao().observeOrg(session.orgId).first() ?: return
        val updated = org.copy(
            planDayStartHour = PlanSettings.cleanHour(settings.dayStartHour),
            driversSeeAll = settings.driversSeeAll,
            updatedAt = clock.now(),
        )
        db.orgDao().upsertOrg(updated)
        syncQueue.enqueueUpsert(
            session.orgId, SyncQueue.TABLE_ORGS, updated.id, OutboxJson.encodeToString(updated)
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
    suspend fun recordSignIn(uid: String, phone: String?, email: String? = null) {
        prefs.edit()
            .putString(KEY_PERSON, uid)
            .apply { phone?.let { putString(KEY_PHONE, it) } }
            .apply { if (email != null) putString(KEY_EMAIL, email) else remove(KEY_EMAIL) }
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
        askForTour()
        prefs.edit().putString(KEY_ORG, orgId).apply()
        // A driver is not running a business of their own, whatever tabs the owner
        // of this phone may have switched on before.
        prefs.edit().putBoolean(KEY_IS_BUSINESS, false).apply()
        // Whatever they were told about being let go has been answered by this.
        // Left behind, the notice outlives the thing it describes: somebody taken
        // off in April and taken back in June would meet "you are no longer with
        // them" the next time a join screen appeared, which by then is a lie.
        prefs.edit().remove(KEY_REMOVED_FROM).apply()
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
        // The identity left behind is remembered, so it can never walk back in.
        //
        // Everything above was rewritten onto the proven id, but the membership
        // already sent to the server under the OLD one is still sitting there
        // and nothing can take it down: deleting a membership is refused
        // outright, and this phone cannot update it either, because the rules
        // key a self-write on the id inside the document's name. So every pull
        // finds it and writes it back as a second live member, wearing the same
        // name as the person reading the screen. Dropped on the way in instead.
        prefs.edit()
            .putStringSet(KEY_ADOPTED_AWAY, adoptedAway() + stored)
            .putString(KEY_PERSON, uid)
            .apply()

        // The server still holds the rows under the old id. Re-queueing the
        // membership is what tells it who this is now; without that, the rules keep
        // refusing and the queue stays stuck behind a change it cannot explain.
        //
        // Every business gets told, not just the one open now, and each is told which
        // identity this one replaces: that is what lets every other phone stop listing
        // the left-behind row as a second person.
        db.orgDao().membershipsOf(uid).forEach { membership ->
            val told = membership.copy(replacesPersonId = stored, updatedAt = clock.now())
            db.orgDao().upsertMembership(told)
            syncQueue.enqueueUpsert(
                told.orgId, SyncQueue.TABLE_MEMBERSHIPS, told.id,
                OutboxJson.encodeToString(told),
            )
        }
        _startup.value = Startup.Loading
        load()
        return true
    }

    /**
     * Brings a person's work back onto a handset that has never seen it.
     *
     * The thing that makes "your work is backed up" true rather than a figure of
     * speech. Signing in on a new phone with the same number should hand somebody
     * their own records, and until this existed it handed them an empty app and an
     * invitation to set up a business they already had.
     *
     * Only runs when the device has nothing for that business. A phone that is
     * already carrying the work does not need it fetched, and [RestoreApplier]
     * refuses to overwrite anything still waiting to be sent — a restore must never
     * cost somebody the morning's work in the act of recovering the month's.
     */
    suspend fun restoreFor(
        personId: String,
        restore: RemoteRestore,
        applier: RestoreApplier,
    ): RestoreResult = try {
        val orgs = restore.orgsFor(personId)
        if (orgs.isEmpty()) {
            RestoreResult.NothingToRestore
        } else {
            var rows = 0
            for (org in orgs) {
                if (db.orgDao().membership(org.orgId, personId) != null) continue
                rows += applier.apply(restore.fetchOrg(org.orgId))
            }
            // Work from the first business found, unless one is already chosen.
            if (prefs.getString(KEY_ORG, null) == null) {
                orgs.firstOrNull()?.let { prefs.edit().putString(KEY_ORG, it.orgId).apply() }
            }
            _startup.value = Startup.Loading
            load()
            RestoreResult.Restored(orgs, rows)
        }
    } catch (failure: Exception) {
        // A restore that cannot reach the server is not a failed sign-in. They can
        // work on this phone regardless, and it will try again next time.
        RestoreResult.Unreachable(failure.message ?: failure::class.java.simpleName)
    }

    /**
     * Checks what the owner has decided about this person, and acts on it.
     *
     * The other half of a permission switch. An owner toggling "see money" writes
     * it on their own phone and syncs it up; until this ran, the driver's handset
     * never heard, and went on showing money it had been told to stop showing.
     * Capabilities are read from the local membership row, so bringing that row up
     * to date is the whole mechanism — the session reloads and every screen
     * watching it follows.
     *
     * Being removed is the same check with a different answer. A membership gone
     * from the server, or come back marked inactive, means this phone is no longer
     * part of that business, and it stops working from it.
     *
     * Deliberately quiet on failure. Somebody in a field with no signal keeps the
     * permissions they had this morning, which is the only workable answer: the
     * server is the authority, and the rules refuse what this device must not do
     * regardless of what it still believes.
     */
    suspend fun refreshMembership(
        restore: RemoteRestore,
        applier: RestoreApplier,
    ): MembershipRefresh {
        val personId = prefs.getString(KEY_PERSON, null) ?: return MembershipRefresh.Unchanged
        val orgId = prefs.getString(KEY_ORG, null) ?: return MembershipRefresh.Unchanged
        // The practice business is on this phone alone; there is nothing on a server to ask.
        if (isSample(orgId)) return MembershipRefresh.Unchanged
        val local = db.orgDao().membership(orgId, personId) ?: return MembershipRefresh.Unchanged

        val remote = try {
            restore.fetchMyMembership(orgId, personId)
        } catch (failure: Exception) {
            return MembershipRefresh.Unchanged
        }

        // Nothing came back. See [noAnswerIsNotRemoval]: this used to be read as
        // removal, and it is the most expensive mistake in the file.
        if (remote == null) return MembershipRefresh.Unchanged

        // Through the same applier as a restore, so it inherits the rule that
        // matters: a change this phone has not managed to send yet is never
        // overwritten. A rename made in a field this morning outranks the
        // server's older copy of the same row, and the rules refuse anything this
        // device should not be doing regardless of what it believes.
        applier.apply(listOf(remote))
        val applied = db.orgDao().membership(orgId, personId)
            ?: return MembershipRefresh.Unchanged

        if (!applied.active) return leaveOrg(orgId)

        val changed = applied.role != local.role ||
            applied.canSeeMoney != local.canSeeMoney ||
            applied.canAddCustomers != local.canAddCustomers ||
            applied.canCloseBills != local.canCloseBills

        if (!changed) return MembershipRefresh.Unchanged

        _startup.value = Startup.Loading
        load()
        return MembershipRefresh.PermissionsChanged
    }

    /**
     * Stops working from a business this person is no longer in.
     *
     * The work itself is left on the phone rather than wiped. It is a record of
     * what somebody did, some of it possibly not yet sent, and deleting it the
     * moment an owner clicks remove would destroy evidence of work that was really
     * done — including work that is still owed for. The session simply stops
     * pointing at that organisation, and the server refuses it from here on.
     */
    private suspend fun leaveOrg(orgId: String): MembershipRefresh {
        // Whatever is still queued for that business can never be accepted now.
        // Left in the queue, the first of them would block it for ever — and a
        // driver who works for two businesses would find the second one stop
        // syncing because of the one he was let go from. Set aside, not deleted.
        db.outboxDao().setAsideForOrg(orgId, clock.now())
        // Read this business from the beginning if they are ever let back in.
        // Resuming behind the old mark would silently skip everything that
        // happened while they were gone.
        marks.forget(orgId)
        prefs.edit()
            .remove(KEY_ORG)
            // Remembered so the next screen can say which business, and why they
            // are suddenly being asked for a code again.
            .putString(KEY_REMOVED_FROM, orgId)
            .apply()
        _startup.value = Startup.Loading
        load()
        return MembershipRefresh.Removed(orgId)
    }

    /**
     * Why an empty answer never takes a business away from anybody.
     *
     * A missing membership document reads like proof of removal, and for a while
     * this file treated it that way. It is not proof of anything. Firestore
     * answers a read out of its local cache when it cannot reach the network, and
     * a document missing from an empty cache comes back as a document that does
     * not exist — no error, no warning, just an authoritative-looking "no". Asking
     * for [com.google.firebase.firestore.Source.SERVER] does not reliably prevent
     * it either; on a handset with a live interface and no working DNS it still
     * returns absence rather than raising, which is exactly the state a phone is
     * in at the edge of a village.
     *
     * So removal has to be something the server says, not something it fails to
     * say. An owner letting somebody go writes `active = false` and keeps the row
     * — the team screen deactivates, it does not delete — and that flag is
     * positive evidence this code can act on. Silence now changes nothing.
     *
     * The cost of this is narrow and worth naming: a membership document deleted
     * by hand outside the app will no longer eject the driver's phone by itself.
     * The rules refuse every read they attempt from that moment, so they lose
     * access either way; they simply find out by being refused rather than by
     * being told. The alternative was throwing working drivers out of businesses
     * they had never left, every time they walked out of coverage.
     */
    private val noAnswerIsNotRemoval = Unit

    /**
     * Asks the server about a business before moving into it.
     *
     * [refreshMembership] only ever looks at the business currently in use, which
     * leaves a gap exactly where it is least visible: being removed from somewhere
     * you were not working at the time. Nothing would notice until the switch had
     * already happened, and the phone would sit in a business it had been thrown
     * out of, showing its customers from the local copy while the server refused
     * every read behind it.
     *
     * Returns whether they may still work there. Unreachable counts as yes — the
     * same answer the rest of the sync gives when there is no signal, and the one
     * that lets somebody keep working in a field. The rules refuse anything this
     * phone should not be doing regardless of what it believes.
     */
    suspend fun verifyMembership(
        orgId: String,
        restore: RemoteRestore,
        applier: RestoreApplier,
    ): Boolean {
        val personId = prefs.getString(KEY_PERSON, null) ?: return false
        val remote = try {
            restore.fetchMyMembership(orgId, personId)
        } catch (failure: Exception) {
            return true
        }

        // Nothing came back, so nothing is known. See [noAnswerIsNotRemoval].
        if (remote == null) return true

        applier.apply(listOf(remote))
        val applied = db.orgDao().membership(orgId, personId)
        if (applied?.active == true) return true

        db.outboxDao().setAsideForOrg(orgId, clock.now())
        marks.forget(orgId)
        return false
    }

    /**
     * Stops this phone working for anybody, because the account has been turned off.
     *
     * Every business at once, which is what makes it different from being removed
     * from one. The work stays on the handset and the queue is parked rather than
     * thrown away: an account turned off by mistake, or turned on again later, has
     * lost nothing. What it cannot do is keep sending.
     *
     * Returns whether this changed anything, so a caller can avoid tearing the
     * session down again on every sync once it is already locked.
     */
    suspend fun lockOutDisabledAccount(): Boolean {
        if (prefs.getBoolean(KEY_DISABLED, false)) return false
        db.outboxDao().setAsideEverything(clock.now())
        prefs.edit()
            .putBoolean(KEY_DISABLED, true)
            .remove(KEY_ORG)
            .apply()
        _startup.value = Startup.Loading
        load()
        return true
    }

    fun accountDisabled(): Boolean = prefs.getBoolean(KEY_DISABLED, false)

    /**
     * Lifted once the server says the account is live again.
     *
     * Only ever called with an answer from the server in hand. A lock the handset
     * can lift by itself is not a lock.
     */
    suspend fun clearAccountDisabled() {
        if (!prefs.getBoolean(KEY_DISABLED, false)) return
        val edit = prefs.edit().remove(KEY_DISABLED)

        // Locking out cleared the business they were in, so putting the account
        // back has to point them at one again. Without this they would come back
        // to the wizard that asks somebody to name their first business — which,
        // to a driver who works for three, reads as though the app has thrown
        // all of them away.
        if (prefs.getString(KEY_ORG, null) == null) {
            prefs.getString(KEY_PERSON, null)
                ?.let { db.orgDao().firstActiveMembershipFor(it) }
                ?.let { edit.putString(KEY_ORG, it.orgId) }
        }
        edit.apply()

        _startup.value = Startup.Loading
        load()
    }

    /** The business this person was last removed from, if they were. */
    fun removedFrom(): String? = prefs.getString(KEY_REMOVED_FROM, null)

    /** Cleared once they have been told. */
    fun clearRemovedNotice() {
        prefs.edit().remove(KEY_REMOVED_FROM).apply()
    }

    /**
     * Identities this install used to have, and has since proved its way out of.
     *
     * Never cleared: the stale documents they name stay on the server for good,
     * so the phone has to go on recognising them for good.
     */
    fun adoptedAway(): Set<String> =
        prefs.getStringSet(KEY_ADOPTED_AWAY, emptySet()).orEmpty()

    /**
     * Pairs an owner has said are two different people.
     *
     * Kept so the question is asked once. Two brothers sharing a family phone
     * is ordinary, and a prompt that cannot be settled is one that gets ignored.
     */
    fun dismissedDuplicates(): Set<String> =
        prefs.getStringSet(KEY_NOT_DUPLICATES, emptySet()).orEmpty()

    fun rememberNotDuplicates(a: String, b: String) {
        prefs.edit()
            .putStringSet(KEY_NOT_DUPLICATES, dismissedDuplicates() + listOf(a, b).sorted().joinToString("|"))
            .apply()
    }

    /** The signed-in identity, before any business is chosen. */
    fun personIdOrNull(): String? = prefs.getString(KEY_PERSON, null)

    /** The number this device signed in with, for screens that show it back. */
    fun signedInPhone(): String? = prefs.getString(KEY_PHONE, null)

    /** The Google account this person signed in with, when that is how they came in. */
    fun signedInEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Remembers a Google account added to an existing sign-in, so Profile can say so. */
    fun recordLinkedEmail(email: String?) {
        prefs.edit().apply { if (email != null) putString(KEY_EMAIL, email) else remove(KEY_EMAIL) }.apply()
    }

    /**
     * Signs this person out of the app.
     *
     * The local database is deliberately left alone. Two reasons, and the first is
     * the one that decides it: the outbox may still be holding work nobody has
     * sent, and wiping on sign-out would destroy a day in the field to tidy up a
     * screen. The second is that it is not needed — every query is scoped by
     * organisation, so somebody else signing in on this handset lands in their own
     * business and never sees a row of this one's.
     *
     * Honest about the limit: the rows are still on the disk. That is a reasonable
     * place to stop for a shared phone in a village, and not good enough for a
     * handset that gets sold. Wiping safely needs the queue drained first, which
     * needs a network, which is exactly what cannot be assumed here.
     */
    suspend fun signOut(auth: PhoneAuth?) {
        runCatching { auth?.signOut() }
        prefs.edit()
            .remove(KEY_PERSON)
            .remove(KEY_ORG)
            .remove(KEY_PHONE)
            .remove(KEY_EMAIL)
            .apply()
        _businessMode.value = false
        // The session is derived from startup, so this is the whole of it.
        _startup.value = Startup.NeedsSignIn
    }

    /**
     * Starts another business of this person's own, in its own books.
     *
     * For somebody who runs two enterprises, or works in two currencies: a business has one
     * currency for good, and the way to keep books in another is another business. The
     * person is its owner, it is made the one on screen, and nothing about the business
     * they are leaving is touched.
     */
    suspend fun startAnotherBusiness(
        businessName: String,
        landUnit: MeasureUnit,
        currencyCode: String,
        moneyStepMinor: Long,
        fiscalStartMonth: Int,
        /** A practice business: marked before anything is queued, so nothing of it ever leaves the phone. */
        sample: Boolean = false,
        ownLandUnit: String? = null,
    ): String? {
        val personId = prefs.getString(KEY_PERSON, null) ?: return null
        val displayName = current()?.displayName ?: return null
        val now = clock.now()
        val orgId = ids.newId()
        if (sample) prefs.edit().putString(KEY_SAMPLE_ORG, orgId).apply()
        val org = OrgEntity(
            id = orgId,
            name = businessName.trim(),
            defaultLandUnit = landUnit,
            ownLandUnit = ownLandName(ownLandUnit),
            currencyCode = currencyCode,
            moneyStepMinor = moneyStepMinor,
            fiscalStartMonth = fiscalStartMonth,
            createdAt = now,
            updatedAt = now,
        )
        val membership = MembershipEntity(
            id = membershipId(orgId, personId),
            orgId = orgId,
            personId = personId,
            displayName = displayName,
            role = MemberRole.Owner,
            canSeeMoney = true,
            canAddCustomers = true,
            canCloseBills = true,
            joinedAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            db.orgDao().upsertOrg(org)
            db.orgDao().upsertMembership(membership)
        }
        syncQueue.enqueueUpsert(orgId, SyncQueue.TABLE_ORGS, orgId, OutboxJson.encodeToString(org))
        syncQueue.enqueueMembership(
            orgId = orgId,
            personId = personId,
            membershipId = membership.id,
            payloadJson = OutboxJson.encodeToString(membership),
            indexJson = OutboxJson.encodeToString(MemberOrgDoc(personId, orgId, businessName.trim(), now)),
        )
        switchTo(orgId)
        return orgId
    }

    /** The practice business on this phone, if one was made. It exists nowhere else. */
    fun sampleOrgId(): String? = prefs.getString(KEY_SAMPLE_ORG, null)

    fun isSample(orgId: String): Boolean = orgId == sampleOrgId()

    /**
     * Takes the practice business off this phone: every row it owns, and the mark. If it
     * was the one on screen, the person's own business takes its place. Nothing of it was
     * ever sent anywhere, so there is nothing to undo elsewhere.
     */
    suspend fun removeSample() {
        val orgId = sampleOrgId() ?: return
        val personId = prefs.getString(KEY_PERSON, null)
        val next = personId?.let { id ->
            db.orgDao().membershipsOf(id).firstOrNull { it.active && it.orgId != orgId }?.orgId
        }
        db.withTransaction { db.sampleDao().wipe(orgId) }
        prefs.edit().remove(KEY_SAMPLE_ORG).apply()
        if (prefs.getString(KEY_ORG, null) == orgId && next != null) switchTo(next)
    }

    /** What came of asking to delete the account. */
    enum class DeletionResult { Done, CouldNotReachServer }

    /** The businesses that would go with the account: this person's alone, with nobody else to run them. */
    suspend fun businessesOwnedAlone(): List<String> {
        val personId = prefs.getString(KEY_PERSON, null) ?: return emptyList()
        return db.orgDao().membershipsOf(personId)
            .filter { it.active && !isSample(it.orgId) && it.role == MemberRole.Owner && db.orgDao().activeOwnerCount(it.orgId) <= 1 }
            .map { it.orgId }
    }

    /**
     * Deletes this person's account and what is theirs alone.
     *
     * The request goes to the server first and the phone is only wiped once the server has
     * it, so a phone with no signal cannot lose the account on the phone while it still
     * exists everywhere else. The request names the businesses that go with it, those with
     * no other owner. After that the sign-in is deleted, everything on this phone is
     * cleared, and the app is back at its first screen.
     */
    suspend fun deleteAccount(transport: com.example.fieldtrack.data.sync.SyncTransport?, auth: PhoneAuth?): DeletionResult {
        val personId = prefs.getString(KEY_PERSON, null) ?: return DeletionResult.Done
        val memberships = db.orgDao().membershipsOf(personId)
        val ownedAlone = businessesOwnedAlone()
        val accepted = transport?.requestAccountDeletion(
            personId = personId,
            orgIds = memberships.map { it.orgId }.distinct(),
            ownedAlone = ownedAlone,
            at = clock.now(),
        ) == true
        if (!accepted) return DeletionResult.CouldNotReachServer
        runCatching { auth?.deleteAccount() }
        runCatching { auth?.signOut() }
        withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        prefs.edit().clear().apply()
        _businessMode.value = false
        Money.style = MoneyStyle()
        _startup.value = Startup.NeedsSignIn
        return DeletionResult.Done
    }

    /** How much work would be left behind by signing out right now. */
    suspend fun unsentCount(): Int = db.outboxDao().pendingCount()

    /**
     * Brings the session up to date with what has reached this phone since it was read.
     *
     * The session is a copy taken when the app loaded: what this person may do, what
     * the business is called, what land is measured in. The pull writes the owner's
     * changes to those rows and nothing told the copy, so a switch turned on for a
     * driver, a corrected business name or a different land unit stayed unseen until
     * the app was killed and reopened. Run after every pull.
     *
     * Only ever moves the session for the business it is already in. A membership
     * that has ended is left to [refreshMembership], which knows how to leave.
     */
    suspend fun refreshFromStorage() {
        val session = current() ?: return
        val membership = db.orgDao().membership(session.orgId, session.personId) ?: return
        if (!membership.active) return
        val org = db.orgDao().findOrg(session.orgId)
        val fresh = session.copy(
            displayName = membership.displayName,
            businessName = org?.name ?: session.businessName,
            role = membership.role,
            landUnit = org?.defaultLandUnit ?: session.landUnit,
            ownLandUnit = org?.ownLandUnit ?: session.ownLandUnit,
            can = Capabilities.of(membership),
            currencyCode = org?.currencyCode ?: session.currencyCode,
            moneyStepMinor = org?.moneyStepMinor ?: session.moneyStepMinor,
            fiscalStartMonth = org?.fiscalStartMonth ?: session.fiscalStartMonth,
            timeZoneId = org?.timeZoneId ?: session.timeZoneId,
            paymentMethods = org?.paymentMethods ?: session.paymentMethods,
        )
        if (fresh == session) return
        // Only if nothing moved while the rows were being read: a switch of business
        // in the meantime must not be overwritten by the one just left.
        _startup.update { now ->
            val ready = now as? Startup.Ready
            if (ready != null && ready.session == session) Startup.Ready(fresh) else now
        }
    }

    /** Move the session to another business this person belongs to. */
    suspend fun switchTo(orgId: String) {
        val personId = current()?.personId ?: return
        // Refuse to switch into an organisation this person is not a member of,
        // rather than trusting the caller — this is the privacy boundary.
        //
        // Ended memberships are refused too, and the row surviving is the whole
        // reason to say so: removal sets active = 0 and keeps the row, so that the
        // switcher can still list the business as somewhere they used to work. The
        // rows that business owns are still in this database. Letting anybody back
        // in on a row that only proves they once belonged would show them a removed
        // employer's customers and jobs out of the local cache, with the server
        // refusing every read behind it and nothing on screen saying why.
        val membership = db.orgDao().membership(orgId, personId) ?: return
        if (!membership.active) return
        prefs.edit().putString(KEY_ORG, orgId).apply()
        _startup.value = Startup.Loading
        load()
    }

    private companion object {
        const val PREFS = "fieldtrack.session"
        private const val KEY_SAMPLE_ORG = "sampleOrg"
        private const val KEY_TOUR_PENDING = "tourPending"
        private const val KEY_TOUR_SEEN = "tourSeen"
        const val KEY_PERSON = "personId"
        const val KEY_ORG = "orgId"
        const val KEY_IS_BUSINESS = "isBusiness"
        const val KEY_PER_CUSTOMER_RATES = "rememberPerCustomerRates"
        const val KEY_PHONE = "phone"
        const val KEY_EMAIL = "email"
        const val KEY_REMOVED_FROM = "removedFromOrgId"
        const val KEY_DISABLED = "accountDisabled"
        const val KEY_ADOPTED_AWAY = "adoptedAwayPersonIds"
        const val KEY_NOT_DUPLICATES = "notDuplicateMembershipPairs"
    }
}

/** A land unit's own name: a word or two, never blank. */
internal fun ownLandName(name: String?): String? = name?.trim()?.ifBlank { null }?.take(MAX_LAND_NAME)

private const val MAX_LAND_NAME = 24

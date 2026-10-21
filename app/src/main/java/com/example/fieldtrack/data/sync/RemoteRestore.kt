package com.example.fieldtrack.data.sync

/** A business this person belongs to, as the server knows it. */
data class RemoteOrg(val orgId: String, val orgName: String)

/** One row fetched back, still as the server stored it. */
data class RemoteRow(
    val table: String,
    val entityId: String,
    val fields: Map<String, Any?>,
)

/**
 * One pass of changes, with the mark to resume from next time.
 *
 * [watermark] is a server write time, never this phone's clock, and it only ever
 * moves forward. When a pass is truncated it deliberately stops short of the
 * newest row seen, so the next pass picks up the remainder — re-reading rows that
 * have already been applied is free, and missing one is not.
 */
data class PullPage(
    val rows: List<RemoteRow>,
    val watermark: Long,
    /**
     * Collections the server would not let this person read.
     *
     * Not an error. A driver without sight of wages is refused `payouts` by
     * design, and that refusal has to be survivable — reported here so the
     * caller can tell "there is nothing new" from "you were not allowed to
     * look", which matters the day somebody is allowed to look after all.
     */
    val refused: Set<String> = emptySet(),
)

sealed interface RestoreResult {
    data class Restored(val orgs: List<RemoteOrg>, val rowCount: Int) : RestoreResult

    /** Signed in fine, but the server holds nothing for this person. */
    data object NothingToRestore : RestoreResult

    data class Unreachable(val reason: String) : RestoreResult
}

/**
 * Fetching a person's work back onto a phone that has never seen it.
 *
 * The counterpart to [SyncTransport], and deliberately separate for the same
 * reason [InviteLookup] is: that one pushes a queue outward and never reads. This
 * runs once, when somebody signs in on a handset that does not have their records
 * — a new phone, or the old one wiped — and it is the difference between "your
 * work is backed up" being true and being a figure of speech.
 */
interface RemoteRestore {
    /** Which businesses this person belongs to, read from under their own id. */
    suspend fun orgsFor(personId: String): List<RemoteOrg>

    /** Everything stored under one business. */
    suspend fun fetchOrg(orgId: String): List<RemoteRow>

    /**
     * This person's own membership, as the owner last left it.
     *
     * One document, fetched often. It is how a permission granted or withdrawn on
     * the owner's phone reaches the driver's at all: without it, an owner can take
     * money off somebody's screen and watch nothing happen, because the only copy
     * that governs their app is the one on their own handset.
     *
     * Null when the server has no membership for them there — which, read plainly,
     * means they are not in that business any more.
     *
     * Which is why an implementation must **raise** rather than return null when
     * it cannot reach the server. Null is an answer, and callers act on it by
     * taking a business away from somebody. "I could not ask" is not that answer,
     * and the two must never arrive looking alike.
     */
    suspend fun fetchMyMembership(orgId: String, personId: String): RemoteRow?

    /**
     * Everything in one business written since [since].
     *
     * The other direction of the sync, and the one that was missing: until this
     * existed a driver's machines, customers and rates were frozen at the moment
     * they signed in. An owner could buy a tractor, hire somebody, change a rate
     * or correct the business name that prints on every bill, and no phone but
     * their own would ever hear about it.
     *
     * [since] of zero means "everything", which is also what the first pass after
     * an upgrade asks for.
     */
    suspend fun fetchChangesSince(orgId: String, since: Long): PullPage

    /**
     * Whether this account has been turned off outright.
     *
     * Separate from membership, because the two answer different questions. Being
     * removed from a business is that business's decision and leaves the person
     * free to work for anybody else; being disabled is about the account itself,
     * and takes every business away at once. A lost handset needs the second.
     *
     * Raises rather than guessing when the server cannot be reached: false here
     * unlocks an account, and a phone with no signal has no business doing that.
     */
    suspend fun isAccountDisabled(personId: String): Boolean
}

/** Stands in when there is no backend, so sign-in still works. */
object NoRemoteRestore : RemoteRestore {
    override suspend fun orgsFor(personId: String) = emptyList<RemoteOrg>()
    override suspend fun fetchOrg(orgId: String) = emptyList<RemoteRow>()
    override suspend fun fetchMyMembership(orgId: String, personId: String): RemoteRow? = null
    override suspend fun fetchChangesSince(orgId: String, since: Long) =
        PullPage(emptyList(), since)

    override suspend fun isAccountDisabled(personId: String) = false
}

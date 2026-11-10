package com.example.fieldtrack.data.sync

import com.example.fieldtrack.data.db.entity.InviteCodeDoc

/** What came back from asking the server about a code. */
sealed interface InviteLookupResult {
    data class Found(val invite: InviteCodeDoc) : InviteLookupResult

    /** No such code. Either mistyped, or it was withdrawn. */
    data object NotFound : InviteLookupResult

    /** The code was real but has lapsed or already been used. */
    data class NoLongerValid(val invite: InviteCodeDoc) : InviteLookupResult

    /** Could not ask. No signal, or the server did not answer. */
    data class Unreachable(val reason: String) : InviteLookupResult
}

/**
 * Looking up an invite code on the server.
 *
 * Deliberately its own interface rather than a method on [SyncTransport]. That one
 * pushes a queue of local changes outward and knows nothing about reading; this one
 * asks a single question, needs an answer now, and is the only operation in the app
 * that cannot be served from the phone. Joining a business is the one thing a driver
 * genuinely needs signal for, and keeping it separate is what stops "the app works
 * offline" quietly becoming "most of the app works offline".
 */
interface InviteLookup {
    suspend fun find(code: String): InviteLookupResult
}

/** Stands in when there is no backend, so the screen can still say something true. */
object NoInviteLookup : InviteLookup {
    override suspend fun find(code: String) =
        InviteLookupResult.Unreachable("Joining needs a server, and none is set up")
}

package com.example.fieldtrack.data.repository

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.InviteCodeDoc
import com.example.fieldtrack.data.db.entity.InviteEntity
import kotlinx.coroutines.flow.first
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

/** Why a change to the team was refused. */
sealed class TeamError(message: String) : Exception(message) {
    class NoSuchMember(id: String) : TeamError("No member $id")

    /**
     * Someone tried to change their own permissions or remove themselves.
     *
     * Not allowed from this screen at all. An owner who could switch off their own
     * access would lock themselves out of the only app holding their season's work,
     * with nobody to let them back in.
     */
    class NotOnYourself : TeamError("You cannot change your own access here")

    /** The last owner cannot be removed; the business would have nobody in charge. */
    class LastOwner : TeamError("A business must keep at least one owner")
}

/**
 * The people who work for a business.
 *
 * A membership is the join between a person and an organisation, and it carries what
 * that person may do *here* — the same driver can have a different set of permissions
 * for a different contractor, and sees nothing of one from the other.
 */
class TeamRepository(
    private val db: FieldTrackDatabase,
    private val syncQueue: SyncQueue,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val org = db.orgDao()

    /** Invite codes must not be guessable, so this is not [java.util.Random]. */
    private val secureRandom by lazy { java.security.SecureRandom() }

    fun observeTeam(orgId: String): Flow<List<MembershipEntity>> = org.observeTeam(orgId)

    // -------------------------------------------------------------------- invites

    /** The code currently being handed out, or null when there is none. */
    fun observeOpenInvite(orgId: String): Flow<InviteEntity?> =
        org.observeOpenInvite(orgId, clock.now())

    /**
     * Makes a code a driver can type to join this business.
     *
     * Only one is open at a time: an owner who presses "new code" means the old one
     * should stop working, and a pile of live codes nobody is tracking is how a
     * driver who was let go a season ago walks back in.
     *
     * The permissions are decided here, by the person doing the inviting, and
     * travel with the code. Granting them at redemption instead would mean the
     * driver's own device deciding what the driver may do.
     */
    suspend fun createInvite(
        orgId: String,
        orgName: String,
        can: Capabilities,
        canSeeMoney: Boolean = false,
        canAddCustomers: Boolean = false,
        canCloseBills: Boolean = false,
        randomInt: (Int) -> Int = { secureRandom.nextInt(it) },
    ): InviteEntity {
        can.require(Capability.EditTeam)
        val now = clock.now()
        val invite = InviteEntity(
            id = ids.newId(),
            orgId = orgId,
            code = generateInviteCode(randomInt),
            role = MemberRole.Operator,
            canSeeMoney = canSeeMoney,
            canAddCustomers = canAddCustomers,
            canCloseBills = canCloseBills,
            createdAt = now,
            expiresAt = now + INVITE_LIFETIME_MILLIS,
        )
        db.withTransaction {
            // Whatever was open stops working, including its public code.
            org.observeOpenInvite(orgId, now).first()?.let { previous ->
                syncQueue.enqueueDelete(orgId, SyncQueue.TABLE_INVITE_CODES, previous.code)
            }
            org.closeOpenInvites(orgId, now)
            org.upsertInvite(invite)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_INVITES, invite.id, OutboxJson.encodeToString(invite)
            )
            // And the public half, which is the only part a driver can reach before
            // they are a member of anything.
            syncQueue.enqueueUpsert(
                orgId,
                SyncQueue.TABLE_INVITE_CODES,
                invite.code,
                OutboxJson.encodeToString(invite.asPublicCode(orgName)),
            )
        }
        return invite
    }

    /** Stops a code working without issuing another. */
    suspend fun revokeInvites(orgId: String, can: Capabilities) {
        can.require(Capability.EditTeam)
        val now = clock.now()
        db.withTransaction {
            val open = org.observeOpenInvite(orgId, now).first()
            org.closeOpenInvites(orgId, now)
            open?.let {
                val closed = it.copy(expiresAt = now)
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_INVITES, closed.id, OutboxJson.encodeToString(closed)
                )
                // Taking the public code down is the part that actually stops it:
                // the private row is only ever read by people already inside.
                syncQueue.enqueueDelete(orgId, SyncQueue.TABLE_INVITE_CODES, it.code)
            }
        }
    }

    /**
     * Joins the business a code belongs to, as the person holding this device.
     *
     * The membership is created with the caller's own authenticated id, never one
     * the inviting owner chose. That is the whole difference between this and the
     * hand-entered member the team screen makes today: an owner can say "a driver
     * called Jaswant works here", but only Jaswant's phone can say "and it is me".
     *
     * The organisation row is written from what the code carried rather than fetched,
     * because the driver cannot read the organisation until they are in it, and they
     * are not in it until this succeeds. Everything else about the business arrives
     * when a pull sync exists; the name is enough to show them where they are.
     */
    suspend fun redeemInvite(
        invite: InviteCodeDoc,
        personId: String,
        displayName: String,
        phone: String?,
    ): MembershipEntity {
        val now = clock.now()
        val membership = MembershipEntity(
            id = membershipId(invite.orgId, personId),
            orgId = invite.orgId,
            personId = personId,
            displayName = displayName.trim(),
            phone = phone,
            role = invite.role,
            canSeeMoney = invite.canSeeMoney,
            canAddCustomers = invite.canAddCustomers,
            canCloseBills = invite.canCloseBills,
            joinedAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            // A placeholder so the business has a name on this device. A real pull
            // replaces it; until then the driver at least knows whose jobs these are.
            if (org.findOrg(invite.orgId) == null) {
                org.upsertOrg(
                    OrgEntity(
                        id = invite.orgId,
                        name = invite.orgName,
                        defaultLandUnit = MeasureUnit.Acre,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            org.upsertMembership(membership)
            syncQueue.enqueueUpsert(
                invite.orgId, SyncQueue.TABLE_MEMBERSHIPS, membership.id,
                OutboxJson.encodeToString(membership),
            )
            // Marks the code spent. Published rather than merely recorded, so the
            // next person to type it is told it is gone instead of joining twice.
            syncQueue.enqueueUpsert(
                invite.orgId,
                SyncQueue.TABLE_INVITE_CODES,
                invite.code,
                OutboxJson.encodeToString(invite.copy(redeemedAt = now)),
            )
        }
        return membership
    }

    /** The part of an invite a stranger holding the code is allowed to see. */
    private fun InviteEntity.asPublicCode(orgName: String) = InviteCodeDoc(
        code = code,
        orgId = orgId,
        orgName = orgName,
        inviteId = id,
        role = role,
        canSeeMoney = canSeeMoney,
        canAddCustomers = canAddCustomers,
        canCloseBills = canCloseBills,
        expiresAt = expiresAt,
    )

    /** Everyone ever on the books, so a driver who left is still findable. */
    fun observeEveryone(orgId: String): Flow<List<MembershipEntity>> = org.observeEveryone(orgId)

    fun observeMember(orgId: String, membershipId: String): Flow<MembershipEntity?> =
        org.observeMembershipById(orgId, membershipId)

    suspend fun find(orgId: String, membershipId: String): MembershipEntity? =
        org.membershipById(orgId, membershipId)

    /**
     * Put a driver on the books.
     *
     * Creates the person as well as the membership: until there is a server to
     * authenticate against, a driver exists because their owner says so. The id
     * generated here is what their own install adopts when they join.
     */
    suspend fun addMember(
        orgId: String,
        can: Capabilities,
        displayName: String,
        phone: String? = null,
        canSeeMoney: Boolean = false,
        canAddCustomers: Boolean = false,
        canCloseBills: Boolean = false,
    ): String {
        can.require(Capability.EditTeam)
        val now = clock.now()
        // A placeholder identity until this person signs in and redeems an invite.
        // Their real one is their auth subject, which only they can prove.
        val placeholderPersonId = ids.newId()
        val membership = MembershipEntity(
            id = membershipId(orgId, placeholderPersonId),
            orgId = orgId,
            personId = placeholderPersonId,
            displayName = displayName.trim(),
            phone = phone?.trim()?.ifBlank { null },
            role = MemberRole.Operator,
            canSeeMoney = canSeeMoney,
            canAddCustomers = canAddCustomers,
            canCloseBills = canCloseBills,
            joinedAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            org.upsertMembership(membership)
            enqueue(membership)
        }
        return membership.id
    }

    /**
     * Change a driver's name, number, or what they are trusted with.
     *
     * [bySelfPersonId] is the person making the change, and is refused if it is the
     * same person as the one being changed — see [TeamError.NotOnYourself].
     */
    suspend fun updateMember(
        orgId: String,
        can: Capabilities,
        membershipId: String,
        bySelfPersonId: String,
        displayName: String,
        phone: String?,
        canSeeMoney: Boolean,
        canAddCustomers: Boolean,
        canCloseBills: Boolean,
    ) {
        can.require(Capability.EditTeam)
        db.withTransaction {
            val existing = org.membershipById(orgId, membershipId)
                ?: throw TeamError.NoSuchMember(membershipId)
            if (existing.personId == bySelfPersonId) throw TeamError.NotOnYourself()

            val updated = existing.copy(
                displayName = displayName.trim().ifBlank { existing.displayName },
                phone = phone?.trim()?.ifBlank { null },
                canSeeMoney = canSeeMoney,
                canAddCustomers = canAddCustomers,
                canCloseBills = canCloseBills,
                updatedAt = clock.now(),
            )
            org.upsertMembership(updated)
            enqueue(updated)
        }
    }

    /**
     * Take a driver off the books, or put them back on.
     *
     * Deactivates rather than deletes: their jobs, their hours and the money they
     * brought in are part of the business's record, and a driver who leaves in
     * October does not erase the work they did in June.
     */
    suspend fun setActive(
        orgId: String,
        can: Capabilities,
        membershipId: String,
        bySelfPersonId: String,
        active: Boolean,
    ) {
        can.require(Capability.EditTeam)
        db.withTransaction {
            val existing = org.membershipById(orgId, membershipId)
                ?: throw TeamError.NoSuchMember(membershipId)
            if (existing.personId == bySelfPersonId) throw TeamError.NotOnYourself()

            if (!active && existing.role == MemberRole.Owner && org.activeOwnerCount(orgId) <= 1) {
                throw TeamError.LastOwner()
            }

            val now = clock.now()
            val updated = existing.copy(
                active = active,
                leftAt = if (active) null else now,
                updatedAt = now,
            )
            org.upsertMembership(updated)
            enqueue(updated)
        }
    }

    private suspend fun enqueue(membership: MembershipEntity) = syncQueue.enqueueUpsert(
        membership.orgId,
        SyncQueue.TABLE_MEMBERSHIPS,
        membership.id,
        OutboxJson.encodeToString(membership),
    )
}

/** How long a code stays good: long enough to reach somebody, short enough to lapse. */
private const val INVITE_LIFETIME_MILLIS = 7L * 24 * 60 * 60 * 1000

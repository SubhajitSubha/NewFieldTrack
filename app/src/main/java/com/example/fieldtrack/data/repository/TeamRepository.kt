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
import com.example.fieldtrack.data.db.entity.MemberOrgDoc
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import com.example.fieldtrack.data.auth.indianMobileDigits
import com.example.fieldtrack.data.db.JobStatus
import kotlinx.coroutines.flow.map
import com.example.fieldtrack.data.db.TeamEventKind
import com.example.fieldtrack.data.db.entity.TeamEventEntity

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

    /**
     * Typing a code for a business you are already in.
     *
     * Refused rather than treated as a re-join, because redeeming writes the
     * membership the code describes over whatever is there. An owner who pastes
     * their own driver code to see what it looks like would be demoted to a
     * driver in their own business by doing so, and with the server refusing
     * self-promotion there is no way back from inside the app.
     */
    class AlreadyInBusiness(val orgId: String) :
        TeamError("You already work for this business")
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
    /**
     * Stops a job that is still running when its driver is taken off the team.
     *
     * A lambda rather than the work repository itself, because the team side has
     * no other business knowing about jobs, and the default no-op keeps this
     * class constructible on its own.
     */
    private val pauseLiveJob: suspend (orgId: String, jobId: String) -> Unit = { _, _ -> },
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
        /** Who sent it, for the log. Absent only where nobody is signed in. */
        byPersonId: String? = null,
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
            record(
                orgId = orgId,
                kind = TeamEventKind.CodeSent,
                by = byPersonId?.let { org.membership(orgId, it) },
                detail = invite.code,
            )

            // Codes issued earlier are left alone.
            //
            // Making one used to retire every other, public code and all. An
            // owner taking on a crew hands a different code to each driver, and
            // the second one silently killed the first — so the driver who was
            // still typing the message you sent yesterday was told his code was
            // not good, with nothing anywhere to explain it. Each code stands on
            // its own and carries its own terms; [revokeInvite] takes one down.
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

    fun observeOpenInvites(orgId: String): Flow<List<InviteEntity>> =
        // Filtered here, not in SQL: the clock is read again on every emission,
        // so a code withdrawn while somebody is looking at the list leaves it.
        org.observeUnusedInvites(orgId).map { invites ->
            invites.filter { it.expiresAt > clock.now() }
        }

    fun observeRedeemedInvites(orgId: String): Flow<List<InviteEntity>> =
        org.observeRedeemedInvites(orgId)

    /**
     * Takes one code down, leaving the others working.
     *
     * The public document is deleted rather than marked: it is the only copy
     * anybody outside the business can reach, and a code that still answers is
     * still a way in however the private row is flagged.
     */
    suspend fun revokeInvite(
        orgId: String,
        inviteId: String,
        can: Capabilities,
        byPersonId: String? = null,
    ) {
        can.require(Capability.EditTeam)
        db.withTransaction {
            val invite = org.inviteById(inviteId) ?: return@withTransaction
            if (invite.orgId != orgId) return@withTransaction
            val now = clock.now()
            val closed = invite.copy(expiresAt = now)
            org.upsertInvite(closed)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_INVITES, closed.id, OutboxJson.encodeToString(closed)
            )
            syncQueue.enqueueDelete(orgId, SyncQueue.TABLE_INVITE_CODES, invite.code)
            record(
                orgId = orgId,
                kind = TeamEventKind.CodeCancelled,
                by = byPersonId?.let { org.membership(orgId, it) },
                detail = invite.code,
            )
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
            // Ten digits, like every phone number the app stores. What arrives
            // here is the signed-in identity in full international form, and
            // keeping it that way left two spellings of the same number in one
            // column — which is how a member screen came to read "+91 +9190000...".
            phone = phone?.let { indianMobileDigits(it) }?.ifBlank { null },
            role = invite.role,
            canSeeMoney = invite.canSeeMoney,
            canAddCustomers = invite.canAddCustomers,
            canCloseBills = invite.canCloseBills,
            joinedAt = now,
            // The proof travels with the membership, because the server cannot take
            // "a driver says he was invited" on trust.
            joinedWithCode = invite.code,
            updatedAt = now,
        )
        db.withTransaction {
            // Checked inside the transaction, so two taps in quick succession
            // cannot both find nothing and both write.
            // Only a LIVE membership blocks this. Removal keeps the row with
            // active = false so the business stays in their history, and a driver
            // taken off in April and wanted again in June must be able to type the
            // new code — refusing on the mere existence of the row would make
            // every removal permanent.
            org.membership(invite.orgId, personId)?.takeIf { it.active }?.let {
                throw TeamError.AlreadyInBusiness(invite.orgId)
            }

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
            // The membership is queued FIRST, and the order is not cosmetic.
            //
            // The outbox sends strictly in order, and every rule guarding this
            // business asks whether the caller is a member by fetching their
            // membership document from the server. Until that document lands,
            // the joiner is a stranger. A team event queued ahead of it is
            // therefore refused — permanently, since nothing about it will ever
            // change — and because a refusal at the head stops the queue, a
            // driver who had just joined could never sync anything at all. The
            // first thing they ever recorded would be the last.
            syncQueue.enqueueMembership(
                orgId = invite.orgId,
                personId = personId,
                membershipId = membership.id,
                payloadJson = OutboxJson.encodeToString(membership),
                indexJson = OutboxJson.encodeToString(
                    MemberOrgDoc(personId, invite.orgId, invite.orgName, now)
                ),
            )
            // Somebody joining acts on their own behalf, so they are the actor.
            record(
                orgId = invite.orgId,
                kind = TeamEventKind.Joined,
                by = membership,
                detail = invite.code,
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
        canEditTeam: Boolean = false,
        canEditMachines: Boolean = false,
        canPayOut: Boolean = false,
        canSeePeopleCost: Boolean = false,
        /** Null leaves the role alone; passing one promotes or demotes. */
        role: MemberRole? = null,
    ) {
        can.require(Capability.EditTeam)
        db.withTransaction {
            val existing = org.membershipById(orgId, membershipId)
                ?: throw TeamError.NoSuchMember(membershipId)
            if (existing.personId == bySelfPersonId) throw TeamError.NotOnYourself()

            val newRole = role ?: existing.role
            // The same guard deactivation has, for the same reason: a business
            // with nobody who can change anything is a business nobody can fix.
            // Unreachable today, since you cannot do this to yourself and so there
            // is always another owner left — but the invariant is worth stating
            // where it is enforced rather than inferred from two other rules.
            if (existing.role == MemberRole.Owner && newRole != MemberRole.Owner &&
                org.activeOwnerCount(orgId) <= 1
            ) {
                throw TeamError.LastOwner()
            }

            val actor = org.membership(orgId, bySelfPersonId)
            val updated = existing.copy(
                displayName = displayName.trim().ifBlank { existing.displayName },
                phone = phone?.trim()?.ifBlank { null },
                role = newRole,
                // An owner has every capability by definition, so the switches
                // stop meaning anything the moment somebody is promoted. Storing
                // them as granted keeps the row honest: demote them later and they
                // do not silently come back as a driver who may do nothing.
                // An owner has every capability by definition, so the switches
                // stop meaning anything while somebody holds the rank. Stored as
                // granted keeps the row honest about what they can presently do.
                //
                // Coming back down, they are dropped. The screen shows an owner's
                // switches as on — it has to, they are — so a demotion saves
                // whatever was on screen, and the man you just took the rank from
                // kept full sight of the money with every switch lit. Taking the
                // rank away takes away what came with it; an owner who wants him
                // to keep the money can switch it back on deliberately, which is
                // the only way that decision should ever get made.
                canSeeMoney = whenDemoted(existing, newRole) ?: canSeeMoney,
                canAddCustomers = whenDemoted(existing, newRole) ?: canAddCustomers,
                canCloseBills = whenDemoted(existing, newRole) ?: canCloseBills,
                canEditTeam = whenDemoted(existing, newRole) ?: canEditTeam,
                canEditMachines = whenDemoted(existing, newRole) ?: canEditMachines,
                canPayOut = whenDemoted(existing, newRole) ?: canPayOut,
                canSeePeopleCost = whenDemoted(existing, newRole) ?: canSeePeopleCost,
                updatedAt = clock.now(),
            )
            org.upsertMembership(updated)
            enqueue(updated)

            // Only the changes that alter what somebody may do. A corrected
            // spelling is not worth a line in a log about trust.
            if (updated.role != existing.role) {
                record(
                    orgId = orgId,
                    kind = if (updated.role == MemberRole.Owner) {
                        TeamEventKind.MadeOwner
                    } else {
                        TeamEventKind.SteppedBack
                    },
                    by = actor,
                    about = updated,
                )
            } else if (
                updated.canSeeMoney != existing.canSeeMoney ||
                updated.canAddCustomers != existing.canAddCustomers ||
                updated.canCloseBills != existing.canCloseBills ||
                updated.canEditTeam != existing.canEditTeam ||
                updated.canEditMachines != existing.canEditMachines ||
                updated.canPayOut != existing.canPayOut ||
                updated.canSeePeopleCost != existing.canSeePeopleCost
            ) {
                record(
                    orgId = orgId,
                    kind = TeamEventKind.PermissionsChanged,
                    by = actor,
                    about = updated,
                    // The one people regret, so it is the one named outright.
                    detail = if (updated.canSeeMoney && !existing.canSeeMoney) {
                        TEAM_EVENT_MONEY
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * Steps out of a business of your own accord.
     *
     * Not [setActive], which is an owner removing somebody else and quite rightly
     * refuses to act on yourself. This is the other direction and needs no
     * permission at all: nobody should need their boss's agreement to stop working
     * for them. The last owner still cannot leave, because a business with no
     * owner can never be changed again by anyone.
     */
    suspend fun leaveOrg(orgId: String, personId: String) {
        db.withTransaction {
            val mine = org.membership(orgId, personId)
                ?: throw TeamError.NoSuchMember(personId)
            if (mine.role == MemberRole.Owner && org.activeOwnerCount(orgId) <= 1) {
                throw TeamError.LastOwner()
            }
            val now = clock.now()
            val left = mine.copy(active = false, leftAt = now, updatedAt = now)
            org.upsertMembership(left)
            enqueue(left)
        }
    }

    /**
     * Puts two records of one person back together.
     *
     * An owner writes a driver down by hand before that driver has a phone, and
     * the same man later joins with a code under an identity only he can prove.
     * Nothing links them: the business ends up with two rows wearing one name,
     * his season split across both, and no way to see what he actually did.
     *
     * The work moves onto the proven identity and the hand-written row is
     * retired. The row is kept rather than deleted, as every retired membership
     * is, so nothing that still points at it is left pointing at nothing.
     *
     * Every moved row is sent again afterwards. Without that the merge would be
     * a local tidy-up only: the server would go on holding the work under the
     * old id, and the next phone to read the business from scratch would find
     * the two of them split apart exactly as before.
     */
    suspend fun mergePeople(
        orgId: String,
        can: Capabilities,
        keepMembershipId: String,
        absorbMembershipId: String,
    ) {
        can.require(Capability.EditTeam)
        db.withTransaction {
            val keep = org.membershipById(orgId, keepMembershipId)
                ?: throw TeamError.NoSuchMember(keepMembershipId)
            val absorb = org.membershipById(orgId, absorbMembershipId)
                ?: throw TeamError.NoSuchMember(absorbMembershipId)
            if (keep.id == absorb.id) throw TeamError.NotOnYourself()

            val from = absorb.personId
            val to = keep.personId

            db.jobDao().adoptJobs(from, to)
            db.jobDao().adoptJobEvents(from, to)
            db.machineDao().adoptFuel(from, to)
            db.machineDao().adoptBreakdowns(from, to)
            db.ledgerDao().adoptEntries(from, to)
            org.adoptInvites(from, to)

            // Read back under the new id and queued, so the server is brought
            // into line rather than left holding the old split.
            db.jobDao().jobsBy(to).forEach {
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_JOBS, it.id, OutboxJson.encodeToString(it)
                )
            }
            db.jobDao().eventsBy(to).forEach {
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_JOB_EVENTS, it.id, OutboxJson.encodeToString(it)
                )
            }
            db.machineDao().fuelBy(to).forEach {
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_FUEL, it.id, OutboxJson.encodeToString(it)
                )
            }
            db.machineDao().breakdownsBy(to).forEach {
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_BREAKDOWNS, it.id, OutboxJson.encodeToString(it)
                )
            }
            db.ledgerDao().entriesBy(to).forEach {
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_LEDGER, it.id, OutboxJson.encodeToString(it)
                )
            }

            val now = clock.now()
            val retired = absorb.copy(active = false, leftAt = now, updatedAt = now)
            org.upsertMembership(retired)
            enqueue(retired)
            record(orgId, TeamEventKind.Merged, by = keep, about = retired)
        }
    }

    /**
     * Makes somebody else an owner, and optionally steps back from it yourself.
     *
     * One operation rather than two, because the halves are not independent.
     * Promoting alone is safe and useful; stepping back alone is impossible, and
     * the app refuses it — a business with nobody in charge is a business nobody
     * can fix. Done together and in this order there is never a moment with no
     * owner, even if the phone dies between the two writes: the promotion lands
     * first.
     *
     * The reason to offer it at all is that a business presently lives or dies
     * with one handset. Lose that phone and there is no way back in from inside
     * the app; the only remedy is somebody with console access. A second owner
     * is the difference between an inconvenience and a lost season.
     */
    suspend fun handOverOwnership(
        orgId: String,
        can: Capabilities,
        toMembershipId: String,
        bySelfPersonId: String,
        stepBack: Boolean,
    ) {
        can.require(Capability.EditTeam)
        db.withTransaction {
            val heir = org.membershipById(orgId, toMembershipId)
                ?: throw TeamError.NoSuchMember(toMembershipId)
            if (heir.personId == bySelfPersonId) throw TeamError.NotOnYourself()
            if (!heir.active) throw TeamError.NoSuchMember(toMembershipId)

            val now = clock.now()
            val promoted = heir.copy(
                role = MemberRole.Owner,
                canEditTeam = true,
                canEditMachines = true,
                canPayOut = true,
                canSeePeopleCost = true,
                // An owner has every capability by definition. Written down
                // rather than implied, so stepping them back later does not
                // silently restore a driver who may do nothing.
                canSeeMoney = true,
                canAddCustomers = true,
                canCloseBills = true,
                updatedAt = now,
            )
            org.upsertMembership(promoted)
            enqueue(promoted)
            val actor = org.membership(orgId, bySelfPersonId)
            record(orgId, TeamEventKind.MadeOwner, by = actor, about = promoted)

            if (!stepBack) return@withTransaction

            val mine = org.membership(orgId, bySelfPersonId)
                ?: throw TeamError.NoSuchMember(bySelfPersonId)
            // Counted after the promotion, which is the point of the ordering:
            // there are two owners by now, so standing down leaves one.
            if (org.activeOwnerCount(orgId) <= 1) throw TeamError.LastOwner()
            val steppedBack = mine.copy(
                role = MemberRole.Operator,
                // Standing down takes with it everything the rank carried.
                canSeeMoney = false,
                canAddCustomers = false,
                canCloseBills = false,
                canEditTeam = false,
                canEditMachines = false,
                canPayOut = false,
                canSeePeopleCost = false,
                updatedAt = now,
            )
            org.upsertMembership(steppedBack)
            enqueue(steppedBack)
            record(orgId, TeamEventKind.SteppedBack, by = steppedBack, about = promoted)
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
            record(
                orgId = orgId,
                kind = if (active) TeamEventKind.TakenBack else TeamEventKind.Removed,
                by = org.membership(orgId, bySelfPersonId),
                about = updated,
            )
        }

        // Outside the transaction above, because pausing opens its own.
        if (!active) stopWorkLeftRunning(orgId, membershipId)
    }

    /**
     * Ends work the person was in the middle of when they were taken off.
     *
     * Removing somebody used to touch their membership and nothing else, which
     * left whatever they were driving running for ever: the job stayed in the
     * owner's live list with a clock still going, the machine stayed Working
     * because a live job says it is, and the finish could never arrive — their
     * phone is cut off from this business the moment the removal reaches it, and
     * anything it had not sent is set aside there.
     *
     * Paused rather than finished, because only a person can say what the work
     * was worth. It stays in the live list, where the owner can close it off
     * properly and free the machine.
     */
    private suspend fun stopWorkLeftRunning(orgId: String, membershipId: String) {
        val personId = org.membershipById(orgId, membershipId)?.personId ?: return
        val live = db.jobDao().liveJobFor(orgId, personId) ?: return
        if (live.status != JobStatus.Running) return
        runCatching { pauseLiveJob(orgId, live.id) }
    }

    /**
     * `true` while somebody is an owner, `false` as they step down, null otherwise.
     *
     * Null means "leave it to the caller" — an ordinary edit of a driver, where
     * the switches on screen are the owner's actual intent.
     */
    private fun whenDemoted(existing: MembershipEntity, newRole: MemberRole): Boolean? = when {
        newRole == MemberRole.Owner -> true
        existing.role == MemberRole.Owner -> false
        else -> null
    }

    /**
     * Writes one change to the team down, and sends it on.
     *
     * Names are copied in rather than looked up later. The log is read long
     * after the fact, and a sentence about who let whom see the money has to go
     * on saying that even if either of them is renamed afterwards.
     */
    private suspend fun record(
        orgId: String,
        kind: TeamEventKind,
        by: MembershipEntity?,
        about: MembershipEntity? = null,
        detail: String? = null,
    ) {
        val event = TeamEventEntity(
            id = ids.newId(),
            orgId = orgId,
            atMillis = clock.now(),
            kind = kind,
            byPersonId = by?.personId,
            byName = by?.displayName.orEmpty(),
            aboutPersonId = about?.personId,
            aboutName = about?.displayName,
            detail = detail,
        )
        org.addTeamEvent(event)
        syncQueue.enqueueUpsert(
            orgId, SyncQueue.TABLE_TEAM_EVENTS, event.id, OutboxJson.encodeToString(event)
        )
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

/** Marks the permission change an owner is most likely to go looking for. */
const val TEAM_EVENT_MONEY = "money"

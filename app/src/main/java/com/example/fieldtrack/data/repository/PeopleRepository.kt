package com.example.fieldtrack.data.repository

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.PayBasis
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.TeamEventKind
import com.example.fieldtrack.data.db.dao.WageFacts
import com.example.fieldtrack.data.db.entity.CashHandoverEntity
import com.example.fieldtrack.data.db.entity.MemberDetailsEntity
import com.example.fieldtrack.data.db.entity.MemberPayTermsEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString

/** What one person has earned under their pay terms, what was paid, and what is left. */
data class WageBalance(
    val personId: String,
    val basis: PayBasis,
    val rateMinor: Long,
    val fromMillis: Long,
    val earnedMinor: Long,
    val paidMinor: Long,
) {
    val leftMinor: Long get() = earnedMinor - paidMinor
}

/**
 * The people side of the money: cash a team member is holding, their particulars, and how
 * they are paid with what that comes to.
 */
class PeopleRepository(
    private val db: FieldTrackDatabase,
    private val syncQueue: SyncQueue,
    private val team: TeamRepository,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val org = db.orgDao()

    // ------------------------------------------------------------ cash in hand

    /**
     * The business's cash each person is holding: what they took from farmers in cash and
     * recorded themselves, less what they have handed over. Only people holding some.
     */
    fun observeCashHeld(orgId: String): Flow<Map<String, Long>> =
        combine(org.observeCashTaken(orgId), org.observeCashHandedOver(orgId)) { taken, handed ->
            val given = handed.associate { it.personId to it.amountMinor }
            taken.associate { it.personId to it.amountMinor - (given[it.personId] ?: 0L) }
                .filterValues { it > 0 }
        }

    fun observeHandovers(orgId: String): Flow<List<CashHandoverEntity>> = org.observeHandovers(orgId)

    /** [fromPersonId] handed [amountMinor] of cash to the person writing it down. */
    suspend fun recordHandover(
        orgId: String,
        can: Capabilities,
        fromPersonId: String,
        amountMinor: Long,
        byPersonId: String?,
        note: String? = null,
    ): String {
        require(Capability.TakePayment in can || Capability.PayOut in can) { "Not one who receives the cash" }
        require(amountMinor > 0) { "A hand-over is a positive amount" }
        val handover = CashHandoverEntity(
            id = ids.newId(),
            orgId = orgId,
            fromPersonId = fromPersonId,
            amountMinor = amountMinor,
            atMillis = clock.now(),
            byPersonId = byPersonId,
            note = note?.trim()?.ifBlank { null },
            createdAt = clock.now(),
        )
        db.withTransaction {
            org.upsertHandover(handover)
            syncQueue.enqueueUpsert(orgId, SyncQueue.TABLE_CASH_HANDOVERS, handover.id, OutboxJson.encodeToString(handover))
        }
        return handover.id
    }

    /** Takes back a hand-over written down by mistake. Kept, and counted for nothing. */
    suspend fun voidHandover(orgId: String, can: Capabilities, id: String, byPersonId: String?) {
        require(Capability.TakePayment in can || Capability.PayOut in can) { "Not one who receives the cash" }
        db.withTransaction {
            val handover = org.findHandover(orgId, id) ?: return@withTransaction
            if (handover.voidedAt != null) return@withTransaction
            val voided = handover.copy(voidedAt = clock.now(), voidedByPersonId = byPersonId)
            org.upsertHandover(voided)
            syncQueue.enqueueUpsert(orgId, SyncQueue.TABLE_CASH_HANDOVERS, voided.id, OutboxJson.encodeToString(voided))
        }
    }

    // ------------------------------------------------------------ particulars

    fun observeDetails(orgId: String, personId: String): Flow<MemberDetailsEntity?> =
        org.observeMemberDetails(orgId, personId)

    /**
     * Saves a person's particulars. The person may always change their own; somebody who runs
     * the team may change anybody's, and when they do, the log says so, so nobody's pay can be
     * sent somewhere else without it being seen.
     */
    suspend fun saveDetails(
        orgId: String,
        can: Capabilities,
        personId: String,
        byPersonId: String,
        payoutUpi: String?,
        payoutBank: String?,
        licenceNumber: String?,
        licenceExpiresDay: Long?,
        emergencyName: String?,
        emergencyPhone: String?,
    ) {
        val self = personId == byPersonId
        require(self || Capability.EditTeam in can) { "Not theirs to change" }
        val details = MemberDetailsEntity(
            id = membershipId(orgId, personId),
            orgId = orgId,
            personId = personId,
            payoutUpi = payoutUpi?.trim()?.ifBlank { null },
            payoutBank = payoutBank?.trim()?.ifBlank { null },
            licenceNumber = licenceNumber?.trim()?.uppercase()?.ifBlank { null },
            licenceExpiresDay = licenceExpiresDay,
            emergencyName = emergencyName?.trim()?.ifBlank { null },
            emergencyPhone = emergencyPhone?.trim()?.ifBlank { null },
            updatedAt = clock.now(),
            updatedByPersonId = byPersonId,
        )
        db.withTransaction {
            org.upsertMemberDetails(details)
            syncQueue.enqueueUpsert(orgId, SyncQueue.TABLE_MEMBER_DETAILS, details.id, OutboxJson.encodeToString(details))
            if (!self) {
                team.record(
                    orgId, TeamEventKind.DetailsChanged,
                    by = org.membership(orgId, byPersonId),
                    about = org.membership(orgId, personId),
                )
            }
        }
    }

    // ------------------------------------------------------------ pay

    fun observePayTerms(orgId: String, personId: String): Flow<MemberPayTermsEntity?> =
        org.observePayTerms(orgId, personId)

    /** Sets how somebody is paid, from [fromMillis]. Only for whoever pays people. */
    suspend fun savePayTerms(
        orgId: String,
        can: Capabilities,
        personId: String,
        byPersonId: String?,
        basis: PayBasis,
        rateMinor: Long,
        fromMillis: Long,
    ) {
        can.require(Capability.PayOut)
        require(rateMinor > 0) { "A rate is a positive amount" }
        val terms = MemberPayTermsEntity(
            id = membershipId(orgId, personId),
            orgId = orgId,
            personId = personId,
            basis = basis,
            rateMinor = rateMinor,
            fromMillis = fromMillis,
            updatedAt = clock.now(),
            updatedByPersonId = byPersonId,
        )
        db.withTransaction {
            org.upsertPayTerms(terms)
            syncQueue.enqueueUpsert(orgId, SyncQueue.TABLE_PAY_TERMS, terms.id, OutboxJson.encodeToString(terms))
            team.record(
                orgId, TeamEventKind.PayTermsChanged,
                by = byPersonId?.let { org.membership(orgId, it) },
                about = org.membership(orgId, personId),
            )
        }
    }

    /** For each person with pay terms: what their work since then has earned, and what was paid. */
    fun observeWages(orgId: String): Flow<Map<String, WageBalance>> {
        val offsetMillis = BusinessClock.zone.rules.getOffset(java.time.Instant.now()).totalSeconds * 1000L
        return combine(
            org.observeAllPayTerms(orgId),
            org.observeWageFacts(orgId, offsetMillis),
            org.observeWagesPaid(orgId),
        ) { terms, facts, paid ->
            val factsOf = facts.associateBy { it.personId }
            val paidTo = paid.associate { it.personId to it.amountMinor }
            terms.associate { t ->
                t.personId to WageBalance(
                    personId = t.personId,
                    basis = t.basis,
                    rateMinor = t.rateMinor,
                    fromMillis = t.fromMillis,
                    earnedMinor = Wages.earnedMinor(t, factsOf[t.personId], clock.now()),
                    paidMinor = paidTo[t.personId] ?: 0L,
                )
            }
        }
    }
}

/** What work earns under a person's pay terms. Worked out, never typed. */
object Wages {
    private const val DAY_MILLIS = 86_400_000L

    fun earnedMinor(terms: MemberPayTermsEntity, facts: WageFacts?, now: Long): Long {
        val rate = terms.rateMinor
        return when (terms.basis) {
            PayBasis.PerHour -> Math.round((facts?.seconds ?: 0L) / 3600.0 * rate)
            PayBasis.PerDay -> (facts?.days ?: 0).toLong() * rate
            PayBasis.PerAcre -> Math.round((facts?.landQuantity ?: 0.0) * rate)
            // A month's pay builds up day by day, a thirtieth at a time, so the figure on any
            // day is what would be fair to pay somebody leaving that day.
            PayBasis.Monthly -> {
                val days = ((now - terms.fromMillis) / DAY_MILLIS).coerceAtLeast(0)
                rate * days / 30
            }
        }.coerceAtLeast(0)
    }
}

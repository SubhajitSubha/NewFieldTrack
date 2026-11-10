package com.example.fieldtrack.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fieldtrack.data.db.PayBasis
import com.example.fieldtrack.data.db.SyncState
import kotlinx.serialization.Serializable

/**
 * Cash a team member took from a farmer, handed over to whoever keeps the business's money.
 *
 * A driver who takes ₹4,000 in the field is holding the business's money until it is handed
 * over, and that is where the money goes missing in this trade: not in a theft, in a "I gave
 * it to you on Tuesday". What somebody holds is the cash payments they recorded less what they
 * have handed over, and every hand-over is written down by the person who received it.
 *
 * Never deleted. One recorded by mistake is taken back, like a payment, and kept.
 */
@Entity(tableName = "cash_handovers", indices = [Index("orgId"), Index("fromPersonId")])
@Serializable
data class CashHandoverEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    /** Who handed the cash over. */
    val fromPersonId: String,
    val amountMinor: Long,
    val atMillis: Long,
    /** Who received it and wrote it down. */
    val byPersonId: String? = null,
    val note: String? = null,
    val voidedAt: Long? = null,
    val voidedByPersonId: String? = null,
    val createdAt: Long,
    val syncState: SyncState = SyncState.Pending,
    val deviceId: String? = null,
)

/**
 * One team member's own particulars in one business: how to pay them, their driving licence,
 * and who to call if something happens to them.
 *
 * Kept apart from the membership, which every member can read. These are read by the owner,
 * whoever manages the team or pays people, and the person themselves. The id is the
 * membership's id, so there is one per person per business.
 */
@Entity(tableName = "member_details", indices = [Index("orgId")])
@Serializable
data class MemberDetailsEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val personId: String,
    /** A UPI id, for paying them from the app in India. */
    val payoutUpi: String? = null,
    /** Bank account, branch code and name on the account, as written. */
    val payoutBank: String? = null,
    val licenceNumber: String? = null,
    /** The last day the licence is valid, as an epoch day. */
    val licenceExpiresDay: Long? = null,
    val emergencyName: String? = null,
    val emergencyPhone: String? = null,
    val updatedAt: Long,
    /** Who last changed this, so the person can be told when somebody else did. */
    val updatedByPersonId: String? = null,
    val syncState: SyncState = SyncState.Pending,
)

/**
 * How one team member is paid: by the day, the hour, the acre or the month, at what rate,
 * from when.
 *
 * Kept where only the money people and the person can read it: what somebody earns is the
 * figure that cannot be un-seen in a crew of four. What they have earned is worked out from
 * their work since [fromMillis], never typed in.
 */
@Entity(tableName = "member_pay_terms", indices = [Index("orgId")])
@Serializable
data class MemberPayTermsEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val personId: String,
    val basis: PayBasis,
    val rateMinor: Long,
    val fromMillis: Long,
    val updatedAt: Long,
    val updatedByPersonId: String? = null,
    val syncState: SyncState = SyncState.Pending,
)

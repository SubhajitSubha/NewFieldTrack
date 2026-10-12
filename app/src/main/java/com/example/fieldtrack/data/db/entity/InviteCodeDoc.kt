package com.example.fieldtrack.data.db.entity

import com.example.fieldtrack.data.db.MemberRole
import kotlinx.serialization.Serializable

/**
 * An invite code, published where somebody who has never seen this business can
 * find it.
 *
 * Everything else the app syncs lives under `orgs/{orgId}`, which works because
 * every reader is already a member and knows which organisation they are in. A
 * driver holding a code knows neither. So this one record sits at the top level,
 * keyed by the code itself, and holds just enough to show a preview and apply the
 * right permissions — the business name to recognise, and the terms the owner set.
 *
 * Deliberately not a copy of the organisation. A code is handed out to somebody who
 * is not in the business yet, and may never join; what it exposes to them is a name
 * and nothing more. The jobs, the customers and the money stay behind the membership
 * check where they belong.
 */
@Serializable
data class InviteCodeDoc(
    val code: String,
    val orgId: String,
    /** Shown in the preview so a driver can tell they are joining the right place. */
    val orgName: String,
    val inviteId: String,
    val role: MemberRole = MemberRole.Operator,
    val canSeeMoney: Boolean = false,
    val canAddCustomers: Boolean = false,
    val canCloseBills: Boolean = false,
    val expiresAt: Long,
    val redeemedAt: Long? = null,
)

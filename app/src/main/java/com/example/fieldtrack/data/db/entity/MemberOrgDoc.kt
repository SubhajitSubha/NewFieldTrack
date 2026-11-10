package com.example.fieldtrack.data.db.entity

import kotlinx.serialization.Serializable

/**
 * A pointer from a person to a business they belong to.
 *
 * Filed under the person rather than the business, because it answers a question
 * only a freshly signed-in handset asks: "which businesses am I in?" Everything
 * else in this app is filed by organisation, which is useless to a phone that does
 * not yet know an organisation id. Security rules cannot search a collection, so
 * the answer has to live somewhere the asker can name — and the only thing they can
 * name is themselves.
 *
 * Carries the name so the restore can say whose work it is fetching before any of
 * it has arrived.
 */
@Serializable
data class MemberOrgDoc(
    val personId: String,
    val orgId: String,
    val orgName: String,
    val updatedAt: Long,
)

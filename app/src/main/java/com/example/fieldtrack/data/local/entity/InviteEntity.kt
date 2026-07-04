package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val role: String,
    val code: String,
    val method: String, // SMS, WhatsApp, Link, QR
    val state: String = "CREATED", // CREATED, PENDING, ACCEPTED, EXPIRED, CANCELLED, REJECTED
    val targetPhone: String? = null,
    val createdBy: String,
    val expiresAt: Long = System.currentTimeMillis() + 86400000 * 7, // 7 days
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val version: Int = 1
)

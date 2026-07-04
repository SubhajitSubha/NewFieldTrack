package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val actorId: String,
    val deviceId: String?,
    val action: String, // e.g., CUSTOMER_DELETED, JOB_EDITED, PAYMENT_ADDED, ROLE_CHANGED
    val entityType: String, // Customer, Job, Payment, TeamMember
    val entityId: String,
    val beforeState: String? = null, // JSON string
    val afterState: String? = null, // JSON string
    val isOffline: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING",
    val version: Int = 1
)

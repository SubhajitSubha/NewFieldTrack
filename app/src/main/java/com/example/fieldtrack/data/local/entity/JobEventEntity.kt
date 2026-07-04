package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "job_events")
data class JobEventEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val jobId: String,
    val eventType: String, // CREATED, ASSIGNED, ACCEPTED, STARTED, PAUSED, RESUMED, LOAD_ADDED, COMPLETED, APPROVED, PAID
    val description: String? = null,
    val actorId: String, // User who performed the action
    val deviceId: String? = null,
    val isOffline: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

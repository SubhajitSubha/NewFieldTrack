package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val customerId: String,
    val fieldId: String? = null,
    val machineId: String?,
    val operatorId: String?,
    val status: String = "DRAFT", // DRAFT, SCHEDULED, ASSIGNED, ACCEPTED, TRAVELLING, RUNNING, PAUSED, COMPLETED, APPROVED, PAID, ARCHIVED
    val workType: String = "Custom Work", // Ploughing, Harvesting, etc.
    val priority: String = "NORMAL", // NORMAL, HIGH, EMERGENCY
    val date: Long? = null,
    val estimatedArea: Double? = null,
    val estimatedHours: Double? = null,
    val notes: String? = null,
    val jobName: String,
    val location: String?,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val travelDistance: Double? = null,
    val startedAt: Long?,
    val completedAt: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

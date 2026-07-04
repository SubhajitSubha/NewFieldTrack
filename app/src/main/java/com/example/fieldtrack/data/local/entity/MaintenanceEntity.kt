package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maintenance")
data class MaintenanceEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val machineId: String,
    val type: String, // Engine Oil, Hydraulic Oil, Greasing, Air Filter, etc.
    val date: Long,
    val cost: Double,
    val vendorId: String?,
    val notes: String?,
    val meterReading: Double?,
    val nextDueHours: Double?,
    val nextDueDate: Long?,
    val partsChanged: String?,
    val technician: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

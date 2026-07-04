package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "breakdowns")
data class BreakdownEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val machineId: String,
    val time: Long,
    val operatorId: String?,
    val customerId: String?,
    val fieldId: String?,
    val failureType: String,
    val description: String?,
    val temporaryFix: String?,
    val finalResolution: String?,
    val downtimeHours: Double = 0.0,
    val repairCost: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

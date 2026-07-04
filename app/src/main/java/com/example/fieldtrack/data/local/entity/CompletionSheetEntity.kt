package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completion_sheets")
data class CompletionSheetEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val jobId: String,
    val machineId: String,
    val operatorId: String,
    val customerId: String,
    val fieldId: String,
    val totalTimeMs: Long,
    val pausedTimeMs: Long = 0,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val totalLoads: Int,
    val notes: String?,
    val createdBy: String? = null,
    val version: Int = 1,
    val completedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

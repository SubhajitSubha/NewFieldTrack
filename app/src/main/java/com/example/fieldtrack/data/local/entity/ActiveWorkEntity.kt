package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_work")
data class ActiveWorkEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val jobId: String,
    val machineId: String,
    val operatorId: String,
    val isPaused: Boolean = false,
    val startTime: Long,
    val totalPausedTime: Long = 0,
    val currentLoads: Int = 0,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val routePoints: String? = null,
    val distance: Double? = null,
    val duration: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

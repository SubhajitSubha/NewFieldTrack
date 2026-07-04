package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val orgId: String,
    val deviceName: String,
    val androidVersion: String,
    val appVersion: String,
    val status: String = "ACTIVE", // ACTIVE, DEACTIVATED, LOST
    val lastSync: Long? = null,
    val lastActive: Long = System.currentTimeMillis(),
    val pendingUploads: Int = 0,
    val storageUsageBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val version: Int = 1
)

package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val customerId: String,
    val name: String,
    val localNickname: String? = null,
    val village: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val area: Double?,
    val areaUnit: String? = "Acres",
    val soilType: String? = null,
    val cropName: String? = null,
    val cropVariety: String? = null,
    val lastWorkDate: Long? = null,
    val totalJobs: Int = 0,
    val totalCost: Double = 0.0,
    val totalLoads: Int = 0,
    val totalMachineHours: Double = 0.0,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

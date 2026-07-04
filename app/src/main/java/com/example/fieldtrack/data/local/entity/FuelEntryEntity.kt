package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_entries")
data class FuelEntryEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val machineId: String,
    val date: Long,
    val quantity: Double,
    val price: Double,
    val vendorId: String?,
    val operatorId: String?,
    val meterReading: Double?,
    val notes: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

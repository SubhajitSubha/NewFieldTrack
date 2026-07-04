package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val name: String,
    val type: String, // Oil, Grease, Filters, Belts, Tyres, Hydraulic Oil, Battery, etc.
    val quantity: Double,
    val minimumStock: Double,
    val vendorId: String?,
    val purchasePrice: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

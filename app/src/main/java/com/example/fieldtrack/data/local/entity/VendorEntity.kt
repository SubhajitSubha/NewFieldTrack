package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vendors")
data class VendorEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val name: String,
    val type: String, // Mechanics, Fuel Pumps, Parts Vendors, Tyre Shops, Insurance Agents, Finance Providers
    val phone: String?,
    val address: String?,
    val gst: String?,
    val outstandingBalance: Double = 0.0,
    val lastTransaction: Long?,
    val rating: Double?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

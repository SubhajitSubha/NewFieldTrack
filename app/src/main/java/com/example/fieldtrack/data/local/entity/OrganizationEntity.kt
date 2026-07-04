package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organizations")
data class OrganizationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val ownerId: String,
    val businessPhone: String? = null,
    val gstNumber: String? = null,
    val address: String? = null,
    val totalMembers: Int = 1,
    val totalMachines: Int = 0,
    val totalCustomers: Int = 0,
    val totalJobs: Int = 0,
    val subscription: String = "FREE",
    val storageUsedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val version: Int = 1
)

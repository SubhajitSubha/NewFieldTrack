package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val name: String,
    val phone: String?,
    val alternatePhone: String? = null,
    val village: String? = null,
    val panchayat: String? = null,
    val district: String? = null,
    val state: String? = null,
    val address: String?,
    val notes: String?,
    val totalJobs: Int = 0,
    val totalArea: Double = 0.0,
    val totalWorkValue: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val totalPaid: Double = 0.0,
    val paymentStatus: String = "PAID",
    val lastPaymentDate: Long? = null,
    val lastWorkDate: Long? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

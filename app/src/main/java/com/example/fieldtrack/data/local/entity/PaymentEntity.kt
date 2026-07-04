package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val customerId: String,
    val fieldId: String? = null,
    val jobId: String? = null,
    val completionSheetId: String? = null,
    val amount: Double,
    val method: String,
    val status: String = "PAID", // PENDING, PARTIAL, PAID, CANCELLED
    val referenceNumber: String? = null,
    val notes: String? = null,
    val date: Long = System.currentTimeMillis(),
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val version: Int = 1
)

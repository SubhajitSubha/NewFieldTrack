package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_insights")
data class AIInsightEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val type: String, // SUMMARY, PREDICTION, ALERT, RECOMMENDATION, INSIGHT
    val category: String, // REVENUE, MACHINE, OPERATOR, PAYMENT, MAINTENANCE
    val title: String,
    val description: String,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val date: Long = System.currentTimeMillis(),
    val relatedEntityId: String? = null,
    val relatedEntityType: String? = null,
    val isRead: Boolean = false,
    val isActionable: Boolean = false,
    val actionType: String? = null, // e.g., SCHEDULE_MAINTENANCE, REMIND_PAYMENT
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val userId: String,
    val role: String, // OWNER, MANAGER, SUPERVISOR, OPERATOR, HELPER, ACCOUNTANT, VIEWER
    val name: String,
    val phone: String?,
    val status: String = "ACTIVE", // ACTIVE, DISABLED, PENDING
    val lastActive: Long? = null,
    val currentMachineId: String? = null,
    val currentJobId: String? = null,
    val currentLocation: String? = null,
    val isOnline: Boolean = false,
    val avatarUrl: String? = null,
    val assignedJobsCount: Int = 0,
    val completedJobsCount: Int = 0,
    val workingHours: Double = 0.0,
    val paymentsGenerated: Double = 0.0,
    val joinedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val version: Int = 1
)

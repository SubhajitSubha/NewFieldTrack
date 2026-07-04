package com.example.fieldtrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "machines")
data class MachineEntity(
    @PrimaryKey
    val id: String,
    val orgId: String,
    val name: String,
    val type: String, // e.g., Tractor, Harvester
    val registrationNumber: String?,
    val active: Boolean = true,
    
    // Master Profile additions
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val engineNumber: String? = null,
    val chassisNumber: String? = null,
    val manufacturingYear: Int? = null,
    val purchaseDate: Long? = null,
    val purchasePrice: Double? = null,
    val currentEstimatedValue: Double? = null,
    val ownershipStatus: String? = null, // e.g., Owned, Financed, Leased
    val financeInformation: String? = null,
    val insuranceDetails: String? = null,
    val permitDetails: String? = null,
    val rcDetails: String? = null,
    val pollutionCertificate: String? = null,
    val fitnessCertificate: String? = null,
    
    // Expiry dates
    val insuranceExpiryDate: Long? = null,
    val permitExpiryDate: Long? = null,
    val fitnessExpiryDate: Long? = null,
    val pollutionExpiryDate: Long? = null,
    
    // Lifecycle / State
    // Available, Assigned, Working, Idle, Under Maintenance, Service Due, Fuel Required, Breakdown, Retired, Sold
    val status: String = "Available", 
    
    // Assignment Engine Tracking
    val currentOperatorId: String? = null,
    val currentJobId: String? = null,
    val currentCustomerId: String? = null,
    val currentFieldId: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING"
)

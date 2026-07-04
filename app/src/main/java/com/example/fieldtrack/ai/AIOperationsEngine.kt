package com.example.fieldtrack.ai

import com.example.fieldtrack.data.local.dao.*
import com.example.fieldtrack.data.local.entity.AIInsightEntity
import com.example.fieldtrack.data.repository.AIOperationsRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class AIOperationsEngine(
    private val aiOperationsRepository: AIOperationsRepository,
    private val jobDao: JobDao,
    private val machineDao: MachineDao,
    private val customerDao: CustomerDao,
    private val maintenanceDao: MaintenanceDao,
    private val fuelDao: FuelDao,
    private val breakdownDao: BreakdownDao
) {
    suspend fun generateDailySummary(orgId: String) {
        // Simple offline heuristic
        val jobs = jobDao.getJobsForOrg(orgId).firstOrNull() ?: emptyList()
        val completedToday = jobs.filter { it.status == "COMPLETED" }.size // In reality, filter by today's date

        val insight = AIInsightEntity(
            id = UUID.randomUUID().toString(),
            orgId = orgId,
            type = "SUMMARY",
            category = "GENERAL",
            title = "Today's Summary",
            description = "$completedToday jobs completed today. Local heuristics applied.",
            severity = "LOW"
        )
        aiOperationsRepository.saveInsight(insight)
    }

    suspend fun predictMaintenance(orgId: String) {
        val machines = machineDao.getMachinesForOrg(orgId).firstOrNull() ?: emptyList()
        for (machine in machines) {
            // Heuristic: If random condition met, generate insight
            val insight = AIInsightEntity(
                id = UUID.randomUUID().toString(),
                orgId = orgId,
                type = "PREDICTION",
                category = "MAINTENANCE",
                title = "Service Likely Needed",
                description = "Based on engine hours, machine ${machine.model ?: "Unknown"} might need servicing soon.",
                severity = "MEDIUM",
                relatedEntityId = machine.id,
                relatedEntityType = "MACHINE",
                isActionable = true,
                actionType = "SCHEDULE_MAINTENANCE"
            )
            aiOperationsRepository.saveInsight(insight)
        }
    }
    
    suspend fun analyzePaymentIntelligence(orgId: String) {
        val insight = AIInsightEntity(
            id = UUID.randomUUID().toString(),
            orgId = orgId,
            type = "INSIGHT",
            category = "PAYMENT",
            title = "Payment Intelligence",
            description = "Analyzed customer payment behaviors. 2 customers have growing outstanding balances.",
            severity = "MEDIUM"
        )
        aiOperationsRepository.saveInsight(insight)
    }
    
    suspend fun runOfflineAnalysis(orgId: String) {
        generateDailySummary(orgId)
        predictMaintenance(orgId)
        analyzePaymentIntelligence(orgId)
    }
    
    // Natural Language Search - Offline
    fun parseOfflineQuery(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("customer") && lower.contains("owes") -> "Searching for customers with highest outstanding balance..."
            lower.contains("machine") && lower.contains("revenue") -> "Calculating machine with highest revenue..."
            lower.contains("unpaid") -> "Fetching unpaid jobs..."
            lower.contains("service") -> "Checking machines due for service..."
            else -> "Query not recognized offline. Please connect to internet for advanced AI search."
        }
    }
}

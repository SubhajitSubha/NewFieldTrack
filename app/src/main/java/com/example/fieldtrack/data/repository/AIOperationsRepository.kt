package com.example.fieldtrack.data.repository

import com.example.fieldtrack.data.local.dao.AIInsightDao
import com.example.fieldtrack.data.local.entity.AIInsightEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AIOperationsRepository(
    private val aiInsightDao: AIInsightDao
) {
    suspend fun saveInsight(insight: AIInsightEntity) {
        aiInsightDao.insertInsight(insight)
    }

    fun getInsightsByOrg(orgId: String): Flow<List<AIInsightEntity>> {
        return aiInsightDao.getInsightsByOrg(orgId)
    }

    suspend fun markInsightAsRead(id: String) {
        aiInsightDao.markAsRead(id)
    }
}

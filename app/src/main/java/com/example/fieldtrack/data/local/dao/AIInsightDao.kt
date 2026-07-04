package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.AIInsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIInsightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: AIInsightEntity)

    @Query("SELECT * FROM ai_insights WHERE orgId = :orgId AND isDeleted = 0 ORDER BY date DESC")
    fun getInsightsByOrg(orgId: String): Flow<List<AIInsightEntity>>

    @Query("SELECT * FROM ai_insights WHERE orgId = :orgId AND type = :type AND isDeleted = 0 ORDER BY date DESC")
    fun getInsightsByType(orgId: String, type: String): Flow<List<AIInsightEntity>>

    @Query("UPDATE ai_insights SET isRead = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun markAsRead(id: String, timestamp: Long = System.currentTimeMillis())
}

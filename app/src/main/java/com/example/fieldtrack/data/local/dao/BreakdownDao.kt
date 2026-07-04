package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.BreakdownEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakdown(breakdown: BreakdownEntity)

    @Query("SELECT * FROM breakdowns WHERE orgId = :orgId AND isDeleted = 0")
    fun getBreakdownsByOrg(orgId: String): Flow<List<BreakdownEntity>>

    @Query("SELECT * FROM breakdowns WHERE machineId = :machineId AND isDeleted = 0 ORDER BY time DESC")
    fun getBreakdownsByMachine(machineId: String): Flow<List<BreakdownEntity>>
}

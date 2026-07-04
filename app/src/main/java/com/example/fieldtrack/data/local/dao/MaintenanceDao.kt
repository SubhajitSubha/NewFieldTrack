package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.MaintenanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenance(maintenance: MaintenanceEntity)

    @Query("SELECT * FROM maintenance WHERE orgId = :orgId AND isDeleted = 0")
    fun getMaintenanceByOrg(orgId: String): Flow<List<MaintenanceEntity>>

    @Query("SELECT * FROM maintenance WHERE machineId = :machineId AND isDeleted = 0 ORDER BY date DESC")
    fun getMaintenanceByMachine(machineId: String): Flow<List<MaintenanceEntity>>
}

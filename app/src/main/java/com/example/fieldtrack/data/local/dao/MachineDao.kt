package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.MachineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineDao {
    @Query("SELECT * FROM machines WHERE orgId = :orgId")
    fun getMachinesForOrg(orgId: String): Flow<List<MachineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: MachineEntity)

    @Query("DELETE FROM machines WHERE id = :id")
    suspend fun deleteMachineById(id: String)
}

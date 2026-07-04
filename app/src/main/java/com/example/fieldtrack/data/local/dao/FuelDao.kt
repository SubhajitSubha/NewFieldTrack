package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.FuelEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuelEntry(fuelEntry: FuelEntryEntity)

    @Query("SELECT * FROM fuel_entries WHERE orgId = :orgId AND isDeleted = 0")
    fun getFuelEntriesByOrg(orgId: String): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM fuel_entries WHERE machineId = :machineId AND isDeleted = 0 ORDER BY date DESC")
    fun getFuelEntriesByMachine(machineId: String): Flow<List<FuelEntryEntity>>
}

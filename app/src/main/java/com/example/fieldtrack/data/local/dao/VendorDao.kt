package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.VendorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: VendorEntity)

    @Query("SELECT * FROM vendors WHERE orgId = :orgId AND isDeleted = 0")
    fun getVendorsByOrg(orgId: String): Flow<List<VendorEntity>>
}

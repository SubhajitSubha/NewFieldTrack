package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItemEntity)

    @Query("SELECT * FROM inventory_items WHERE orgId = :orgId AND isDeleted = 0")
    fun getInventoryItemsByOrg(orgId: String): Flow<List<InventoryItemEntity>>
}

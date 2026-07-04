package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.FieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDao {
    @Query("SELECT * FROM fields WHERE orgId = :orgId AND customerId = :customerId")
    fun getFieldsForCustomer(orgId: String, customerId: String): Flow<List<FieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: FieldEntity)

    @Query("DELETE FROM fields WHERE id = :id")
    suspend fun deleteFieldById(id: String)
}

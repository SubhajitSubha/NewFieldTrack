package com.example.fieldtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldtrack.data.local.entity.JobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs WHERE orgId = :orgId ORDER BY createdAt DESC")
    fun getJobsForOrg(orgId: String): Flow<List<JobEntity>>
    
    @Query("SELECT * FROM jobs WHERE orgId = :orgId AND status = :status ORDER BY createdAt DESC")
    fun getJobsByStatus(orgId: String, status: String): Flow<List<JobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteJobById(id: String)
}

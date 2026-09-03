package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ExportEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationJobDao {
    @Query("SELECT * FROM generation_jobs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getJobsByProjectId(projectId: String): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE projectId = :projectId AND status = :status ORDER BY createdAt DESC")
    fun getJobsByStatus(projectId: String, status: JobStatus): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE projectId = :projectId AND type = :type ORDER BY createdAt DESC")
    fun getJobsByType(projectId: String, type: JobType): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    fun getJobById(id: String): Flow<GenerationJobEntity?>

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    suspend fun findJobById(id: String): GenerationJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: GenerationJobEntity)

    @Update
    suspend fun updateJob(job: GenerationJobEntity)

    @Query("UPDATE generation_jobs SET status = :status, rawResult = :rawResult, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateJobOutcome(id: String, status: JobStatus, rawResult: String?, errorMessage: String?, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteJob(job: GenerationJobEntity)

    @Query("DELETE FROM generation_jobs WHERE projectId = :projectId")
    suspend fun deleteJobsByProjectId(projectId: String)
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM exports WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getExportsByProjectId(projectId: String): Flow<List<ExportEntity>>

    @Query("SELECT * FROM exports WHERE id = :id")
    fun getExportById(id: String): Flow<ExportEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: ExportEntity)

    @Update
    suspend fun updateExport(export: ExportEntity)

    @Delete
    suspend fun deleteExport(export: ExportEntity)
}

package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.HumorDNAEntity
import com.example.data.local.entity.VisualBibleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisualBibleDao {
    @Query("SELECT * FROM visual_bibles WHERE projectId = :projectId LIMIT 1")
    fun getVisualBibleByProjectId(projectId: String): Flow<VisualBibleEntity?>

    @Query("SELECT * FROM visual_bibles WHERE projectId = :projectId LIMIT 1")
    suspend fun findVisualBibleByProjectId(projectId: String): VisualBibleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVisualBible(visualBible: VisualBibleEntity)

    @Delete
    suspend fun deleteVisualBible(visualBible: VisualBibleEntity)
}

@Dao
interface HumorDnaDao {
    @Query("SELECT * FROM humor_dna WHERE projectId = :projectId LIMIT 1")
    fun getHumorDnaByProjectId(projectId: String): Flow<HumorDNAEntity?>

    @Query("SELECT * FROM humor_dna WHERE projectId = :projectId LIMIT 1")
    suspend fun findHumorDnaByProjectId(projectId: String): HumorDNAEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHumorDna(humorDna: HumorDNAEntity)

    @Delete
    suspend fun deleteHumorDna(humorDna: HumorDNAEntity)
}

@Dao
interface CoverDao {
    @Query("SELECT * FROM covers WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getCoversByProjectId(projectId: String): Flow<List<CoverEntity>>

    @Query("SELECT * FROM covers WHERE projectId = :projectId AND isCanon = 1 LIMIT 1")
    fun getCanonCoverByProjectId(projectId: String): Flow<CoverEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCover(cover: CoverEntity)

    @Update
    suspend fun updateCover(cover: CoverEntity)

    @Delete
    suspend fun deleteCover(cover: CoverEntity)
}

package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.StoryObjectEntity
import com.example.data.local.entity.WorldBibleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE projectId = :projectId ORDER BY name ASC")
    fun getLocationsByProjectId(projectId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun getLocationById(id: String): Flow<LocationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)
}

@Dao
interface StoryObjectDao {
    @Query("SELECT * FROM story_objects WHERE projectId = :projectId ORDER BY name ASC")
    fun getObjectsByProjectId(projectId: String): Flow<List<StoryObjectEntity>>

    @Query("SELECT * FROM story_objects WHERE id = :id")
    fun getObjectById(id: String): Flow<StoryObjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObject(storyObject: StoryObjectEntity)

    @Update
    suspend fun updateObject(storyObject: StoryObjectEntity)

    @Delete
    suspend fun deleteObject(storyObject: StoryObjectEntity)
}

@Dao
interface WorldBibleDao {
    @Query("SELECT * FROM world_bibles WHERE projectId = :projectId LIMIT 1")
    fun getWorldBibleByProjectId(projectId: String): Flow<WorldBibleEntity?>

    @Query("SELECT * FROM world_bibles WHERE projectId = :projectId LIMIT 1")
    suspend fun findWorldBibleByProjectId(projectId: String): WorldBibleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWorldBible(worldBible: WorldBibleEntity)

    @Delete
    suspend fun deleteWorldBible(worldBible: WorldBibleEntity)
}

package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.local.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryBibleDao {
    @Query("SELECT * FROM story_bibles WHERE projectId = :projectId LIMIT 1")
    fun getStoryBibleByProjectId(projectId: String): Flow<StoryBibleEntity?>

    @Query("SELECT * FROM story_bibles WHERE projectId = :projectId LIMIT 1")
    suspend fun findStoryBibleByProjectId(projectId: String): StoryBibleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStoryBible(storyBible: StoryBibleEntity)

    @Delete
    suspend fun deleteStoryBible(storyBible: StoryBibleEntity)
}

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM timeline_events WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getEventsByProjectId(projectId: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE projectId = :projectId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonEventsByProjectId(projectId: String): Flow<List<TimelineEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TimelineEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<TimelineEventEntity>)

    @Update
    suspend fun updateEvent(event: TimelineEventEntity)

    @Delete
    suspend fun deleteEvent(event: TimelineEventEntity)
}

@Dao
interface CanonMemoryDao {
    @Query("SELECT * FROM canon_memories WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getCanonMemoriesByProjectId(projectId: String): Flow<List<CanonMemoryEntity>>

    @Query("SELECT * FROM canon_memories WHERE projectId = :projectId AND category = :category ORDER BY createdAt ASC")
    fun getCanonMemoriesByCategory(projectId: String, category: String): Flow<List<CanonMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonMemory(canonMemory: CanonMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonMemories(canonMemories: List<CanonMemoryEntity>)

    @Update
    suspend fun updateCanonMemory(canonMemory: CanonMemoryEntity)

    @Delete
    suspend fun deleteCanonMemory(canonMemory: CanonMemoryEntity)
}

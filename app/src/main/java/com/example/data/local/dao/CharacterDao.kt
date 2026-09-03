package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CharacterStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE projectId = :projectId ORDER BY name ASC")
    fun getCharactersByProjectId(projectId: String): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    fun getCharacterById(id: String): Flow<CharacterEntity?>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun findCharacterById(id: String): CharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacters(characters: List<CharacterEntity>)

    @Update
    suspend fun updateCharacter(character: CharacterEntity)

    @Delete
    suspend fun deleteCharacter(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacterById(id: String)
}

@Dao
interface CharacterStateDao {
    @Query("SELECT * FROM character_states WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getStatesByProjectId(projectId: String): Flow<List<CharacterStateEntity>>

    @Query("SELECT * FROM character_states WHERE characterId = :characterId ORDER BY timestamp DESC")
    fun getStatesByCharacterId(characterId: String): Flow<List<CharacterStateEntity>>

    @Query("SELECT * FROM character_states WHERE characterId = :characterId AND chapterId = :chapterId")
    fun getStateByCharacterAndChapter(characterId: String, chapterId: String): Flow<CharacterStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: CharacterStateEntity)

    @Update
    suspend fun updateState(state: CharacterStateEntity)

    @Delete
    suspend fun deleteState(state: CharacterStateEntity)
}

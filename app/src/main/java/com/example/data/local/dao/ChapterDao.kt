package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.SceneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE projectId = :projectId ORDER BY number ASC")
    fun getChaptersByProjectId(projectId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE projectId = :projectId AND isCanon = 1 ORDER BY number ASC")
    fun getCanonChaptersByProjectId(projectId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE projectId = :projectId ORDER BY number ASC LIMIT 1")
    fun getDraftChapterByProjectId(projectId: String): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterById(id: String): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun findChapterById(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)
}

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getScenesByProjectId(projectId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE projectId = :projectId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonScenesByProjectId(projectId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId ORDER BY orderIndex ASC")
    fun getScenesByChapterId(chapterId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonScenesByChapterId(chapterId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE id = :id")
    fun getSceneById(id: String): Flow<SceneEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenes(scenes: List<SceneEntity>)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Delete
    suspend fun deleteScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE chapterId = :chapterId")
    suspend fun deleteScenesByChapterId(chapterId: String)
}

@Dao
interface PanelDao {
    @Query("SELECT * FROM panels WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getPanelsByProjectId(projectId: String): Flow<List<PanelEntity>>

    @Query("SELECT * FROM panels WHERE projectId = :projectId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonPanelsByProjectId(projectId: String): Flow<List<PanelEntity>>

    @Query("SELECT * FROM panels WHERE chapterId = :chapterId ORDER BY orderIndex ASC")
    fun getPanelsByChapterId(chapterId: String): Flow<List<PanelEntity>>

    @Query("SELECT * FROM panels WHERE sceneId = :sceneId ORDER BY orderIndex ASC")
    fun getPanelsBySceneId(sceneId: String): Flow<List<PanelEntity>>

    @Query("SELECT * FROM panels WHERE chapterId = :chapterId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonPanelsByChapterId(chapterId: String): Flow<List<PanelEntity>>

    @Query("SELECT * FROM panels WHERE id = :id")
    fun getPanelById(id: String): Flow<PanelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanel(panel: PanelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanels(panels: List<PanelEntity>)

    @Update
    suspend fun updatePanel(panel: PanelEntity)

    @Delete
    suspend fun deletePanel(panel: PanelEntity)

    @Query("DELETE FROM panels WHERE sceneId = :sceneId")
    suspend fun deletePanelsBySceneId(sceneId: String)

    @Query("DELETE FROM panels WHERE chapterId = :chapterId")
    suspend fun deletePanelsByChapterId(chapterId: String)
}

@Dao
interface DialogueDao {
    @Query("SELECT * FROM dialogues WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getDialoguesByProjectId(projectId: String): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues WHERE projectId = :projectId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonDialoguesByProjectId(projectId: String): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues WHERE panelId = :panelId ORDER BY orderIndex ASC")
    fun getDialoguesByPanelId(panelId: String): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues WHERE panelId = :panelId AND isCanon = 1 ORDER BY orderIndex ASC")
    fun getCanonDialoguesByPanelId(panelId: String): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues WHERE id = :id")
    fun getDialogueById(id: String): Flow<DialogueEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogue(dialogue: DialogueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogues(dialogues: List<DialogueEntity>)

    @Update
    suspend fun updateDialogue(dialogue: DialogueEntity)

    @Delete
    suspend fun deleteDialogue(dialogue: DialogueEntity)

    @Query("DELETE FROM dialogues WHERE panelId = :panelId")
    suspend fun deleteDialoguesByPanelId(panelId: String)
}

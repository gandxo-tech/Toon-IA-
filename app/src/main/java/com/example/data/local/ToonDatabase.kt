package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.converters.ToonTypeConverters
import com.example.data.local.dao.CanonMemoryDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.CharacterDao
import com.example.data.local.dao.CharacterStateDao
import com.example.data.local.dao.CoverDao
import com.example.data.local.dao.DialogueDao
import com.example.data.local.dao.ExportDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.HumorDnaDao
import com.example.data.local.dao.LocationDao
import com.example.data.local.dao.PanelDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.SceneDao
import com.example.data.local.dao.StoryBibleDao
import com.example.data.local.dao.StoryObjectDao
import com.example.data.local.dao.TimelineEventDao
import com.example.data.local.dao.VisualBibleDao
import com.example.data.local.dao.WorldBibleDao
import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CharacterStateEntity
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.ExportEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.HumorDNAEntity
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.local.entity.StoryObjectEntity
import com.example.data.local.entity.TimelineEventEntity
import com.example.data.local.entity.VisualBibleEntity
import com.example.data.local.entity.WorldBibleEntity

/**
 * Base de données Room principale pour ToonIA.
 * Contient l'ensemble des 18 entités Room du projet (Phase 1).
 */
@Database(
    entities = [
        ProjectEntity::class,
        CharacterEntity::class,
        CharacterStateEntity::class,
        LocationEntity::class,
        StoryObjectEntity::class,
        ChapterEntity::class,
        SceneEntity::class,
        PanelEntity::class,
        DialogueEntity::class,
        CoverEntity::class,
        CanonMemoryEntity::class,
        TimelineEventEntity::class,
        StoryBibleEntity::class,
        WorldBibleEntity::class,
        VisualBibleEntity::class,
        HumorDNAEntity::class,
        GenerationJobEntity::class,
        ExportEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(ToonTypeConverters::class)
abstract class ToonDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun characterDao(): CharacterDao
    abstract fun characterStateDao(): CharacterStateDao
    abstract fun locationDao(): LocationDao
    abstract fun storyObjectDao(): StoryObjectDao
    abstract fun chapterDao(): ChapterDao
    abstract fun sceneDao(): SceneDao
    abstract fun panelDao(): PanelDao
    abstract fun dialogueDao(): DialogueDao
    abstract fun coverDao(): CoverDao
    abstract fun canonMemoryDao(): CanonMemoryDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun storyBibleDao(): StoryBibleDao
    abstract fun worldBibleDao(): WorldBibleDao
    abstract fun visualBibleDao(): VisualBibleDao
    abstract fun humorDnaDao(): HumorDnaDao
    abstract fun generationJobDao(): GenerationJobDao
    abstract fun exportDao(): ExportDao

    companion object {
        @Volatile
        private var INSTANCE: ToonDatabase? = null

        fun getInstance(context: Context): ToonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToonDatabase::class.java,
                    "toonia.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

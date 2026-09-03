package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bible de l'histoire (StoryBible) : thème principal, structure en arcs, climax et rythme.
 */
@Entity(
    tableName = "story_bibles",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"], unique = true)
    ]
)
data class StoryBibleEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val mainTheme: String = "",
    val narrativeArc: String = "",
    val climaxDescription: String = "",
    val narrativePacing: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Événement chronologique dans la frise temporelle de l'histoire.
 */
@Entity(
    tableName = "timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["chapterId"])
    ]
)
data class TimelineEventEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val chapterId: String? = null,
    val orderIndex: Int = 0,
    val title: String,
    val description: String = "",
    val dateOrEpoch: String = "",
    val isCanon: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Mémoire canonique (CanonMemory) : conserve les faits incontournables et règles absolues
 * pour garantir la cohérence narrative à chaque génération IA.
 */
@Entity(
    tableName = "canon_memories",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["category"])
    ]
)
data class CanonMemoryEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val category: String, // Règle Univers, Événement Clé, Mort, Révélation, Serment...
    val fact: String,
    val impact: String = "",
    val establishedInChapterId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

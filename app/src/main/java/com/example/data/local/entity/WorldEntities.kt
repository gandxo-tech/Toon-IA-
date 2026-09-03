package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité représentant un lieu dans l'univers du webtoon.
 */
@Entity(
    tableName = "locations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"])
    ]
)
data class LocationEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val description: String,
    val atmosphere: String = "",
    val visualReferencePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entité représentant un objet narratif (artefact, symbole, arme, objet clé).
 */
@Entity(
    tableName = "story_objects",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"])
    ]
)
data class StoryObjectEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val significance: String,
    val description: String = "",
    val ownerCharacterId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Bible d'univers (WorldBible) : règles magiques/technologiques, géographie, factions, lore.
 */
@Entity(
    tableName = "world_bibles",
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
data class WorldBibleEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val magicOrTechRules: String = "",
    val factions: String = "",
    val geography: String = "",
    val socialStructure: String = "",
    val historyLore: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

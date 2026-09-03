package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité représentant l'identité stable d'un personnage.
 */
@Entity(
    tableName = "characters",
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
data class CharacterEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val role: String, // Protagoniste, Antagoniste, Allié, Mentor, Secondaire...
    val archetype: String = "",
    val personalityTraits: String = "",
    val backstory: String = "",
    val visualDescription: String = "",
    /**
     * Chemin local vers l'image de référence canonique (nullable au départ, complété en Phase 14).
     * Stockage fichier local uniquement (jamais de base64 en base).
     */
    val canonicalImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entité représentant l'état d'un personnage à un moment donné de l'histoire
 * (apparence actuelle, relations actuelles, état psychologique) — distinct de Character.
 */
@Entity(
    tableName = "character_states",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["projectId"]),
        Index(value = ["chapterId"]),
        Index(value = ["sceneId"])
    ]
)
data class CharacterStateEntity(
    @PrimaryKey
    val id: String,
    val characterId: String,
    val projectId: String,
    val chapterId: String? = null,
    val sceneId: String? = null,
    val currentAppearance: String, // ex: armure brisée, tenue civile, blessures
    val currentRelationships: String = "", // ex: rivalité avec X, méfiance envers Y
    val psychologicalState: String = "", // ex: déterminé, traumatisé, confiant
    val timestamp: Long = System.currentTimeMillis()
)

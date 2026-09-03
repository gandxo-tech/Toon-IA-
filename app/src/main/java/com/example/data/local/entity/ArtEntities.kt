package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bible visuelle (VisualBible) : direction artistique, palettes dominantes, style de découpage webtoon.
 */
@Entity(
    tableName = "visual_bibles",
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
data class VisualBibleEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val artStyle: String = "", // Manhwa action, Romance pastel, Cyberpunk néon, Comic noir...
    val colorPalette: String = "",
    val panelingStyle: String = "", // Défilement vertical standard, gouttières aérées...
    val lightingMood: String = "",
    val recurringVisualElements: String = "", // Caractéristiques visuelles récurrentes
    val framingRules: String = "", // Règles de cadrage privilégiées
    val continuityElements: String = "", // Éléments visuels à conserver d'un chapitre à l'autre
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * ADN humoristique (HumorDNA) : définit le registre d'humour, gestion des inserts chibi et running gags.
 */
@Entity(
    tableName = "humor_dna",
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
data class HumorDNAEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val humorStyle: String = "", // Ironie, comique de répétition, burlesque, cynique...
    val chibiMomentsAllowed: Boolean = true,
    val runningGags: String = "",
    val punchlineFrequency: String = "",
    val characterHumorHooks: String = "", // Blagues propres à certains personnages validés
    val comicSituations: String = "", // Types de situations comiques récurrentes envisagées
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Couverture du webtoon.
 * Stocke le chemin d'accès au fichier local pour l'illustration de couverture.
 */
@Entity(
    tableName = "covers",
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
data class CoverEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val title: String,
    val subtitle: String? = null,
    /**
     * Chemin vers le fichier local de la couverture (jamais base64).
     */
    val localImagePath: String? = null,
    val tagline: String? = null,
    /**
     * Description textuelle détaillée de ce que la couverture visuelle doit représenter
     * (composition, personnages présents, ambiance) - SANS aucun texte dans l'image.
     */
    val visualDescription: String = "",
    val isCanon: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

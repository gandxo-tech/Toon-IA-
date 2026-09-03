package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chapitre du webtoon.
 * Permet de distinguer contenu 'validé/canon' de 'brouillon en cours de génération'.
 */
@Entity(
    tableName = "chapters",
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
        Index(value = ["number"])
    ]
)
data class ChapterEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val number: Int,
    val title: String,
    val synopsis: String = "",
    /**
     * true = contenu validé / canon, false = brouillon en cours de génération.
     */
    val isCanon: Boolean = false,
    val draftNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Scène d'un chapitre.
 */
@Entity(
    tableName = "scenes",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
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
        Index(value = ["chapterId"]),
        Index(value = ["projectId"]),
        Index(value = ["locationId"])
    ]
)
data class SceneEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val projectId: String,
    val locationId: String? = null,
    val orderIndex: Int = 0,
    val title: String,
    val narrativeSummary: String = "",
    val charactersPresent: String = "",
    val locationName: String = "",
    val atmosphere: String = "",
    val narrativeBeats: String = "",
    val emotionalArc: String = "",
    val pacingNotes: String = "",
    /**
     * true = contenu validé / canon, false = brouillon en cours de génération.
     */
    val isCanon: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Panel (case de webtoon) dans une scène.
 * Conformément aux règles d'architecture : stocke un chemin fichier local, JAMAIS de base64 en base.
 */
@Entity(
    tableName = "panels",
    foreignKeys = [
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
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
        Index(value = ["sceneId"]),
        Index(value = ["chapterId"]),
        Index(value = ["projectId"])
    ]
)
data class PanelEntity(
    @PrimaryKey
    val id: String,
    val sceneId: String,
    val chapterId: String,
    val projectId: String,
    val orderIndex: Int = 0,
    val cameraAngle: String = "", // ex: Gros plan, Plan moyen, Plan d'ensemble, Plongée, Contre-plongée
    val visualDescription: String = "",
    val charactersPresent: String = "", // Personnages avec leur état actuel (tenue, émotion, posture)
    val location: String = "", // Lieu / décor spécifique
    val actionDescription: String = "", // Action principale en cours dans ce plan
    val dominantEmotion: String = "", // Émotion dominante
    val composition: String = "", // Composition, cadrage, disposition des masses
    val lightingAndAtmosphere: String = "", // Ambiance et éclairage (lumières, contrastes)
    val backgroundDetails: String = "", // Description détaillée de l'arrière-plan
    val visualEffects: String = "", // Effets visuels (lignes de vitesse, auras, particules)
    val visualPrompt: String = "", // Prompt visuel textuel précis prêt pour la génération d'image (Phase 14)
    val continuityNotes: String = "", // Notes de raccord par rapport au panel précédent
    /**
     * Chemin vers le fichier image local (ex: /files/panels/panel_123.jpg).
     * Strictement aucun stockage base64 dans Room.
     */
    val localImagePath: String? = null,
    val soundEffects: String? = null, // Onomatopées webtoon (ex: BAM, CRASH, TAP)
    /**
     * true = contenu validé / canon, false = brouillon en cours de génération.
     */
    val isCanon: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Dialogue ou bulle de texte rattachée à un panel.
 */
@Entity(
    tableName = "dialogues",
    foreignKeys = [
        ForeignKey(
            entity = PanelEntity::class,
            parentColumns = ["id"],
            childColumns = ["panelId"],
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
        Index(value = ["panelId"]),
        Index(value = ["projectId"]),
        Index(value = ["characterId"])
    ]
)
data class DialogueEntity(
    @PrimaryKey
    val id: String,
    val panelId: String,
    val projectId: String,
    val characterId: String? = null, // Nullable si voix off ou narration neutre
    val speakerName: String = "", // Nom d'affichage (ex: 'Lucas', 'Narrateur', 'Voix off')
    val orderIndex: Int = 0,
    val type: String = "SPEECH", // SPEECH, THOUGHT, SHOUT, WHISPER, NARRATION
    val emotion: String = "", // ex: 'Colère', 'Peur', 'Surprise', 'Amusé', 'Neutre'
    val placement: String = "TOP_LEFT", // TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    val soundEffect: String? = null, // SFX ou onomatopée intégrée si applicable
    val text: String,
    /**
     * true = contenu validé / canon, false = brouillon en cours de génération.
     */
    val isCanon: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

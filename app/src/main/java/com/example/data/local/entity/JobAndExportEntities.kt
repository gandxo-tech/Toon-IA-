package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tâche d'exécution IA (GenerationJob).
 * Conforme aux règles d'architecture : aucun appel IA nu sans gestion d'état et suivi de retry.
 */
@Entity(
    tableName = "generation_jobs",
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
        Index(value = ["status"]),
        Index(value = ["type"])
    ]
)
data class GenerationJobEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val type: JobType,
    val status: JobStatus = JobStatus.QUEUED,
    /**
     * Données d'entrée (prompts, contraintes, contextes injectés sous format JSON).
     */
    val inputPayload: String,
    /**
     * Résultat brut retourné par l'IA (JSON structuré selon responseSchema).
     */
    val rawResult: String? = null,
    /**
     * Message d'erreur explicite en cas d'échec.
     */
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Entité d'exportation (Webtoon, PDF, bande verticale).
 */
@Entity(
    tableName = "exports",
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
data class ExportEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val chapterId: String? = null,
    val format: String = "PDF", // PDF, ZIP, CBZ, LONG_STRIP
    /**
     * Chemin vers le fichier généré stocké localement.
     */
    val localFilePath: String,
    val pageCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val isCanon: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

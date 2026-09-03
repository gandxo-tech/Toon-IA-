package com.example.domain.usecase

import android.content.Context
import com.example.ai.GeminiCoverImageGenerator
import com.example.data.local.dao.CoverDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.VisualBibleDao
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.domain.canon.CanonMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * UseCase pour la Phase 9 : Génération de l'image de couverture.
 *
 * RÈGLES APPLIQUÉES :
 * - Tracé via GenerationJob (JobType.COUVERTURE, statuts QUEUED -> RUNNING -> SUCCESS / FAILED).
 * - S'appuie sur la description validée en Phase 8 et la Visual Bible.
 * - Stockage fichier local de l'image (jamais de base64 en base Room).
 * - Mise à jour de CoverEntity.localImagePath.
 */
class GenerateCoverImageUseCase(
    private val projectDao: ProjectDao,
    private val coverDao: CoverDao,
    private val visualBibleDao: VisualBibleDao,
    private val jobDao: GenerationJobDao,
    private val canonRepository: CanonMemoryRepository,
    private val imageGenerator: GeminiCoverImageGenerator
) {

    /**
     * Exécute la génération de l'illustration de couverture via Gemini.
     */
    suspend fun executeGenerateCoverImage(
        context: Context,
        projectId: String,
        coverId: String? = null
    ): Pair<GenerationJobEntity, Result<CoverEntity>> = withContext(Dispatchers.IO) {
        val project = projectDao.findProjectById(projectId)
            ?: return@withContext Pair(
                createFailedJob(projectId, "Projet introuvable pour l'id: $projectId"),
                Result.failure(IllegalArgumentException("Projet introuvable."))
            )

        // Récupérer la couverture canonique ou la plus récente du projet
        val targetCover = if (!coverId.isNullOrBlank()) {
            val list = coverDao.getCoversByProjectId(projectId).firstOrNull() ?: emptyList()
            list.firstOrNull { it.id == coverId }
        } else {
            coverDao.getCanonCoverByProjectId(projectId).firstOrNull()
                ?: (coverDao.getCoversByProjectId(projectId).firstOrNull()?.firstOrNull())
        }

        if (targetCover == null) {
            return@withContext Pair(
                createFailedJob(projectId, "Aucune description de couverture trouvée pour le projet $projectId. Validez d'abord la Phase 8."),
                Result.failure(IllegalStateException("Aucune description de couverture enregistrée. Veuillez valider la Phase 8 avant de générer l'image."))
            )
        }

        val visualBible = visualBibleDao.findVisualBibleByProjectId(projectId)
        val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

        val jobId = UUID.randomUUID().toString()
        val inputPayload = JSONObject().apply {
            put("projectId", projectId)
            put("coverId", targetCover.id)
            put("title", targetCover.title)
            put("visualDescription", targetCover.visualDescription)
            put("artStyle", visualBible?.artStyle)
        }.toString()

        val runningJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.COUVERTURE,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        jobDao.insertJob(runningJob)

        try {
            val localImagePath = imageGenerator.generateCoverImage(
                context = context,
                projectId = projectId,
                cover = targetCover,
                visualBible = visualBible,
                canonSnapshot = canonSnapshot
            )

            // Mise à jour de la couverture avec le chemin du fichier local
            val updatedCover = targetCover.copy(
                localImagePath = localImagePath
            )
            coverDao.insertCover(updatedCover)

            val successJob = runningJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = localImagePath,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(successJob)

            Pair(successJob, Result.success(updatedCover))
        } catch (e: Exception) {
            val failedJob = runningJob.copy(
                status = JobStatus.FAILED,
                errorMessage = e.message ?: "Erreur inconnue lors de la génération de l'image de couverture",
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(failedJob)

            Pair(failedJob, Result.failure(e))
        }
    }

    private fun createFailedJob(projectId: String, errorMessage: String): GenerationJobEntity {
        return GenerationJobEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            type = JobType.COUVERTURE,
            status = JobStatus.FAILED,
            inputPayload = "{}",
            errorMessage = errorMessage
        )
    }
}

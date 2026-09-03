package com.example.domain.usecase

import com.example.ai.GeminiVisualHumorGenerator
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.HumorDnaDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.VisualBibleDao
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.HumorDnaResult
import com.example.domain.model.VisualBibleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * UseCase orchestrant la Phase 7 : Visual Bible (Direction Artistique) et Humor DNA.
 *
 * RÈGLES FONDAMENTALES :
 * - Toute génération IA passe par un GenerationJob traçable (type = VISUEL ou HUMOUR).
 * - La mémoire canonique actuelle (CanonSnapshot) est toujours fournie en contexte.
 * - Aucune écriture dans CanonMemory / Room sans validation explicite de l'utilisateur.
 * - Les contradictions sont détectées et présentées à l'utilisateur, jamais corrigées en silence.
 */
class VisualHumorUseCase(
    private val projectDao: ProjectDao,
    private val jobDao: GenerationJobDao,
    private val visualBibleDao: VisualBibleDao,
    private val humorDnaDao: HumorDnaDao,
    private val canonRepository: CanonMemoryRepository,
    private val generator: GeminiVisualHumorGenerator = GeminiVisualHumorGenerator()
) {

    /**
     * Charge la Visual Bible canonique enregistrée pour ce projet, ou null si inexistante.
     */
    suspend fun loadVisualBible(projectId: String): VisualBibleResult? = withContext(Dispatchers.IO) {
        val entity = visualBibleDao.findVisualBibleByProjectId(projectId) ?: return@withContext null
        VisualBibleResult.fromEntity(entity)
    }

    /**
     * Charge l'Humor DNA canonique enregistrée pour ce projet, ou null si inexistante.
     */
    suspend fun loadHumorDna(projectId: String): HumorDnaResult? = withContext(Dispatchers.IO) {
        val entity = humorDnaDao.findHumorDnaByProjectId(projectId) ?: return@withContext null
        HumorDnaResult.fromEntity(entity)
    }

    /**
     * Exécute un job de génération IA pour la Visual Bible.
     */
    suspend fun executeGenerateVisualJob(
        projectId: String
    ): Pair<GenerationJobEntity, Result<VisualBibleResult>> = withContext(Dispatchers.IO) {
        val project = projectDao.findProjectById(projectId)
            ?: return@withContext Pair(
                GenerationJobEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    type = JobType.VISUEL,
                    status = JobStatus.FAILED,
                    inputPayload = "{}",
                    errorMessage = "Projet introuvable pour l'id : $projectId"
                ),
                Result.failure(IllegalArgumentException("Projet introuvable."))
            )

        val jobId = UUID.randomUUID().toString()
        val inputPayload = JSONObject().apply {
            put("projectId", projectId)
            put("title", project.title)
            put("genre", project.genre)
            put("tone", project.tone)
        }.toString()

        val runningJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.VISUEL,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            retryCount = 0
        )
        jobDao.insertJob(runningJob)

        try {
            val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

            val visualResult = generator.generateVisualBible(
                projectTitle = project.title,
                projectSynopsis = project.synopsis,
                projectGenre = project.genre,
                projectTone = project.tone,
                canonSnapshot = canonSnapshot
            )

            val rawResult = visualResult.toEntity().toString()
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.SUCCESS,
                rawResult = rawResult,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            val successJob = runningJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = rawResult,
                updatedAt = System.currentTimeMillis()
            )

            Pair(successJob, Result.success(visualResult))
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Erreur inconnue lors de la génération visuelle."
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.FAILED,
                rawResult = null,
                errorMessage = errorMsg,
                updatedAt = System.currentTimeMillis()
            )

            val failedJob = runningJob.copy(
                status = JobStatus.FAILED,
                errorMessage = errorMsg,
                updatedAt = System.currentTimeMillis()
            )

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Exécute un job de génération IA pour l'Humor DNA.
     */
    suspend fun executeGenerateHumorJob(
        projectId: String
    ): Pair<GenerationJobEntity, Result<HumorDnaResult>> = withContext(Dispatchers.IO) {
        val project = projectDao.findProjectById(projectId)
            ?: return@withContext Pair(
                GenerationJobEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    type = JobType.HUMOUR,
                    status = JobStatus.FAILED,
                    inputPayload = "{}",
                    errorMessage = "Projet introuvable pour l'id : $projectId"
                ),
                Result.failure(IllegalArgumentException("Projet introuvable."))
            )

        val jobId = UUID.randomUUID().toString()
        val inputPayload = JSONObject().apply {
            put("projectId", projectId)
            put("title", project.title)
            put("genre", project.genre)
            put("tone", project.tone)
        }.toString()

        val runningJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.HUMOUR,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            retryCount = 0
        )
        jobDao.insertJob(runningJob)

        try {
            val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

            val humorResult = generator.generateHumorDna(
                projectTitle = project.title,
                projectSynopsis = project.synopsis,
                projectGenre = project.genre,
                projectTone = project.tone,
                canonSnapshot = canonSnapshot
            )

            val rawResult = humorResult.toEntity().toString()
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.SUCCESS,
                rawResult = rawResult,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            val successJob = runningJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = rawResult,
                updatedAt = System.currentTimeMillis()
            )

            Pair(successJob, Result.success(humorResult))
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Erreur inconnue lors de la génération de l'Humor DNA."
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.FAILED,
                rawResult = null,
                errorMessage = errorMsg,
                updatedAt = System.currentTimeMillis()
            )

            val failedJob = runningJob.copy(
                status = JobStatus.FAILED,
                errorMessage = errorMsg,
                updatedAt = System.currentTimeMillis()
            )

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Vérifie si la Visual Bible proposée contredit des éléments canoniques existants.
     */
    suspend fun checkVisualContradictions(
        projectId: String,
        visualBible: VisualBibleResult
    ): List<Contradiction> = withContext(Dispatchers.IO) {
        val element = CanonElement.VisualBible(visualBible.toEntity(), isUserValidated = false)
        canonRepository.detectContradictions(projectId, element)
    }

    /**
     * Vérifie si l'Humor DNA proposée contredit des éléments canoniques existants ou le ton de la Story Bible.
     */
    suspend fun checkHumorContradictions(
        projectId: String,
        humorDna: HumorDnaResult
    ): List<Contradiction> = withContext(Dispatchers.IO) {
        val element = CanonElement.HumorDNA(humorDna.toEntity(), isUserValidated = false)
        canonRepository.detectContradictions(projectId, element)
    }

    /**
     * Valide EXPLICITEMENT la Visual Bible dans le CanonMemory et la base Room.
     */
    suspend fun validateVisualBibleToCanon(
        projectId: String,
        visualBible: VisualBibleResult
    ): Pair<VisualBibleResult, List<Contradiction>> = withContext(Dispatchers.IO) {
        val element = CanonElement.VisualBible(visualBible.toEntity(), isUserValidated = true)
        val contradictions = canonRepository.updateCanon(projectId, element)
        Pair(visualBible.copy(isValidatedInCanon = true), contradictions)
    }

    /**
     * Valide EXPLICITEMENT l'Humor DNA dans le CanonMemory et la base Room.
     */
    suspend fun validateHumorDnaToCanon(
        projectId: String,
        humorDna: HumorDnaResult
    ): Pair<HumorDnaResult, List<Contradiction>> = withContext(Dispatchers.IO) {
        val element = CanonElement.HumorDNA(humorDna.toEntity(), isUserValidated = true)
        val contradictions = canonRepository.updateCanon(projectId, element)
        Pair(humorDna.copy(isValidatedInCanon = true), contradictions)
    }
}

package com.example.domain.usecase

import com.example.ai.GeminiIdeaAnalyzer
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.domain.model.AdvancedIdeaDetails
import com.example.domain.model.IdeaAnalysisResult
import org.json.JSONObject
import java.util.UUID

/**
 * Cas d'utilisation orchestrant l'analyse d'idée de webtoon :
 * 1. Initialisation ou récupération du projet
 * 2. Création et enregistrement persistant du GenerationJob (QUEUED)
 * 3. Passage à l'état RUNNING et appel de l'API Gemini avec schéma JSON strict
 * 4. Persistance du statut réel (SUCCESS ou FAILED avec message d'erreur clair)
 * 5. Validation et enregistrement dans le projet pour transition vers l'étape suivante.
 */
class AnalyzeIdeaUseCase(
    private val projectDao: ProjectDao,
    private val jobDao: GenerationJobDao,
    private val geminiAnalyzer: GeminiIdeaAnalyzer = GeminiIdeaAnalyzer()
) {

    /**
     * Exécute le GenerationJob d'analyse d'idée.
     */
    suspend fun executeAnalysisJob(
        projectId: String,
        ideaText: String,
        advancedDetails: AdvancedIdeaDetails? = null,
        existingJobId: String? = null
    ): Pair<GenerationJobEntity, Result<IdeaAnalysisResult>> {
        // Préparer le payload d'entrée
        val inputPayloadJson = JSONObject().apply {
            put("ideaText", ideaText)
            if (advancedDetails != null) {
                put("advancedDetails", JSONObject(advancedDetails.toJsonString()))
            }
        }.toString()

        val jobId = existingJobId ?: UUID.randomUUID().toString()
        val existingJob = if (existingJobId != null) jobDao.findJobById(existingJobId) else null

        val currentJob = if (existingJob != null) {
            existingJob.copy(
                status = JobStatus.QUEUED,
                inputPayload = inputPayloadJson,
                retryCount = existingJob.retryCount + 1,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            GenerationJobEntity(
                id = jobId,
                projectId = projectId,
                type = JobType.ANALYSE,
                status = JobStatus.QUEUED,
                inputPayload = inputPayloadJson,
                rawResult = null,
                errorMessage = null,
                retryCount = 0
            )
        }

        jobDao.insertJob(currentJob)

        // 2. Passage à l'état RUNNING
        jobDao.updateJobOutcome(
            id = jobId,
            status = JobStatus.RUNNING,
            rawResult = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )

        // 3. Appel de l'analyseur Gemini réel
        return try {
            val analysisResult = geminiAnalyzer.analyzeIdea(ideaText, advancedDetails)
            val resultJson = analysisResult.toJsonString()

            // 4. Succès du job
            val updatedJob = currentJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = resultJson,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.SUCCESS,
                rawResult = resultJson,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            Pair(updatedJob, Result.success(analysisResult))
        } catch (e: Throwable) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Erreur inattendue durant l'analyse"

            // 4. Échec explicite du job
            val failedJob = currentJob.copy(
                status = JobStatus.FAILED,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.FAILED,
                rawResult = null,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Valide l'analyse (après éventuelle modification manuelle par l'utilisateur)
     * et met à jour le projet en base pour passer à l'étape suivante (personnages).
     */
    suspend fun validateAndSaveToProject(
        projectId: String,
        originalIdeaText: String,
        validatedAnalysis: IdeaAnalysisResult
    ): ProjectEntity {
        val existingProject = projectDao.findProjectById(projectId)
            ?: ProjectEntity(
                id = projectId,
                title = "Nouveau Webtoon",
                synopsis = originalIdeaText
            ).also { projectDao.insertProject(it) }

        val updatedProject = existingProject.copy(
            genre = validatedAnalysis.genrePrincipal,
            tone = validatedAnalysis.ton,
            synopsis = originalIdeaText,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.updateProject(updatedProject)
        return updatedProject
    }
}

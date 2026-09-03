package com.example.domain.usecase

import com.example.ai.GeminiMarketingCoverGenerator
import com.example.data.local.dao.CoverDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.CoverVisualProposal
import com.example.domain.model.MarketingCoverProposalsResult
import com.example.domain.model.MarketingSelection
import com.example.domain.model.SynopsisVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * UseCase pour la Phase 8 : Titre, synopsis et description de couverture (Marketing).
 *
 * RÈGLES APPLIQUÉES :
 * - Appel IA tracé par un GenerationJob (JobType.COUVERTURE).
 * - Utilisation de l'intégralité du canon (CanonSnapshot).
 * - Validation explicite de l'utilisateur requise avant inscription au canon.
 * - Le projet est mis à jour avec le titre final et le synopsis final.
 * - Aucune correction silencieuse de contradiction.
 */
class MarketingCoverUseCase(
    private val projectDao: ProjectDao,
    private val coverDao: CoverDao,
    private val jobDao: GenerationJobDao,
    private val canonRepository: CanonMemoryRepository,
    private val generator: GeminiMarketingCoverGenerator
) {

    /**
     * Charge l'état actuel marketing du projet (titre final, synopsis final, couverture canon).
     */
    suspend fun loadCurrentMarketing(projectId: String): MarketingSelection? = withContext(Dispatchers.IO) {
        val project = projectDao.findProjectById(projectId) ?: return@withContext null
        val canonCover = coverDao.getCanonCoverByProjectId(projectId).firstOrNull()
            ?: coverDao.getCoversByProjectId(projectId).firstOrNull()?.firstOrNull()

        val finalTitle = project.finalTitle ?: project.title
        val finalSynopsis = project.finalSynopsis ?: project.synopsis
        val isCanon = !project.finalTitle.isNullOrBlank() || (canonCover != null && canonCover.isCanon)

        MarketingSelection(
            selectedTitle = finalTitle,
            selectedSynopsis = finalSynopsis,
            selectedTagline = canonCover?.tagline ?: "",
            selectedCoverDescription = canonCover?.visualDescription ?: "",
            isCanonValidated = isCanon,
            coverImagePath = canonCover?.localImagePath,
            coverId = canonCover?.id
        )
    }

    /**
     * Exécute le job de génération IA pour les titres, synopsis et description de couverture.
     */
    suspend fun executeGenerateMarketingJob(
        projectId: String
    ): Pair<GenerationJobEntity, Result<MarketingCoverProposalsResult>> = withContext(Dispatchers.IO) {
        val project = projectDao.findProjectById(projectId)
            ?: return@withContext Pair(
                GenerationJobEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    type = JobType.COUVERTURE,
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
            type = JobType.COUVERTURE,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            retryCount = 0
        )
        jobDao.insertJob(runningJob)

        try {
            val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

            val proposalsResult = generator.generateMarketingAndCover(
                projectTitle = project.title,
                projectSynopsis = project.synopsis,
                projectGenre = project.genre,
                projectTone = project.tone,
                canonSnapshot = canonSnapshot
            )

            val rawResult = serializeProposalsToJson(proposalsResult)
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

            Pair(successJob, Result.success(proposalsResult))
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Erreur inconnue lors de la génération marketing."
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
     * Détecte les contradictions éventuelles pour le titre, synopsis et la description de couverture
     * contre la Story Bible, la Character Bible et les règles absolues.
     */
    suspend fun detectContradictions(
        projectId: String,
        chosenTitle: String,
        chosenSynopsis: String,
        coverDescription: String
    ): List<Contradiction> = withContext(Dispatchers.IO) {
        val metadataContradictions = canonRepository.detectContradictions(
            projectId = projectId,
            element = CanonElement.ProjectMetadata(
                finalTitle = chosenTitle,
                finalSynopsis = chosenSynopsis,
                isUserValidated = false
            )
        )

        val coverContradictions = canonRepository.detectContradictions(
            projectId = projectId,
            element = CanonElement.Cover(
                entity = CoverEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = chosenTitle,
                    visualDescription = coverDescription,
                    isCanon = false
                ),
                isUserValidated = false
            )
        )

        metadataContradictions + coverContradictions
    }

    /**
     * Valide et inscrit les éléments choisis par l'utilisateur dans le CanonMemory et la base Room.
     * Met à jour ProjectEntity (finalTitle, finalSynopsis) et insère CoverEntity.
     */
    suspend fun validateAndSaveToCanon(
        projectId: String,
        chosenTitle: String,
        chosenSynopsis: String,
        chosenTagline: String,
        coverDescription: String
    ): List<Contradiction> = withContext(Dispatchers.IO) {
        // 1. Inscription du titre final et du synopsis final dans le canon
        val metadataContradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.ProjectMetadata(
                finalTitle = chosenTitle,
                finalSynopsis = chosenSynopsis,
                isUserValidated = true
            )
        )

        // 2. Inscription de la description de couverture dans le canon
        val coverEntity = CoverEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            title = chosenTitle,
            tagline = chosenTagline.ifBlank { null },
            visualDescription = coverDescription,
            isCanon = true,
            createdAt = System.currentTimeMillis()
        )

        val coverContradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.Cover(
                entity = coverEntity,
                isUserValidated = true
            )
        )

        metadataContradictions + coverContradictions
    }

    private fun serializeProposalsToJson(result: MarketingCoverProposalsResult): String {
        return JSONObject().apply {
            put("projectId", result.projectId)
            put("titleProposals", JSONArray(result.titleProposals))

            val synArray = JSONArray()
            result.synopsisVariants.forEach { s ->
                synArray.put(JSONObject().apply {
                    put("lengthType", s.lengthType)
                    put("text", s.text)
                    put("tagline", s.tagline)
                })
            }
            put("synopsisVariants", synArray)

            put("coverProposal", JSONObject().apply {
                put("composition", result.coverProposal.composition)
                put("charactersPresent", JSONArray(result.coverProposal.charactersPresent))
                put("atmosphereAndLighting", result.coverProposal.atmosphereAndLighting)
                put("colorPalette", result.coverProposal.colorPalette)
                put("detailedDescriptionNoText", result.coverProposal.detailedDescriptionNoText)
            })
        }.toString()
    }
}

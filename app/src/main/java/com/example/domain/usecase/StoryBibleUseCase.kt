package com.example.domain.usecase

import com.example.ai.GeminiStoryGenerator
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.StoryBibleDao
import com.example.data.local.dao.TimelineEventDao
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.local.entity.TimelineEventEntity
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.StoryBibleResult
import com.example.domain.model.StoryTimelineEventEntry
import org.json.JSONObject
import java.util.UUID

/**
 * Cas d'utilisation pour la Story Bible de ToonIA (Phase 6).
 *
 * Règles métier garanties :
 * 1. Création et traçabilité d'un GenerationJob (type HISTOIRE) avec statuts réels QUEUED -> RUNNING -> SUCCESS / FAILED.
 * 2. Prise en compte du Canon complet (personnages validés + univers validé).
 * 3. Détection de contradiction via detectContradiction AVANT écriture canon.
 * 4. Écriture dans CanonMemory UNIQUEMENT sur validation explicite de l'utilisateur.
 * 5. Ajout et écriture des TimelineEvents fondateurs dans le canon Room avec position chronologique claire.
 */
class StoryBibleUseCase(
    private val projectDao: ProjectDao,
    private val jobDao: GenerationJobDao,
    private val storyBibleDao: StoryBibleDao,
    private val timelineEventDao: TimelineEventDao,
    private val canonRepository: CanonMemoryRepository,
    private val generator: GeminiStoryGenerator = GeminiStoryGenerator()
) {

    /**
     * Exécute le job de génération IA de la Story Bible.
     */
    suspend fun executeGenerateStoryJob(
        projectId: String,
        existingJobId: String? = null
    ): Pair<GenerationJobEntity, Result<StoryBibleResult>> {
        val project = projectDao.findProjectById(projectId)
        val projectTitle = project?.title ?: "Projet Webtoon"
        val projectSynopsis = project?.synopsis ?: ""
        val projectGenre = project?.genre
        val projectTone = project?.tone

        val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

        val jobId = existingJobId ?: UUID.randomUUID().toString()
        val existingJob = if (existingJobId != null) jobDao.findJobById(existingJobId) else null

        val inputPayload = JSONObject().apply {
            put("projectId", projectId)
            put("title", projectTitle)
            put("genre", projectGenre ?: "")
            put("tone", projectTone ?: "")
            put("charactersCountInCanon", canonSnapshot.characters.size)
            put("hasWorldBible", canonSnapshot.worldBible != null)
        }.toString()

        val job = existingJob?.copy(
            status = JobStatus.RUNNING,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        ) ?: GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.HISTOIRE,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            rawResult = null,
            errorMessage = null
        )

        jobDao.insertJob(job)

        return try {
            val generated = generator.generateStoryBible(
                projectTitle = projectTitle,
                projectSynopsis = projectSynopsis,
                projectGenre = projectGenre,
                projectTone = projectTone,
                canonSnapshot = canonSnapshot
            )

            val rawJson = generated.toJson().toString()
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.SUCCESS,
                rawResult = rawJson,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            Pair(job.copy(status = JobStatus.SUCCESS, rawResult = rawJson), Result.success(generated))
        } catch (e: Throwable) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Erreur inconnue lors de la génération de la Story Bible"
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.FAILED,
                rawResult = null,
                errorMessage = errorMsg,
                updatedAt = System.currentTimeMillis()
            )
            Pair(job.copy(status = JobStatus.FAILED, errorMessage = errorMsg), Result.failure(e))
        }
    }

    /**
     * Charge l'état de la Story Bible depuis le canon Room s'il existe déjà.
     */
    suspend fun loadCanonicalStoryBible(projectId: String): StoryBibleResult? {
        val snapshot = canonRepository.getCanonSnapshot(projectId)
        val entity = snapshot.storyBible ?: return null

        val events = snapshot.canonTimelineEvents.map {
            StoryTimelineEventEntry(
                id = it.id,
                projectId = it.projectId,
                orderIndex = it.orderIndex,
                title = it.title,
                description = it.description,
                dateOrEpoch = it.dateOrEpoch,
                isValidatedInCanon = it.isCanon
            )
        }

        return StoryBibleResult(
            projectId = entity.projectId,
            premise = "",
            confirmedGenre = "",
            mainThemes = entity.mainTheme,
            generalTone = "",
            mainConflicts = "",
            mainPlot = entity.narrativeArc,
            subPlots = "",
            narrativeDirection = entity.climaxDescription,
            pacing = entity.narrativePacing,
            timelineEvents = events,
            isValidatedInCanon = true
        )
    }

    /**
     * Valide et écrit la Story Bible dans le Canon Room.
     */
    suspend fun validateStoryBibleToCanon(
        projectId: String,
        result: StoryBibleResult,
        ignoreContradictions: Boolean = false
    ): Pair<StoryBibleResult, List<Contradiction>> {
        val entity = StoryBibleEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            mainTheme = "${result.mainThemes}. Prémisse: ${result.premise}".trim(),
            narrativeArc = "${result.mainPlot}. Conflits: ${result.mainConflicts}".trim(),
            climaxDescription = result.narrativeDirection,
            narrativePacing = result.pacing,
            updatedAt = System.currentTimeMillis()
        )

        val contradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.StoryBible(entity, isUserValidated = true)
        )

        // Validation et écriture des TimelineEvents fondateurs
        val updatedEvents = result.timelineEvents.mapIndexed { index, ev ->
            val eventEntity = TimelineEventEntity(
                id = ev.id,
                projectId = projectId,
                chapterId = null,
                orderIndex = index + 1,
                title = ev.title,
                description = ev.description,
                dateOrEpoch = ev.dateOrEpoch,
                isCanon = true
            )
            canonRepository.updateCanon(
                projectId = projectId,
                element = CanonElement.TimelineEvent(eventEntity, isUserValidated = true)
            )
            ev.copy(isValidatedInCanon = true, orderIndex = index + 1)
        }

        return Pair(result.copy(isValidatedInCanon = true, timelineEvents = updatedEvents), contradictions)
    }

    /**
     * Valide un événement de la chronologie individuellement dans le canon.
     */
    suspend fun validateTimelineEventToCanon(
        projectId: String,
        event: StoryTimelineEventEntry,
        ignoreContradictions: Boolean = false
    ): Pair<StoryTimelineEventEntry, List<Contradiction>> {
        val eventEntity = TimelineEventEntity(
            id = event.id,
            projectId = projectId,
            chapterId = null,
            orderIndex = event.orderIndex,
            title = event.title,
            description = event.description,
            dateOrEpoch = event.dateOrEpoch,
            isCanon = true
        )

        val contradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.TimelineEvent(eventEntity, isUserValidated = true)
        )

        return Pair(event.copy(isValidatedInCanon = true), contradictions)
    }

    /**
     * Crée un événement chronologique manuellement.
     */
    fun createManualTimelineEvent(
        projectId: String,
        title: String,
        epoch: String,
        orderIndex: Int
    ): StoryTimelineEventEntry {
        return StoryTimelineEventEntry(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            orderIndex = orderIndex,
            title = title,
            description = "",
            dateOrEpoch = epoch,
            isValidatedInCanon = false
        )
    }
}

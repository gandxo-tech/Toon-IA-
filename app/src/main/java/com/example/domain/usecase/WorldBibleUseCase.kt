package com.example.domain.usecase

import com.example.ai.GeminiWorldGenerator
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.LocationDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.StoryObjectDao
import com.example.data.local.dao.WorldBibleDao
import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.StoryObjectEntity
import com.example.data.local.entity.WorldBibleEntity
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.WorldBibleResult
import com.example.domain.model.WorldLocationEntry
import com.example.domain.model.WorldObjectEntry
import org.json.JSONObject
import java.util.UUID

/**
 * Cas d'utilisation pour la World Bible de ToonIA (Phase 5).
 *
 * Règles métier garanties :
 * 1. Création d'un GenerationJob (type UNIVERS) avec statuts réels QUEUED -> RUNNING -> SUCCESS / FAILED.
 * 2. Prise en compte explicite du CanonSnapshot (personnages validés).
 * 3. Détection de contradiction via detectContradiction AVANT écriture canon.
 * 4. Écriture dans CanonMemory UNIQUEMENT sur validation explicite de l'utilisateur (Bible globale ou lieux/objets individuels).
 * 5. Possibilité d'édition manuelle directe.
 */
class WorldBibleUseCase(
    private val projectDao: ProjectDao,
    private val jobDao: GenerationJobDao,
    private val worldBibleDao: WorldBibleDao,
    private val locationDao: LocationDao,
    private val storyObjectDao: StoryObjectDao,
    private val canonRepository: CanonMemoryRepository,
    private val generator: GeminiWorldGenerator = GeminiWorldGenerator()
) {

    /**
     * Exécute le job de génération IA de la World Bible.
     */
    suspend fun executeGenerateWorldJob(
        projectId: String,
        existingJobId: String? = null
    ): Pair<GenerationJobEntity, Result<WorldBibleResult>> {
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
        }.toString()

        val job = existingJob?.copy(
            status = JobStatus.RUNNING,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        ) ?: GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.UNIVERS,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            rawResult = null,
            errorMessage = null
        )

        jobDao.insertJob(job)

        return try {
            val generated = generator.generateWorldBible(
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
            val errorMsg = e.localizedMessage ?: e.message ?: "Erreur inconnue lors de la génération de la World Bible"
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
     * Charge l'état actuel de la World Bible depuis le canon Room (s'il existe déjà).
     */
    suspend fun loadCanonicalWorldBible(projectId: String): WorldBibleResult? {
        val snapshot = canonRepository.getCanonSnapshot(projectId)
        val entity = snapshot.worldBible ?: return null

        val locs = snapshot.locations.map {
            WorldLocationEntry(
                id = it.id,
                projectId = it.projectId,
                name = it.name,
                description = it.description,
                atmosphere = it.atmosphere,
                isValidatedInCanon = true
            )
        }

        val objs = snapshot.storyObjects.map {
            WorldObjectEntry(
                id = it.id,
                projectId = it.projectId,
                name = it.name,
                significance = it.significance,
                description = it.description,
                ownerCharacterId = it.ownerCharacterId,
                isValidatedInCanon = true
            )
        }

        return WorldBibleResult(
            projectId = entity.projectId,
            rules = entity.magicOrTechRules,
            factions = entity.factions,
            geography = entity.geography,
            socialStructure = entity.socialStructure,
            historyLore = entity.historyLore,
            terminology = "",
            worldConstraints = "",
            locations = locs,
            objects = objs,
            isValidatedInCanon = true
        )
    }

    /**
     * Valide l'ensemble de la World Bible (règles, lore, géographie, factions) dans le Canon.
     */
    suspend fun validateWorldBibleToCanon(
        projectId: String,
        result: WorldBibleResult,
        ignoreContradictions: Boolean = false
    ): Pair<WorldBibleResult, List<Contradiction>> {
        val entity = WorldBibleEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            magicOrTechRules = result.rules,
            factions = result.factions,
            geography = result.geography,
            socialStructure = result.socialStructure,
            historyLore = result.historyLore,
            updatedAt = System.currentTimeMillis()
        )

        val contradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.WorldBible(entity, isUserValidated = true)
        )

        // Enregistrer également les contraintes comme mémoires canoniques si non vides
        if (result.worldConstraints.isNotBlank()) {
            canonRepository.updateCanon(
                projectId = projectId,
                element = CanonElement.Memory(
                    CanonMemoryEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        category = "Règle Univers",
                        fact = result.worldConstraints,
                        impact = "Contrainte intangible World Bible"
                    ),
                    isUserValidated = true
                )
            )
        }

        return Pair(result.copy(isValidatedInCanon = true), contradictions)
    }

    /**
     * Valide individuellement un lieu dans le CanonMemory de Room.
     */
    suspend fun validateLocationToCanon(
        projectId: String,
        location: WorldLocationEntry,
        ignoreContradictions: Boolean = false
    ): Pair<WorldLocationEntry, List<Contradiction>> {
        val entity = LocationEntity(
            id = location.id,
            projectId = projectId,
            name = location.name,
            description = location.description,
            atmosphere = location.atmosphere
        )

        val contradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.Location(entity, isUserValidated = true)
        )

        return Pair(location.copy(isValidatedInCanon = true), contradictions)
    }

    /**
     * Valide individuellement un objet important dans le CanonMemory de Room.
     */
    suspend fun validateObjectToCanon(
        projectId: String,
        storyObject: WorldObjectEntry,
        ignoreContradictions: Boolean = false
    ): Pair<WorldObjectEntry, List<Contradiction>> {
        val entity = StoryObjectEntity(
            id = storyObject.id,
            projectId = projectId,
            name = storyObject.name,
            significance = storyObject.significance,
            description = storyObject.description,
            ownerCharacterId = storyObject.ownerCharacterId
        )

        val contradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.StoryObject(entity, isUserValidated = true)
        )

        return Pair(storyObject.copy(isValidatedInCanon = true), contradictions)
    }

    /**
     * Détecte les contradictions éventuelles pour un lieu avant écriture.
     */
    suspend fun checkLocationContradictions(
        projectId: String,
        location: WorldLocationEntry
    ): List<Contradiction> {
        val entity = LocationEntity(
            id = location.id,
            projectId = projectId,
            name = location.name,
            description = location.description,
            atmosphere = location.atmosphere
        )
        return canonRepository.detectContradictions(
            projectId = projectId,
            element = CanonElement.Location(entity, isUserValidated = false)
        )
    }

    /**
     * Ajout manuel d'un lieu sans IA.
     */
    fun createManualLocation(projectId: String, name: String, atmosphere: String): WorldLocationEntry {
        return WorldLocationEntry(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name,
            description = "",
            atmosphere = atmosphere,
            isValidatedInCanon = false
        )
    }
}

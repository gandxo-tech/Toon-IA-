package com.example.domain.usecase

import com.example.ai.GeminiCharacterGenerator
import com.example.data.local.dao.CharacterDao
import com.example.data.local.dao.CharacterStateDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CharacterStateEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.CharacterBibleEntry
import com.example.domain.model.CharacterBibleResult
import org.json.JSONObject
import java.util.UUID

/**
 * Cas d'utilisation orchestrant la Character Bible de ToonIA (Phase 4).
 *
 * Règles métier garanties :
 * 1. Création et traçabilité d'un GenerationJob (type PERSONNAGES) avec statuts réels QUEUED -> RUNNING -> SUCCESS / FAILED.
 * 2. Prise en compte du CanonSnapshot via getCanonSnapshot.
 * 3. Détection de contradiction via detectContradiction AVANT écriture canon.
 * 4. Écriture dans CanonMemory UNIQUEMENT sur validation explicite de l'utilisateur.
 * 5. Possibilité d'ajouter manuellement un personnage sans IA.
 * 6. Possibilité de régénérer spécifiquement un seul personnage sans toucher aux autres.
 */
class CharacterBibleUseCase(
    private val projectDao: ProjectDao,
    private val jobDao: GenerationJobDao,
    private val characterDao: CharacterDao,
    private val characterStateDao: CharacterStateDao,
    private val canonRepository: CanonMemoryRepository,
    private val generator: GeminiCharacterGenerator = GeminiCharacterGenerator()
) {

    /**
     * Génère une liste de personnages à partir de l'analyse validée du projet et du canon actuel.
     */
    suspend fun executeGenerateCharactersJob(
        projectId: String,
        existingJobId: String? = null
    ): Pair<GenerationJobEntity, Result<CharacterBibleResult>> {
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
            put("projectTitle", projectTitle)
            put("genre", projectGenre ?: "")
            put("tone", projectTone ?: "")
            put("action", "generate_character_bible")
        }.toString()

        val currentJob = if (existingJob != null) {
            existingJob.copy(
                status = JobStatus.QUEUED,
                inputPayload = inputPayload,
                retryCount = existingJob.retryCount + 1,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            GenerationJobEntity(
                id = jobId,
                projectId = projectId,
                type = JobType.PERSONNAGES,
                status = JobStatus.QUEUED,
                inputPayload = inputPayload,
                rawResult = null,
                errorMessage = null,
                retryCount = 0
            )
        }

        jobDao.insertJob(currentJob)

        // Passage à RUNNING
        jobDao.updateJobOutcome(
            id = jobId,
            status = JobStatus.RUNNING,
            rawResult = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )

        return try {
            val result = generator.generateCharacterBible(
                projectTitle = projectTitle,
                projectSynopsis = projectSynopsis,
                projectGenre = projectGenre,
                projectTone = projectTone,
                canonSnapshot = canonSnapshot
            )

            val rawJson = result.toJsonString()

            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.SUCCESS,
                rawResult = rawJson,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            val finishedJob = currentJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = rawJson,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            Pair(finishedJob, Result.success(result))
        } catch (e: Throwable) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Erreur lors de la génération des personnages"

            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.FAILED,
                rawResult = null,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )

            val failedJob = currentJob.copy(
                status = JobStatus.FAILED,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Régénère spécifiquement un unique personnage sans écraser les autres.
     */
    suspend fun executeRegenerateSingleCharacterJob(
        projectId: String,
        targetCharacter: CharacterBibleEntry,
        userGuidance: String?
    ): Pair<GenerationJobEntity, Result<CharacterBibleEntry>> {
        val project = projectDao.findProjectById(projectId)
        val projectTitle = project?.title ?: "Projet Webtoon"
        val projectSynopsis = project?.synopsis ?: ""
        val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

        val jobId = UUID.randomUUID().toString()
        val inputPayload = JSONObject().apply {
            put("projectId", projectId)
            put("characterId", targetCharacter.id)
            put("characterName", targetCharacter.name)
            put("userGuidance", userGuidance ?: "")
            put("action", "regenerate_single_character")
        }.toString()

        val job = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.PERSONNAGES,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            rawResult = null,
            errorMessage = null,
            retryCount = 0
        )
        jobDao.insertJob(job)

        return try {
            val regenerated = generator.regenerateSingleCharacter(
                targetCharacter = targetCharacter,
                userGuidance = userGuidance,
                projectTitle = projectTitle,
                projectSynopsis = projectSynopsis,
                canonSnapshot = canonSnapshot
            )

            val rawJson = regenerated.toJson().toString()
            jobDao.updateJobOutcome(
                id = jobId,
                status = JobStatus.SUCCESS,
                rawResult = rawJson,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )

            Pair(job.copy(status = JobStatus.SUCCESS, rawResult = rawJson), Result.success(regenerated))
        } catch (e: Throwable) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Erreur de régénération"
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
     * Vérifie si un personnage candidat entre en contradiction avec le canon actuel sans écrire en base.
     */
    suspend fun checkContradictions(
        projectId: String,
        entry: CharacterBibleEntry
    ): List<Contradiction> {
        val candidateEntity = toCharacterEntity(projectId, entry)
        return canonRepository.detectContradictions(
            projectId = projectId,
            element = CanonElement.Character(candidateEntity, isUserValidated = false)
        )
    }

    /**
     * Écrit le personnage dans le CanonMemory de Room UNIQUEMENT lors d'une validation explicite.
     * Enregistre également son état initial (CharacterStateEntity).
     * Retourne les contradictions éventuelles détectées.
     */
    suspend fun validateAndWriteToCanon(
        projectId: String,
        entry: CharacterBibleEntry,
        ignoreContradictions: Boolean = false
    ): Pair<CharacterBibleEntry, List<Contradiction>> {
        val entity = toCharacterEntity(projectId, entry)
        val contradictions = canonRepository.updateCanon(
            projectId = projectId,
            element = CanonElement.Character(entity, isUserValidated = true)
        )

        // Enregistrer également l'état initial (CharacterStateEntity)
        val stateEntity = CharacterStateEntity(
            id = UUID.randomUUID().toString(),
            characterId = entry.id,
            projectId = projectId,
            chapterId = null,
            sceneId = null,
            currentAppearance = "${entry.physicalAppearance}. Vêtements : ${entry.characteristicClothing}".trim(),
            currentRelationships = entry.relationships,
            psychologicalState = entry.currentState.ifBlank { entry.personality },
            timestamp = System.currentTimeMillis()
        )
        characterStateDao.insertState(stateEntity)

        val validatedEntry = entry.copy(isValidatedInCanon = true)
        return Pair(validatedEntry, contradictions)
    }

    /**
     * Crée un personnage manuellement sans passer par l'IA.
     */
    fun createManualCharacter(
        projectId: String,
        name: String,
        role: String
    ): CharacterBibleEntry {
        return CharacterBibleEntry(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name.ifBlank { "Nouveau personnage" },
            role = role.ifBlank { "Secondaire" },
            personality = "",
            physicalAppearance = "",
            distinctiveFeatures = "",
            characteristicClothing = "",
            abilities = "",
            relationships = "",
            plannedArc = "",
            currentState = "",
            isValidatedInCanon = false
        )
    }

    /**
     * Charge les personnages déjà validés dans le canon pour ce projet.
     */
    suspend fun loadCanonicalCharacters(projectId: String): List<CharacterBibleEntry> {
        val snapshot = canonRepository.getCanonSnapshot(projectId)
        return snapshot.characters.map { entity ->
            CharacterBibleEntry(
                id = entity.id,
                projectId = entity.projectId,
                name = entity.name,
                role = entity.role,
                personality = entity.personalityTraits,
                physicalAppearance = entity.visualDescription,
                distinctiveFeatures = "",
                characteristicClothing = "",
                abilities = "",
                relationships = "",
                plannedArc = entity.backstory,
                currentState = "",
                isValidatedInCanon = true
            )
        }
    }

    private fun toCharacterEntity(projectId: String, entry: CharacterBibleEntry): CharacterEntity {
        val combinedVisual = buildString {
            append(entry.physicalAppearance)
            if (entry.distinctiveFeatures.isNotBlank()) {
                append(". Signes distinctifs : ").append(entry.distinctiveFeatures)
            }
            if (entry.characteristicClothing.isNotBlank()) {
                append(". Tenue : ").append(entry.characteristicClothing)
            }
        }.trim()

        return CharacterEntity(
            id = entry.id,
            projectId = projectId,
            name = entry.name,
            role = entry.role,
            archetype = entry.role,
            personalityTraits = entry.personality,
            backstory = entry.plannedArc,
            visualDescription = combinedVisual,
            canonicalImagePath = null,
            createdAt = System.currentTimeMillis()
        )
    }
}

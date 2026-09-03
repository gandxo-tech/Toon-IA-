package com.example.domain.usecase

import com.example.ai.GeminiChapterScriptGenerator
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.CharacterDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.LocationDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.SceneDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.CanonContradictionDetector
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.CanonSnapshot
import com.example.domain.canon.Contradiction
import com.example.domain.canon.ContradictionSeverity
import com.example.domain.model.ChapterScriptDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Données complètes d'un script de chapitre avec ses scènes et ses contradictions éventuelles.
 */
data class ChapterScriptResult(
    val chapter: ChapterEntity,
    val scenes: List<SceneEntity>,
    val contradictions: List<Contradiction> = emptyList()
)

/**
 * UseCase pour la Phase 10 : Script structuré du Chapitre 1.
 *
 * RÈGLES APPLIQUÉES :
 * - Tracé via GenerationJob (JobType.CHAPITRE, statuts QUEUED -> RUNNING -> SUCCESS / FAILED).
 * - S'appuie sur l'intégralité du canon actuel (CanonSnapshot).
 * - Le script généré est un BROUILLON MODIFIABLE : isCanon = false (aucune écriture définitive
 *   dans le canon tant que le chapitre entier n'est pas validé en Phase 18).
 * - Détection et signalement des contradictions sans correction silencieuse.
 */
class ChapterScriptUseCase(
    private val projectDao: ProjectDao,
    private val chapterDao: ChapterDao,
    private val sceneDao: SceneDao,
    private val locationDao: LocationDao,
    private val characterDao: CharacterDao,
    private val jobDao: GenerationJobDao,
    private val canonRepository: CanonMemoryRepository,
    private val contradictionDetector: CanonContradictionDetector = CanonContradictionDetector(),
    private val generator: GeminiChapterScriptGenerator
) {

    /**
     * Charge le brouillon du Chapitre 1 et ses scènes associées.
     */
    suspend fun loadChapter1Draft(projectId: String): Pair<ChapterEntity?, List<SceneEntity>> = withContext(Dispatchers.IO) {
        val chapters = chapterDao.getChaptersByProjectId(projectId).firstOrNull() ?: emptyList()
        val chapter1 = chapters.firstOrNull { it.number == 1 } ?: return@withContext Pair(null, emptyList())
        val scenes = sceneDao.getScenesByChapterId(chapter1.id).firstOrNull() ?: emptyList()
        Pair(chapter1, scenes)
    }

    /**
     * Exécute la génération IA du script du Chapitre 1.
     */
    suspend fun executeGenerateChapter1Script(
        projectId: String,
        userOrientation: String?
    ): Pair<GenerationJobEntity, Result<ChapterScriptResult>> = withContext(Dispatchers.IO) {
        val project = projectDao.findProjectById(projectId)
            ?: return@withContext Pair(
                createFailedJob(projectId, "Projet introuvable pour l'id: $projectId"),
                Result.failure(IllegalArgumentException("Projet introuvable."))
            )

        val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

        val jobId = UUID.randomUUID().toString()
        val inputPayload = JSONObject().apply {
            put("projectId", projectId)
            put("chapterNumber", 1)
            put("userOrientation", userOrientation ?: "")
            put("charactersCount", canonSnapshot.characters.size)
            put("locationsCount", canonSnapshot.locations.size)
        }.toString()

        val runningJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.CHAPITRE,
            status = JobStatus.RUNNING,
            inputPayload = inputPayload,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        jobDao.insertJob(runningJob)

        try {
            // Appel au générateur Gemini
            val scriptDraft = generator.generateChapter1Script(
                userOrientation = userOrientation,
                canonSnapshot = canonSnapshot
            )

            // Récupérer ou initialiser l'entité ChapterEntity pour le chapitre 1
            val existingChapters = chapterDao.getChaptersByProjectId(projectId).firstOrNull() ?: emptyList()
            val existingChapter1 = existingChapters.firstOrNull { it.number == 1 }

            val chapterId = existingChapter1?.id ?: UUID.randomUUID().toString()
            val chapterEntity = ChapterEntity(
                id = chapterId,
                projectId = projectId,
                number = 1,
                title = scriptDraft.title,
                synopsis = scriptDraft.synopsis,
                isCanon = false, // BROUILLON MODIFIABLE : Jamais canonique à ce stade !
                draftNotes = userOrientation,
                createdAt = existingChapter1?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            chapterDao.insertChapter(chapterEntity)

            // Supprimer les anciennes scènes de ce chapitre brouillon s'il s'agit d'une régénération
            sceneDao.deleteScenesByChapterId(chapterId)

            // Insérer les nouvelles scènes du script
            val scenesToInsert = scriptDraft.scenes.map { sceneDraft ->
                // Associer au lieu canonique si le nom concorde
                val matchedLocation = canonSnapshot.locations.firstOrNull {
                    it.name.trim().equals(sceneDraft.locationName.trim(), ignoreCase = true)
                }

                // Construire le résumé complet incluant le lieu, les personnages et les beats narratifs
                val formattedSummary = buildSceneNarrativeContent(sceneDraft)

                SceneEntity(
                    id = UUID.randomUUID().toString(),
                    chapterId = chapterId,
                    projectId = projectId,
                    locationId = matchedLocation?.id,
                    orderIndex = sceneDraft.orderIndex,
                    title = sceneDraft.title,
                    narrativeSummary = formattedSummary,
                    charactersPresent = sceneDraft.charactersPresent.joinToString(", "),
                    locationName = sceneDraft.locationName,
                    atmosphere = "",
                    narrativeBeats = sceneDraft.narrativeBeats.joinToString("\n• ", prefix = if (sceneDraft.narrativeBeats.isNotEmpty()) "• " else ""),
                    isCanon = false // BROUILLON : Non canonique
                )
            }

            sceneDao.insertScenes(scenesToInsert)

            // Détection des contradictions avec le canon
            val contradictions = mutableListOf<Contradiction>()

            // 1. Vérification au niveau du chapitre
            contradictions.addAll(
                contradictionDetector.detectContradiction(
                    CanonElement.Chapter(chapterEntity, isUserValidated = false),
                    canonSnapshot
                )
            )

            // 2. Vérification de la cohérence des personnages et lieux mentionnés dans les scènes
            contradictions.addAll(
                checkScenesContradictions(scriptDraft, canonSnapshot)
            )

            // Mémorisation du résultat brut dans le job
            val rawResultJson = serializeScriptDraftToJson(scriptDraft)
            val successJob = runningJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = rawResultJson,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(successJob)

            Pair(
                successJob,
                Result.success(
                    ChapterScriptResult(
                        chapter = chapterEntity,
                        scenes = scenesToInsert,
                        contradictions = contradictions
                    )
                )
            )
        } catch (e: Exception) {
            val failedJob = runningJob.copy(
                status = JobStatus.FAILED,
                errorMessage = e.message ?: "Erreur inconnue lors de la génération du script",
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(failedJob)

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Met à jour le titre ou le synopsis du chapitre brouillon dans Room.
     */
    suspend fun updateChapterDraft(chapter: ChapterEntity) = withContext(Dispatchers.IO) {
        chapterDao.updateChapter(chapter.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Met à jour une scène spécifique du chapitre brouillon dans Room.
     */
    suspend fun updateSceneDraft(scene: SceneEntity) = withContext(Dispatchers.IO) {
        sceneDao.updateScene(scene)
    }

    private fun buildSceneNarrativeContent(sceneDraft: com.example.domain.model.SceneScriptDraft): String {
        return buildString {
            appendLine("Lieu : ${sceneDraft.locationName}")
            if (sceneDraft.charactersPresent.isNotEmpty()) {
                appendLine("Personnages : ${sceneDraft.charactersPresent.joinToString(", ")}")
            }
            appendLine()
            appendLine("Résumé de l'action :")
            appendLine(sceneDraft.summary)
            if (sceneDraft.narrativeBeats.isNotEmpty()) {
                appendLine()
                appendLine("Beats narratifs :")
                sceneDraft.narrativeBeats.forEachIndexed { idx, beat ->
                    appendLine("${idx + 1}. $beat")
                }
            }
        }.trim()
    }

    private fun checkScenesContradictions(
        draft: ChapterScriptDraft,
        canonSnapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()

        // Vérifier si des personnages mentionnés n'existent pas du tout dans le canon
        val canonNames = canonSnapshot.characters.map { it.name.trim().lowercase() }
        if (canonNames.isNotEmpty()) {
            draft.scenes.forEach { scene ->
                scene.charactersPresent.forEach { charName ->
                    val trimmed = charName.trim().lowercase()
                    if (!canonNames.contains(trimmed) && !canonNames.any { it.contains(trimmed) || trimmed.contains(it) }) {
                        contradictions.add(
                            Contradiction(
                                targetType = "Scène ${scene.orderIndex}",
                                targetName = charName,
                                fieldName = "Personnage Inconnu",
                                canonicalValue = canonSnapshot.characters.joinToString(", ") { it.name },
                                conflictingValue = charName,
                                description = "Le personnage '$charName' apparaît dans la scène '${scene.title}' mais n'est pas encore répertorié dans la Bible Personnages.",
                                severity = ContradictionSeverity.MEDIUM
                            )
                        )
                    }
                }
            }
        }

        return contradictions
    }

    private fun serializeScriptDraftToJson(draft: ChapterScriptDraft): String {
        return JSONObject().apply {
            put("chapterNumber", draft.chapterNumber)
            put("title", draft.title)
            put("synopsis", draft.synopsis)
            val scenesArray = JSONArray()
            draft.scenes.forEach { s ->
                scenesArray.put(JSONObject().apply {
                    put("orderIndex", s.orderIndex)
                    put("title", s.title)
                    put("locationName", s.locationName)
                    put("charactersPresent", JSONArray(s.charactersPresent))
                    put("summary", s.summary)
                    put("narrativeBeats", JSONArray(s.narrativeBeats))
                })
            }
            put("scenes", scenesArray)
        }.toString()
    }

    private fun createFailedJob(projectId: String, errorMessage: String): GenerationJobEntity {
        return GenerationJobEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            type = JobType.CHAPITRE,
            status = JobStatus.FAILED,
            inputPayload = "{}",
            errorMessage = errorMessage
        )
    }
}

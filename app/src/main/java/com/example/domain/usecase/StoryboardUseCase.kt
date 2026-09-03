package com.example.domain.usecase

import com.example.ai.GeminiSceneDetailGenerator
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.SceneDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.model.SceneDetailDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * UseCase pour la Phase 11 : Storyboard & Découpage en scènes détaillées.
 *
 * Gère :
 * - L'affichage et la persistance locale des scènes (Room).
 * - Le réordonnancement manuel des scènes (haut/bas).
 * - La fusion de deux scènes adjacentes.
 * - La scission d'une scène en deux scènes distinctes.
 * - L'enrichissement IA ciblé d'une scène (rythme, arc émotionnel, micro-beats).
 * - La validation explicite des scènes vers le Canon.
 */
class StoryboardUseCase(
    private val chapterDao: ChapterDao,
    private val sceneDao: SceneDao,
    private val jobDao: GenerationJobDao,
    private val canonRepository: CanonMemoryRepository,
    private val detailGenerator: GeminiSceneDetailGenerator
) {

    fun getScenesByChapter(chapterId: String): Flow<List<SceneEntity>> {
        return sceneDao.getScenesByChapterId(chapterId)
    }

    fun getChapterForProject(projectId: String): Flow<ChapterEntity?> {
        return chapterDao.getDraftChapterByProjectId(projectId)
    }

    /**
     * Déplace une scène vers le haut (directionUp = true) ou vers le bas (directionUp = false).
     */
    suspend fun moveScene(chapterId: String, sceneId: String, directionUp: Boolean) = withContext(Dispatchers.IO) {
        val scenes = sceneDao.getScenesByChapterId(chapterId).firstOrNull()?.sortedBy { it.orderIndex } ?: return@withContext
        val currentIndex = scenes.indexOfFirst { it.id == sceneId }
        if (currentIndex == -1) return@withContext

        val targetIndex = if (directionUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex !in scenes.indices) return@withContext

        val currentScene = scenes[currentIndex]
        val targetScene = scenes[targetIndex]

        val updatedCurrent = currentScene.copy(orderIndex = targetScene.orderIndex)
        val updatedTarget = targetScene.copy(orderIndex = currentScene.orderIndex)

        sceneDao.updateScene(updatedCurrent)
        sceneDao.updateScene(updatedTarget)
    }

    /**
     * Fusionne deux scènes en une seule :
     * Le contenu de [secondSceneId] est agrégé dans [firstSceneId],
     * [secondSceneId] est supprimée, et les scènes suivantes sont réindexées.
     */
    suspend fun mergeScenes(chapterId: String, firstSceneId: String, secondSceneId: String) = withContext(Dispatchers.IO) {
        val scenes = sceneDao.getScenesByChapterId(chapterId).firstOrNull()?.sortedBy { it.orderIndex } ?: return@withContext
        val scene1 = scenes.firstOrNull { it.id == firstSceneId } ?: return@withContext
        val scene2 = scenes.firstOrNull { it.id == secondSceneId } ?: return@withContext

        // Fusion des contenus
        val mergedTitle = "${scene1.title} & ${scene2.title}"
        val mergedSummary = buildString {
            appendLine(scene1.narrativeSummary)
            appendLine("--- Suite (Fusion) ---")
            appendLine(scene2.narrativeSummary)
        }.trim()

        val mergedCharacters = setOf(
            *scene1.charactersPresent.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray(),
            *scene2.charactersPresent.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
        ).joinToString(", ")

        val mergedBeats = listOf(scene1.narrativeBeats, scene2.narrativeBeats)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val mergedAtmosphere = listOf(scene1.atmosphere, scene2.atmosphere)
            .filter { it.isNotBlank() }
            .joinToString(" / ")

        val mergedScene1 = scene1.copy(
            title = mergedTitle,
            narrativeSummary = mergedSummary,
            charactersPresent = mergedCharacters,
            narrativeBeats = mergedBeats,
            atmosphere = mergedAtmosphere,
            isCanon = false
        )

        sceneDao.updateScene(mergedScene1)
        sceneDao.deleteScene(scene2)

        // Ré-indexation propre
        reindexScenes(chapterId)
    }

    /**
     * Scinde une scène en deux :
     * Modifie [originalSceneId] avec le contenu de la première partie,
     * et insère une nouvelle scène immédiatement après pour la deuxième partie.
     */
    suspend fun splitScene(
        chapterId: String,
        projectId: String,
        originalSceneId: String,
        splitTitle: String,
        splitSummary: String,
        splitBeats: String = "",
        splitCharacters: String = "",
        splitAtmosphere: String = ""
    ) = withContext(Dispatchers.IO) {
        val scenes = sceneDao.getScenesByChapterId(chapterId).firstOrNull()?.sortedBy { it.orderIndex } ?: return@withContext
        val originalScene = scenes.firstOrNull { it.id == originalSceneId } ?: return@withContext

        // Décaler les scènes qui suivent la scène originale
        scenes.filter { it.orderIndex > originalScene.orderIndex }.forEach { s ->
            sceneDao.updateScene(s.copy(orderIndex = s.orderIndex + 1))
        }

        val newScene = SceneEntity(
            id = UUID.randomUUID().toString(),
            chapterId = chapterId,
            projectId = projectId,
            orderIndex = originalScene.orderIndex + 1,
            title = splitTitle,
            narrativeSummary = splitSummary,
            narrativeBeats = splitBeats,
            charactersPresent = splitCharacters.ifBlank { originalScene.charactersPresent },
            locationName = originalScene.locationName,
            atmosphere = splitAtmosphere.ifBlank { originalScene.atmosphere },
            isCanon = false
        )

        sceneDao.insertScene(newScene)

        // Réindexer toutes les scènes de façon séquentielle
        reindexScenes(chapterId)
    }

    /**
     * Réindexe les orderIndex des scènes d'un chapitre de 1 à N.
     */
    private suspend fun reindexScenes(chapterId: String) {
        val updatedScenes = sceneDao.getScenesByChapterId(chapterId).firstOrNull()
            ?.sortedWith(compareBy({ it.orderIndex }, { it.createdAt }))
            ?: return

        updatedScenes.forEachIndexed { index, scene ->
            val correctOrder = index + 1
            if (scene.orderIndex != correctOrder) {
                sceneDao.updateScene(scene.copy(orderIndex = correctOrder))
            }
        }
    }

    /**
     * Met à jour manuellement une scène (titre, résumé, beats, etc.).
     */
    suspend fun updateSceneDraft(scene: SceneEntity) = withContext(Dispatchers.IO) {
        sceneDao.updateScene(scene.copy(isCanon = false))
    }

    /**
     * Supprime une scène et réindexe le chapitre.
     */
    suspend fun deleteScene(scene: SceneEntity) = withContext(Dispatchers.IO) {
        sceneDao.deleteScene(scene)
        reindexScenes(scene.chapterId)
    }

    /**
     * Enrichit une scène précise avec l'IA Gemini (Phase 11).
     * Crée un GenerationJobEntity et met à jour les champs de la scène dans Room.
     */
    suspend fun detailSceneWithAi(
        projectId: String,
        scene: SceneEntity
    ): Pair<GenerationJobEntity, Result<SceneDetailDraft>> = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val initialJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.STORYBOARD,
            status = JobStatus.RUNNING,
            inputPayload = "Détailler la scène ${scene.orderIndex} : ${scene.title}"
        )
        jobDao.insertJob(initialJob)

        try {
            val canonSnapshot = canonRepository.getCanonSnapshot(projectId)
            val chapter = chapterDao.findChapterById(scene.chapterId)

            val detailDraft = detailGenerator.detailScene(
                scene = scene,
                canonSnapshot = canonSnapshot,
                chapterSynopsis = chapter?.synopsis ?: ""
            )

            // Mise à jour de la scène dans Room avec les éléments enrichis
            val formattedBeats = detailDraft.detailedNarrativeBeats.joinToString("\n• ", prefix = if (detailDraft.detailedNarrativeBeats.isNotEmpty()) "• " else "")

            val updatedScene = scene.copy(
                narrativeSummary = detailDraft.enrichedSummary.ifBlank { scene.narrativeSummary },
                pacingNotes = detailDraft.pacingNotes,
                emotionalArc = detailDraft.emotionalArc,
                narrativeBeats = formattedBeats.ifBlank { scene.narrativeBeats },
                atmosphere = detailDraft.atmosphereAndSensory.ifBlank { scene.atmosphere },
                isCanon = false // Reste brouillon tant que non validé
            )
            sceneDao.updateScene(updatedScene)

            // Sauvegarde du résultat brut dans le Job
            val rawResult = JSONObject().apply {
                put("sceneId", scene.id)
                put("enrichedSummary", detailDraft.enrichedSummary)
                put("pacingNotes", detailDraft.pacingNotes)
                put("emotionalArc", detailDraft.emotionalArc)
                put("detailedNarrativeBeats", JSONArray(detailDraft.detailedNarrativeBeats))
                put("atmosphereAndSensory", detailDraft.atmosphereAndSensory)
                put("characterInteractions", detailDraft.characterInteractions)
            }.toString()

            val successJob = initialJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = rawResult,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(successJob)

            Pair(successJob, Result.success(detailDraft))
        } catch (e: Exception) {
            val failedJob = initialJob.copy(
                status = JobStatus.FAILED,
                errorMessage = e.message ?: "Erreur lors de l'enrichissement de la scène",
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(failedJob)

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Valide explicitement l'ensemble des scènes d'un chapitre dans le Canon.
     */
    suspend fun validateStoryboardToCanon(chapterId: String, projectId: String) = withContext(Dispatchers.IO) {
        val scenes = sceneDao.getScenesByChapterId(chapterId).firstOrNull() ?: return@withContext
        scenes.forEach { scene ->
            val canonScene = scene.copy(isCanon = true)
            sceneDao.updateScene(canonScene)
            canonRepository.updateCanon(projectId, CanonElement.Scene(canonScene, isUserValidated = true))
        }
    }
}

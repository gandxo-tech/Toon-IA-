package com.example.domain.usecase

import com.example.ai.GeminiPanelGenerator
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.PanelDao
import com.example.data.local.dao.SceneDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.model.ScenePanelsDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * UseCase pour la Phase 12 : Découpage des Scènes en Panels (cases de webtoon).
 *
 * Gère :
 * - Le découpage automatique par l'IA d'une scène en panels ordonnés.
 * - Le découpage global de toutes les scènes d'un chapitre.
 * - La manipulation complète des panels (édition, réordonnancement, ajout/suppression manuelle).
 * - Le contrôle visuel de l'ordre de lecture.
 * - La persistance stricte dans Room (brouillon isCanon = false puis validation).
 */
class PanelUseCase(
    private val chapterDao: ChapterDao,
    private val sceneDao: SceneDao,
    private val panelDao: PanelDao,
    private val jobDao: GenerationJobDao,
    private val canonRepository: CanonMemoryRepository,
    private val panelGenerator: GeminiPanelGenerator
) {

    fun getPanelsByScene(sceneId: String): Flow<List<PanelEntity>> {
        return panelDao.getPanelsBySceneId(sceneId)
    }

    fun getPanelsByChapter(chapterId: String): Flow<List<PanelEntity>> {
        return panelDao.getPanelsByChapterId(chapterId)
    }

    fun getScenesByChapter(chapterId: String): Flow<List<SceneEntity>> {
        return sceneDao.getScenesByChapterId(chapterId)
    }

    fun getChapterForProject(projectId: String): Flow<ChapterEntity?> {
        return chapterDao.getDraftChapterByProjectId(projectId)
    }

    /**
     * Génère les panels pour une scène donnée via Gemini.
     */
    suspend fun generatePanelsForScene(
        projectId: String,
        scene: SceneEntity,
        previousSceneSummary: String? = null,
        targetPanelCount: Int = 5
    ): Pair<GenerationJobEntity, Result<ScenePanelsDraft>> = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val initialJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.PANEL,
            status = JobStatus.RUNNING,
            inputPayload = "Découper la scène ${scene.orderIndex} (${scene.title}) en $targetPanelCount panels"
        )
        jobDao.insertJob(initialJob)

        try {
            val canonSnapshot = canonRepository.getCanonSnapshot(projectId)

            val panelsDraft = panelGenerator.generatePanelsForScene(
                scene = scene,
                canonSnapshot = canonSnapshot,
                previousSceneSummary = previousSceneSummary,
                targetPanelCount = targetPanelCount
            )

            // Supprimer les anciens panels de cette scène si régénération
            panelDao.deletePanelsBySceneId(scene.id)

            // Convertir et insérer les nouveaux panels
            val entitiesToInsert = panelsDraft.panels.map { draft ->
                PanelEntity(
                    id = UUID.randomUUID().toString(),
                    sceneId = scene.id,
                    chapterId = scene.chapterId,
                    projectId = projectId,
                    orderIndex = draft.orderIndex,
                    cameraAngle = draft.cameraAngle,
                    visualDescription = draft.actionDescription,
                    charactersPresent = draft.charactersPresent,
                    location = draft.location,
                    actionDescription = draft.actionDescription,
                    dominantEmotion = draft.dominantEmotion,
                    composition = draft.composition,
                    lightingAndAtmosphere = draft.lightingAndAtmosphere,
                    backgroundDetails = draft.backgroundDetails,
                    visualEffects = draft.visualEffects,
                    visualPrompt = draft.visualPrompt,
                    continuityNotes = draft.continuityNotes,
                    soundEffects = draft.soundEffects,
                    isCanon = false
                )
            }

            panelDao.insertPanels(entitiesToInsert)

            // Sauvegarder le résultat dans le job
            val rawResult = serializePanelsDraftToJson(panelsDraft)
            val successJob = initialJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = rawResult,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(successJob)

            Pair(successJob, Result.success(panelsDraft))
        } catch (e: Exception) {
            val failedJob = initialJob.copy(
                status = JobStatus.FAILED,
                errorMessage = e.message ?: "Erreur lors du découpage en panels",
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(failedJob)

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Génère les panels pour l'ensemble des scènes d'un chapitre.
     */
    suspend fun generatePanelsForWholeChapter(
        projectId: String,
        chapterId: String
    ): Pair<GenerationJobEntity, Result<List<ScenePanelsDraft>>> = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val initialJob = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.PANEL,
            status = JobStatus.RUNNING,
            inputPayload = "Découper l'ensemble des scènes du chapitre $chapterId en panels"
        )
        jobDao.insertJob(initialJob)

        try {
            val scenes = sceneDao.getScenesByChapterId(chapterId).firstOrNull()?.sortedBy { it.orderIndex }
                ?: emptyList()

            if (scenes.isEmpty()) {
                throw IllegalStateException("Aucune scène trouvée dans le storyboard du chapitre.")
            }

            val canonSnapshot = canonRepository.getCanonSnapshot(projectId)
            val allResults = mutableListOf<ScenePanelsDraft>()

            // Supprimer tous les panels existants du chapitre
            panelDao.deletePanelsByChapterId(chapterId)

            var previousSummary: String? = null
            for (scene in scenes) {
                val panelsDraft = panelGenerator.generatePanelsForScene(
                    scene = scene,
                    canonSnapshot = canonSnapshot,
                    previousSceneSummary = previousSummary,
                    targetPanelCount = 5
                )
                allResults.add(panelsDraft)
                previousSummary = scene.narrativeSummary

                val entitiesToInsert = panelsDraft.panels.map { draft ->
                    PanelEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = scene.id,
                        chapterId = chapterId,
                        projectId = projectId,
                        orderIndex = draft.orderIndex,
                        cameraAngle = draft.cameraAngle,
                        visualDescription = draft.actionDescription,
                        charactersPresent = draft.charactersPresent,
                        location = draft.location,
                        actionDescription = draft.actionDescription,
                        dominantEmotion = draft.dominantEmotion,
                        composition = draft.composition,
                        lightingAndAtmosphere = draft.lightingAndAtmosphere,
                        backgroundDetails = draft.backgroundDetails,
                        visualEffects = draft.visualEffects,
                        visualPrompt = draft.visualPrompt,
                        continuityNotes = draft.continuityNotes,
                        soundEffects = draft.soundEffects,
                        isCanon = false
                    )
                }
                panelDao.insertPanels(entitiesToInsert)
            }

            val successJob = initialJob.copy(
                status = JobStatus.SUCCESS,
                rawResult = "Génération réussie pour ${scenes.size} scènes (${allResults.sumOf { it.panels.size }} panels au total).",
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(successJob)

            Pair(successJob, Result.success(allResults))
        } catch (e: Exception) {
            val failedJob = initialJob.copy(
                status = JobStatus.FAILED,
                errorMessage = e.message ?: "Erreur lors du découpage global des panels",
                updatedAt = System.currentTimeMillis()
            )
            jobDao.updateJob(failedJob)

            Pair(failedJob, Result.failure(e))
        }
    }

    /**
     * Met à jour n'importe quel champ d'un panel dans Room.
     */
    suspend fun updatePanel(panel: PanelEntity) = withContext(Dispatchers.IO) {
        panelDao.updatePanel(panel.copy(isCanon = false))
    }

    /**
     * Déplace un panel vers le haut ou vers le bas dans sa scène.
     */
    suspend fun movePanel(sceneId: String, panelId: String, directionUp: Boolean) = withContext(Dispatchers.IO) {
        val panels = panelDao.getPanelsBySceneId(sceneId).firstOrNull()?.sortedBy { it.orderIndex } ?: return@withContext
        val currentIndex = panels.indexOfFirst { it.id == panelId }
        if (currentIndex == -1) return@withContext

        val targetIndex = if (directionUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex !in panels.indices) return@withContext

        val currentPanel = panels[currentIndex]
        val targetPanel = panels[targetIndex]

        val updatedCurrent = currentPanel.copy(orderIndex = targetPanel.orderIndex)
        val updatedTarget = targetPanel.copy(orderIndex = currentPanel.orderIndex)

        panelDao.updatePanel(updatedCurrent)
        panelDao.updatePanel(updatedTarget)
    }

    /**
     * Ajoute manuellement un panel dans une scène.
     */
    suspend fun addManualPanel(
        sceneId: String,
        chapterId: String,
        projectId: String,
        afterOrderIndex: Int
    ) = withContext(Dispatchers.IO) {
        val existingPanels = panelDao.getPanelsBySceneId(sceneId).firstOrNull() ?: emptyList()
        existingPanels.filter { it.orderIndex > afterOrderIndex }.forEach { p ->
            panelDao.updatePanel(p.copy(orderIndex = p.orderIndex + 1))
        }

        val newPanel = PanelEntity(
            id = UUID.randomUUID().toString(),
            sceneId = sceneId,
            chapterId = chapterId,
            projectId = projectId,
            orderIndex = afterOrderIndex + 1,
            cameraAngle = "Plan moyen",
            visualDescription = "Nouveau panel",
            actionDescription = "Action à définir",
            dominantEmotion = "Neutre",
            composition = "Cadrage standard vertical",
            lightingAndAtmosphere = "Lumière du jour naturelle",
            backgroundDetails = "Arrière-plan de la scène",
            visualPrompt = "Webtoon style panel, characters in dynamic posture, detailed background, clean linework",
            continuityNotes = "Continuité avec le panel précédent",
            isCanon = false
        )

        panelDao.insertPanel(newPanel)
        reindexPanelsForScene(sceneId)
    }

    /**
     * Supprime un panel et réindexe la scène.
     */
    suspend fun deletePanel(panel: PanelEntity) = withContext(Dispatchers.IO) {
        panelDao.deletePanel(panel)
        reindexPanelsForScene(panel.sceneId)
    }

    private suspend fun reindexPanelsForScene(sceneId: String) {
        val panels = panelDao.getPanelsBySceneId(sceneId).firstOrNull()
            ?.sortedWith(compareBy({ it.orderIndex }, { it.createdAt }))
            ?: return

        panels.forEachIndexed { index, p ->
            val correctOrder = index + 1
            if (p.orderIndex != correctOrder) {
                panelDao.updatePanel(p.copy(orderIndex = correctOrder))
            }
        }
    }

    /**
     * Valide l'ensemble des panels du chapitre dans le Canon.
     */
    suspend fun validatePanelsToCanon(chapterId: String) = withContext(Dispatchers.IO) {
        val panels = panelDao.getPanelsByChapterId(chapterId).firstOrNull() ?: return@withContext
        panels.forEach { panel ->
            panelDao.updatePanel(panel.copy(isCanon = true))
        }
    }

    private fun serializePanelsDraftToJson(draft: ScenePanelsDraft): String {
        return JSONObject().apply {
            put("sceneId", draft.sceneId)
            put("sceneTitle", draft.sceneTitle)
            val arr = JSONArray()
            draft.panels.forEach { p ->
                arr.put(JSONObject().apply {
                    put("orderIndex", p.orderIndex)
                    put("charactersPresent", p.charactersPresent)
                    put("location", p.location)
                    put("actionDescription", p.actionDescription)
                    put("dominantEmotion", p.dominantEmotion)
                    put("cameraAngle", p.cameraAngle)
                    put("composition", p.composition)
                    put("lightingAndAtmosphere", p.lightingAndAtmosphere)
                    put("backgroundDetails", p.backgroundDetails)
                    put("visualEffects", p.visualEffects)
                    put("visualPrompt", p.visualPrompt)
                    put("continuityNotes", p.continuityNotes)
                    p.soundEffects?.let { put("soundEffects", it) }
                })
            }
            put("panels", arr)
        }.toString()
    }
}

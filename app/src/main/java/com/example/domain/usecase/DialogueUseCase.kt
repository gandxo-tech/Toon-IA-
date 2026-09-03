package com.example.domain.usecase

import com.example.ai.GeminiDialogueGenerator
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.DialogueDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.PanelDao
import com.example.data.local.dao.SceneDao
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Use Case pour la gestion et la génération des Dialogues (Phase 13).
 *
 * RÈGLES MAÎTRES :
 * 1. Dialogues structurés (speaker, text, type, emotion, orderIndex, placement) liés aux panels.
 * 2. Totalement indépendants du rendu visuel de l'image (éditables avant et après génération visuelle).
 * 3. Générations IA traçables via GenerationJobEntity avec gestion des statuts (QUEUED, RUNNING, SUCCESS, FAILED).
 * 4. Validation explicite de l'utilisateur requise pour intégration canonique.
 */
class DialogueUseCase(
    private val dialogueDao: DialogueDao,
    private val panelDao: PanelDao,
    private val sceneDao: SceneDao,
    private val chapterDao: ChapterDao,
    private val jobDao: GenerationJobDao,
    private val canonMemoryRepository: CanonMemoryRepository,
    private val dialogueGenerator: GeminiDialogueGenerator = GeminiDialogueGenerator()
) {

    fun getDialoguesByPanelId(panelId: String): Flow<List<DialogueEntity>> {
        return dialogueDao.getDialoguesByPanelId(panelId)
    }

    fun getDialoguesByProjectId(projectId: String): Flow<List<DialogueEntity>> {
        return dialogueDao.getDialoguesByProjectId(projectId)
    }

    suspend fun getDialogueById(dialogueId: String): DialogueEntity? = withContext(Dispatchers.IO) {
        dialogueDao.getDialogueById(dialogueId).firstOrNull()
    }

    /**
     * Génère les répliques / bulles pour un panel spécifique via Gemini.
     */
    suspend fun generateDialoguesForPanel(
        projectId: String,
        panelId: String
    ): Result<List<DialogueEntity>> = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val job = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.DIALOGUE,
            status = JobStatus.RUNNING,
            inputPayload = "Panel ID: $panelId"
        )
        jobDao.insertJob(job)

        try {
            val panel = panelDao.getPanelById(panelId).firstOrNull()
                ?: throw IllegalArgumentException("Panel introuvable : $panelId")

            val scene = sceneDao.getSceneById(panel.sceneId).firstOrNull()
            val chapter = scene?.let { chapterDao.getChapterById(it.chapterId).firstOrNull() }
            val canonSnapshot = canonMemoryRepository.getCanonSnapshot(projectId)

            jobDao.updateJob(
                job.copy(
                    updatedAt = System.currentTimeMillis()
                )
            )

            val generated = dialogueGenerator.generateDialoguesForPanel(
                projectId = projectId,
                panel = panel,
                scene = scene,
                chapter = chapter,
                canonSnapshot = canonSnapshot
            )

            // Supprimer les brouillons non validés précédents de ce panel pour éviter les doublons accidentels
            val existing = dialogueDao.getDialoguesByPanelId(panelId).firstOrNull() ?: emptyList()
            existing.filter { !it.isCanon }.forEach { dialogueDao.deleteDialogue(it) }

            // Insérer les nouveaux dialogues générés (isCanon = false par défaut)
            dialogueDao.insertDialogues(generated)

            jobDao.updateJob(
                job.copy(
                    status = JobStatus.SUCCESS,
                    rawResult = "${generated.size} dialogues générés",
                    updatedAt = System.currentTimeMillis()
                )
            )

            Result.success(generated)
        } catch (e: Exception) {
            jobDao.updateJob(
                job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = e.message ?: "Échec de génération des dialogues.",
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Génère les dialogues pour l'ensemble des panels d'une scène.
     */
    suspend fun generateDialoguesForScene(
        projectId: String,
        sceneId: String
    ): Result<List<DialogueEntity>> = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val job = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.DIALOGUE,
            status = JobStatus.RUNNING,
            inputPayload = "Scene ID: $sceneId"
        )
        jobDao.insertJob(job)

        try {
            val scene = sceneDao.getSceneById(sceneId).firstOrNull()
                ?: throw IllegalArgumentException("Scène introuvable : $sceneId")

            val panels = panelDao.getPanelsBySceneId(sceneId).firstOrNull() ?: emptyList()
            if (panels.isEmpty()) {
                throw IllegalStateException("Aucun panel trouvé dans cette scène. Créez d'abord des panels.")
            }

            val chapter = chapterDao.getChapterById(scene.chapterId).firstOrNull()
            val canonSnapshot = canonMemoryRepository.getCanonSnapshot(projectId)

            jobDao.updateJob(
                job.copy(
                    updatedAt = System.currentTimeMillis()
                )
            )

            val generated = dialogueGenerator.generateDialoguesForScene(
                projectId = projectId,
                scene = scene,
                panels = panels,
                chapter = chapter,
                canonSnapshot = canonSnapshot
            )

            // Nettoyage des brouillons non validés
            panels.forEach { p ->
                val existing = dialogueDao.getDialoguesByPanelId(p.id).firstOrNull() ?: emptyList()
                existing.filter { !it.isCanon }.forEach { dialogueDao.deleteDialogue(it) }
            }

            dialogueDao.insertDialogues(generated)

            jobDao.updateJob(
                job.copy(
                    status = JobStatus.SUCCESS,
                    rawResult = "${generated.size} dialogues générés",
                    updatedAt = System.currentTimeMillis()
                )
            )

            Result.success(generated)
        } catch (e: Exception) {
            jobDao.updateJob(
                job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = e.message ?: "Échec de génération des dialogues de la scène.",
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Ajoute une nouvelle réplique personnalisée manuelle.
     */
    suspend fun addDialogue(
        panelId: String,
        projectId: String,
        speakerName: String,
        characterId: String?,
        text: String,
        type: String = "SPEECH",
        emotion: String = "Neutre",
        placement: String = "TOP_LEFT"
    ): DialogueEntity = withContext(Dispatchers.IO) {
        val existing = dialogueDao.getDialoguesByPanelId(panelId).firstOrNull() ?: emptyList()
        val nextIndex = if (existing.isEmpty()) 1 else existing.maxOf { it.orderIndex } + 1

        val newDialogue = DialogueEntity(
            id = UUID.randomUUID().toString(),
            panelId = panelId,
            projectId = projectId,
            characterId = characterId,
            speakerName = speakerName.trim(),
            orderIndex = nextIndex,
            type = type,
            emotion = emotion.trim(),
            placement = placement,
            text = text.trim(),
            isCanon = false,
            createdAt = System.currentTimeMillis()
        )
        dialogueDao.insertDialogue(newDialogue)
        newDialogue
    }

    /**
     * Met à jour une réplique existante.
     */
    suspend fun updateDialogue(dialogue: DialogueEntity) = withContext(Dispatchers.IO) {
        dialogueDao.updateDialogue(dialogue)
    }

    /**
     * Supprime une réplique et réordonne la suite.
     */
    suspend fun deleteDialogue(dialogueId: String) = withContext(Dispatchers.IO) {
        val dialogue = dialogueDao.getDialogueById(dialogueId).firstOrNull() ?: return@withContext
        dialogueDao.deleteDialogue(dialogue)

        // Réindexer
        val remaining = dialogueDao.getDialoguesByPanelId(dialogue.panelId).firstOrNull()
            ?.sortedBy { it.orderIndex } ?: return@withContext

        remaining.forEachIndexed { idx, item ->
            if (item.orderIndex != idx + 1) {
                dialogueDao.updateDialogue(item.copy(orderIndex = idx + 1))
            }
        }
    }

    /**
     * Déplace une réplique vers le haut ou le bas dans l'ordre de lecture du panel.
     */
    suspend fun moveDialogue(
        panelId: String,
        dialogueId: String,
        directionUp: Boolean
    ) = withContext(Dispatchers.IO) {
        val dialogues = dialogueDao.getDialoguesByPanelId(panelId).firstOrNull()
            ?.sortedBy { it.orderIndex } ?: return@withContext

        val currentIndex = dialogues.indexOfFirst { it.id == dialogueId }
        if (currentIndex == -1) return@withContext

        val targetIndex = if (directionUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex < 0 || targetIndex >= dialogues.size) return@withContext

        val currentItem = dialogues[currentIndex]
        val targetItem = dialogues[targetIndex]

        val updatedCurrent = currentItem.copy(orderIndex = targetItem.orderIndex)
        val updatedTarget = targetItem.copy(orderIndex = currentItem.orderIndex)

        dialogueDao.updateDialogue(updatedCurrent)
        dialogueDao.updateDialogue(updatedTarget)
    }

    /**
     * Valide explicitement un dialogue dans le CanonMemory.
     */
    suspend fun validateDialogueAsCanon(
        projectId: String,
        dialogue: DialogueEntity
    ): List<Contradiction> = withContext(Dispatchers.IO) {
        val updated = dialogue.copy(isCanon = true)
        dialogueDao.updateDialogue(updated)
        canonMemoryRepository.updateCanon(projectId, CanonElement.Dialogue(updated, isUserValidated = true))
    }

    /**
     * Valide tous les dialogues d'un panel dans le canon.
     */
    suspend fun validateAllDialoguesForPanel(
        projectId: String,
        panelId: String
    ): List<Contradiction> = withContext(Dispatchers.IO) {
        val dialogues = dialogueDao.getDialoguesByPanelId(panelId).firstOrNull() ?: emptyList()
        val allContradictions = mutableListOf<Contradiction>()

        dialogues.forEach { d ->
            val updated = d.copy(isCanon = true)
            dialogueDao.updateDialogue(updated)
            val res = canonMemoryRepository.updateCanon(projectId, CanonElement.Dialogue(updated, isUserValidated = true))
            allContradictions.addAll(res)
        }
        allContradictions
    }
}

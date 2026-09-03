package com.example.domain.usecase

import android.content.Context
import com.example.ai.GeminiVisualPanelGenerator
import com.example.ai.PanelImageGenerationResult
import com.example.data.local.dao.CharacterDao
import com.example.data.local.dao.GenerationJobDao
import com.example.data.local.dao.PanelDao
import com.example.data.local.dao.VisualBibleDao
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.PanelEntity
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Cas d'utilisation pour la génération visuelle des Panels et la cohérence de personnage (Phase 14).
 *
 * RÈGLES MAÎTRES :
 * 1. Génère d'abord l'image de référence canonique d'un personnage si elle n'existe pas encore,
 *    et l'enregistre de façon définitive dans le profil du personnage.
 * 2. Génération de chaque panel avec prompt visuel + images de référence des personnages présents
 *    transmises au modèle Gemini flash-image.
 * 3. Chaque génération passe par un GenerationJobEntity avec statut (QUEUED/RUNNING/SUCCESS/FAILED)
 *    et support de retry.
 * 4. Bouton "Régénérer ce panel" indépendant : ne touche ni aux autres panels ni aux dialogues existants.
 * 5. Aucune promesse de cohérence parfaite dans l'UI : simple mention "Généré à partir de la référence du personnage".
 */
class PanelVisualUseCase(
    private val panelDao: PanelDao,
    private val characterDao: CharacterDao,
    private val visualBibleDao: VisualBibleDao,
    private val jobDao: GenerationJobDao,
    private val canonMemoryRepository: CanonMemoryRepository,
    private val visualPanelGenerator: GeminiVisualPanelGenerator = GeminiVisualPanelGenerator()
) {

    /**
     * Génère l'image de référence canonique d'un personnage seul.
     */
    suspend fun generateCharacterReferenceImage(
        context: Context,
        projectId: String,
        characterId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val character = characterDao.getCharacterById(characterId).firstOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Personnage introuvable : $characterId"))

        val jobId = UUID.randomUUID().toString()
        val job = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.PERSONNAGES,
            status = JobStatus.RUNNING,
            inputPayload = "Character: ${character.name} ($characterId)"
        )
        jobDao.insertJob(job)

        try {
            val visualBible = visualBibleDao.getVisualBibleByProjectId(projectId).firstOrNull()
            val localPath = visualPanelGenerator.generateCharacterReferenceImage(
                context = context,
                projectId = projectId,
                character = character,
                visualBible = visualBible
            )

            // Mettre à jour le personnage en base et dans le canon
            val updatedChar = character.copy(canonicalImagePath = localPath)
            characterDao.updateCharacter(updatedChar)
            canonMemoryRepository.updateCanon(projectId, CanonElement.Character(updatedChar, isUserValidated = true))

            jobDao.updateJob(
                job.copy(
                    status = JobStatus.SUCCESS,
                    rawResult = localPath,
                    updatedAt = System.currentTimeMillis()
                )
            )

            Result.success(localPath)
        } catch (e: Exception) {
            jobDao.updateJob(
                job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = e.message ?: "Échec de génération de la référence du personnage.",
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Génère (ou régénère) l'illustration d'un panel unique.
     * Si un personnage présent n'a pas encore d'image de référence, elle est générée au préalable.
     */
    suspend fun generatePanelImage(
        context: Context,
        projectId: String,
        panelId: String
    ): Result<PanelImageGenerationResult> = withContext(Dispatchers.IO) {
        val panel = panelDao.getPanelById(panelId).firstOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Panel introuvable : $panelId"))

        val jobId = UUID.randomUUID().toString()
        val job = GenerationJobEntity(
            id = jobId,
            projectId = projectId,
            type = JobType.PANEL,
            status = JobStatus.RUNNING,
            inputPayload = "Panel #${panel.orderIndex} ($panelId)"
        )
        jobDao.insertJob(job)

        try {
            val canonSnapshot = canonMemoryRepository.getCanonSnapshot(projectId)
            val visualBible = visualBibleDao.getVisualBibleByProjectId(projectId).firstOrNull()
            val allCharacters = characterDao.getCharactersByProjectId(projectId).firstOrNull() ?: emptyList()

            // Identifier les personnages présents dans la case
            val charactersInPanel = identifyPresentCharacters(panel, allCharacters)

            // Étape 1 : Vérifier et générer les images de référence manquantes des personnages présents
            val readyCharacters = mutableListOf<CharacterEntity>()
            for (char in charactersInPanel) {
                var currentChar = char
                val existingRef = currentChar.canonicalImagePath
                val refFileExists = existingRef?.let { File(it).exists() } == true

                if (!refFileExists) {
                    jobDao.updateJob(
                        job.copy(
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    try {
                        val newRefPath = visualPanelGenerator.generateCharacterReferenceImage(
                            context = context,
                            projectId = projectId,
                            character = currentChar,
                            visualBible = visualBible
                        )
                        currentChar = currentChar.copy(canonicalImagePath = newRefPath)
                        characterDao.updateCharacter(currentChar)
                        canonMemoryRepository.updateCanon(projectId, CanonElement.Character(currentChar, isUserValidated = true))
                    } catch (refError: Exception) {
                        // Si la référence échoue, on continue la génération du panel sans bloquer tout le processus
                    }
                }
                readyCharacters.add(currentChar)
            }

            // Étape 2 : Générer l'image du panel avec injection multimodale
            jobDao.updateJob(
                job.copy(
                    updatedAt = System.currentTimeMillis()
                )
            )

            val result = visualPanelGenerator.generatePanelImage(
                context = context,
                projectId = projectId,
                panel = panel,
                visualBible = visualBible,
                canonSnapshot = canonSnapshot,
                presentCharacters = readyCharacters
            )

            // Mettre à jour le chemin local du panel en base
            val updatedPanel = panel.copy(localImagePath = result.localImagePath)
            panelDao.updatePanel(updatedPanel)

            jobDao.updateJob(
                job.copy(
                    status = JobStatus.SUCCESS,
                    rawResult = result.localImagePath,
                    updatedAt = System.currentTimeMillis()
                )
            )

            Result.success(result)
        } catch (e: Exception) {
            jobDao.updateJob(
                job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = e.message ?: "Échec de génération de l'illustration du panel.",
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Génère les illustrations de tous les panels d'une scène séquentiellement.
     */
    suspend fun generateAllPanelsForScene(
        context: Context,
        projectId: String,
        sceneId: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        val panels = panelDao.getPanelsBySceneId(sceneId).firstOrNull()?.sortedBy { it.orderIndex }
            ?: return@withContext Result.failure(IllegalArgumentException("Aucun panel trouvé dans la scène"))

        var successCount = 0
        panels.forEachIndexed { index, p ->
            onProgress(index + 1, panels.size)
            val res = generatePanelImage(context, projectId, p.id)
            if (res.isSuccess) {
                successCount++
            }
        }
        Result.success(successCount)
    }

    /**
     * Identifie les entités personnages mentionnées dans le panel.
     */
    private fun identifyPresentCharacters(
        panel: PanelEntity,
        allCharacters: List<CharacterEntity>
    ): List<CharacterEntity> {
        if (allCharacters.isEmpty()) return emptyList()

        val textToSearch = "${panel.charactersPresent} ${panel.actionDescription} ${panel.visualDescription}".lowercase()

        return allCharacters.filter { char ->
            val charName = char.name.trim().lowercase()
            charName.isNotBlank() && textToSearch.contains(charName)
        }
    }
}

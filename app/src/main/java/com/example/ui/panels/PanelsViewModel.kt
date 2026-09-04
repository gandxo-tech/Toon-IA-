package com.example.ui.panels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiPanelGenerator
import com.example.ai.GeminiVisualPanelGenerator
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.usecase.PanelUseCase
import com.example.domain.usecase.PanelVisualUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class PanelsUiState(
    val isLoading: Boolean = false,
    val isGeneratingAi: Boolean = false,
    val generatingSceneId: String? = null,
    val isGeneratingImage: Boolean = false,
    val generatingImagePanelId: String? = null,
    val chapter: ChapterEntity? = null,
    val scenes: List<SceneEntity> = emptyList(),
    val selectedSceneId: String? = null, // null = toutes les scènes
    val panels: List<PanelEntity> = emptyList(),
    val latestJob: GenerationJobEntity? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isValidatedInCanon: Boolean = false
)

class PanelsViewModel(
    private val projectId: String,
    private val panelUseCase: PanelUseCase,
    private val panelVisualUseCase: PanelVisualUseCase,
    private val canonRepository: CanonMemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PanelsUiState(isLoading = true))
    val uiState: StateFlow<PanelsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            panelUseCase.getChapterForProject(projectId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Erreur de chargement du chapitre : ${e.message}"
                    )
                }
                .collect { chapter ->
                    if (chapter == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            chapter = null,
                            scenes = emptyList(),
                            panels = emptyList()
                        )
                    } else {
                        // Charger les scènes et les panels
                        panelUseCase.getScenesByChapter(chapter.id).collect { sceneList ->
                            val sortedScenes = sceneList.sortedBy { it.orderIndex }
                            _uiState.value = _uiState.value.copy(
                                chapter = chapter,
                                scenes = sortedScenes
                            )

                            // Charger les panels du chapitre
                            panelUseCase.getPanelsByChapter(chapter.id).collect { panelList ->
                                val sortedPanels = panelList.sortedWith(compareBy({ it.sceneId }, { it.orderIndex }))
                                val allCanon = sortedPanels.isNotEmpty() && sortedPanels.all { it.isCanon }
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    panels = sortedPanels,
                                    isValidatedInCanon = allCanon
                                )
                            }
                        }
                    }
                }
        }
    }

    fun selectSceneFilter(sceneId: String?) {
        _uiState.value = _uiState.value.copy(selectedSceneId = sceneId)
    }

    fun generatePanelsForScene(scene: SceneEntity, targetCount: Int = 5) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingAi = true,
                generatingSceneId = scene.id,
                errorMessage = null,
                successMessage = null
            )

            val (job, result) = panelUseCase.generatePanelsForScene(
                projectId = projectId,
                scene = scene,
                targetPanelCount = targetCount
            )

            result.fold(
                onSuccess = { draft ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAi = false,
                        generatingSceneId = null,
                        latestJob = job,
                        successMessage = "${draft.panels.size} panels générés pour la Scène ${scene.orderIndex} !"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAi = false,
                        generatingSceneId = null,
                        latestJob = job,
                        errorMessage = e.message ?: "Échec du découpage en panels."
                    )
                }
            )
        }
    }

    fun generatePanelsForWholeChapter() {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingAi = true,
                generatingSceneId = null,
                errorMessage = null,
                successMessage = null
            )

            val (job, result) = panelUseCase.generatePanelsForWholeChapter(projectId, chapterId)

            result.fold(
                onSuccess = { drafts ->
                    val total = drafts.sumOf { it.panels.size }
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAi = false,
                        latestJob = job,
                        successMessage = "Découpage réussi : $total panels générés pour l'ensemble du chapitre !"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAi = false,
                        latestJob = job,
                        errorMessage = e.message ?: "Échec du découpage global des panels."
                    )
                }
            )
        }
    }

    /**
     * Génère l'illustration visuelle d'un panel donné (Phase 14).
     */
    fun generatePanelImage(context: Context, panelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingImage = true,
                generatingImagePanelId = panelId,
                errorMessage = null,
                successMessage = null
            )

            val result = panelVisualUseCase.generatePanelImage(context, projectId, panelId)
            result.fold(
                onSuccess = { genResult ->
                    val refMsg = if (genResult.referencedCharacters.isNotEmpty()) {
                        " (Réf. : ${genResult.referencedCharacters.joinToString(", ")})"
                    } else ""
                    _uiState.value = _uiState.value.copy(
                        isGeneratingImage = false,
                        generatingImagePanelId = null,
                        successMessage = "Illustration du panel générée avec succès$refMsg !"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingImage = false,
                        generatingImagePanelId = null,
                        errorMessage = "Échec de génération d'image : ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * Génère les illustrations de tous les panels de la scène active (Phase 14).
     */
    fun generateAllPanelImagesForScene(context: Context, sceneId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingImage = true,
                generatingImagePanelId = null,
                errorMessage = null,
                successMessage = "Génération des illustrations de la scène en cours..."
            )

            val result = panelVisualUseCase.generateAllPanelsForScene(context, projectId, sceneId)
            result.fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingImage = false,
                        successMessage = "$count illustration(s) générée(s) pour la scène !"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingImage = false,
                        errorMessage = "Erreur de génération des illustrations : ${e.message}"
                    )
                }
            )
        }
    }

    fun updatePanel(panel: PanelEntity) {
        viewModelScope.launch {
            try {
                panelUseCase.updatePanel(panel)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Panel ${panel.orderIndex} mis à jour",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de mise à jour : ${e.message}")
            }
        }
    }

    fun movePanel(panel: PanelEntity, directionUp: Boolean) {
        viewModelScope.launch {
            try {
                panelUseCase.movePanel(panel.sceneId, panel.id, directionUp)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de déplacement : ${e.message}")
            }
        }
    }

    fun addManualPanel(sceneId: String, afterOrderIndex: Int) {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            try {
                panelUseCase.addManualPanel(sceneId, chapterId, projectId, afterOrderIndex)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Nouveau panel ajouté !",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur d'ajout de panel : ${e.message}")
            }
        }
    }

    fun deletePanel(panel: PanelEntity) {
        viewModelScope.launch {
            try {
                panelUseCase.deletePanel(panel)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Panel supprimé",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de suppression : ${e.message}")
            }
        }
    }

    fun validatePanelsToCanon() {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            try {
                panelUseCase.validatePanelsToCanon(chapterId)
                _uiState.value = _uiState.value.copy(
                    isValidatedInCanon = true,
                    successMessage = "Panels validés dans le Canon !",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de validation canon : ${e.message}")
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    companion object {
        fun provideFactory(
            projectId: String,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = ToonDatabase.getInstance(context)
                val canonRepo = CanonMemoryRepositoryImpl(db)
                val panelGenerator = GeminiPanelGenerator()
                val visualPanelGenerator = GeminiVisualPanelGenerator()
                val useCase = PanelUseCase(
                    chapterDao = db.chapterDao(),
                    sceneDao = db.sceneDao(),
                    panelDao = db.panelDao(),
                    jobDao = db.generationJobDao(),
                    canonRepository = canonRepo,
                    panelGenerator = panelGenerator
                )
                val visualUseCase = PanelVisualUseCase(
                    panelDao = db.panelDao(),
                    characterDao = db.characterDao(),
                    visualBibleDao = db.visualBibleDao(),
                    jobDao = db.generationJobDao(),
                    canonMemoryRepository = canonRepo,
                    visualPanelGenerator = visualPanelGenerator
                )
                return PanelsViewModel(projectId, useCase, visualUseCase, canonRepo) as T
            }
        }
    }
}


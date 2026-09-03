package com.example.ui.storyboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiSceneDetailGenerator
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.SceneEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.usecase.StoryboardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StoryboardUiState(
    val isLoading: Boolean = false,
    val isGeneratingAi: Boolean = false,
    val activeSceneAiId: String? = null,
    val chapter: ChapterEntity? = null,
    val scenes: List<SceneEntity> = emptyList(),
    val latestJob: GenerationJobEntity? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isValidatedInCanon: Boolean = false
)

class StoryboardViewModel(
    private val projectId: String,
    private val storyboardUseCase: StoryboardUseCase,
    private val canonRepository: CanonMemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryboardUiState(isLoading = true))
    val uiState: StateFlow<StoryboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            storyboardUseCase.getChapterForProject(projectId)
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
                            scenes = emptyList()
                        )
                    } else {
                        // Charger les scènes associées
                        storyboardUseCase.getScenesByChapter(chapter.id)
                            .collect { sceneList ->
                                val sortedScenes = sceneList.sortedBy { it.orderIndex }
                                val allCanon = sortedScenes.isNotEmpty() && sortedScenes.all { it.isCanon }
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    chapter = chapter,
                                    scenes = sortedScenes,
                                    isValidatedInCanon = allCanon
                                )
                            }
                    }
                }
        }
    }

    fun moveScene(sceneId: String, directionUp: Boolean) {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            try {
                storyboardUseCase.moveScene(chapterId, sceneId, directionUp)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de déplacement : ${e.message}")
            }
        }
    }

    fun mergeScenes(firstSceneId: String, secondSceneId: String) {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            try {
                storyboardUseCase.mergeScenes(chapterId, firstSceneId, secondSceneId)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Scènes fusionnées avec succès !",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur lors de la fusion : ${e.message}")
            }
        }
    }

    fun splitScene(
        originalSceneId: String,
        splitTitle: String,
        splitSummary: String,
        splitBeats: String,
        splitCharacters: String,
        splitAtmosphere: String
    ) {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            try {
                storyboardUseCase.splitScene(
                    chapterId = chapterId,
                    projectId = projectId,
                    originalSceneId = originalSceneId,
                    splitTitle = splitTitle,
                    splitSummary = splitSummary,
                    splitBeats = splitBeats,
                    splitCharacters = splitCharacters,
                    splitAtmosphere = splitAtmosphere
                )
                _uiState.value = _uiState.value.copy(
                    successMessage = "Scène scindée en deux avec succès !",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur lors de la scission : ${e.message}")
            }
        }
    }

    fun updateScene(scene: SceneEntity) {
        viewModelScope.launch {
            try {
                storyboardUseCase.updateSceneDraft(scene)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Scène mise à jour",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de mise à jour : ${e.message}")
            }
        }
    }

    fun deleteScene(scene: SceneEntity) {
        viewModelScope.launch {
            try {
                storyboardUseCase.deleteScene(scene)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Scène supprimée",
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Erreur de suppression : ${e.message}")
            }
        }
    }

    fun detailSceneWithAi(scene: SceneEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingAi = true,
                activeSceneAiId = scene.id,
                errorMessage = null,
                successMessage = null
            )

            val (job, result) = storyboardUseCase.detailSceneWithAi(projectId, scene)

            result.fold(
                onSuccess = { detail ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAi = false,
                        activeSceneAiId = null,
                        latestJob = job,
                        successMessage = "Scène \"${scene.title}\" enrichie avec succès par l'IA !"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAi = false,
                        activeSceneAiId = null,
                        latestJob = job,
                        errorMessage = e.message ?: "Échec de l'enrichissement par l'IA."
                    )
                }
            )
        }
    }

    fun validateStoryboardToCanon() {
        val chapterId = _uiState.value.chapter?.id ?: return
        viewModelScope.launch {
            try {
                storyboardUseCase.validateStoryboardToCanon(chapterId, projectId)
                _uiState.value = _uiState.value.copy(
                    isValidatedInCanon = true,
                    successMessage = "Storyboard validé et intégré au Canon du projet !",
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
                val detailGenerator = GeminiSceneDetailGenerator(apiKeyOverride = ApiKeyManager.getApiKey(context))
                val useCase = StoryboardUseCase(
                    chapterDao = db.chapterDao(),
                    sceneDao = db.sceneDao(),
                    jobDao = db.generationJobDao(),
                    canonRepository = canonRepo,
                    detailGenerator = detailGenerator
                )
                return StoryboardViewModel(projectId, useCase, canonRepo) as T
            }
        }
    }
}

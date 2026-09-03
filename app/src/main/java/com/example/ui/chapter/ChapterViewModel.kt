package com.example.ui.chapter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.Contradiction
import com.example.domain.usecase.ChapterScriptUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'UI pour la Phase 10 : Script du Chapitre 1.
 */
data class ChapterUiState(
    val chapter: ChapterEntity? = null,
    val scenes: List<SceneEntity> = emptyList(),
    val userOrientation: String = "",
    val isGenerating: Boolean = false,
    val currentJob: GenerationJobEntity? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val activeContradictions: List<Contradiction> = emptyList(),
    val showContradictionDialog: Boolean = false,
    val editingScene: SceneEntity? = null
)

/**
 * ViewModel pour la Phase 10 : Script du Chapitre 1 (Brouillon modifiable).
 */
class ChapterViewModel(
    private val useCase: ChapterScriptUseCase,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChapterUiState())
    val uiState: StateFlow<ChapterUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val (chapter, scenes) = useCase.loadChapter1Draft(projectId)
            _uiState.update { current ->
                current.copy(
                    chapter = chapter,
                    scenes = scenes,
                    userOrientation = chapter?.draftNotes ?: current.userOrientation
                )
            }
        }
    }

    fun updateOrientation(text: String) {
        _uiState.update { it.copy(userOrientation = text) }
    }

    fun generateChapterScript() {
        val orientation = _uiState.value.userOrientation
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    successMessage = null
                )
            }

            val (job, result) = useCase.executeGenerateChapter1Script(
                projectId = projectId,
                userOrientation = orientation.ifBlank { null }
            )

            result.fold(
                onSuccess = { scriptResult ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentJob = job,
                            chapter = scriptResult.chapter,
                            scenes = scriptResult.scenes,
                            activeContradictions = scriptResult.contradictions,
                            showContradictionDialog = scriptResult.contradictions.isNotEmpty(),
                            successMessage = "Script du Chapitre 1 généré avec succès ! (Brouillon modifiable)"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentJob = job,
                            errorMessage = error.localizedMessage ?: error.message ?: "Échec de la génération du script."
                        )
                    }
                }
            )
        }
    }

    fun updateChapterTitle(newTitle: String) {
        val currentChapter = _uiState.value.chapter ?: return
        val updated = currentChapter.copy(title = newTitle)
        _uiState.update { it.copy(chapter = updated) }
        viewModelScope.launch {
            useCase.updateChapterDraft(updated)
        }
    }

    fun updateChapterSynopsis(newSynopsis: String) {
        val currentChapter = _uiState.value.chapter ?: return
        val updated = currentChapter.copy(synopsis = newSynopsis)
        _uiState.update { it.copy(chapter = updated) }
        viewModelScope.launch {
            useCase.updateChapterDraft(updated)
        }
    }

    fun startEditingScene(scene: SceneEntity) {
        _uiState.update { it.copy(editingScene = scene) }
    }

    fun cancelEditingScene() {
        _uiState.update { it.copy(editingScene = null) }
    }

    fun saveSceneEdit(updatedScene: SceneEntity) {
        val updatedScenes = _uiState.value.scenes.map {
            if (it.id == updatedScene.id) updatedScene else it
        }
        _uiState.update { it.copy(scenes = updatedScenes, editingScene = null) }
        viewModelScope.launch {
            useCase.updateSceneDraft(updatedScene)
        }
    }

    fun dismissContradictionDialog() {
        _uiState.update { it.copy(showContradictionDialog = false) }
    }

    fun validateChapter() {
        val chapter = _uiState.value.chapter ?: return
        viewModelScope.launch {
            val updatedChapter = chapter.copy(isCanon = true, updatedAt = System.currentTimeMillis())
            useCase.updateChapterDraft(updatedChapter)
            _uiState.update {
                it.copy(
                    chapter = updatedChapter,
                    successMessage = "Chapitre 1 validé et versé dans le Canon ! Chapitre 2 débloqué."
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    class Factory(
        private val useCase: ChapterScriptUseCase,
        private val projectId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChapterViewModel(useCase, projectId) as T
        }
    }
}

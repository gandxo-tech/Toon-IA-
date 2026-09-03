package com.example.ui.dialogues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.usecase.DialogueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DialoguesUiState(
    val projectId: String = "",
    val chapters: List<ChapterEntity> = emptyList(),
    val selectedChapter: ChapterEntity? = null,
    val scenes: List<SceneEntity> = emptyList(),
    val selectedScene: SceneEntity? = null,
    val panels: List<PanelEntity> = emptyList(),
    val dialoguesByPanelId: Map<String, List<DialogueEntity>> = emptyMap(),
    val characters: List<CharacterEntity> = emptyList(),
    val activeJob: GenerationJobEntity? = null,
    val isGenerating: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null,
    val dialogueBeingEdited: DialogueEntity? = null,
    val isEditingNewDialogue: Boolean = false,
    val targetPanelForNewDialogue: String? = null
)

class DialoguesViewModel(
    private val projectId: String,
    private val dialogueUseCase: DialogueUseCase,
    private val database: ToonDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DialoguesUiState(projectId = projectId))
    val uiState: StateFlow<DialoguesUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Observer les chapitres
            database.chapterDao().getChaptersByProjectId(projectId).collect { chaps ->
                _uiState.update { state ->
                    val selected = state.selectedChapter?.let { current -> chaps.find { it.id == current.id } }
                        ?: chaps.firstOrNull()
                    state.copy(chapters = chaps, selectedChapter = selected)
                }
            }
        }

        viewModelScope.launch {
            // Observer les personnages pour les suggestions de locuteur
            database.characterDao().getCharactersByProjectId(projectId).collect { chars ->
                _uiState.update { it.copy(characters = chars) }
            }
        }

        viewModelScope.launch {
            // Observer les scènes du projet
            database.sceneDao().getScenesByProjectId(projectId).collect { scs ->
                _uiState.update { state ->
                    val filteredScenes = if (state.selectedChapter != null) {
                        scs.filter { it.chapterId == state.selectedChapter.id }.sortedBy { it.orderIndex }
                    } else scs.sortedBy { it.orderIndex }

                    val selectedSc = state.selectedScene?.let { current -> filteredScenes.find { it.id == current.id } }
                        ?: filteredScenes.firstOrNull()

                    state.copy(scenes = filteredScenes, selectedScene = selectedSc)
                }
            }
        }

        viewModelScope.launch {
            // Observer les panels
            database.panelDao().getPanelsByProjectId(projectId).collect { pns ->
                _uiState.update { state ->
                    val currentSceneId = state.selectedScene?.id
                    val filteredPanels = if (currentSceneId != null) {
                        pns.filter { it.sceneId == currentSceneId }.sortedBy { it.orderIndex }
                    } else pns.sortedBy { it.orderIndex }

                    state.copy(panels = filteredPanels)
                }
            }
        }

        viewModelScope.launch {
            // Observer tous les dialogues du projet
            dialogueUseCase.getDialoguesByProjectId(projectId).collect { dls ->
                val grouped = dls.groupBy { it.panelId }
                    .mapValues { entry -> entry.value.sortedBy { it.orderIndex } }

                _uiState.update { it.copy(dialoguesByPanelId = grouped) }
            }
        }

        viewModelScope.launch {
            // Observer les jobs IA actifs
            database.generationJobDao().getJobsByProjectId(projectId).collect { jobs ->
                val lastJob = jobs.find { it.status == com.example.data.local.entity.JobStatus.RUNNING }
                _uiState.update {
                    it.copy(
                        activeJob = lastJob,
                        isGenerating = lastJob != null
                    )
                }
            }
        }
    }

    fun selectChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            val scs = database.sceneDao().getScenesByProjectId(projectId)
            _uiState.update { state ->
                state.copy(
                    selectedChapter = chapter,
                    selectedScene = null
                )
            }
        }
    }

    fun selectScene(scene: SceneEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedScene = scene) }
        }
    }

    fun generateDialoguesForPanel(panelId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, userMessage = "Génération des dialogues du panel en cours...", errorMessage = null) }
            val res = dialogueUseCase.generateDialoguesForPanel(projectId, panelId)
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        userMessage = "${list.size} réplique(s) générée(s) avec succès !"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = "Erreur : ${err.message}"
                    )
                }
            }
        }
    }

    fun generateDialoguesForCurrentScene() {
        val sceneId = _uiState.value.selectedScene?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, userMessage = "Génération des dialogues de la scène en cours...", errorMessage = null) }
            val res = dialogueUseCase.generateDialoguesForScene(projectId, sceneId)
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        userMessage = "${list.size} réplique(s) générée(s) pour la scène entière !"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = "Erreur : ${err.message}"
                    )
                }
            }
        }
    }

    fun openNewDialogueDialog(panelId: String) {
        _uiState.update {
            it.copy(
                isEditingNewDialogue = true,
                targetPanelForNewDialogue = panelId,
                dialogueBeingEdited = DialogueEntity(
                    id = "",
                    panelId = panelId,
                    projectId = projectId,
                    characterId = null,
                    speakerName = "",
                    orderIndex = 1,
                    type = "SPEECH",
                    emotion = "Neutre",
                    placement = "TOP_LEFT",
                    text = "",
                    isCanon = false
                )
            )
        }
    }

    fun openEditDialogueDialog(dialogue: DialogueEntity) {
        _uiState.update {
            it.copy(
                isEditingNewDialogue = false,
                targetPanelForNewDialogue = dialogue.panelId,
                dialogueBeingEdited = dialogue
            )
        }
    }

    fun closeEditDialog() {
        _uiState.update {
            it.copy(
                dialogueBeingEdited = null,
                isEditingNewDialogue = false,
                targetPanelForNewDialogue = null
            )
        }
    }

    fun saveEditedDialogue(
        speakerName: String,
        characterId: String?,
        text: String,
        type: String,
        emotion: String,
        placement: String
    ) {
        val currentState = _uiState.value
        val editing = currentState.dialogueBeingEdited ?: return

        viewModelScope.launch {
            if (currentState.isEditingNewDialogue) {
                val panelId = currentState.targetPanelForNewDialogue ?: return@launch
                dialogueUseCase.addDialogue(
                    panelId = panelId,
                    projectId = projectId,
                    speakerName = speakerName,
                    characterId = characterId,
                    text = text,
                    type = type,
                    emotion = emotion,
                    placement = placement
                )
                _uiState.update { it.copy(userMessage = "Nouvelle réplique ajoutée.") }
            } else {
                val updated = editing.copy(
                    speakerName = speakerName,
                    characterId = characterId,
                    text = text,
                    type = type,
                    emotion = emotion,
                    placement = placement
                )
                dialogueUseCase.updateDialogue(updated)
                _uiState.update { it.copy(userMessage = "Réplique modifiée.") }
            }
            closeEditDialog()
        }
    }

    fun deleteDialogue(dialogueId: String) {
        viewModelScope.launch {
            dialogueUseCase.deleteDialogue(dialogueId)
            _uiState.update { it.copy(userMessage = "Réplique supprimée.") }
        }
    }

    fun moveDialogue(panelId: String, dialogueId: String, directionUp: Boolean) {
        viewModelScope.launch {
            dialogueUseCase.moveDialogue(panelId, dialogueId, directionUp)
        }
    }

    fun validatePanelDialogues(panelId: String) {
        viewModelScope.launch {
            val contradictions = dialogueUseCase.validateAllDialoguesForPanel(projectId, panelId)
            if (contradictions.isEmpty()) {
                _uiState.update { it.copy(userMessage = "Dialogues de la case validés dans le Canon !") }
            } else {
                _uiState.update {
                    it.copy(userMessage = "Dialogues validés (${contradictions.size} contradiction(s) signalée(s)).")
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }

    class Factory(
        private val projectId: String,
        private val database: ToonDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val canonRepo = CanonMemoryRepositoryImpl(database)
            val useCase = DialogueUseCase(
                dialogueDao = database.dialogueDao(),
                panelDao = database.panelDao(),
                sceneDao = database.sceneDao(),
                chapterDao = database.chapterDao(),
                jobDao = database.generationJobDao(),
                canonMemoryRepository = canonRepo
            )
            return DialoguesViewModel(projectId, useCase, database) as T
        }
    }
}

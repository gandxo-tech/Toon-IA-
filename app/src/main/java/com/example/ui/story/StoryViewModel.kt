package com.example.ui.story

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiStoryGenerator
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.StoryBibleResult
import com.example.domain.model.StoryTimelineEventEntry
import com.example.domain.usecase.StoryBibleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class StoryGenerationUiStatus {
    object Idle : StoryGenerationUiStatus()
    object Running : StoryGenerationUiStatus()
    data class Success(val message: String) : StoryGenerationUiStatus()
    data class Failed(val errorMessage: String) : StoryGenerationUiStatus()
}

data class StoryUiState(
    val projectId: String = "",
    val projectTitle: String = "",
    val canonicalCharactersCount: Int = 0,
    val hasCanonicalWorld: Boolean = false,
    val status: StoryGenerationUiStatus = StoryGenerationUiStatus.Idle,
    val storyBible: StoryBibleResult? = null,
    val generalFeedback: String? = null,
    val activeContradictionTarget: Pair<String, List<Contradiction>>? = null,
    val showAddManualEventDialog: Boolean = false,
    val manualEventTitle: String = "",
    val manualEventEpoch: String = ""
)

class StoryViewModel(
    application: Application,
    private val useCaseOverride: StoryBibleUseCase? = null
) : AndroidViewModel(application) {

    private val db = ToonDatabase.getInstance(application)
    private val canonRepo: CanonMemoryRepository = CanonMemoryRepositoryImpl(db)

    private val useCase: StoryBibleUseCase = useCaseOverride ?: StoryBibleUseCase(
        projectDao = db.projectDao(),
        jobDao = db.generationJobDao(),
        storyBibleDao = db.storyBibleDao(),
        timelineEventDao = db.timelineEventDao(),
        canonRepository = canonRepo,
        generator = GeminiStoryGenerator(apiKeyOverride = ApiKeyManager.getApiKey(application))
    )

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    fun initializeProject(projectId: String) {
        if (_uiState.value.projectId == projectId && _uiState.value.storyBible != null) return

        _uiState.update { it.copy(projectId = projectId) }
        viewModelScope.launch {
            val project = db.projectDao().findProjectById(projectId)
            val title = project?.title ?: "Webtoon"
            val snapshot = canonRepo.getCanonSnapshot(projectId)

            _uiState.update {
                it.copy(
                    projectTitle = title,
                    canonicalCharactersCount = snapshot.characters.size,
                    hasCanonicalWorld = snapshot.worldBible != null
                )
            }

            // Charger les données de la Story Bible si déjà au canon
            val canonicalStory = useCase.loadCanonicalStoryBible(projectId)
            if (canonicalStory != null && _uiState.value.storyBible == null) {
                _uiState.update { it.copy(storyBible = canonicalStory) }
            }
        }
    }

    fun generateStoryBible() {
        val currentProjectId = _uiState.value.projectId
        if (currentProjectId.isBlank()) return

        _uiState.update { it.copy(status = StoryGenerationUiStatus.Running, generalFeedback = null) }

        viewModelScope.launch {
            val (_, result) = useCase.executeGenerateStoryJob(currentProjectId)
            result.onSuccess { generated ->
                _uiState.update {
                    it.copy(
                        storyBible = generated,
                        status = StoryGenerationUiStatus.Success("Story Bible générée avec succès !"),
                        generalFeedback = "Histoire générée. Validez la bible et la chronologie pour les inscrire dans le Canon."
                    )
                }
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Erreur inconnue lors de la génération de l'histoire"
                _uiState.update {
                    it.copy(
                        status = StoryGenerationUiStatus.Failed(msg),
                        generalFeedback = msg
                    )
                }
            }
        }
    }

    fun updateStoryBibleField(transform: (StoryBibleResult) -> StoryBibleResult) {
        val current = _uiState.value.storyBible ?: return
        _uiState.update { it.copy(storyBible = transform(current)) }
    }

    fun validateFullStoryBible(forceIgnoreContradictions: Boolean = false) {
        val currentProjectId = _uiState.value.projectId
        val currentStory = _uiState.value.storyBible ?: return

        viewModelScope.launch {
            val (validated, contradictions) = useCase.validateStoryBibleToCanon(
                projectId = currentProjectId,
                result = currentStory,
                ignoreContradictions = forceIgnoreContradictions
            )

            if (contradictions.isNotEmpty() && !forceIgnoreContradictions) {
                _uiState.update { it.copy(activeContradictionTarget = Pair("Story Bible", contradictions)) }
            } else {
                _uiState.update {
                    it.copy(
                        storyBible = validated,
                        activeContradictionTarget = null,
                        generalFeedback = "Story Bible et chronologie fondatrice inscrites au Canon !"
                    )
                }
            }
        }
    }

    fun validateTimelineEvent(eventId: String) {
        val currentProjectId = _uiState.value.projectId
        val currentStory = _uiState.value.storyBible ?: return
        val event = currentStory.timelineEvents.firstOrNull { it.id == eventId } ?: return

        viewModelScope.launch {
            val (validatedEvent, _) = useCase.validateTimelineEventToCanon(currentProjectId, event)
            val updatedList = currentStory.timelineEvents.map { if (it.id == eventId) validatedEvent else it }
            _uiState.update {
                it.copy(
                    storyBible = currentStory.copy(timelineEvents = updatedList),
                    generalFeedback = "Événement '${validatedEvent.title}' validé au canon !"
                )
            }
        }
    }

    fun showAddEventDialog(show: Boolean) {
        _uiState.update { it.copy(showAddManualEventDialog = show) }
    }

    fun updateManualEventTitle(title: String) {
        _uiState.update { it.copy(manualEventTitle = title) }
    }

    fun updateManualEventEpoch(epoch: String) {
        _uiState.update { it.copy(manualEventEpoch = epoch) }
    }

    fun confirmAddManualEvent() {
        val title = _uiState.value.manualEventTitle.trim()
        val epoch = _uiState.value.manualEventEpoch.trim()
        val currentProjectId = _uiState.value.projectId
        if (title.isBlank() || currentProjectId.isBlank()) return

        val currentStory = _uiState.value.storyBible ?: StoryBibleResult(projectId = currentProjectId)
        val newOrder = currentStory.timelineEvents.size + 1
        val newEvent = useCase.createManualTimelineEvent(currentProjectId, title, epoch, newOrder)
        val updatedEvents = currentStory.timelineEvents + newEvent

        _uiState.update {
            it.copy(
                storyBible = currentStory.copy(timelineEvents = updatedEvents),
                showAddManualEventDialog = false,
                manualEventTitle = "",
                manualEventEpoch = "",
                generalFeedback = "Événement '$title' ajouté (non canon tant que non validé)."
            )
        }
    }

    fun dismissContradictionDialog() {
        _uiState.update { it.copy(activeContradictionTarget = null) }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(generalFeedback = null) }
    }
}

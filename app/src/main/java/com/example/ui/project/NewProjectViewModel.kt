package com.example.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiIdeaAnalyzer
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.ProjectEntity
import com.example.domain.model.AdvancedIdeaDetails
import com.example.domain.model.IdeaAnalysisResult
import com.example.domain.usecase.AnalyzeIdeaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AnalysisUiStatus {
    object Idle : AnalysisUiStatus
    data class Running(val job: GenerationJobEntity) : AnalysisUiStatus
    data class Success(val job: GenerationJobEntity, val result: IdeaAnalysisResult) : AnalysisUiStatus
    data class Failed(val job: GenerationJobEntity, val errorMessage: String) : AnalysisUiStatus
}

data class NewProjectUiState(
    val projectId: String = UUID.randomUUID().toString(),
    val projectTitle: String = "Nouveau Projet Webtoon",
    val ideaText: String = "",
    val advancedDetails: AdvancedIdeaDetails = AdvancedIdeaDetails(),
    val isAdvancedDetailsExpanded: Boolean = false,
    val currentJob: GenerationJobEntity? = null,
    val status: AnalysisUiStatus = AnalysisUiStatus.Idle,
    val editableResult: IdeaAnalysisResult? = null,
    val isValidated: Boolean = false
)

class NewProjectViewModel @JvmOverloads constructor(
    application: Application,
    private val useCaseOverride: AnalyzeIdeaUseCase? = null
) : AndroidViewModel(application) {

    private val db = ToonDatabase.getInstance(application)
    private val useCase: AnalyzeIdeaUseCase = useCaseOverride ?: AnalyzeIdeaUseCase(
        projectDao = db.projectDao(),
        jobDao = db.generationJobDao(),
        geminiAnalyzer = GeminiIdeaAnalyzer()
    )
    private val _uiState = MutableStateFlow(NewProjectUiState())
    val uiState: StateFlow<NewProjectUiState> = _uiState.asStateFlow()

    fun updateIdeaText(newText: String) {
        _uiState.update { it.copy(ideaText = newText) }
    }

    fun updateProjectTitle(newTitle: String) {
        _uiState.update { it.copy(projectTitle = newTitle) }
    }

    fun toggleAdvancedDetails() {
        _uiState.update { it.copy(isAdvancedDetailsExpanded = !it.isAdvancedDetailsExpanded) }
    }

    fun updateAdvancedDetails(updated: AdvancedIdeaDetails) {
        _uiState.update { it.copy(advancedDetails = updated) }
    }

    fun fillSampleVagueIdea() {
        _uiState.update {
            it.copy(
                ideaText = "C'est l'histoire d'un jeune héros qui a des super pouvoirs et qui se bat contre une organisation de méchants dans une grande ville.",
                advancedDetails = AdvancedIdeaDetails(),
                isAdvancedDetailsExpanded = false
            )
        }
    }

    fun fillSampleDetailedIdea() {
        _uiState.update {
            it.copy(
                ideaText = "Dans un Paris cyberpunk de 2088 submergé par les pluies acides, Kael, un mécanicien clandestin de membres bioniques, découvre un implant quantique prohibé qui appartenait à sa sœur disparue. Cet implant lui permet de rembobiner le temps de 5 secondes, mais à chaque utilisation, son propre système nerveux se calcifie. Pourchassé par les agents de la corporation Chimera Dynamics, il doit s'allier avec une hackeuse rebelle pour démanteler le réseau avant que son corps ne cède.",
                advancedDetails = AdvancedIdeaDetails(
                    genre = "Cyberpunk / Sci-Fi",
                    ambiance = "Sombre, pluvieux, haute technologie et bas-fonds",
                    epoque = "Futur dystopique (2088)",
                    personnagesSouhaites = "Kael (protagoniste résilient), Mira (hackeuse sarcastique)",
                    inspirations = "Akira, Blade Runner, Arcane",
                    longueurVisee = "Série longue (~60 chapitres)",
                    publicCible = "Jeunes adultes (16-30 ans)",
                    contraintes = "Règle du retour temporel limitée à 5 secondes avec contrepartie physique directe",
                    evenementsSouhaites = "Infiltration d'un laboratoire volant, trahison du mentor"
                ),
                isAdvancedDetailsExpanded = true
            )
        }
    }

    fun analyzeIdea() {
        val currentState = _uiState.value
        if (currentState.ideaText.isBlank()) return

        viewModelScope.launch {
            // Créer ou assurer l'existence du projet dans Room
            val project = ProjectEntity(
                id = currentState.projectId,
                title = currentState.projectTitle.ifBlank { "Projet Webtoon" },
                synopsis = currentState.ideaText,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.projectDao().insertProject(project)

            // Déclencher le job via le cas d'utilisation
            val (initialJob, _) = useCase.executeAnalysisJob(
                projectId = currentState.projectId,
                ideaText = currentState.ideaText,
                advancedDetails = if (currentState.advancedDetails.hasAnyDetails()) currentState.advancedDetails else null
            )

            _uiState.update {
                it.copy(
                    currentJob = initialJob,
                    status = AnalysisUiStatus.Running(initialJob)
                )
            }

            // Exécuter l'analyse
            val (finalJob, result) = useCase.executeAnalysisJob(
                projectId = currentState.projectId,
                ideaText = currentState.ideaText,
                advancedDetails = if (currentState.advancedDetails.hasAnyDetails()) currentState.advancedDetails else null,
                existingJobId = initialJob.id
            )

            result.fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            currentJob = finalJob,
                            status = AnalysisUiStatus.Success(finalJob, analysis),
                            editableResult = analysis
                        )
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Erreur inconnue"
                    _uiState.update {
                        it.copy(
                            currentJob = finalJob,
                            status = AnalysisUiStatus.Failed(finalJob, errorMsg)
                        )
                    }
                }
            )
        }
    }

    fun retryAnalysis() {
        analyzeIdea()
    }

    fun updateGenrePrincipal(newGenre: String) {
        _uiState.update { state ->
            state.editableResult?.let { res ->
                val updated = res.copy(genrePrincipal = newGenre)
                state.copy(editableResult = updated)
            } ?: state
        }
    }

    fun updateTon(newTon: String) {
        _uiState.update { state ->
            state.editableResult?.let { res ->
                val updated = res.copy(ton = newTon)
                state.copy(editableResult = updated)
            } ?: state
        }
    }

    fun addTheme(theme: String) {
        if (theme.isBlank()) return
        _uiState.update { state ->
            state.editableResult?.let { res ->
                if (!res.themesDetectes.contains(theme)) {
                    val updated = res.copy(themesDetectes = res.themesDetectes + theme)
                    state.copy(editableResult = updated)
                } else state
            } ?: state
        }
    }

    fun removeTheme(theme: String) {
        _uiState.update { state ->
            state.editableResult?.let { res ->
                val updated = res.copy(themesDetectes = res.themesDetectes - theme)
                state.copy(editableResult = updated)
            } ?: state
        }
    }

    fun addSousGenre(subgenre: String) {
        if (subgenre.isBlank()) return
        _uiState.update { state ->
            state.editableResult?.let { res ->
                if (!res.sousGenres.contains(subgenre)) {
                    val updated = res.copy(sousGenres = res.sousGenres + subgenre)
                    state.copy(editableResult = updated)
                } else state
            } ?: state
        }
    }

    fun removeSousGenre(subgenre: String) {
        _uiState.update { state ->
            state.editableResult?.let { res ->
                val updated = res.copy(sousGenres = res.sousGenres - subgenre)
                state.copy(editableResult = updated)
            } ?: state
        }
    }

    fun validateAndProceed(onNavigateToNextStep: (projectId: String) -> Unit) {
        val currentState = _uiState.value
        val result = currentState.editableResult ?: return

        viewModelScope.launch {
            useCase.validateAndSaveToProject(
                projectId = currentState.projectId,
                originalIdeaText = currentState.ideaText,
                validatedAnalysis = result
            )
            _uiState.update { it.copy(isValidated = true) }
            onNavigateToNextStep(currentState.projectId)
        }
    }
}

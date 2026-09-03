package com.example.ui.cover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GenerationJobEntity
import com.example.domain.canon.Contradiction
import com.example.domain.model.CoverVisualProposal
import com.example.domain.model.MarketingCoverProposalsResult
import com.example.domain.model.SynopsisVariant
import com.example.domain.usecase.GenerateCoverImageUseCase
import com.example.domain.usecase.MarketingCoverUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarketingCoverUiState(
    val selectedTitle: String = "",
    val selectedSynopsis: String = "",
    val selectedLengthType: String = "Moyen",
    val selectedTagline: String = "",
    val coverVisualDescription: String = "",
    val coverComposition: String = "",
    val coverCharactersPresent: List<String> = emptyList(),
    val coverLighting: String = "",
    val coverPalette: String = "",
    val proposalsResult: MarketingCoverProposalsResult? = null,
    val isGenerating: Boolean = false,
    val currentJob: GenerationJobEntity? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isCanonValidated: Boolean = false,
    val activeContradictions: List<Contradiction> = emptyList(),
    val showContradictionDialog: Boolean = false,
    // Phase 9 - Image de couverture
    val coverImagePath: String? = null,
    val coverId: String? = null,
    val isGeneratingImage: Boolean = false,
    val imageErrorMessage: String? = null,
    val authorName: String = ""
)

/**
 * ViewModel pour la Phase 8 (Marketing) et Phase 9 (Illustration de couverture).
 */
class MarketingCoverViewModel(
    private val useCase: MarketingCoverUseCase,
    private val imageUseCase: GenerateCoverImageUseCase? = null,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketingCoverUiState())
    val uiState: StateFlow<MarketingCoverUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val currentMarketing = useCase.loadCurrentMarketing(projectId)
            if (currentMarketing != null) {
                _uiState.update { current ->
                    current.copy(
                        selectedTitle = if (current.selectedTitle.isBlank()) currentMarketing.selectedTitle else current.selectedTitle,
                        selectedSynopsis = if (current.selectedSynopsis.isBlank()) currentMarketing.selectedSynopsis else current.selectedSynopsis,
                        selectedTagline = if (current.selectedTagline.isBlank()) currentMarketing.selectedTagline else current.selectedTagline,
                        coverVisualDescription = if (current.coverVisualDescription.isBlank()) currentMarketing.selectedCoverDescription else current.coverVisualDescription,
                        isCanonValidated = currentMarketing.isCanonValidated,
                        coverImagePath = currentMarketing.coverImagePath ?: current.coverImagePath,
                        coverId = currentMarketing.coverId ?: current.coverId
                    )
                }
            }
        }
    }

    fun generateMarketing() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    successMessage = null
                )
            }

            val (job, result) = useCase.executeGenerateMarketingJob(projectId)

            result.fold(
                onSuccess = { proposals ->
                    // Sélection par défaut des premières propositions
                    val defaultTitle = proposals.titleProposals.firstOrNull() ?: _uiState.value.selectedTitle
                    val defaultSynopsis = proposals.synopsisVariants.firstOrNull { it.lengthType.equals("Moyen", ignoreCase = true) }
                        ?: proposals.synopsisVariants.firstOrNull()
                    val defaultCover = proposals.coverProposal

                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentJob = job,
                            proposalsResult = proposals,
                            selectedTitle = defaultTitle,
                            selectedSynopsis = defaultSynopsis?.text ?: it.selectedSynopsis,
                            selectedLengthType = defaultSynopsis?.lengthType ?: "Moyen",
                            selectedTagline = defaultSynopsis?.tagline ?: it.selectedTagline,
                            coverVisualDescription = defaultCover.detailedDescriptionNoText,
                            coverComposition = defaultCover.composition,
                            coverCharactersPresent = defaultCover.charactersPresent,
                            coverLighting = defaultCover.atmosphereAndLighting,
                            coverPalette = defaultCover.colorPalette,
                            isCanonValidated = false,
                            successMessage = "Propositions de titres, synopsis et couverture générées avec succès !"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentJob = job,
                            errorMessage = error.localizedMessage ?: error.message ?: "Erreur de génération marketing."
                        )
                    }
                }
            )
        }
    }

    fun selectTitle(title: String) {
        _uiState.update { it.copy(selectedTitle = title, isCanonValidated = false) }
    }

    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(selectedTitle = newTitle, isCanonValidated = false) }
    }

    fun selectSynopsisVariant(variant: SynopsisVariant) {
        _uiState.update {
            it.copy(
                selectedSynopsis = variant.text,
                selectedLengthType = variant.lengthType,
                selectedTagline = variant.tagline,
                isCanonValidated = false
            )
        }
    }

    fun updateSynopsis(newSynopsis: String) {
        _uiState.update {
            it.copy(
                selectedSynopsis = newSynopsis,
                selectedLengthType = "Personnalisé",
                isCanonValidated = false
            )
        }
    }

    fun updateTagline(newTagline: String) {
        _uiState.update { it.copy(selectedTagline = newTagline, isCanonValidated = false) }
    }

    fun updateCoverDescription(newDescription: String) {
        _uiState.update { it.copy(coverVisualDescription = newDescription, isCanonValidated = false) }
    }

    fun validateToCanon(forceValidation: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.selectedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Veuillez choisir ou saisir un titre avant de valider.") }
            return
        }

        viewModelScope.launch {
            if (!forceValidation) {
                // Détection préalable des contradictions
                val contradictions = useCase.detectContradictions(
                    projectId = projectId,
                    chosenTitle = currentState.selectedTitle,
                    chosenSynopsis = currentState.selectedSynopsis,
                    coverDescription = currentState.coverVisualDescription
                )

                if (contradictions.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            activeContradictions = contradictions,
                            showContradictionDialog = true
                        )
                    }
                    return@launch
                }
            }

            // Inscription effective dans le CanonMemory et la base Room
            val contradictions = useCase.validateAndSaveToCanon(
                projectId = projectId,
                chosenTitle = currentState.selectedTitle,
                chosenSynopsis = currentState.selectedSynopsis,
                chosenTagline = currentState.selectedTagline,
                coverDescription = currentState.coverVisualDescription
            )

            _uiState.update {
                it.copy(
                    isCanonValidated = true,
                    activeContradictions = contradictions,
                    showContradictionDialog = false,
                    successMessage = "Titre, synopsis et description de couverture validés avec succès dans le canon !"
                )
            }
        }
    }

    fun updateAuthorName(name: String) {
        _uiState.update { it.copy(authorName = name) }
    }

    /**
     * Phase 9 : Génération de l'illustration de la couverture via Gemini.
     * Enregistrée en local, avec superposition native des textes.
     */
    fun generateCoverIllustration(context: Context) {
        val imageUseCase = this.imageUseCase ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingImage = true,
                    imageErrorMessage = null
                )
            }

            val (job, result) = imageUseCase.executeGenerateCoverImage(
                context = context,
                projectId = projectId,
                coverId = _uiState.value.coverId
            )

            result.fold(
                onSuccess = { updatedCover ->
                    _uiState.update {
                        it.copy(
                            isGeneratingImage = false,
                            coverImagePath = updatedCover.localImagePath,
                            coverId = updatedCover.id,
                            successMessage = "Illustration de couverture générée avec succès !"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGeneratingImage = false,
                            imageErrorMessage = error.localizedMessage ?: error.message ?: "Échec de la génération de l'illustration."
                        )
                    }
                }
            )
        }
    }

    fun dismissContradictionDialog() {
        _uiState.update { it.copy(showContradictionDialog = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, imageErrorMessage = null) }
    }

    /**
     * Valide explicitement et passe à l'étape suivante.
     */
    fun validateAllMarketingCover() {
        if (!_uiState.value.isCanonValidated) {
            validateToCanon(forceValidation = true)
        }
    }

    class Factory(
        private val useCase: MarketingCoverUseCase,
        private val imageUseCase: GenerateCoverImageUseCase? = null,
        private val projectId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MarketingCoverViewModel(useCase, imageUseCase, projectId) as T
        }
    }
}

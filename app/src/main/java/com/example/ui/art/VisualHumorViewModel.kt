package com.example.ui.art

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GenerationJobEntity
import com.example.domain.canon.Contradiction
import com.example.domain.model.CharacterHumorHook
import com.example.domain.model.HumorDnaResult
import com.example.domain.model.VisualBibleResult
import com.example.domain.usecase.VisualHumorUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ArtTab {
    VISUAL_BIBLE,
    HUMOR_DNA
}

data class VisualHumorUiState(
    val selectedTab: ArtTab = ArtTab.VISUAL_BIBLE,
    val visualBible: VisualBibleResult? = null,
    val humorDna: HumorDnaResult? = null,
    val visualJob: GenerationJobEntity? = null,
    val humorJob: GenerationJobEntity? = null,
    val isGeneratingVisual: Boolean = false,
    val isGeneratingHumor: Boolean = false,
    val visualError: String? = null,
    val humorError: String? = null,
    val successMessage: String? = null,
    val activeContradictions: List<Contradiction> = emptyList(),
    val showContradictionDialog: Boolean = false,
    val pendingValidationType: String? = null // "VISUAL" ou "HUMOR"
)

/**
 * ViewModel pour l'écran Direction Artistique & Humour DNA (Phase 7).
 */
class VisualHumorViewModel(
    private val useCase: VisualHumorUseCase,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisualHumorUiState())
    val uiState: StateFlow<VisualHumorUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun selectTab(tab: ArtTab) {
        _uiState.update { it.copy(selectedTab = tab, visualError = null, humorError = null, successMessage = null) }
    }

    fun loadData() {
        viewModelScope.launch {
            val visual = useCase.loadVisualBible(projectId)
            val humor = useCase.loadHumorDna(projectId)
            _uiState.update {
                it.copy(
                    visualBible = visual ?: it.visualBible,
                    humorDna = humor ?: it.humorDna
                )
            }
        }
    }

    fun generateVisualBible() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingVisual = true,
                    visualError = null,
                    successMessage = null
                )
            }

            val (job, result) = useCase.executeGenerateVisualJob(projectId)

            if (result.isSuccess) {
                val generated = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isGeneratingVisual = false,
                        visualBible = generated,
                        visualJob = job,
                        successMessage = "Visual Bible générée avec succès. Relisez et modifiez les éléments avant validation canonique."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isGeneratingVisual = false,
                        visualJob = job,
                        visualError = result.exceptionOrNull()?.message ?: "Erreur inconnue lors de la génération visuelle."
                    )
                }
            }
        }
    }

    fun generateHumorDna() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingHumor = true,
                    humorError = null,
                    successMessage = null
                )
            }

            val (job, result) = useCase.executeGenerateHumorJob(projectId)

            if (result.isSuccess) {
                val generated = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isGeneratingHumor = false,
                        humorDna = generated,
                        humorJob = job,
                        successMessage = "Humor DNA générée avec succès. Relisez attentivement l'harmonie tonale avant validation."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isGeneratingHumor = false,
                        humorJob = job,
                        humorError = result.exceptionOrNull()?.message ?: "Erreur inconnue lors de la génération de l'Humor DNA."
                    )
                }
            }
        }
    }

    fun updateVisualBible(updated: VisualBibleResult) {
        _uiState.update { it.copy(visualBible = updated.copy(isValidatedInCanon = false)) }
    }

    fun updateHumorDna(updated: HumorDnaResult) {
        _uiState.update { it.copy(humorDna = updated.copy(isValidatedInCanon = false)) }
    }

    fun requestValidateVisualBible() {
        val current = _uiState.value.visualBible ?: return
        viewModelScope.launch {
            val contradictions = useCase.checkVisualContradictions(projectId, current)
            if (contradictions.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        activeContradictions = contradictions,
                        showContradictionDialog = true,
                        pendingValidationType = "VISUAL"
                    )
                }
            } else {
                performValidateVisualBible(current)
            }
        }
    }

    fun requestValidateHumorDna() {
        val current = _uiState.value.humorDna ?: return
        viewModelScope.launch {
            val contradictions = useCase.checkHumorContradictions(projectId, current)
            if (contradictions.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        activeContradictions = contradictions,
                        showContradictionDialog = true,
                        pendingValidationType = "HUMOR"
                    )
                }
            } else {
                performValidateHumorDna(current)
            }
        }
    }

    fun confirmPendingValidation() {
        val type = _uiState.value.pendingValidationType
        _uiState.update { it.copy(showContradictionDialog = false, pendingValidationType = null) }
        when (type) {
            "VISUAL" -> {
                val current = _uiState.value.visualBible ?: return
                performValidateVisualBible(current)
            }
            "HUMOR" -> {
                val current = _uiState.value.humorDna ?: return
                performValidateHumorDna(current)
            }
        }
    }

    fun dismissContradictionDialog() {
        _uiState.update {
            it.copy(
                showContradictionDialog = false,
                activeContradictions = emptyList(),
                pendingValidationType = null
            )
        }
    }

    private fun performValidateVisualBible(visual: VisualBibleResult) {
        viewModelScope.launch {
            val (validated, contradictions) = useCase.validateVisualBibleToCanon(projectId, visual)
            _uiState.update {
                it.copy(
                    visualBible = validated,
                    activeContradictions = contradictions,
                    successMessage = "Direction Artistique validée avec succès dans le Canon Memory."
                )
            }
        }
    }

    private fun performValidateHumorDna(humor: HumorDnaResult) {
        viewModelScope.launch {
            val (validated, contradictions) = useCase.validateHumorDnaToCanon(projectId, humor)
            _uiState.update {
                it.copy(
                    humorDna = validated,
                    activeContradictions = contradictions,
                    successMessage = "Humor DNA validée avec succès dans le Canon Memory."
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(visualError = null, humorError = null, successMessage = null) }
    }

    class Factory(
        private val useCase: VisualHumorUseCase,
        private val projectId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VisualHumorViewModel(useCase, projectId) as T
        }
    }
}

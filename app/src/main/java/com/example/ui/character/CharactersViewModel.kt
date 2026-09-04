package com.example.ui.character

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiCharacterGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.Contradiction
import com.example.domain.model.CharacterBibleEntry
import com.example.domain.usecase.CharacterBibleUseCase
import com.example.util.ApiKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CharacterGenerationUiStatus {
    object Idle : CharacterGenerationUiStatus
    data class Running(val job: GenerationJobEntity) : CharacterGenerationUiStatus
    data class Success(val job: GenerationJobEntity) : CharacterGenerationUiStatus
    data class Failed(val job: GenerationJobEntity, val errorMessage: String) : CharacterGenerationUiStatus
}

data class CharacterUiItem(
    val entry: CharacterBibleEntry,
    val isExpanded: Boolean = true,
    val isRegenerating: Boolean = false,
    val singleRegenGuidance: String = "",
    val showRegenGuidanceDialog: Boolean = false,
    val contradictions: List<Contradiction> = emptyList(),
    val isCheckingContradictions: Boolean = false,
    val isValidating: Boolean = false
)

data class CharactersScreenUiState(
    val projectId: String = "",
    val projectTitle: String = "Mon Webtoon",
    val characters: List<CharacterUiItem> = emptyList(),
    val status: CharacterGenerationUiStatus = CharacterGenerationUiStatus.Idle,
    val currentJob: GenerationJobEntity? = null,
    val showAddManualDialog: Boolean = false,
    val manualName: String = "",
    val manualRole: String = "Protagoniste",
    val activeContradictionTarget: CharacterUiItem? = null,
    val generalFeedback: String? = null
)

class CharactersViewModel @JvmOverloads constructor(
    application: Application,
    private val useCaseOverride: CharacterBibleUseCase? = null
) : AndroidViewModel(application) {

    private val db = ToonDatabase.getInstance(application)
    private val useCase: CharacterBibleUseCase = useCaseOverride ?: CharacterBibleUseCase(
        projectDao = db.projectDao(),
        jobDao = db.generationJobDao(),
        characterDao = db.characterDao(),
        characterStateDao = db.characterStateDao(),
        canonRepository = CanonMemoryRepositoryImpl(db),
        generator = GeminiCharacterGenerator()
    )

    private val _uiState = MutableStateFlow(CharactersScreenUiState())
    val uiState: StateFlow<CharactersScreenUiState> = _uiState.asStateFlow()

    fun initializeProject(projectId: String) {
        if (_uiState.value.projectId == projectId && _uiState.value.characters.isNotEmpty()) return

        _uiState.update { it.copy(projectId = projectId) }

        viewModelScope.launch {
            val project = db.projectDao().findProjectById(projectId)
            val title = project?.title ?: "Webtoon"
            _uiState.update { it.copy(projectTitle = title) }

            // Si la liste locale est vide, charger les personnages déjà validés dans le canon
            val canonicalEntries = useCase.loadCanonicalCharacters(projectId)
            if (canonicalEntries.isNotEmpty() && _uiState.value.characters.isEmpty()) {
                val items = canonicalEntries.map { CharacterUiItem(entry = it) }
                _uiState.update { it.copy(characters = items) }
            }
        }
    }

    /**
     * Génère la liste complète de personnages via l'IA Gemini.
     */
    fun generateCharacters() {
        val currentProjectId = _uiState.value.projectId.ifBlank {
            // Prendre le premier projet disponible
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = CharacterGenerationUiStatus.Idle) }

            val (job, result) = useCase.executeGenerateCharactersJob(currentProjectId)
            if (result.isSuccess) {
                val bibleResult = result.getOrNull()
                val items = bibleResult?.characters?.map { entry ->
                    CharacterUiItem(entry = entry.copy(projectId = currentProjectId))
                } ?: emptyList()

                _uiState.update {
                    it.copy(
                        characters = items,
                        currentJob = job,
                        status = CharacterGenerationUiStatus.Success(job),
                        generalFeedback = "${items.size} personnages générés avec succès !"
                    )
                }
            } else {
                val errorMsg = job.errorMessage ?: "Échec de génération"
                _uiState.update {
                    it.copy(
                        currentJob = job,
                        status = CharacterGenerationUiStatus.Failed(job, errorMsg)
                    )
                }
            }
        }
    }

    /**
     * Modifie un champ quelconque d'un personnage en direct (édition manuelle).
     */
    fun updateCharacterField(
        characterId: String,
        update: (CharacterBibleEntry) -> CharacterBibleEntry
    ) {
        _uiState.update { state ->
            val updated = state.characters.map { item ->
                if (item.entry.id == characterId) {
                    val newEntry = update(item.entry).copy(isValidatedInCanon = false)
                    item.copy(entry = newEntry, contradictions = emptyList())
                } else {
                    item
                }
            }
            state.copy(characters = updated)
        }
    }

    /**
     * Ouvre ou ferme la fiche d'un personnage.
     */
    fun toggleCharacterExpansion(characterId: String) {
        _uiState.update { state ->
            val updated = state.characters.map {
                if (it.entry.id == characterId) it.copy(isExpanded = !it.isExpanded) else it
            }
            state.copy(characters = updated)
        }
    }

    /**
     * Valide explicitement un personnage pour l'écrire dans CanonMemory.
     * Appelle d'abord detectContradiction.
     * Si une contradiction est détectée, elle est affichée à l'utilisateur,
     * qui peut décider de valider quand même (ignorer) ou corriger.
     */
    fun validateCharacter(characterId: String, forceIgnoreContradictions: Boolean = false) {
        val targetItem = _uiState.value.characters.find { it.entry.id == characterId } ?: return
        val projectId = _uiState.value.projectId

        viewModelScope.launch {
            // 1. Détecter les contradictions préalables
            val contradictions = useCase.checkContradictions(projectId, targetItem.entry)

            if (contradictions.isNotEmpty() && !forceIgnoreContradictions) {
                // Avertissement visible à l'utilisateur avant écriture
                _uiState.update { state ->
                    val updated = state.characters.map {
                        if (it.entry.id == characterId) it.copy(contradictions = contradictions) else it
                    }
                    state.copy(characters = updated, activeContradictionTarget = targetItem.copy(contradictions = contradictions))
                }
                return@launch
            }

            // 2. Écrire dans CanonMemory (validation confirmée ou sans contradiction)
            val (validatedEntry, _) = useCase.validateAndWriteToCanon(
                projectId = projectId,
                entry = targetItem.entry,
                ignoreContradictions = forceIgnoreContradictions
            )

            _uiState.update { state ->
                val updated = state.characters.map {
                    if (it.entry.id == characterId) {
                        it.copy(
                            entry = validatedEntry,
                            contradictions = emptyList()
                        )
                    } else {
                        it
                    }
                }
                state.copy(
                    characters = updated,
                    activeContradictionTarget = null,
                    generalFeedback = "${validatedEntry.name} a été validé et inscrit dans le CanonMemory !"
                )
            }
        }
    }

    /**
     * Relance la génération IA spécifiquement pour UN personnage précis sans toucher aux autres.
     */
    fun regenerateSingleCharacter(characterId: String, userGuidance: String? = null) {
        val targetItem = _uiState.value.characters.find { it.entry.id == characterId } ?: return
        val projectId = _uiState.value.projectId

        viewModelScope.launch {
            // Indiquer que ce personnage précis est en cours de régénération
            _uiState.update { state ->
                val updated = state.characters.map {
                    if (it.entry.id == characterId) it.copy(isRegenerating = true) else it
                }
                state.copy(characters = updated)
            }

            val (job, result) = useCase.executeRegenerateSingleCharacterJob(
                projectId = projectId,
                targetCharacter = targetItem.entry,
                userGuidance = userGuidance
            )

            if (result.isSuccess) {
                val newChar = result.getOrNull() ?: targetItem.entry
                _uiState.update { state ->
                    val updated = state.characters.map {
                        if (it.entry.id == characterId) {
                            it.copy(
                                entry = newChar,
                                isRegenerating = false,
                                showRegenGuidanceDialog = false,
                                contradictions = emptyList()
                            )
                        } else {
                            it
                        }
                    }
                    state.copy(
                        characters = updated,
                        generalFeedback = "Le personnage ${newChar.name} a été régénéré avec succès."
                    )
                }
            } else {
                val errorMsg = job.errorMessage ?: "Échec de régénération"
                _uiState.update { state ->
                    val updated = state.characters.map {
                        if (it.entry.id == characterId) it.copy(isRegenerating = false) else it
                    }
                    state.copy(
                        characters = updated,
                        generalFeedback = "Erreur : $errorMsg"
                    )
                }
            }
        }
    }

    /**
     * Ouvre la boîte de dialogue pour ajouter un personnage manuellement sans IA.
     */
    fun showManualAddDialog(show: Boolean) {
        _uiState.update { it.copy(showAddManualDialog = show, manualName = "", manualRole = "Protagoniste") }
    }

    fun updateManualName(name: String) {
        _uiState.update { it.copy(manualName = name) }
    }

    fun updateManualRole(role: String) {
        _uiState.update { it.copy(manualRole = role) }
    }

    fun confirmAddManualCharacter() {
        val name = _uiState.value.manualName.trim()
        val role = _uiState.value.manualRole.trim()
        val projectId = _uiState.value.projectId

        if (name.isBlank()) return

        val newEntry = useCase.createManualCharacter(projectId, name, role)
        _uiState.update { state ->
            state.copy(
                characters = state.characters + CharacterUiItem(entry = newEntry),
                showAddManualDialog = false,
                manualName = "",
                generalFeedback = "Personnage '$name' ajouté manuellement. Complétez sa fiche puis validez-le."
            )
        }
    }

    fun showRegenDialog(characterId: String, show: Boolean) {
        _uiState.update { state ->
            val updated = state.characters.map {
                if (it.entry.id == characterId) it.copy(showRegenGuidanceDialog = show) else it
            }
            state.copy(characters = updated)
        }
    }

    fun updateRegenGuidance(characterId: String, guidance: String) {
        _uiState.update { state ->
            val updated = state.characters.map {
                if (it.entry.id == characterId) it.copy(singleRegenGuidance = guidance) else it
            }
            state.copy(characters = updated)
        }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(generalFeedback = null) }
    }

    fun dismissContradictionDialog() {
        _uiState.update { it.copy(activeContradictionTarget = null) }
    }

    /**
     * Valide explicitement tous les personnages et ignore les contradictions
     * pour un workflow plus rapide, puis passe à l'étape suivante.
     */
    fun validateAllCharacters() {
        _uiState.value.characters.forEach { item ->
            if (!item.entry.isValidatedInCanon) {
                validateCharacter(item.entry.id, forceIgnoreContradictions = true)
            }
        }
    }
}

package com.example.ui.universe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiWorldGenerator
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.Contradiction
import com.example.domain.model.WorldBibleResult
import com.example.domain.model.WorldLocationEntry
import com.example.domain.model.WorldObjectEntry
import com.example.domain.usecase.WorldBibleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class WorldGenerationUiStatus {
    object Idle : WorldGenerationUiStatus()
    object Running : WorldGenerationUiStatus()
    data class Success(val message: String) : WorldGenerationUiStatus()
    data class Failed(val errorMessage: String) : WorldGenerationUiStatus()
}

data class UniverseUiState(
    val projectId: String = "",
    val projectTitle: String = "",
    val canonicalCharactersCount: Int = 0,
    val status: WorldGenerationUiStatus = WorldGenerationUiStatus.Idle,
    val worldBible: WorldBibleResult? = null,
    val generalFeedback: String? = null,
    val activeContradictionTarget: Pair<String, List<Contradiction>>? = null,
    val showAddManualLocationDialog: Boolean = false,
    val manualLocationName: String = "",
    val manualLocationAtmosphere: String = ""
)

class UniverseViewModel @JvmOverloads constructor(
    application: Application,
    private val useCaseOverride: WorldBibleUseCase? = null
) : AndroidViewModel(application) {

    private val db = ToonDatabase.getInstance(application)
    private val canonRepo: CanonMemoryRepository = CanonMemoryRepositoryImpl(db)

    private val useCase: WorldBibleUseCase = useCaseOverride ?: WorldBibleUseCase(
        projectDao = db.projectDao(),
        jobDao = db.generationJobDao(),
        worldBibleDao = db.worldBibleDao(),
        locationDao = db.locationDao(),
        storyObjectDao = db.storyObjectDao(),
        canonRepository = canonRepo,
        generator = GeminiWorldGenerator(apiKeyOverride = ApiKeyManager.getApiKey(application))
    )

    private val _uiState = MutableStateFlow(UniverseUiState())
    val uiState: StateFlow<UniverseUiState> = _uiState.asStateFlow()

    fun initializeProject(projectId: String) {
        if (_uiState.value.projectId == projectId && _uiState.value.worldBible != null) return

        _uiState.update { it.copy(projectId = projectId) }
        viewModelScope.launch {
            val project = db.projectDao().findProjectById(projectId)
            val title = project?.title ?: "Webtoon"
            val snapshot = canonRepo.getCanonSnapshot(projectId)

            _uiState.update {
                it.copy(
                    projectTitle = title,
                    canonicalCharactersCount = snapshot.characters.size
                )
            }

            // Charger les données de la World Bible si déjà validées au canon
            val canonicalWorld = useCase.loadCanonicalWorldBible(projectId)
            if (canonicalWorld != null && _uiState.value.worldBible == null) {
                _uiState.update { it.copy(worldBible = canonicalWorld) }
            }
        }
    }

    fun generateWorldBible() {
        val currentProjectId = _uiState.value.projectId
        if (currentProjectId.isBlank()) return

        _uiState.update { it.copy(status = WorldGenerationUiStatus.Running, generalFeedback = null) }

        viewModelScope.launch {
            val (_, result) = useCase.executeGenerateWorldJob(currentProjectId)
            result.onSuccess { generated ->
                _uiState.update {
                    it.copy(
                        worldBible = generated,
                        status = WorldGenerationUiStatus.Success("World Bible générée avec succès !"),
                        generalFeedback = "Univers généré. Validez les sections et lieux pour les inscrire dans le Canon."
                    )
                }
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Erreur inconnue lors de la génération de l'univers"
                _uiState.update {
                    it.copy(
                        status = WorldGenerationUiStatus.Failed(msg),
                        generalFeedback = msg
                    )
                }
            }
        }
    }

    fun updateWorldBibleField(transform: (WorldBibleResult) -> WorldBibleResult) {
        val current = _uiState.value.worldBible ?: return
        _uiState.update { it.copy(worldBible = transform(current)) }
    }

    fun validateFullWorldBible(forceIgnoreContradictions: Boolean = false) {
        val currentProjectId = _uiState.value.projectId
        val currentWorld = _uiState.value.worldBible ?: return

        viewModelScope.launch {
            val (validated, contradictions) = useCase.validateWorldBibleToCanon(
                projectId = currentProjectId,
                result = currentWorld,
                ignoreContradictions = forceIgnoreContradictions
            )

            if (contradictions.isNotEmpty() && !forceIgnoreContradictions) {
                _uiState.update { it.copy(activeContradictionTarget = Pair("World Bible", contradictions)) }
            } else {
                _uiState.update {
                    it.copy(
                        worldBible = validated,
                        activeContradictionTarget = null,
                        generalFeedback = "World Bible validée et inscrite au Canon !"
                    )
                }
            }
        }
    }

    fun validateLocation(locationId: String, forceIgnoreContradictions: Boolean = false) {
        val currentProjectId = _uiState.value.projectId
        val currentWorld = _uiState.value.worldBible ?: return
        val loc = currentWorld.locations.firstOrNull { it.id == locationId } ?: return

        viewModelScope.launch {
            if (!forceIgnoreContradictions) {
                val contradictions = useCase.checkLocationContradictions(currentProjectId, loc)
                if (contradictions.isNotEmpty()) {
                    _uiState.update { it.copy(activeContradictionTarget = Pair("Lieu : ${loc.name}", contradictions)) }
                    return@launch
                }
            }

            val (validatedLoc, _) = useCase.validateLocationToCanon(currentProjectId, loc, forceIgnoreContradictions)
            val updatedList = currentWorld.locations.map { if (it.id == locationId) validatedLoc else it }
            _uiState.update {
                it.copy(
                    worldBible = currentWorld.copy(locations = updatedList),
                    activeContradictionTarget = null,
                    generalFeedback = "Lieu '${validatedLoc.name}' validé dans le canon !"
                )
            }
        }
    }

    fun validateObject(objectId: String) {
        val currentProjectId = _uiState.value.projectId
        val currentWorld = _uiState.value.worldBible ?: return
        val obj = currentWorld.objects.firstOrNull { it.id == objectId } ?: return

        viewModelScope.launch {
            val (validatedObj, _) = useCase.validateObjectToCanon(currentProjectId, obj)
            val updatedList = currentWorld.objects.map { if (it.id == objectId) validatedObj else it }
            _uiState.update {
                it.copy(
                    worldBible = currentWorld.copy(objects = updatedList),
                    generalFeedback = "Objet '${validatedObj.name}' validé dans le canon !"
                )
            }
        }
    }

    fun showAddLocationDialog(show: Boolean) {
        _uiState.update { it.copy(showAddManualLocationDialog = show) }
    }

    fun updateManualLocationName(name: String) {
        _uiState.update { it.copy(manualLocationName = name) }
    }

    fun updateManualLocationAtmosphere(atmosphere: String) {
        _uiState.update { it.copy(manualLocationAtmosphere = atmosphere) }
    }

    fun confirmAddManualLocation() {
        val name = _uiState.value.manualLocationName.trim()
        val atmosphere = _uiState.value.manualLocationAtmosphere.trim()
        val currentProjectId = _uiState.value.projectId
        if (name.isBlank() || currentProjectId.isBlank()) return

        val newLoc = useCase.createManualLocation(currentProjectId, name, atmosphere)
        val currentWorld = _uiState.value.worldBible ?: WorldBibleResult(projectId = currentProjectId)
        val updatedLocs = currentWorld.locations + newLoc

        _uiState.update {
            it.copy(
                worldBible = currentWorld.copy(locations = updatedLocs),
                showAddManualLocationDialog = false,
                manualLocationName = "",
                manualLocationAtmosphere = "",
                generalFeedback = "Lieu '$name' ajouté (non canon tant que non validé)."
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

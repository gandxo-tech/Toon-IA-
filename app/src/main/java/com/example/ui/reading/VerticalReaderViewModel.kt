package com.example.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ToonDatabase
import com.example.ui.lettering.PanelWithDialogues
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class VerticalReaderUiState(
    val panelsWithDialogues: List<PanelWithDialogues> = emptyList(),
    val isLoading: Boolean = false
)

class VerticalReaderViewModel(
    private val projectId: String,
    private val database: ToonDatabase
) : ViewModel() {

    val uiState: StateFlow<VerticalReaderUiState> = combine(
        database.panelDao().getPanelsByProjectId(projectId),
        database.dialogueDao().getDialoguesByProjectId(projectId)
    ) { panels, dialogues ->
        val dialoguesByPanel = dialogues.groupBy { it.panelId }
        val list = panels.map { panel ->
            PanelWithDialogues(
                panel = panel,
                dialogues = dialoguesByPanel[panel.id] ?: emptyList()
            )
        }
        VerticalReaderUiState(panelsWithDialogues = list, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VerticalReaderUiState(isLoading = true)
    )

    class Factory(
        private val projectId: String,
        private val database: ToonDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VerticalReaderViewModel(projectId, database) as T
        }
    }
}

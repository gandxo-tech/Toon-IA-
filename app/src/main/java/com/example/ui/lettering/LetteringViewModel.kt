package com.example.ui.lettering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.PanelEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PanelWithDialogues(
    val panel: PanelEntity,
    val dialogues: List<DialogueEntity>
)

data class LetteringUiState(
    val panelsWithDialogues: List<PanelWithDialogues> = emptyList(),
    val isLoading: Boolean = false
)

class LetteringViewModel(
    private val projectId: String,
    private val database: ToonDatabase
) : ViewModel() {

    val uiState: StateFlow<LetteringUiState> = combine(
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
        LetteringUiState(panelsWithDialogues = list, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LetteringUiState(isLoading = true)
    )

    fun updateDialogueText(dialogue: DialogueEntity, newText: String) {
        viewModelScope.launch {
            val updated = dialogue.copy(text = newText)
            database.dialogueDao().updateDialogue(updated)
        }
    }

    class Factory(
        private val projectId: String,
        private val database: ToonDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LetteringViewModel(projectId, database) as T
        }
    }
}

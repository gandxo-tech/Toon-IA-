package com.example.ui.dialogues

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.PanelEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialoguesScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DialoguesViewModel = viewModel(
        factory = DialoguesViewModel.Factory(
            projectId = projectId,
            database = ToonDatabase.getInstance(LocalContext.current)
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage, state.errorMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag("dialogues_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dialogues & Récit",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Superposition native • Indépendant du rendu visuel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    if (state.selectedScene != null && state.panels.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.generateDialoguesForCurrentScene() },
                            enabled = !state.isGenerating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("generate_scene_dialogues_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Générer la scène", fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bannière de statut de génération IA
            AnimatedVisibility(visible = state.isGenerating) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.activeJob?.inputPayload ?: "Génération des dialogues par IA...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Sélecteur de Chapitre
            if (state.chapters.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.chapters) { chapter ->
                        FilterChip(
                            selected = state.selectedChapter?.id == chapter.id,
                            onClick = { viewModel.selectChapter(chapter) },
                            label = { Text("Ch. ${chapter.number}: ${chapter.title}") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
            }

            // Sélecteur de Scène
            if (state.scenes.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.scenes) { scene ->
                        FilterChip(
                            selected = state.selectedScene?.id == scene.id,
                            onClick = { viewModel.selectScene(scene) },
                            label = { Text("Scène #${scene.orderIndex}: ${scene.title}") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
            }

            if (state.panels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aucune case disponible",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Veuillez d'abord générer ou configurer les cases (Panels) dans l'étape précédente du workflow.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("dialogues_panels_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.panels, key = { it.id }) { panel ->
                        val dialogues = state.dialoguesByPanelId[panel.id] ?: emptyList()
                        PanelDialogueCard(
                            panel = panel,
                            dialogues = dialogues,
                            isGenerating = state.isGenerating,
                            onGenerateDialogues = { viewModel.generateDialoguesForPanel(panel.id) },
                            onAddNewDialogue = { viewModel.openNewDialogueDialog(panel.id) },
                            onEditDialogue = { viewModel.openEditDialogueDialog(it) },
                            onDeleteDialogue = { viewModel.deleteDialogue(it.id) },
                            onMoveDialogue = { dId, up -> viewModel.moveDialogue(panel.id, dId, up) },
                            onValidatePanelDialogues = { viewModel.validatePanelDialogues(panel.id) }
                        )
                    }
                }
            }
        }
    }

    // Modal d'ajout / modification de dialogue
    state.dialogueBeingEdited?.let { dialogue ->
        DialogueEditDialog(
            dialogue = dialogue,
            isNew = state.isEditingNewDialogue,
            characters = state.characters,
            onDismiss = { viewModel.closeEditDialog() },
            onConfirm = { spk, cId, txt, typ, emo, plc ->
                viewModel.saveEditedDialogue(spk, cId, txt, typ, emo, plc)
            }
        )
    }
}

@Composable
fun PanelDialogueCard(
    panel: PanelEntity,
    dialogues: List<DialogueEntity>,
    isGenerating: Boolean,
    onGenerateDialogues: () -> Unit,
    onAddNewDialogue: () -> Unit,
    onEditDialogue: (DialogueEntity) -> Unit,
    onDeleteDialogue: (DialogueEntity) -> Unit,
    onMoveDialogue: (dialogueId: String, moveUp: Boolean) -> Unit,
    onValidatePanelDialogues: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_card_${panel.orderIndex}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // En-tête du Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Case #${panel.orderIndex}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = panel.cameraAngle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (dialogues.isNotEmpty()) {
                    val allCanon = dialogues.all { it.isCanon }
                    Surface(
                        color = if (allCanon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (allCanon) "Canon validé" else "${dialogues.size} réplique(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allCanon) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Aperçu visuel ou résumé d'action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!panel.localImagePath.isNullOrBlank() && File(panel.localImagePath).exists()) {
                    AsyncImage(
                        model = File(panel.localImagePath),
                        contentDescription = "Illustration case",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = panel.actionDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (panel.charactersPresent.isNotBlank()) {
                        Text(
                            text = "Présents : ${panel.charactersPresent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Liste des bulles de dialogue
            if (dialogues.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun dialogue pour cette case (case muette ou à générer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dialogues.forEachIndexed { index, dialogue ->
                        DialogueBubbleItem(
                            dialogue = dialogue,
                            isFirst = index == 0,
                            isLast = index == dialogues.size - 1,
                            onEdit = { onEditDialogue(dialogue) },
                            onDelete = { onDeleteDialogue(dialogue) },
                            onMoveUp = { onMoveDialogue(dialogue.id, true) },
                            onMoveDown = { onMoveDialogue(dialogue.id, false) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions de la case
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onGenerateDialogues,
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("gen_panel_dialogue_${panel.orderIndex}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Générer IA", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onAddNewDialogue,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_dialogue_${panel.orderIndex}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter", fontSize = 12.sp)
                    }
                }

                if (dialogues.isNotEmpty() && dialogues.any { !it.isCanon }) {
                    Button(
                        onClick = onValidatePanelDialogues,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("validate_dialogues_${panel.orderIndex}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Valider Canon", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DialogueBubbleItem(
    dialogue: DialogueEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleShape = when (dialogue.type) {
        "THOUGHT" -> RoundedCornerShape(20.dp)
        "SHOUT" -> RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        "NARRATION" -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(14.dp)
    }

    val bubbleBg = when (dialogue.type) {
        "THOUGHT" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        "SHOUT" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        "NARRATION" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        "WHISPER" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    Surface(
        shape = bubbleShape,
        color = bubbleBg,
        border = if (dialogue.type == "THOUGHT") borderStrokeThought() else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dialogue_bubble_${dialogue.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Locuteur + type + position
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dialogue.speakerName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dialogue.speakerName.ifBlank { "Voix off" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = dialogueTypeLabel(dialogue.type),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (dialogue.emotion.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(${dialogue.emotion})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions réorganisation / édition
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Monter",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Descendre",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Texte de la réplique
            Text(
                text = dialogue.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (dialogue.type == "SHOUT") FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (dialogue.type == "THOUGHT" || dialogue.type == "WHISPER") FontStyle.Italic else FontStyle.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // SFX & placement tag
            if (dialogue.soundEffect != null || dialogue.placement.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (dialogue.soundEffect != null) {
                        Text(
                            text = "SFX: ${dialogue.soundEffect}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = placementLabel(dialogue.placement),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogueEditDialog(
    dialogue: DialogueEntity,
    isNew: Boolean,
    characters: List<CharacterEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        speakerName: String,
        characterId: String?,
        text: String,
        type: String,
        emotion: String,
        placement: String
    ) -> Unit
) {
    var speakerName by remember { mutableStateOf(dialogue.speakerName) }
    var selectedCharacterId by remember { mutableStateOf(dialogue.characterId) }
    var text by remember { mutableStateOf(dialogue.text) }
    var selectedType by remember { mutableStateOf(dialogue.type) }
    var emotion by remember { mutableStateOf(dialogue.emotion) }
    var placement by remember { mutableStateOf(dialogue.placement) }

    val types = listOf(
        "SPEECH" to "Parole normale",
        "THOUGHT" to "Pensée",
        "SHOUT" to "Cri / Exclamation",
        "WHISPER" to "Chuchotement",
        "NARRATION" to "Narration / Voix off"
    )

    val placements = listOf(
        "TOP_LEFT" to "Haut Gauche",
        "TOP_RIGHT" to "Haut Droite",
        "BOTTOM_LEFT" to "Bas Gauche",
        "BOTTOM_RIGHT" to "Bas Droite",
        "CENTER" to "Centre"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNew) "Nouvelle bulle de dialogue" else "Modifier la réplique")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Choix rapide parmi les personnages du projet
                if (characters.isNotEmpty()) {
                    Text(
                        text = "Sélectionner un personnage du canon :",
                        style = MaterialTheme.typography.labelSmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(characters) { char ->
                            FilterChip(
                                selected = speakerName.equals(char.name, ignoreCase = true),
                                onClick = {
                                    speakerName = char.name
                                    selectedCharacterId = char.id
                                },
                                label = { Text(char.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = speakerName,
                    onValueChange = { speakerName = it },
                    label = { Text("Nom du locuteur") },
                    placeholder = { Text("ex: Lucas, Narrateur, Voix off") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialogue_speaker_input")
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Texte de la réplique *") },
                    placeholder = { Text("Ce que dit le personnage...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialogue_text_input")
                )

                // Type de bulle
                Text(text = "Type de bulle :", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(types) { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = emotion,
                        onValueChange = { emotion = it },
                        label = { Text("Émotion") },
                        placeholder = { Text("Colère, Peur...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Placement
                Text(text = "Emplacement suggéré :", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(placements) { (key, label) ->
                        FilterChip(
                            selected = placement == key,
                            onClick = { placement = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        speakerName.ifBlank { "Voix off" },
                        selectedCharacterId,
                        text,
                        selectedType,
                        emotion,
                        placement
                    )
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("dialogue_confirm_save_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

private fun dialogueTypeLabel(type: String): String {
    return when (type) {
        "THOUGHT" -> "Pensée"
        "SHOUT" -> "Cri"
        "WHISPER" -> "Chuchotement"
        "NARRATION" -> "Narration"
        else -> "Parole"
    }
}

private fun placementLabel(placement: String): String {
    return when (placement) {
        "TOP_LEFT" -> "Haut-G"
        "TOP_RIGHT" -> "Haut-D"
        "BOTTOM_LEFT" -> "Bas-G"
        "BOTTOM_RIGHT" -> "Bas-D"
        "CENTER" -> "Centre"
        else -> "Auto"
    }
}

private fun borderStrokeThought(): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = Color(0xFF9C27B0).copy(alpha = 0.4f)
    )
}

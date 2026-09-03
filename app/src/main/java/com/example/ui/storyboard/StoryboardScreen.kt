package com.example.ui.storyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.SceneEntity

/**
 * Écran pour la Phase 11 : Storyboard & Découpage des Scènes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryboardScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPanels: () -> Unit = {},
    onNavigateToCanonMemory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StoryboardViewModel = viewModel(
        factory = StoryboardViewModel.provideFactory(projectId, LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    var sceneToEdit by remember { mutableStateOf<SceneEntity?>(null) }
    var sceneToSplit by remember { mutableStateOf<SceneEntity?>(null) }
    var sceneToMergeWithNext by remember { mutableStateOf<Pair<SceneEntity, SceneEntity>?>(null) }
    var sceneToDelete by remember { mutableStateOf<SceneEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Phase 11 : Storyboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.chapter?.let { "Chapitre ${it.number} : ${it.title}" }
                                ?: "Structuration des scènes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("storyboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToCanonMemory,
                        modifier = Modifier.testTag("storyboard_canon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Mémoire Canon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bouton Valider Canon
                        Button(
                            onClick = { viewModel.validateStoryboardToCanon() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("validate_storyboard_canon_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isValidatedInCanon)
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.primary
                            ),
                            enabled = uiState.scenes.isNotEmpty() && !uiState.isGeneratingAi
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isValidatedInCanon) "Storyboard Canon ✓" else "Valider dans le Canon")
                        }

                        // Bouton vers Panels (Phase 12)
                        Button(
                            onClick = onNavigateToPanels,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("storyboard_to_panels_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            enabled = uiState.scenes.isNotEmpty()
                        ) {
                            Text("Panels (Phase 12)")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.chapter == null && uiState.scenes.isEmpty() -> {
                    EmptyStoryboardState(onNavigateBack = onNavigateBack)
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Bannière de messages
                        item {
                            MessageBanners(
                                errorMessage = uiState.errorMessage,
                                successMessage = uiState.successMessage,
                                onDismiss = { viewModel.clearMessages() }
                            )
                        }

                        // En-tête informatif du Storyboard
                        item {
                            StoryboardHeaderCard(
                                scenesCount = uiState.scenes.size,
                                isValidatedInCanon = uiState.isValidatedInCanon
                            )
                        }

                        // Liste des cartes de scènes
                        itemsIndexed(uiState.scenes, key = { _, scene -> scene.id }) { index, scene ->
                            SceneStoryboardCard(
                                scene = scene,
                                index = index,
                                totalCount = uiState.scenes.size,
                                isGeneratingAi = uiState.isGeneratingAi && uiState.activeSceneAiId == scene.id,
                                onMoveUp = { viewModel.moveScene(scene.id, directionUp = true) },
                                onMoveDown = { viewModel.moveScene(scene.id, directionUp = false) },
                                onDetailWithAi = { viewModel.detailSceneWithAi(scene) },
                                onEdit = { sceneToEdit = scene },
                                onSplit = { sceneToSplit = scene },
                                onMergeWithNext = {
                                    val nextScene = uiState.scenes.getOrNull(index + 1)
                                    if (nextScene != null) {
                                        sceneToMergeWithNext = Pair(scene, nextScene)
                                    }
                                },
                                onDelete = { sceneToDelete = scene }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogues de modification / scission / fusion / suppression
    sceneToEdit?.let { scene ->
        EditSceneDialog(
            scene = scene,
            onDismiss = { sceneToEdit = null },
            onConfirm = { updatedScene ->
                viewModel.updateScene(updatedScene)
                sceneToEdit = null
            }
        )
    }

    sceneToSplit?.let { scene ->
        SplitSceneDialog(
            scene = scene,
            onDismiss = { sceneToSplit = null },
            onConfirm = { splitTitle, splitSummary, splitBeats, splitCharacters, splitAtmosphere ->
                viewModel.splitScene(
                    originalSceneId = scene.id,
                    splitTitle = splitTitle,
                    splitSummary = splitSummary,
                    splitBeats = splitBeats,
                    splitCharacters = splitCharacters,
                    splitAtmosphere = splitAtmosphere
                )
                sceneToSplit = null
            }
        )
    }

    sceneToMergeWithNext?.let { (scene1, scene2) ->
        MergeScenesDialog(
            firstScene = scene1,
            secondScene = scene2,
            onDismiss = { sceneToMergeWithNext = null },
            onConfirm = {
                viewModel.mergeScenes(scene1.id, scene2.id)
                sceneToMergeWithNext = null
            }
        )
    }

    sceneToDelete?.let { scene ->
        DeleteSceneDialog(
            scene = scene,
            onDismiss = { sceneToDelete = null },
            onConfirm = {
                viewModel.deleteScene(scene)
                sceneToDelete = null
            }
        )
    }
}

@Composable
private fun MessageBanners(
    errorMessage: String?,
    successMessage: String?,
    onDismiss: () -> Unit
) {
    if (errorMessage != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("storyboard_error_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("OK", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (successMessage != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("storyboard_success_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StoryboardHeaderCard(
    scenesCount: Int,
    isValidatedInCanon: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Structure narrative & Rythme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$scenesCount scènes ordonnées pour ce chapitre",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (isValidatedInCanon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isValidatedInCanon) "Canon Validé" else "Brouillon Storyboard",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isValidatedInCanon) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneStoryboardCard(
    scene: SceneEntity,
    index: Int,
    totalCount: Int,
    isGeneratingAi: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDetailWithAi: () -> Unit,
    onEdit: () -> Unit,
    onSplit: () -> Unit,
    onMergeWithNext: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedDetails by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_card_${scene.orderIndex}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Barre supérieure de la scène : Numéro d'ordre, Titre, Actions de déplacement
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Scène ${scene.orderIndex}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = scene.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Boutons Monter / Descendre
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp).testTag("scene_up_${scene.orderIndex}")
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp).testTag("scene_down_${scene.orderIndex}")
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                    }
                }
            }

            // Chips contextuels : Personnages, Lieu, Ambiance
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (scene.locationName.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(scene.locationName) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                if (scene.charactersPresent.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(scene.charactersPresent) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                if (scene.atmosphere.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(scene.atmosphere) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Mood,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Résumé narratif
            Text(
                text = scene.narrativeSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Micro-beats narratifs
            if (scene.narrativeBeats.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Micro-beats narratifs :",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scene.narrativeBeats,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Détails enrichis IA (Rythme vertical, Arc émotionnel)
            if (scene.pacingNotes.isNotBlank() || scene.emotionalArc.isNotBlank()) {
                OutlinedCard(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (scene.pacingNotes.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Rythme de lecture vertical :",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = scene.pacingNotes,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (scene.emotionalArc.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Mood,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Arc émotionnel :",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = scene.emotionalArc,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Barre d'outils et d'actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton IA Enrichissement
                Button(
                    onClick = onDetailWithAi,
                    enabled = !isGeneratingAi,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("scene_ai_enrich_${scene.orderIndex}")
                ) {
                    if (isGeneratingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enrichissement...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enrichir (IA)", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Actions manuelles (Modifier, Scinder, Fusionner, Supprimer)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("scene_edit_${scene.orderIndex}")) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onSplit, modifier = Modifier.testTag("scene_split_${scene.orderIndex}")) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Scinder la scène",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (index < totalCount - 1) {
                        IconButton(onClick = onMergeWithNext, modifier = Modifier.testTag("scene_merge_${scene.orderIndex}")) {
                            Icon(
                                imageVector = Icons.Default.Merge,
                                contentDescription = "Fusionner avec la suivante",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.testTag("scene_delete_${scene.orderIndex}")) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStoryboardState(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Aucune scène dans le Storyboard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Veuillez d'abord générer le script du Chapitre 1 (Phase 10) pour obtenir les scènes initiales.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateBack) {
            Text("Aller à la Phase Chapitre")
        }
    }
}

@Composable
private fun EditSceneDialog(
    scene: SceneEntity,
    onDismiss: () -> Unit,
    onConfirm: (SceneEntity) -> Unit
) {
    var title by remember { mutableStateOf(scene.title) }
    var location by remember { mutableStateOf(scene.locationName) }
    var characters by remember { mutableStateOf(scene.charactersPresent) }
    var atmosphere by remember { mutableStateOf(scene.atmosphere) }
    var summary by remember { mutableStateOf(scene.narrativeSummary) }
    var beats by remember { mutableStateOf(scene.narrativeBeats) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la Scène ${scene.orderIndex}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de la scène") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_scene_title")
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lieu") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = characters,
                    onValueChange = { characters = it },
                    label = { Text("Personnages présents") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = atmosphere,
                    onValueChange = { atmosphere = it },
                    label = { Text("Ambiance") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Résumé narratif") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("edit_scene_summary")
                )
                OutlinedTextField(
                    value = beats,
                    onValueChange = { beats = it },
                    label = { Text("Micro-beats narratifs") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        scene.copy(
                            title = title,
                            locationName = location,
                            charactersPresent = characters,
                            atmosphere = atmosphere,
                            narrativeSummary = summary,
                            narrativeBeats = beats
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_edit_scene")
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

@Composable
private fun SplitSceneDialog(
    scene: SceneEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var splitTitle by remember { mutableStateOf("${scene.title} (Partie 2)") }
    var splitSummary by remember { mutableStateOf("") }
    var splitBeats by remember { mutableStateOf("") }
    var splitCharacters by remember { mutableStateOf(scene.charactersPresent) }
    var splitAtmosphere by remember { mutableStateOf(scene.atmosphere) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scinder la Scène ${scene.orderIndex}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Créer une nouvelle scène qui succédera immédiatement à la scène actuelle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = splitTitle,
                    onValueChange = { splitTitle = it },
                    label = { Text("Titre de la 2e partie") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = splitSummary,
                    onValueChange = { splitSummary = it },
                    label = { Text("Résumé narratif (Partie 2)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = splitBeats,
                    onValueChange = { splitBeats = it },
                    label = { Text("Micro-beats (Partie 2)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (splitSummary.isNotBlank()) {
                        onConfirm(splitTitle, splitSummary, splitBeats, splitCharacters, splitAtmosphere)
                    }
                },
                enabled = splitSummary.isNotBlank()
            ) {
                Text("Scinder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun MergeScenesDialog(
    firstScene: SceneEntity,
    secondScene: SceneEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fusionner deux scènes ?") },
        text = {
            Text("Voulez-vous fusionner la Scène ${firstScene.orderIndex} (\"${firstScene.title}\") avec la Scène ${secondScene.orderIndex} (\"${secondScene.title}\") ? Les résumés et micro-beats seront combinés.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Fusionner")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun DeleteSceneDialog(
    scene: SceneEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer la Scène ${scene.orderIndex} ?") },
        text = {
            Text("Êtes-vous sûr de vouloir supprimer la scène \"${scene.title}\" ? Cette action réordonnera les scènes suivantes.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

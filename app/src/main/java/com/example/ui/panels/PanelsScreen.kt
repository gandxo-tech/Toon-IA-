package com.example.ui.panels

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.SceneEntity
import java.io.File

/**
 * Écran pour la Phase 12 : Découpage des Scènes en Panels (cases de webtoon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDialogues: () -> Unit = {},
    onNavigateToCanonMemory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PanelsViewModel = viewModel(
        factory = PanelsViewModel.provideFactory(projectId, LocalContext.current)
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var panelToEdit by remember { mutableStateOf<PanelEntity?>(null) }
    var panelToDelete by remember { mutableStateOf<PanelEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Phase 12 : Panels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.chapter?.let { "Chapitre ${it.number} : ${it.title}" }
                                ?: "Découpage des cases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("panels_back_button")
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
                        modifier = Modifier.testTag("panels_canon_button")
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
                            onClick = { viewModel.validatePanelsToCanon() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("validate_panels_canon_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isValidatedInCanon)
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.primary
                            ),
                            enabled = uiState.panels.isNotEmpty() && !uiState.isGeneratingAi
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isValidatedInCanon) "Panels Canon ✓" else "Valider dans le Canon")
                        }

                        // Bouton vers Dialogues (Phase 13)
                        Button(
                            onClick = onNavigateToDialogues,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("panels_to_dialogues_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            enabled = uiState.panels.isNotEmpty()
                        ) {
                            Text("Dialogues (Phase 13)")
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

                uiState.scenes.isEmpty() -> {
                    EmptyPanelsState(onNavigateBack = onNavigateBack)
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

                        // Carte d'actions globales (Générer tous les panels)
                        item {
                            PanelsGlobalActionCard(
                                totalPanelsCount = uiState.panels.size,
                                scenesCount = uiState.scenes.size,
                                isGeneratingAi = uiState.isGeneratingAi,
                                onGenerateAll = { viewModel.generatePanelsForWholeChapter() }
                            )
                        }

                        // Filtres par Scène
                        item {
                            SceneFilterChips(
                                scenes = uiState.scenes,
                                selectedSceneId = uiState.selectedSceneId,
                                onSelectScene = { viewModel.selectSceneFilter(it) }
                            )
                        }

                        // Liste des panels regroupés par scène
                        val displayedScenes = if (uiState.selectedSceneId != null) {
                            uiState.scenes.filter { it.id == uiState.selectedSceneId }
                        } else {
                            uiState.scenes
                        }

                        displayedScenes.forEach { scene ->
                            val scenePanels = uiState.panels
                                .filter { it.sceneId == scene.id }
                                .sortedBy { it.orderIndex }

                            item(key = "header_${scene.id}") {
                                SceneSectionHeader(
                                    scene = scene,
                                    panelsCount = scenePanels.size,
                                    isGenerating = uiState.isGeneratingAi && uiState.generatingSceneId == scene.id,
                                    isGeneratingImages = uiState.isGeneratingImage,
                                    onGenerateScenePanels = { viewModel.generatePanelsForScene(scene) },
                                    onGenerateSceneImages = { viewModel.generateAllPanelImagesForScene(context, scene.id) },
                                    onAddManualPanel = {
                                        val lastOrder = scenePanels.maxOfOrNull { it.orderIndex } ?: 0
                                        viewModel.addManualPanel(scene.id, lastOrder)
                                    }
                                )
                            }

                            if (scenePanels.isEmpty()) {
                                item(key = "empty_${scene.id}") {
                                    EmptyScenePanelsCard(
                                        scene = scene,
                                        isGenerating = uiState.isGeneratingAi && uiState.generatingSceneId == scene.id,
                                        onGenerate = { viewModel.generatePanelsForScene(scene) }
                                    )
                                }
                            } else {
                                itemsIndexed(scenePanels, key = { _, panel -> panel.id }) { index, panel ->
                                    PanelDetailCard(
                                        panel = panel,
                                        index = index,
                                        totalInScene = scenePanels.size,
                                        isGeneratingImage = uiState.isGeneratingImage && (uiState.generatingImagePanelId == panel.id || uiState.generatingImagePanelId == null),
                                        onGenerateImage = { viewModel.generatePanelImage(context, panel.id) },
                                        onMoveUp = { viewModel.movePanel(panel, directionUp = true) },
                                        onMoveDown = { viewModel.movePanel(panel, directionUp = false) },
                                        onEdit = { panelToEdit = panel },
                                        onAddAfter = { viewModel.addManualPanel(panel.sceneId, panel.orderIndex) },
                                        onDelete = { panelToDelete = panel }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue d'édition d'un panel
    panelToEdit?.let { panel ->
        EditPanelDialog(
            panel = panel,
            onDismiss = { panelToEdit = null },
            onConfirm = { updatedPanel ->
                viewModel.updatePanel(updatedPanel)
                panelToEdit = null
            }
        )
    }

    // Dialogue de suppression d'un panel
    panelToDelete?.let { panel ->
        DeletePanelDialog(
            panel = panel,
            onDismiss = { panelToDelete = null },
            onConfirm = {
                viewModel.deletePanel(panel)
                panelToDelete = null
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
            modifier = Modifier.fillMaxWidth().testTag("panels_error_banner")
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
            modifier = Modifier.fillMaxWidth().testTag("panels_success_banner")
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
private fun PanelsGlobalActionCard(
    totalPanelsCount: Int,
    scenesCount: Int,
    isGeneratingAi: Boolean,
    onGenerateAll: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("panels_global_card"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Découpage en Cases (Panels)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalPanelsCount cases créées réparties sur $scenesCount scènes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onGenerateAll,
                enabled = !isGeneratingAi,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_all_panels_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isGeneratingAi) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Découpage IA en cours...")
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (totalPanelsCount > 0) "Régénérer tous les panels du chapitre (IA)" else "Découper tout le chapitre en panels (IA)")
                }
            }
        }
    }
}

@Composable
private fun SceneFilterChips(
    scenes: List<SceneEntity>,
    selectedSceneId: String?,
    onSelectScene: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedSceneId == null,
                onClick = { onSelectScene(null) },
                label = { Text("Toutes les scènes") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
        items(scenes) { scene ->
            FilterChip(
                selected = selectedSceneId == scene.id,
                onClick = { onSelectScene(scene.id) },
                label = { Text("Scène ${scene.orderIndex} : ${scene.title}") }
            )
        }
    }
}

@Composable
private fun SceneSectionHeader(
    scene: SceneEntity,
    panelsCount: Int,
    isGenerating: Boolean,
    isGeneratingImages: Boolean,
    onGenerateScenePanels: () -> Unit,
    onGenerateSceneImages: () -> Unit,
    onAddManualPanel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Scène ${scene.orderIndex} : ${scene.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$panelsCount panels",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (panelsCount > 0) {
                OutlinedButton(
                    onClick = onGenerateSceneImages,
                    enabled = !isGenerating && !isGeneratingImages,
                    modifier = Modifier.testTag("generate_scene_${scene.orderIndex}_images")
                ) {
                    if (isGeneratingImages) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Illustrer (IA)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            OutlinedButton(
                onClick = onGenerateScenePanels,
                enabled = !isGenerating && !isGeneratingImages,
                modifier = Modifier.testTag("generate_scene_${scene.orderIndex}_panels")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Découper (IA)", style = MaterialTheme.typography.labelSmall)
                }
            }

            IconButton(onClick = onAddManualPanel) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter panel")
            }
        }
    }
}

@Composable
private fun EmptyScenePanelsCard(
    scene: SceneEntity,
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Aucun panel découpé pour cette scène.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onGenerate,
                enabled = !isGenerating
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Générer les panels de cette scène")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelDetailCard(
    panel: PanelEntity,
    index: Int,
    totalInScene: Int,
    isGeneratingImage: Boolean,
    onGenerateImage: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onAddAfter: () -> Unit,
    onDelete: () -> Unit
) {
    val hasImage = !panel.localImagePath.isNullOrBlank() && File(panel.localImagePath).exists()

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_card_${panel.orderIndex}"),
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
            // Illustration du panel si déjà générée (Phase 14)
            if (hasImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = File(panel.localImagePath!!),
                        contentDescription = "Illustration du panel ${panel.orderIndex}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Badge de cohérence des personnages
                    if (panel.charactersPresent.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 12.dp, topEnd = 0.dp, bottomStart = 8.dp),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Réf. : ${panel.charactersPresent}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Barre supérieure : Numéro de Panel, Type de Cadrage, Onomatopée, Boutons de déplacement
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Panel ${panel.orderIndex}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (panel.cameraAngle.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = panel.cameraAngle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    panel.soundEffects?.takeIf { it.isNotBlank() }?.let { sfx ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sfx,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Boutons Monter / Descendre
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp).testTag("panel_up_${panel.orderIndex}")
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalInScene - 1,
                        modifier = Modifier.size(32.dp).testTag("panel_down_${panel.orderIndex}")
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                    }
                }
            }

            // Description d'action principale
            Text(
                text = panel.actionDescription.ifBlank { panel.visualDescription },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Chips informatifs : Personnages, Émotion, Lieu, Effets visuels
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (panel.charactersPresent.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(panel.charactersPresent, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(imageVector = Icons.Default.People, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                if (panel.dominantEmotion.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(panel.dominantEmotion, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(imageVector = Icons.Default.Mood, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                if (panel.location.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(panel.location, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                if (panel.lightingAndAtmosphere.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(panel.lightingAndAtmosphere, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            // Composition & Arrière-plan
            if (panel.composition.isNotBlank() || panel.backgroundDetails.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (panel.composition.isNotBlank()) {
                            Text(
                                text = "📐 Cadrage & Composition : ${panel.composition}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (panel.backgroundDetails.isNotBlank()) {
                            Text(
                                text = "🏞️ Arrière-plan : ${panel.backgroundDetails}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (panel.visualEffects.isNotBlank()) {
                            Text(
                                text = "✨ Effets visuels : ${panel.visualEffects}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Prompt Visuel Autonome (Phase 14 - Zéro texte incrusté)
            if (panel.visualPrompt.isNotBlank()) {
                OutlinedCard(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Prompt visuel IA (sans texte incrusté) :",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = panel.visualPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Notes de continuité
            if (panel.continuityNotes.isNotBlank()) {
                Text(
                    text = "🔗 Continuité : ${panel.continuityNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Boutons d'actions : Génération visuelle (Phase 14) + Modification + Suppression
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton génération d'image indépendante pour ce panel
                OutlinedButton(
                    onClick = onGenerateImage,
                    enabled = !isGeneratingImage,
                    modifier = Modifier.testTag("generate_image_panel_${panel.orderIndex}")
                ) {
                    if (isGeneratingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Génération...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(
                            imageVector = if (hasImage) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (hasImage) "Régénérer l'illustration" else "Générer l'illustration IA",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("panel_edit_${panel.orderIndex}")) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onAddAfter, modifier = Modifier.testTag("panel_add_after_${panel.orderIndex}")) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter un panel après",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.testTag("panel_delete_${panel.orderIndex}")) {
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
private fun EmptyPanelsState(onNavigateBack: () -> Unit) {
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
            text = "Aucune scène disponible pour le découpage",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Veuillez d'abord structurer le Storyboard (Phase 11) avec vos scènes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateBack) {
            Text("Aller au Storyboard")
        }
    }
}

@Composable
private fun EditPanelDialog(
    panel: PanelEntity,
    onDismiss: () -> Unit,
    onConfirm: (PanelEntity) -> Unit
) {
    var cameraAngle by remember { mutableStateOf(panel.cameraAngle) }
    var actionDescription by remember { mutableStateOf(panel.actionDescription) }
    var charactersPresent by remember { mutableStateOf(panel.charactersPresent) }
    var location by remember { mutableStateOf(panel.location) }
    var dominantEmotion by remember { mutableStateOf(panel.dominantEmotion) }
    var composition by remember { mutableStateOf(panel.composition) }
    var lightingAndAtmosphere by remember { mutableStateOf(panel.lightingAndAtmosphere) }
    var backgroundDetails by remember { mutableStateOf(panel.backgroundDetails) }
    var visualEffects by remember { mutableStateOf(panel.visualEffects) }
    var visualPrompt by remember { mutableStateOf(panel.visualPrompt) }
    var continuityNotes by remember { mutableStateOf(panel.continuityNotes) }
    var soundEffects by remember { mutableStateOf(panel.soundEffects ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le Panel ${panel.orderIndex}") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = actionDescription,
                        onValueChange = { actionDescription = it },
                        label = { Text("Action principale") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("edit_panel_action")
                    )
                }
                item {
                    OutlinedTextField(
                        value = cameraAngle,
                        onValueChange = { cameraAngle = it },
                        label = { Text("Angle / Type de plan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = charactersPresent,
                        onValueChange = { charactersPresent = it },
                        label = { Text("Personnages & états") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Lieu & cadrage décor") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = dominantEmotion,
                        onValueChange = { dominantEmotion = it },
                        label = { Text("Émotion dominante") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = composition,
                        onValueChange = { composition = it },
                        label = { Text("Composition") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = lightingAndAtmosphere,
                        onValueChange = { lightingAndAtmosphere = it },
                        label = { Text("Lumière & Ambiance") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = backgroundDetails,
                        onValueChange = { backgroundDetails = it },
                        label = { Text("Arrière-plan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = visualEffects,
                        onValueChange = { visualEffects = it },
                        label = { Text("Effets visuels") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = visualPrompt,
                        onValueChange = { visualPrompt = it },
                        label = { Text("Prompt visuel (Phase 14)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = continuityNotes,
                        onValueChange = { continuityNotes = it },
                        label = { Text("Notes de continuité") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = soundEffects,
                        onValueChange = { soundEffects = it },
                        label = { Text("Onomatopée (ex: BAM)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        panel.copy(
                            cameraAngle = cameraAngle,
                            actionDescription = actionDescription,
                            visualDescription = actionDescription,
                            charactersPresent = charactersPresent,
                            location = location,
                            dominantEmotion = dominantEmotion,
                            composition = composition,
                            lightingAndAtmosphere = lightingAndAtmosphere,
                            backgroundDetails = backgroundDetails,
                            visualEffects = visualEffects,
                            visualPrompt = visualPrompt,
                            continuityNotes = continuityNotes,
                            soundEffects = soundEffects.takeIf { it.isNotBlank() }
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_edit_panel")
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
private fun DeletePanelDialog(
    panel: PanelEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer le Panel ${panel.orderIndex} ?") },
        text = {
            Text("Êtes-vous sûr de vouloir supprimer cette case ? L'ordre des cases suivantes sera mis à jour.")
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

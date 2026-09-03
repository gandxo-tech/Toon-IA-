package com.example.ui.chapter

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
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
import com.example.ai.GeminiChapterScriptGenerator
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonContradictionDetector
import com.example.domain.canon.Contradiction
import com.example.domain.usecase.ChapterScriptUseCase

/**
 * Écran pour la Phase 10 : Génération et édition du script du Chapitre 1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToStoryboard: () -> Unit = {},
    onNavigateToCanonMemory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChapterViewModel = createDefaultChapterViewModel(projectId)
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_chapter_script"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chapitre 1 — Script",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Phase 10 • Découpage narratif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_chapter_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToCanonMemory,
                        modifier = Modifier.testTag("btn_chapter_to_canon")
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Mémoire Canon")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState.chapter != null) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                viewModel.validateChapter()
                                onNavigateToStoryboard()
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Tout valider & Continuer vers Storyboard", modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bannière d'état Canon / Brouillon
            item {
                CanonDraftNoticeBanner()
            }

            // Message d'erreur
            if (!uiState.errorMessage.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().testTag("banner_chapter_error")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearMessages() }) { Text("Fermer") }
                        }
                    }
                }
            }

            // Message de succès
            if (!uiState.successMessage.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().testTag("banner_chapter_success")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.successMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") }
                        }
                    }
                }
            }

            // Section 1 : Orientation utilisateur & Génération IA
            item {
                ChapterGenerationSection(
                    userOrientation = uiState.userOrientation,
                    isGenerating = uiState.isGenerating,
                    hasExistingScript = uiState.chapter != null,
                    onOrientationChanged = { viewModel.updateOrientation(it) },
                    onGenerate = { viewModel.generateChapterScript() }
                )
            }

            // Section 2 : Aperçu & Édition du Chapitre
            if (uiState.chapter != null) {
                item {
                    ChapterHeaderCard(
                        chapter = uiState.chapter!!,
                        onTitleChanged = { viewModel.updateChapterTitle(it) },
                        onSynopsisChanged = { viewModel.updateChapterSynopsis(it) }
                    )
                }

                // Section 3 : Liste des Scènes découpées
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Découpage en Scènes (${uiState.scenes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Brouillon modifiable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                items(uiState.scenes, key = { it.id }) { scene ->
                    SceneCard(
                        scene = scene,
                        onEditClicked = { viewModel.startEditingScene(scene) }
                    )
                }

                // Section 4 : Actions de navigation vers le Storyboard
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().testTag("section_chapter_actions")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Étape suivante : Le Storyboard",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Une fois les scènes du Chapitre 1 validées ou ajustées, vous pourrez générer les vignettes et le découpage vertical des cases (panels).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.generateChapterScript() },
                                    enabled = !uiState.isGenerating,
                                    modifier = Modifier.weight(1f).testTag("btn_regenerate_script")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Régénérer")
                                }
                                Button(
                                    onClick = onNavigateToStoryboard,
                                    modifier = Modifier.weight(1.4f).testTag("btn_continue_to_storyboard")
                                ) {
                                    Text("Storyboard")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.validateChapter() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.fillMaxWidth().testTag("btn_validate_chapter")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.chapter?.isCanon == true) "Chapitre validé (Canon)" else "Valider le chapitre (Mise à jour du Canon)")
                            }
                        }
                    }
                }
            } else if (!uiState.isGenerating) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucun script généré pour le Chapitre 1",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cliquez sur « Générer le script du Chapitre 1 » ci-dessus pour que Gemini écrive le scénario complet en s'appuyant sur l'univers, les personnages et les règles du Canon.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialogue d'édition d'une scène
    if (uiState.editingScene != null) {
        SceneEditDialog(
            scene = uiState.editingScene!!,
            onDismiss = { viewModel.cancelEditingScene() },
            onSave = { updated -> viewModel.saveSceneEdit(updated) }
        )
    }

    // Dialogue d'alerte en cas de contradiction avec le Canon
    if (uiState.showContradictionDialog && uiState.activeContradictions.isNotEmpty()) {
        ChapterContradictionDialog(
            contradictions = uiState.activeContradictions,
            onDismiss = { viewModel.dismissContradictionDialog() }
        )
    }
}

/**
 * Notice informant que le script est un brouillon et n'est pas figé dans le canon.
 */
@Composable
private fun CanonDraftNoticeBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Phase 10 : Le script généré constitue un brouillon dynamique de travail. Il n'entre pas de force dans le Canon et reste modifiable à tout moment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section d'orientation et de génération du script IA.
 */
@Composable
private fun ChapterGenerationSection(
    userOrientation: String,
    isGenerating: Boolean,
    hasExistingScript: Boolean,
    onOrientationChanged: (String) -> Unit,
    onGenerate: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("card_chapter_generation")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Orientation Narrative (Optionnel)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Indiquez vos volontés spécifiques pour ce premier chapitre (accroche d'ouverture, focus sur un personnage, ton humoristique, etc.).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = userOrientation,
                onValueChange = onOrientationChanged,
                placeholder = { Text("Ex: Commencer immédiatement par la rencontre tendue au café avant de révéler le pouvoir...") },
                modifier = Modifier.fillMaxWidth().testTag("input_chapter_orientation"),
                minLines = 2,
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isGenerating) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Génération du script par Gemini (gemini-3.5-flash) en cours...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().testTag("btn_generate_chapter_script")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasExistingScript) "Régénérer le script du Chapitre 1 (IA)" else "Générer le script du Chapitre 1 (IA)")
                }
            }
        }
    }
}

/**
 * Carte d'en-tête du Chapitre (Titre et Synopsis éditables).
 */
@Composable
private fun ChapterHeaderCard(
    chapter: ChapterEntity,
    onTitleChanged: (String) -> Unit,
    onSynopsisChanged: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("card_chapter_header")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "CHAPITRE 1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Brouillon actif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = chapter.title,
                onValueChange = onTitleChanged,
                label = { Text("Titre du Chapitre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_chapter_title")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = chapter.synopsis,
                onValueChange = onSynopsisChanged,
                label = { Text("Synopsis du Chapitre") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("input_chapter_synopsis")
            )
        }
    }
}

/**
 * Carte représentant une scène individuelle du chapitre avec ses métadonnées.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneCard(
    scene: SceneEntity,
    onEditClicked: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("card_scene_${scene.orderIndex}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Scène ${scene.orderIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = scene.title.ifBlank { "Sans titre" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onEditClicked,
                    modifier = Modifier.size(32.dp).testTag("btn_edit_scene_${scene.orderIndex}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifier la scène",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = scene.narrativeSummary.ifBlank { "Aucune description narrée." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Dialogue d'édition d'une scène.
 */
@Composable
private fun SceneEditDialog(
    scene: SceneEntity,
    onDismiss: () -> Unit,
    onSave: (SceneEntity) -> Unit
) {
    var title by remember { mutableStateOf(scene.title) }
    var narrativeSummary by remember { mutableStateOf(scene.narrativeSummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Modifier la Scène ${scene.orderIndex}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de la scène") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_scene_title")
                )

                OutlinedTextField(
                    value = narrativeSummary,
                    onValueChange = { narrativeSummary = it },
                    label = { Text("Script et déroulement narratif de la scène") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_scene_summary")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        scene.copy(
                            title = title.trim(),
                            narrativeSummary = narrativeSummary.trim()
                        )
                    )
                },
                modifier = Modifier.testTag("dialog_save_scene")
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

/**
 * Dialogue signalant une contradiction détectée avec le Canon.
 */
@Composable
private fun ChapterContradictionDialog(
    contradictions: List<Contradiction>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text(
                text = "Attention : Incohérences avec le Canon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Le scénario généré présente des points en tension avec les éléments validés dans votre CanonMemory :",
                    style = MaterialTheme.typography.bodySmall
                )
                contradictions.forEach { contradiction ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Élément concerné : ${contradiction.fieldName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = contradiction.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Text(
                    text = "Ces scènes restent en brouillon. Vous pouvez les ajuster à l'aide du bouton d'édition.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("J'ai compris")
            }
        }
    )
}

@Composable
fun createDefaultChapterViewModel(projectId: String): ChapterViewModel {
    val context = LocalContext.current
    val db = ToonDatabase.getInstance(context)
    val canonRepo = CanonMemoryRepositoryImpl(db)
    val detector = CanonContradictionDetector()
    val generator = GeminiChapterScriptGenerator(apiKeyOverride = ApiKeyManager.getApiKey(context))
    val useCase = ChapterScriptUseCase(
        projectDao = db.projectDao(),
        chapterDao = db.chapterDao(),
        sceneDao = db.sceneDao(),
        locationDao = db.locationDao(),
        characterDao = db.characterDao(),
        jobDao = db.generationJobDao(),
        canonRepository = canonRepo,
        contradictionDetector = detector,
        generator = generator
    )
    return viewModel(factory = ChapterViewModel.Factory(useCase, projectId))
}

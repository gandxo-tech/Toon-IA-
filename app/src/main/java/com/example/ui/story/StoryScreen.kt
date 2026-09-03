package com.example.ui.story

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.StoryBibleResult
import com.example.domain.model.StoryTimelineEventEntry

/**
 * Écran Story Bible / Histoire de ToonIA (Phase 6).
 * Permet :
 * - Génération IA de la Story Bible en s'appuyant sur tout le canon (Personnages + Univers validés).
 * - Édition manuelle des sections clés : Prémisse, Genre, Thèmes, Conflits, Intrigues, Direction générale.
 * - Validation de la Bible au CanonMemory.
 * - Validation des TimelineEvents (événements fondateurs antérieurs au chapitre 1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToVisualHumor: (() -> Unit)? = null,
    viewModel: StoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank()) {
            viewModel.initializeProject(projectId)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_story_bible"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Story Bible (Histoire)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("story_title")
                        )
                        Text(
                            text = "Phase 6 : Prémisse, thèmes, intrigues & chronologie fondatrice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_story")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showAddEventDialog(true) },
                        modifier = Modifier.testTag("btn_add_event_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter un événement",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (onNavigateToVisualHumor != null) {
                        IconButton(
                            onClick = onNavigateToVisualHumor,
                            modifier = Modifier.testTag("btn_go_visual_humor")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Direction Artistique & Humour",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (state.storyBible != null || state.storyBible?.timelineEvents?.isNotEmpty() == true) {
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
                                viewModel.validateAllStory()
                                onNavigateToVisualHumor?.invoke()
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Tout valider & Continuer vers la Dir. Artistique", modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Bandeau de feedback
            state.generalFeedback?.let { feedback ->
                FeedbackBanner(message = feedback, onDismiss = { viewModel.dismissFeedback() })
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Carte d'action globale
                item {
                    GlobalStoryActionCard(
                        status = state.status,
                        charactersCount = state.canonicalCharactersCount,
                        hasCanonicalWorld = state.hasCanonicalWorld,
                        isStoryValidated = state.storyBible?.isValidatedInCanon == true,
                        onGenerateStory = { viewModel.generateStoryBible() },
                        onValidateStory = { viewModel.validateFullStoryBible() },
                        onAddEvent = { viewModel.showAddEventDialog(true) }
                    )
                }

                val story = state.storyBible
                if (story == null && state.status !is StoryGenerationUiStatus.Running) {
                    item {
                        EmptyStoryPlaceholder(
                            onGenerateClicked = { viewModel.generateStoryBible() }
                        )
                    }
                } else if (story != null) {
                    // 1. Prémisse dramatique
                    item {
                        StorySectionCard(
                            title = "Prémisse Dramatique",
                            subtitle = "Le pitch central et le point de départ du récit",
                            content = story.premise,
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(premise = updated) }
                            }
                        )
                    }

                    // 2. Genre confirmé & Tonalité
                    item {
                        StorySectionCard(
                            title = "Genre Confirmé & Ton Général",
                            subtitle = "Format webtoon consolidé et couleur émotionnelle",
                            content = "Genre : ${story.confirmedGenre}\nTon : ${story.generalTone}",
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(generalTone = updated) }
                            }
                        )
                    }

                    // 3. Thèmes principaux
                    item {
                        StorySectionCard(
                            title = "Thématiques Fondamentales",
                            subtitle = "Les idées et dilemmes moraux explorés à travers l'œuvre",
                            content = story.mainThemes,
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(mainThemes = updated) }
                            }
                        )
                    }

                    // 4. Conflits principaux
                    item {
                        StorySectionCard(
                            title = "Conflits Majeurs",
                            subtitle = "Forces antagonistes, tensions relationnelles et luttes internes",
                            content = story.mainConflicts,
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(mainConflicts = updated) }
                            }
                        )
                    }

                    // 5. Intrigue principale (Arc global)
                    item {
                        StorySectionCard(
                            title = "Intrigue Principale",
                            subtitle = "L'arc narratif majeur et l'évolution de la tension",
                            content = story.mainPlot,
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(mainPlot = updated) }
                            }
                        )
                    }

                    // 6. Intrigues secondaires (Subplots)
                    item {
                        StorySectionCard(
                            title = "Intrigues Secondaires",
                            subtitle = "Sous-intrigues enrichissant les relations et le lore",
                            content = story.subPlots,
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(subPlots = updated) }
                            }
                        )
                    }

                    // 7. Direction générale du récit
                    item {
                        StorySectionCard(
                            title = "Direction Générale & Climax",
                            subtitle = "La résolution ou finalité dramatique vers laquelle converge l'histoire",
                            content = story.narrativeDirection,
                            onContentChange = { updated ->
                                viewModel.updateStoryBibleField { it.copy(narrativeDirection = updated) }
                            }
                        )
                    }

                    // 8. Événements fondateurs antérieurs au chapitre 1 (Chronologie)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chronologie Fondatrice (${story.timelineEvents.size} faits)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Antérieurs au Chapitre 1",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    items(story.timelineEvents.size) { index ->
                        val event = story.timelineEvents[index]
                        TimelineEventCard(
                            event = event,
                            onFieldChange = { updated ->
                                val updatedList = story.timelineEvents.toMutableList().also { it[index] = updated }
                                viewModel.updateStoryBibleField { it.copy(timelineEvents = updatedList) }
                            },
                            onValidate = { viewModel.validateTimelineEvent(event.id) }
                        )
                    }
                }
            }
        }
    }

    // Dialogue d'ajout manuel d'événement
    if (state.showAddManualEventDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showAddEventDialog(false) },
            title = { Text("Ajouter un événement fondateur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.manualEventTitle,
                        onValueChange = { viewModel.updateManualEventTitle(it) },
                        label = { Text("Titre de l'événement") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.manualEventEpoch,
                        onValueChange = { viewModel.updateManualEventEpoch(it) },
                        label = { Text("Époque / Moment relatif (ex: Il y a 10 ans)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAddManualEvent() },
                    enabled = state.manualEventTitle.isNotBlank()
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAddEventDialog(false) }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialogue d'alerte de contradiction
    state.activeContradictionTarget?.let { (targetName, contradictions) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissContradictionDialog() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Contradiction détectée avec le Canon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("L'élément '$targetName' présente une divergence avec le canon actuel :")
                    contradictions.forEach { c ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = c.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(text = "Canon : ${c.canonicalValue}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.validateFullStoryBible(forceIgnoreContradictions = true)
                    }
                ) {
                    Text("Forcer l'inscription au Canon")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissContradictionDialog() }) {
                    Text("Corriger")
                }
            }
        )
    }
}

@Composable
private fun GlobalStoryActionCard(
    status: StoryGenerationUiStatus,
    charactersCount: Int,
    hasCanonicalWorld: Boolean,
    isStoryValidated: Boolean,
    onGenerateStory: () -> Unit,
    onValidateStory: () -> Unit,
    onAddEvent: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_story_action"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Construction de l'Histoire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Contexte canon : $charactersCount personnage(s), univers ${if (hasCanonicalWorld) "validé" else "en attente"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status is StoryGenerationUiStatus.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else if (isStoryValidated) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CANON VALIDÉ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateStory,
                    enabled = status !is StoryGenerationUiStatus.Running,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_generate_story")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Générer Histoire IA")
                }

                Button(
                    onClick = onValidateStory,
                    enabled = !isStoryValidated,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Valider Histoire")
                }

                OutlinedButton(
                    onClick = onAddEvent,
                    modifier = Modifier.weight(0.8f)
                ) {
                    Text("+ Événement")
                }
            }
        }
    }
}

@Composable
private fun StorySectionCard(
    title: String,
    subtitle: String,
    content: String,
    onContentChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: StoryTimelineEventEntry,
    onFieldChange: (StoryTimelineEventEntry) -> Unit,
    onValidate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (event.isValidatedInCanon) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#${event.orderIndex} ${event.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (event.isValidatedInCanon) {
                    Text("Canon", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedButton(onClick = onValidate, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Valider", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (event.dateOrEpoch.isNotBlank()) {
                Text(
                    text = "Époque : ${event.dateOrEpoch}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = event.description,
                onValueChange = { onFieldChange(event.copy(description = it)) },
                label = { Text("Description de l'événement") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}

@Composable
private fun EmptyStoryPlaceholder(onGenerateClicked: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
            Text("L'histoire et les intrigues n'ont pas encore été écrites.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Générez la Story Bible pour structurer la prémisse, les thèmes, les conflits et la chronologie.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGenerateClicked) {
                Text("Générer l'histoire maintenant")
            }
        }
    }
}

@Composable
private fun FeedbackBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(14.dp))
            }
        }
    }
}

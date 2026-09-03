package com.example.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.domain.model.AdvancedIdeaDetails
import com.example.domain.model.IdeaAnalysisResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    modifier: Modifier = Modifier,
    viewModel: NewProjectViewModel = viewModel(),
    onNavigateToCharacters: (projectId: String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Nouveau Projet Webtoon",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Étape 1 : Saisie de l'idée & Analyse IA",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.testTag("new_project_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Titre du projet
            OutlinedTextField(
                value = state.projectTitle,
                onValueChange = { viewModel.updateProjectTitle(it) },
                label = { Text("Titre de travail du webtoon") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_title_input")
            )

            // Boutons de tests rapides (Idée vague vs Idée détaillée)
            PresetsSection(
                onVagueClick = { viewModel.fillSampleVagueIdea() },
                onDetailedClick = { viewModel.fillSampleDetailedIdea() }
            )

            // Grande zone de texte libre
            IdeaInputSection(
                ideaText = state.ideaText,
                onTextChange = { viewModel.updateIdeaText(it) }
            )

            // Mode "détails avancés" optionnel et repliable
            AdvancedDetailsSection(
                isExpanded = state.isAdvancedDetailsExpanded,
                details = state.advancedDetails,
                onToggle = { viewModel.toggleAdvancedDetails() },
                onDetailsChange = { viewModel.updateAdvancedDetails(it) }
            )

            // Bouton de lancement de l'analyse
            Button(
                onClick = { viewModel.analyzeIdea() },
                enabled = state.ideaText.isNotBlank() && state.status !is AnalysisUiStatus.Running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("analyze_idea_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.status is AnalysisUiStatus.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Analyse en cours par Gemini...")
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.editableResult != null) "Relancer l'analyse IA" else "Analyser l'idée",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Statut du GenerationJob
            state.currentJob?.let { job ->
                JobStatusCard(job = job)
            }

            // Échec explicite avec message clair et bouton Réessayer
            if (state.status is AnalysisUiStatus.Failed) {
                val failedState = state.status as AnalysisUiStatus.Failed
                ErrorJobCard(
                    errorMessage = failedState.errorMessage,
                    onRetry = { viewModel.retryAnalysis() }
                )
            }

            // Résultat de l'analyse avec modification et validation
            state.editableResult?.let { result ->
                AnalysisResultSection(
                    result = result,
                    onGenreChange = { viewModel.updateGenrePrincipal(it) },
                    onTonChange = { viewModel.updateTon(it) },
                    onAddTheme = { viewModel.addTheme(it) },
                    onRemoveTheme = { viewModel.removeTheme(it) },
                    onAddSousGenre = { viewModel.addSousGenre(it) },
                    onRemoveSousGenre = { viewModel.removeSousGenre(it) },
                    onValidateAndNext = {
                        viewModel.validateAndProceed(onNavigateToCharacters)
                    },
                    isValidated = state.isValidated
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PresetsSection(
    onVagueClick: () -> Unit,
    onDetailedClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "💡 Exemples de test rapide :",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onVagueClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_vague_button")
                ) {
                    Text("Idée vague", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onDetailedClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_detailed_button")
                ) {
                    Text("Idée détaillée", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun IdeaInputSection(
    ideaText: String,
    onTextChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Votre idée de webtoon *",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Décrivez librement votre histoire : concept, prémisse, dilemme, univers ou péripéties.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = ideaText,
            onValueChange = onTextChange,
            placeholder = {
                Text("Ex: Dans une métropole sous marine isolée du monde, une cartographe découvre que leur dôme commence à se fissurer depuis l'intérieur...")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("idea_free_text_input")
        )
    }
}

@Composable
private fun AdvancedDetailsSection(
    isExpanded: Boolean,
    details: AdvancedIdeaDetails,
    onToggle: () -> Unit,
    onDetailsChange: (AdvancedIdeaDetails) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toggle_advanced_details_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Détails avancés (facultatifs)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.testTag("toggle_advanced_details_btn")) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Replier" else "Déplier"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = details.genre,
                        onValueChange = { onDetailsChange(details.copy(genre = it)) },
                        label = { Text("Genre souhaité (ex: Dark Fantasy, Comédie romantique)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.ambiance,
                        onValueChange = { onDetailsChange(details.copy(ambiance = it)) },
                        label = { Text("Ambiance (ex: Lumineuse, Anxiogène, Mystérieuse)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.epoque,
                        onValueChange = { onDetailsChange(details.copy(epoque = it)) },
                        label = { Text("Époque / Contexte temporel (ex: Moyen Âge magique, 2099)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.personnagesSouhaites,
                        onValueChange = { onDetailsChange(details.copy(personnagesSouhaites = it)) },
                        label = { Text("Personnages souhaités (ex: Une voleuse cynique et un chevalier déchu)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.inspirations,
                        onValueChange = { onDetailsChange(details.copy(inspirations = it)) },
                        label = { Text("Inspirations / Références (ex: Solo Leveling, Lore Olympus)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.longueurVisee,
                        onValueChange = { onDetailsChange(details.copy(longueurVisee = it)) },
                        label = { Text("Longueur visée (ex: One-shot, 20 chapitres, Série longue)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.publicCible,
                        onValueChange = { onDetailsChange(details.copy(publicCible = it)) },
                        label = { Text("Public cible (ex: Shonen, Seinen, Tout public)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.contraintes,
                        onValueChange = { onDetailsChange(details.copy(contraintes = it)) },
                        label = { Text("Contraintes narratives / interdits créatifs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details.evenementsSouhaites,
                        onValueChange = { onDetailsChange(details.copy(evenementsSouhaites = it)) },
                        label = { Text("Événements souhaités (ex: Tournoi, Révélation d'un traître)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun JobStatusCard(job: GenerationJobEntity) {
    val (statusColor, statusText) = when (job.status) {
        JobStatus.QUEUED -> Pair(Color(0xFFF59E0B), "EN ATTENTE (QUEUED)")
        JobStatus.RUNNING -> Pair(Color(0xFF3B82F6), "EN COURS D'EXÉCUTION (RUNNING)")
        JobStatus.SUCCESS -> Pair(Color(0xFF10B981), "TERMINÉ AVEC SUCCÈS (SUCCESS)")
        JobStatus.FAILED -> Pair(Color(0xFFEF4444), "ÉCHEC D'EXÉCUTION (FAILED)")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("job_status_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Job [${job.type.name}] : $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = "ID: ${job.id.take(8)}... • Essais: ${job.retryCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorJobCard(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("error_job_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "L'analyse IA a échoué",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("retry_analysis_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Réessayer l'analyse")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisResultSection(
    result: IdeaAnalysisResult,
    onGenreChange: (String) -> Unit,
    onTonChange: (String) -> Unit,
    onAddTheme: (String) -> Unit,
    onRemoveTheme: (String) -> Unit,
    onAddSousGenre: (String) -> Unit,
    onRemoveSousGenre: (String) -> Unit,
    onValidateAndNext: () -> Unit,
    isValidated: Boolean
) {
    var newThemeText by remember { mutableStateOf("") }
    var newSubgenreText by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analysis_result_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Résultat de l'analyse (Modifiable)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Vous pouvez ajuster les champs détectés avant de valider pour l'étape suivante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Genre principal & Ton modifiables
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = result.genrePrincipal,
                    onValueChange = onGenreChange,
                    label = { Text("Genre principal") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_genre_principal")
                )
                OutlinedTextField(
                    value = result.ton,
                    onValueChange = onTonChange,
                    label = { Text("Ton narratif") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_ton")
                )
            }

            // Sous-genres
            Column {
                Text(
                    text = "Sous-genres identifiés",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.sousGenres.forEach { sub ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveSousGenre(sub) },
                            label = { Text(sub) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubgenreText,
                        onValueChange = { newSubgenreText = it },
                        placeholder = { Text("Ajouter un sous-genre...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newSubgenreText.isNotBlank()) {
                                onAddSousGenre(newSubgenreText.trim())
                                newSubgenreText = ""
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter")
                    }
                }
            }

            // Thèmes détectés
            Column {
                Text(
                    text = "Thèmes narratifs détectés",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.themesDetectes.forEach { theme ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveTheme(theme) },
                            label = { Text(theme) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newThemeText,
                        onValueChange = { newThemeText = it },
                        placeholder = { Text("Ajouter un thème...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newThemeText.isNotBlank()) {
                                onAddTheme(newThemeText.trim())
                                newThemeText = ""
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter")
                    }
                }
            }

            // Éléments narratifs explicites
            if (result.elementsNarratifsExplicites.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Éléments narratifs explicites fournis :",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        result.elementsNarratifsExplicites.forEach { element ->
                            Text(
                                text = "• $element",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Ambiguïtés à clarifier (si l'idée est trop vague)
            if (result.ambiguitesAClarifier.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ambiguïtés à clarifier / Points à développer :",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        result.ambiguitesAClarifier.forEachIndexed { index, question ->
                            Text(
                                text = "${index + 1}. $question",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bouton de validation pour passer à l'étape suivante
            Button(
                onClick = onValidateAndNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("validate_analysis_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isValidated) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isValidated) "Idée validée ! Passer aux personnages" else "Valider et passer à l'étape Personnages",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

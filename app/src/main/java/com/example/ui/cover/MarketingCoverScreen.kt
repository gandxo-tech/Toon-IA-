package com.example.ui.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ai.GeminiCoverImageGenerator
import com.example.ai.GeminiMarketingCoverGenerator
import com.example.util.ApiKeyManager
import com.example.data.local.ToonDatabase
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.Contradiction
import com.example.domain.model.SynopsisVariant
import com.example.domain.usecase.GenerateCoverImageUseCase
import com.example.domain.usecase.MarketingCoverUseCase
import java.io.File

/**
 * Écran pour la Phase 8 (Marketing) et Phase 9 (Illustration de Couverture).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketingCoverScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCanonMemory: () -> Unit = {},
    onNavigateToChapter: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MarketingCoverViewModel = createDefaultMarketingCoverViewModel(projectId)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_marketing_cover"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Titre, Synopsis & Couverture",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("marketing_cover_top_title")
                        )
                        Text(
                            text = "Phase 8 : Packaging Éditorial & Marketing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_marketing_cover")
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
                        modifier = Modifier.testTag("btn_top_view_canon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Mémoire Canonique"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bannière de messages
            uiState.errorMessage?.let { error ->
                item {
                    ErrorBanner(
                        message = error,
                        onDismiss = { viewModel.clearMessages() }
                    )
                }
            }

            uiState.successMessage?.let { success ->
                item {
                    SuccessBanner(
                        message = success,
                        onDismiss = { viewModel.clearMessages() }
                    )
                }
            }

            // Statut de validation canonique
            if (uiState.isCanonValidated) {
                item {
                    CanonValidatedCard()
                }
            }

            // En-tête de génération IA
            item {
                MarketingGenerationHeader(
                    isGenerating = uiState.isGenerating,
                    hasProposals = uiState.proposalsResult != null,
                    onGenerate = { viewModel.generateMarketing() }
                )
            }

            // Section 1 : Titre du Webtoon
            item {
                TitleSelectionSection(
                    selectedTitle = uiState.selectedTitle,
                    proposals = uiState.proposalsResult?.titleProposals ?: emptyList(),
                    onTitleSelected = { viewModel.selectTitle(it) },
                    onTitleChanged = { viewModel.updateTitle(it) }
                )
            }

            // Section 2 : Variantes de Synopsis
            item {
                SynopsisSelectionSection(
                    selectedSynopsis = uiState.selectedSynopsis,
                    selectedLengthType = uiState.selectedLengthType,
                    selectedTagline = uiState.selectedTagline,
                    variants = uiState.proposalsResult?.synopsisVariants ?: emptyList(),
                    onVariantSelected = { viewModel.selectSynopsisVariant(it) },
                    onSynopsisChanged = { viewModel.updateSynopsis(it) },
                    onTaglineChanged = { viewModel.updateTagline(it) }
                )
            }

            // Section 3 : Description de la Couverture Visuelle (SANS texte)
            item {
                CoverDescriptionSection(
                    visualDescription = uiState.coverVisualDescription,
                    composition = uiState.coverComposition,
                    charactersPresent = uiState.coverCharactersPresent,
                    lighting = uiState.coverLighting,
                    palette = uiState.coverPalette,
                    onDescriptionChanged = { viewModel.updateCoverDescription(it) }
                )
            }

            // Section 4 : Action de validation Canon
            item {
                CanonValidationActionSection(
                    isCanonValidated = uiState.isCanonValidated,
                    onValidate = { viewModel.validateToCanon(forceValidation = false) }
                )
            }

            // Section 5 : Phase 9 — Illustration de Couverture (IA & Superposition Native)
            item {
                CoverIllustrationSection(
                    coverImagePath = uiState.coverImagePath,
                    isGenerating = uiState.isGeneratingImage,
                    errorMessage = uiState.imageErrorMessage,
                    title = uiState.selectedTitle,
                    tagline = uiState.selectedTagline,
                    authorName = uiState.authorName,
                    hasCoverDescription = uiState.coverVisualDescription.isNotBlank(),
                    onAuthorNameChanged = { viewModel.updateAuthorName(it) },
                    onGenerateIllustration = { viewModel.generateCoverIllustration(context) },
                    onNavigateToChapter = onNavigateToChapter
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Dialogue d'alerte en cas de contradiction détectée
    if (uiState.showContradictionDialog) {
        ContradictionAlertDialog(
            contradictions = uiState.activeContradictions,
            onDismiss = { viewModel.dismissContradictionDialog() },
            onForceConfirm = { viewModel.validateToCanon(forceValidation = true) }
        )
    }
}

@Composable
private fun MarketingGenerationHeader(
    isGenerating: Boolean,
    hasProposals: Boolean,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Packaging Marketing & Visuel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Génération IA contrainte par l'intégralité du canon validé",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ToonIA conçoit 3 à 5 titres percutants, 3 variantes de synopsis (court/moyen/long) et la description visuelle détaillée de la couverture — SANS texte dans l'image.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGenerate,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_generate_marketing"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Conception marketing en cours...")
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasProposals) "Régénérer les propositions" else "Générer les propositions marketing")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleSelectionSection(
    selectedTitle: String,
    proposals: List<String>,
    onTitleSelected: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_title_selection"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Title,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "1. Titre du Webtoon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (proposals.isNotEmpty()) {
                Text(
                    text = "Propositions générées par l'IA (cliquez pour sélectionner) :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    proposals.forEachIndexed { index, title ->
                        val isSelected = title.trim().equals(selectedTitle.trim(), ignoreCase = true)
                        OutlinedCard(
                            onClick = { onTitleSelected(title) },
                            modifier = Modifier.testTag("chip_title_proposal_$index"),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = if (isSelected) {
                                    androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sélectionné",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = selectedTitle,
                onValueChange = onTitleChanged,
                label = { Text("Titre retenu (modifiable librement)") },
                placeholder = { Text("Ex: Les Chroniques de l'Éther") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_selected_title")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SynopsisSelectionSection(
    selectedSynopsis: String,
    selectedLengthType: String,
    selectedTagline: String,
    variants: List<SynopsisVariant>,
    onVariantSelected: (SynopsisVariant) -> Unit,
    onSynopsisChanged: (String) -> Unit,
    onTaglineChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_synopsis_selection"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "2. Synopsis & Accroche",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (variants.isNotEmpty()) {
                Text(
                    text = "Variantes de longueur générées :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    variants.forEachIndexed { index, variant ->
                        val isSelected = selectedLengthType.equals(variant.lengthType, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onVariantSelected(variant) },
                            label = { Text(variant.lengthType) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("chip_synopsis_variant_$index")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = selectedTagline,
                onValueChange = onTaglineChanged,
                label = { Text("Phrase d'accroche (Tagline)") },
                placeholder = { Text("Ex: Quand les étoiles s'éteignent, un paria défie le destin.") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_selected_tagline")
            )

            OutlinedTextField(
                value = selectedSynopsis,
                onValueChange = onSynopsisChanged,
                label = { Text("Texte du synopsis ($selectedLengthType - modifiable)") },
                placeholder = { Text("Rédigez ou ajustez le synopsis complet ici...") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_selected_synopsis")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverDescriptionSection(
    visualDescription: String,
    composition: String,
    charactersPresent: List<String>,
    lighting: String,
    palette: String,
    onDescriptionChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_cover_description"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "3. Description Visuelle de Couverture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Règle d'or absolue
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Règle ToonIA : ZÉRO texte dans l'illustration de couverture. Le titre sera superposé nativement par l'application.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (composition.isNotBlank() || lighting.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (composition.isNotBlank()) {
                        Text(
                            text = "Composition : $composition",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (lighting.isNotBlank()) {
                        Text(
                            text = "Lumière & Ambiance : $lighting",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (palette.isNotBlank()) {
                        Text(
                            text = "Dominantes chromatiques : $palette",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (charactersPresent.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Personnages :",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            charactersPresent.forEach { charName ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(charName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = visualDescription,
                onValueChange = onDescriptionChanged,
                label = { Text("Prompt visuel détaillé pour le générateur d'images (SANS TEXTE)") },
                placeholder = { Text("Ex: Plan vertical moyen, le protagoniste Kaelen au premier plan...") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_cover_visual_description")
            )
        }
    }
}

@Composable
private fun CanonValidationActionSection(
    isCanonValidated: Boolean,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCanonValidated) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isCanonValidated) Icons.Default.CheckCircle else Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isCanonValidated) "Validé dans le Canon" else "Validation Canonique",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (isCanonValidated) {
                    "Le titre retenu, le synopsis final et la description de couverture sont enregistrés dans la mémoire canonique du projet."
                } else {
                    "Une fois validés, le titre final et le synopsis final seront inscrits dans le projet et la mémoire canonique (CanonMemory)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onValidate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_validate_canon_main"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isCanonValidated) "Mettre à jour le Canon" else "Valider dans le Canon du Projet")
            }
        }
    }
}

@Composable
private fun CanonValidatedCard(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Statut Canon : Actif & Validé",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Les éléments marketing font partie intégrante de la mémoire canonique du projet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ContradictionAlertDialog(
    contradictions: List<Contradiction>,
    onDismiss: () -> Unit,
    onForceConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Contradiction Détectée",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "L'analyse automatique a détecté des divergences potentielles avec le canon établi (Story Bible ou Character Bible) :",
                    style = MaterialTheme.typography.bodyMedium
                )

                contradictions.forEach { contradiction ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${contradiction.targetType} : ${contradiction.targetName} (${contradiction.fieldName})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = contradiction.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "Conformément au Prompt Maître, aucune correction silencieuse n'est appliquée. Vous pouvez modifier votre sélection ou forcer l'inscription au canon si c'est un choix délibéré.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onForceConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Forcer l'inscription au canon")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Modifier mes choix")
            }
        }
    )
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    }
}

@Composable
private fun SuccessBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
fun createDefaultMarketingCoverViewModel(projectId: String): MarketingCoverViewModel {
    val context = LocalContext.current
    val db = ToonDatabase.getInstance(context)
    val canonRepo = CanonMemoryRepositoryImpl(db)
    val generator = GeminiMarketingCoverGenerator(apiKeyOverride = ApiKeyManager.getApiKey(context))
    val useCase = MarketingCoverUseCase(
        projectDao = db.projectDao(),
        coverDao = db.coverDao(),
        jobDao = db.generationJobDao(),
        canonRepository = canonRepo,
        generator = generator
    )
    val imageGenerator = GeminiCoverImageGenerator(apiKeyOverride = ApiKeyManager.getApiKey(context))
    val imageUseCase = GenerateCoverImageUseCase(
        projectDao = db.projectDao(),
        coverDao = db.coverDao(),
        visualBibleDao = db.visualBibleDao(),
        jobDao = db.generationJobDao(),
        canonRepository = canonRepo,
        imageGenerator = imageGenerator
    )
    return viewModel(factory = MarketingCoverViewModel.Factory(useCase, imageUseCase, projectId))
}

/**
 * Section Phase 9 : Illustration de Couverture générée par IA avec superposition native du texte.
 */
@Composable
private fun CoverIllustrationSection(
    coverImagePath: String?,
    isGenerating: Boolean,
    errorMessage: String?,
    title: String,
    tagline: String,
    authorName: String,
    hasCoverDescription: Boolean,
    onAuthorNameChanged: (String) -> Unit,
    onGenerateIllustration: () -> Unit,
    onNavigateToChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_cover_illustration"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Phase 9 — Illustration de Couverture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "L'IA génère une illustration pure (SANS AUCUN TEXTE). Le titre, le sous-titre et les crédits d'auteur sont superposés nativement par Compose avec une netteté parfaite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Champ de saisie pour le nom de l'auteur (crédit natif)
            OutlinedTextField(
                value = authorName,
                onValueChange = onAuthorNameChanged,
                label = { Text("Nom d'auteur / Signature (superposition native)") },
                placeholder = { Text("Ex: Studio ToonIA / Mon Pseudo") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_cover_author"),
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Erreur de génération avec bouton de relance
            if (!errorMessage.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("card_cover_image_error")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Échec de génération d'image",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onGenerateIllustration,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Réessayer la génération")
                        }
                    }
                }
            }

            // État de chargement
            if (isGenerating) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Génération de la couverture en cours...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Appel au modèle Gemini (gemini-2.5-flash-image) et sauvegarde locale...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            val imageFile = if (!coverImagePath.isNullOrBlank()) File(coverImagePath) else null
            val imageExists = imageFile != null && imageFile.exists()

            if (imageExists) {
                // Aperçu de la couverture avec superposition native
                WebtoonCoverPreview(
                    imageFile = imageFile!!,
                    title = title,
                    tagline = tagline,
                    authorName = authorName,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onGenerateIllustration,
                        enabled = !isGenerating,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_regenerate_cover_image")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Régénérer")
                    }

                    Button(
                        onClick = onNavigateToChapter,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_continue_to_chapter_1")
                    ) {
                        Text("Passer au Chapitre 1")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            } else if (!isGenerating) {
                // Pas encore d'image générée
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aucune illustration générée pour le moment",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasCoverDescription) "La description de couverture est prête. Cliquez ci-dessous pour lancer la génération de l'illustration par l'IA."
                            else "Générez ou validez d'abord une description de couverture ci-dessus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onGenerateIllustration,
                            enabled = !isGenerating && hasCoverDescription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_generate_cover_image")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Générer l'illustration de couverture (IA)")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rendu visuel d'une couverture de Webtoon (ratio vertical 3:4).
 * Affiche l'illustration brute en fond et superpose le dégradé, le badge, le titre et l'auteur
 * NATIVEMENT via Compose (Canvas / Box / Text) — respect strict de la règle "AUCUN TEXTE DANS L'IA".
 */
@Composable
private fun WebtoonCoverPreview(
    imageFile: File,
    title: String,
    tagline: String,
    authorName: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .testTag("cover_preview_card")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Image d'illustration de fond (Générée sans aucun texte par Gemini)
            AsyncImage(
                model = imageFile,
                contentDescription = "Illustration de la couverture du Webtoon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Dégradé sombre natif (scrim) pour assurer une lisibilité absolue du texte
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // 3. En-tête natif (Bandeau et badges éditoriaux)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ORIGINAL WEBTOON",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "CANON VALIDÉ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 4. Titrage et Crédits d'Auteur superposés nativement en bas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (tagline.isNotBlank()) {
                    Text(
                        text = "« $tagline »",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .testTag("cover_overlay_tagline")
                    )
                }

                Text(
                    text = title.ifBlank { "Titre du Webtoon" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("cover_overlay_title")
                )

                Spacer(modifier = Modifier.height(8.dp))

                val authorDisplay = if (authorName.isNotBlank()) "Scénario & Dessin : $authorName" else "Scénario & Dessin : Créateur"
                Text(
                    text = authorDisplay,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("cover_overlay_author")
                )
            }
        }
    }
}


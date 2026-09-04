package com.example.ui.art

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.JobStatus
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.Contradiction
import com.example.domain.model.CharacterHumorHook
import com.example.domain.model.HumorDnaResult
import com.example.domain.model.VisualBibleResult
import com.example.domain.usecase.VisualHumorUseCase

/**
 * Écran Direction Artistique & Humour (Phase 7).
 * Comprend deux onglets complémentaires :
 * 1. Visual Bible : Direction artistique, cadrages webtoon, palettes, continuité visuelle.
 * 2. Humor DNA : Registre comique, running gags, manies des personnages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualHumorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCoverMarketing: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VisualHumorViewModel = createDefaultVisualHumorViewModel(projectId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val rateLimitCountdown by com.example.ai.ApiRateLimitManager.rateLimitCountdown.collectAsState(initial = 0)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_visual_humor"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Direction Artistique & Humour",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("visual_humor_title")
                        )
                        Text(
                            text = "Phase 7 : Visual Bible & Mémoire d'Humour",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_visual_humor")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToCoverMarketing,
                        modifier = Modifier.testTag("btn_top_to_cover_marketing")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Phase 8 : Titre, Synopsis & Couverture"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState.visualBible != null || uiState.humorDna != null) {
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
                                viewModel.validateAllVisualHumor()
                                onNavigateToCoverMarketing()
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Tout valider & Continuer vers la Couverture", modifier = Modifier.padding(8.dp))
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
            // Onglets principaux
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = uiState.selectedTab == ArtTab.VISUAL_BIBLE,
                    onClick = { viewModel.selectTab(ArtTab.VISUAL_BIBLE) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Visual Bible", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_visual_bible")
                )
                Tab(
                    selected = uiState.selectedTab == ArtTab.HUMOR_DNA,
                    onClick = { viewModel.selectTab(ArtTab.HUMOR_DNA) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mood, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Humor DNA", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_humor_dna")
                )
            }

            // Bannière de messages / alertes
            if (uiState.visualError != null && uiState.selectedTab == ArtTab.VISUAL_BIBLE) {
                ErrorBanner(
                    message = uiState.visualError ?: "",
                    onDismiss = { viewModel.clearMessages() }
                )
            }
            if (uiState.humorError != null && uiState.selectedTab == ArtTab.HUMOR_DNA) {
                ErrorBanner(
                    message = uiState.humorError ?: "",
                    onDismiss = { viewModel.clearMessages() }
                )
            }
            if (uiState.successMessage != null) {
                SuccessBanner(
                    message = uiState.successMessage ?: "",
                    onDismiss = { viewModel.clearMessages() }
                )
            }

            // Contenu selon l'onglet actif
            when (uiState.selectedTab) {
                ArtTab.VISUAL_BIBLE -> {
                    VisualBibleContent(
                        visualBible = uiState.visualBible,
                        isGenerating = uiState.isGeneratingVisual,
                        onGenerate = { viewModel.generateVisualBible() },
                        onValidate = { viewModel.requestValidateVisualBible() },
                        onUpdate = { viewModel.updateVisualBible(it) }
                    )
                }
                ArtTab.HUMOR_DNA -> {
                    HumorDnaContent(
                        humorDna = uiState.humorDna,
                        isGenerating = uiState.isGeneratingHumor,
                        onGenerate = { viewModel.generateHumorDna() },
                        onValidate = { viewModel.requestValidateHumorDna() },
                        onUpdate = { viewModel.updateHumorDna(it) }
                    )
                }
            }
        }
    }

    // Dialogue d'alerte en cas de contradiction canonique
    if (uiState.showContradictionDialog) {
        ContradictionAlertDialog(
            contradictions = uiState.activeContradictions,
            onConfirm = { viewModel.confirmPendingValidation() },
            onDismiss = { viewModel.dismissContradictionDialog() }
        )
    }
}

/**
 * Contenu de l'onglet Visual Bible (Direction Artistique).
 */
@Composable
private fun VisualBibleContent(
    visualBible: VisualBibleResult?,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onValidate: () -> Unit,
    onUpdate: (VisualBibleResult) -> Unit
) {
    var editingField by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("list_visual_bible"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête Génération IA & Validation Canon
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatPaint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Direction Artistique du Webtoon",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (visualBible?.isValidatedInCanon == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Canon Validé", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Établit le style visuel récurrent (Manhwa/Webtoon), la palette de couleurs, le ratio vertical mobile et les éléments de continuité obligatoires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onGenerate,
                            enabled = !isGenerating,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_generate_visual")
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Conception IA...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (visualBible == null) "Générer par IA" else "Régénérer")
                            }
                        }

                        if (visualBible != null && !visualBible.isValidatedInCanon) {
                            Button(
                                onClick = onValidate,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_validate_visual_canon")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Valider au Canon")
                            }
                        }
                    }
                }
            }
        }

        if (visualBible == null) {
            item {
                EmptyVisualPlaceholder(onGenerate = onGenerate, isGenerating = isGenerating)
            }
        } else {
            // 1. Style Artistique & Ambiance
            item {
                VisualSectionCard(
                    title = "Style Graphique & Direction Générale",
                    icon = Icons.Default.Palette,
                    content = visualBible.artStyle.ifBlank { "Non renseigné" },
                    subContent = "Éclairage & Ambiance : ${visualBible.lightingMood.ifBlank { "Standard" }}",
                    onEditClicked = { editingField = "artStyle" },
                    testTag = "card_art_style"
                )
            }

            // 2. Palette de Couleurs & Dominantes
            item {
                VisualSectionCard(
                    title = "Palette de Couleurs & Dominantes",
                    icon = Icons.Default.ColorLens,
                    content = visualBible.colorPalette.ifBlank { "Non renseignée" },
                    subContent = null,
                    onEditClicked = { editingField = "colorPalette" },
                    testTag = "card_color_palette"
                )
            }

            // 3. Découpage & Cadrage Webtoon
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_paneling_rules"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Découpage & Règles de Cadrage Webtoon",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { editingField = "panelingStyle" }) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = visualBible.panelingStyle.ifBlank { "Défilement vertical standard avec gouttières aérées." },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (visualBible.framingRules.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Règles de cadrage privilégiées :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            visualBible.framingRules.forEach { rule ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(rule, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Continuité Visuelle & Caractéristiques Récurrentes
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_continuity_elements"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Continuité & Motifs Visuels Fixes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { editingField = "continuity" }) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Éléments à conserver obligatoirement d'un chapitre à l'autre pour la cohérence :",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        if (visualBible.continuityElements.isNotEmpty()) {
                            visualBible.continuityElements.forEach { item ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("★ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(item, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            Text("Aucun élément récurrent enregistré.", style = MaterialTheme.typography.bodySmall)
                        }

                        if (visualBible.recurringVisualElements.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Caractéristiques visuelles récurrentes :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            visualBible.recurringVisualElements.forEach { elem ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(elem, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }

    // Boîte de dialogue d'édition pour la Visual Bible
    if (editingField != null) {
        val currentField = editingField ?: return
        EditVisualBibleDialog(
            fieldName = currentField,
            visualBible = visualBible ?: VisualBibleResult(projectId = ""),
            onSave = { updated ->
                onUpdate(updated)
                editingField = null
            },
            onDismiss = { editingField = null }
        )
    }
}

/**
 * Contenu de l'onglet Humor DNA (ADN Humoristique).
 */
@Composable
private fun HumorDnaContent(
    humorDna: HumorDnaResult?,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onValidate: () -> Unit,
    onUpdate: (HumorDnaResult) -> Unit
) {
    var editingField by remember { mutableStateOf<String?>(null) }
    var addingRunningGag by remember { mutableStateOf(false) }
    var newRunningGagText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("list_humor_dna"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête Génération IA & Validation Canon
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mood, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADN Humoristique (Humor DNA)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (humorDna?.isValidatedInCanon == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Canon Validé", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    // Avertissement clé : pas forcé dans chaque chapitre
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Règle absolue : Ces éléments d'humour sont des outils à la disposition de l'auteur. Ils ne doivent JAMAIS être forcés dans chaque chapitre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onGenerate,
                            enabled = !isGenerating,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_generate_humor")
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyse IA...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (humorDna == null) "Générer par IA" else "Régénérer")
                            }
                        }

                        if (humorDna != null && !humorDna.isValidatedInCanon) {
                            Button(
                                onClick = onValidate,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_validate_humor_canon")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Valider au Canon")
                            }
                        }
                    }
                }
            }
        }

        if (humorDna == null) {
            item {
                EmptyHumorPlaceholder(onGenerate = onGenerate, isGenerating = isGenerating)
            }
        } else {
            // 1. Registre comique & Fréquence
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_humor_style"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Registre d'Humour & Fréquence",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { editingField = "humorStyle" }) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = humorDna.humorStyle.ifBlank { "Non renseigné" },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Fréquence conseillée : ${humorDna.punchlineFrequency.ifBlank { "Ponctuelle / respiration" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Autoriser les respirations Chibi / SD :",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = humorDna.chibiMomentsAllowed,
                                onCheckedChange = { allowed ->
                                    onUpdate(humorDna.copy(chibiMomentsAllowed = allowed))
                                },
                                modifier = Modifier.testTag("switch_chibi_allowed")
                            )
                        }
                    }
                }
            }

            // 2. Running Gags Possibles
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_running_gags"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Running Gags Possibles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { addingRunningGag = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter un gag", modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        if (humorDna.runningGags.isEmpty()) {
                            Text("Aucun running gag enregistré.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            humorDna.runningGags.forEachIndexed { index, gag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f)) {
                                        Text("😂 ", style = MaterialTheme.typography.bodySmall)
                                        Text(gag, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Blagues & Manies des Personnages Canon
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_character_hooks"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Blagues & Manies des Personnages Canoniques",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Accrochées aux traits réels des personnages validés dans le canon :",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        if (humorDna.characterHumorHooks.isEmpty()) {
                            Text("Aucune manie de personnage enregistrée.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            humorDna.characterHumorHooks.forEach { hook ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = hook.characterName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = hook.humorNote,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Situations Comiques Envisagées
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_comic_situations"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Situations Comiques Envisagées",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (humorDna.comicSituations.isEmpty()) {
                            Text("Aucune situation comique répertoriée.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            humorDna.comicSituations.forEach { sit ->
                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Text("• ", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                    Text(sit, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }

    // Dialogue d'ajout manuel de running gag
    if (addingRunningGag) {
        AlertDialog(
            onDismissRequest = { addingRunningGag = false },
            title = { Text("Ajouter un Running Gag") },
            text = {
                OutlinedTextField(
                    value = newRunningGagText,
                    onValueChange = { newRunningGagText = it },
                    label = { Text("Description du gag récurrent") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRunningGagText.isNotBlank() && humorDna != null) {
                            val updatedGags = humorDna.runningGags + newRunningGagText.trim()
                            onUpdate(humorDna.copy(runningGags = updatedGags))
                            newRunningGagText = ""
                            addingRunningGag = false
                        }
                    }
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { addingRunningGag = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialogue d'édition du style d'humour
    if (editingField == "humorStyle" && humorDna != null) {
        var styleValue by remember { mutableStateOf(humorDna.humorStyle) }
        var frequencyValue by remember { mutableStateOf(humorDna.punchlineFrequency) }

        AlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text("Modifier le Registre d'Humour") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = styleValue,
                        onValueChange = { styleValue = it },
                        label = { Text("Style d'humour (ex: Pince-sans-rire, Ironie, Comique de situation)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = frequencyValue,
                        onValueChange = { frequencyValue = it },
                        label = { Text("Fréquence / Périodicité des touches d'humour") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdate(humorDna.copy(humorStyle = styleValue, punchlineFrequency = frequencyValue))
                        editingField = null
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

/**
 * Boîte de dialogue générique d'édition pour la Visual Bible.
 */
@Composable
private fun EditVisualBibleDialog(
    fieldName: String,
    visualBible: VisualBibleResult,
    onSave: (VisualBibleResult) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember {
        mutableStateOf(
            when (fieldName) {
                "artStyle" -> visualBible.artStyle
                "colorPalette" -> visualBible.colorPalette
                "panelingStyle" -> visualBible.panelingStyle
                "continuity" -> visualBible.continuityElements.joinToString("\n")
                else -> ""
            }
        )
    }

    var subTextValue by remember {
        mutableStateOf(
            if (fieldName == "artStyle") visualBible.lightingMood else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (fieldName) {
                    "artStyle" -> "Modifier le Style Graphique & Éclairage"
                    "colorPalette" -> "Modifier la Palette de Couleurs"
                    "panelingStyle" -> "Modifier le Découpage Webtoon"
                    "continuity" -> "Modifier les Éléments de Continuité (1 par ligne)"
                    else -> "Édition"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Contenu principal") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (fieldName == "artStyle") {
                    OutlinedTextField(
                        value = subTextValue,
                        onValueChange = { subTextValue = it },
                        label = { Text("Ambiance lumineuse & ombrages") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = when (fieldName) {
                        "artStyle" -> visualBible.copy(artStyle = textValue.trim(), lightingMood = subTextValue.trim())
                        "colorPalette" -> visualBible.copy(colorPalette = textValue.trim())
                        "panelingStyle" -> visualBible.copy(panelingStyle = textValue.trim())
                        "continuity" -> {
                            val items = textValue.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            visualBible.copy(continuityElements = items)
                        }
                        else -> visualBible
                    }
                    onSave(updated)
                }
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
private fun VisualSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: String,
    subContent: String?,
    onEditClicked: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onEditClicked) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = content, style = MaterialTheme.typography.bodyMedium)

            if (!subContent.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = subContent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyVisualPlaceholder(onGenerate: () -> Unit, isGenerating: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
            Text("La Direction Artistique n'a pas encore été définie.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Cliquez sur 'Générer par IA' pour créer la Visual Bible basée sur votre Canon.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGenerate, enabled = !isGenerating) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Générer la Visual Bible")
            }
        }
    }
}

@Composable
private fun EmptyHumorPlaceholder(onGenerate: () -> Unit, isGenerating: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Mood, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.height(10.dp))
            Text("L'Humor DNA n'a pas encore été définie.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Générez les running gags et manies des personnages en harmonie avec le ton de l'histoire.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGenerate, enabled = !isGenerating) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Générer l'Humor DNA")
            }
        }
    }
}

@Composable
private fun ContradictionAlertDialog(
    contradictions: List<Contradiction>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Divergence avec le Canon Détectée")
            }
        },
        text = {
            Column {
                Text(
                    text = "Une ou plusieurs divergences ont été relevées par rapport aux éléments validés :",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                contradictions.forEach { c ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(c.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Canon : ${c.canonicalValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Text("Proposé : ${c.conflictingValue}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Valider malgré la divergence")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Corriger avant validation")
            }
        }
    )
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun SuccessBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
fun createDefaultVisualHumorViewModel(projectId: String): VisualHumorViewModel {
    val context = LocalContext.current
    val db = ToonDatabase.getInstance(context)
    val canonRepo = CanonMemoryRepositoryImpl(db)
    val useCase = VisualHumorUseCase(
        projectDao = db.projectDao(),
        jobDao = db.generationJobDao(),
        visualBibleDao = db.visualBibleDao(),
        humorDnaDao = db.humorDnaDao(),
        canonRepository = canonRepo
    )
    return viewModel(factory = VisualHumorViewModel.Factory(useCase, projectId))
}

package com.example.ui.universe

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.canon.Contradiction
import com.example.domain.model.WorldBibleResult
import com.example.domain.model.WorldLocationEntry
import com.example.domain.model.WorldObjectEntry

/**
 * Écran World Bible / Univers de ToonIA (Phase 5).
 * Permet :
 * - Génération IA de l'univers en s'appuyant sur le canon actuel (personnages validés).
 * - Édition manuelle de chaque section (règles, factions, géographie, structure sociale, lore, contraintes).
 * - Validation individuelle de la Bible, des lieux et des objets au Canon.
 * - Détection et signalement des contradictions sans correction silencieuse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToNext: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: UniverseViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val rateLimitCountdown by com.example.ai.ApiRateLimitManager.rateLimitCountdown.collectAsState(initial = 0)

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank()) {
            viewModel.initializeProject(projectId)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_universe_bible"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "World Bible (Univers)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("universe_title")
                        )
                        Text(
                            text = "Phase 5 : Univers, règles, factions & lieux canoniques",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_universe")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showAddLocationDialog(true) },
                        modifier = Modifier.testTag("btn_add_location_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter un lieu",
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
            if (state.worldBible != null || state.worldBible?.locations?.isNotEmpty() == true) {
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
                                viewModel.validateAllUniverse()
                                onNavigateToNext()
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Tout valider & Continuer vers l'Histoire", modifier = Modifier.padding(8.dp))
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
                    GlobalUniverseActionCard(
                        status = state.status,
                        charactersInCanonCount = state.canonicalCharactersCount,
                        isWorldValidated = state.worldBible?.isValidatedInCanon == true,
                        onGenerateWorld = { viewModel.generateWorldBible() },
                        onValidateWorld = { viewModel.validateFullWorldBible() },
                        onAddLocation = { viewModel.showAddLocationDialog(true) },
                        rateLimitCountdown = rateLimitCountdown
                    )
                }

                val world = state.worldBible
                if (world == null && state.status !is WorldGenerationUiStatus.Running) {
                    item {
                        EmptyUniversePlaceholder(
                            rateLimitCountdown = rateLimitCountdown,
                            onGenerateClicked = { viewModel.generateWorldBible() }
                        )
                    }
                } else if (world != null) {
                    // 1. Règles fondamentales & lois de l'univers
                    item {
                        UniverseSectionCard(
                            title = "Règles & Lois de l'Univers",
                            subtitle = "Magie, technologies ou lois intangibles régissant ce monde",
                            content = world.rules,
                            onContentChange = { updated ->
                                viewModel.updateWorldBibleField { it.copy(rules = updated) }
                            }
                        )
                    }

                    // 2. Factions & Groupes
                    item {
                        UniverseSectionCard(
                            title = "Factions & Organisations",
                            subtitle = "Guildes, clans, corporations, alliances et rivalités",
                            content = world.factions,
                            onContentChange = { updated ->
                                viewModel.updateWorldBibleField { it.copy(factions = updated) }
                            }
                        )
                    }

                    // 3. Géographie & Environnements
                    item {
                        UniverseSectionCard(
                            title = "Géographie & Territoires",
                            subtitle = "Régions, continents, cités et paysages majeurs",
                            content = world.geography,
                            onContentChange = { updated ->
                                viewModel.updateWorldBibleField { it.copy(geography = updated) }
                            }
                        )
                    }

                    // 4. Structure Sociale & Politique
                    item {
                        UniverseSectionCard(
                            title = "Structure Sociale",
                            subtitle = "Hiérarchie, politique, mode de vie et économie",
                            content = world.socialStructure,
                            onContentChange = { updated ->
                                viewModel.updateWorldBibleField { it.copy(socialStructure = updated) }
                            }
                        )
                    }

                    // 5. Histoire & Lore
                    item {
                        UniverseSectionCard(
                            title = "Histoire & Lore du Monde",
                            subtitle = "Événements historiques marquants du passé qui impactent le présent",
                            content = world.historyLore,
                            onContentChange = { updated ->
                                viewModel.updateWorldBibleField { it.copy(historyLore = updated) }
                            }
                        )
                    }

                    // 6. Contraintes Intangibles
                    item {
                        UniverseSectionCard(
                            title = "Contraintes Intangibles du Monde",
                            subtitle = "Règles absolues à respecter dans toutes les générations narratives futures",
                            content = world.worldConstraints,
                            onContentChange = { updated ->
                                viewModel.updateWorldBibleField { it.copy(worldConstraints = updated) }
                            }
                        )
                    }

                    // 7. Lieux principaux (avec validation individuelle)
                    item {
                        Text(
                            text = "Lieux Principaux (${world.locations.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(world.locations.size) { index ->
                        val loc = world.locations[index]
                        LocationCard(
                            location = loc,
                            onFieldChange = { updated ->
                                val updatedList = world.locations.toMutableList().also { it[index] = updated }
                                viewModel.updateWorldBibleField { it.copy(locations = updatedList) }
                            },
                            onValidate = { viewModel.validateLocation(loc.id) }
                        )
                    }

                    // 8. Objets narratifs et artefacts
                    if (world.objects.isNotEmpty()) {
                        item {
                            Text(
                                text = "Objets Importants & Artefacts (${world.objects.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(world.objects.size) { index ->
                            val obj = world.objects[index]
                            ObjectCard(
                                storyObject = obj,
                                onFieldChange = { updated ->
                                    val updatedList = world.objects.toMutableList().also { it[index] = updated }
                                    viewModel.updateWorldBibleField { it.copy(objects = updatedList) }
                                },
                                onValidate = { viewModel.validateObject(obj.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogue d'ajout manuel de lieu
    if (state.showAddManualLocationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showAddLocationDialog(false) },
            title = { Text("Ajouter un lieu à l'univers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.manualLocationName,
                        onValueChange = { viewModel.updateManualLocationName(it) },
                        label = { Text("Nom du lieu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.manualLocationAtmosphere,
                        onValueChange = { viewModel.updateManualLocationAtmosphere(it) },
                        label = { Text("Ambiance / Atmosphère") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAddManualLocation() },
                    enabled = state.manualLocationName.isNotBlank()
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAddLocationDialog(false) }) {
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
                    Text("L'élément '$targetName' entre en divergence avec un fait déjà validé :")
                    contradictions.forEach { c ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = c.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(text = "Valeur canonique : ${c.canonicalValue}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetName == "World Bible") {
                            viewModel.validateFullWorldBible(forceIgnoreContradictions = true)
                        } else {
                            viewModel.dismissContradictionDialog()
                        }
                    }
                ) {
                    Text("Forcer l'inscription au Canon")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissContradictionDialog() }) {
                    Text("Corriger manuellement")
                }
            }
        )
    }
}

@Composable
private fun GlobalUniverseActionCard(
    status: WorldGenerationUiStatus,
    charactersInCanonCount: Int,
    isWorldValidated: Boolean,
    onGenerateWorld: () -> Unit,
    onValidateWorld: () -> Unit,
    onAddLocation: () -> Unit,
    rateLimitCountdown: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_universe_action"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Conception de l'Univers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Contexte canon : $charactersInCanonCount personnage(s) validé(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status is WorldGenerationUiStatus.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else if (isWorldValidated) {
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
                    onClick = onGenerateWorld,
                    enabled = status !is WorldGenerationUiStatus.Running && rateLimitCountdown == 0,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_generate_universe")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Générer Univers IA")
                }

                Button(
                    onClick = onValidateWorld,
                    enabled = !isWorldValidated,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Valider Bible")
                }

                OutlinedButton(
                    onClick = onAddLocation,
                    modifier = Modifier.weight(0.8f)
                ) {
                    Text("+ Lieu")
                }
            }
        }
    }
}

@Composable
private fun UniverseSectionCard(
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
private fun LocationCard(
    location: WorldLocationEntry,
    onFieldChange: (WorldLocationEntry) -> Unit,
    onValidate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (location.isValidatedInCanon) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
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
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(location.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                if (location.isValidatedInCanon) {
                    Text("Canon", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedButton(onClick = onValidate, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Valider au Canon", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = location.description,
                onValueChange = { onFieldChange(location.copy(description = it)) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = location.atmosphere,
                onValueChange = { onFieldChange(location.copy(atmosphere = it)) },
                label = { Text("Atmosphère / Ambiance") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ObjectCard(
    storyObject: WorldObjectEntry,
    onFieldChange: (WorldObjectEntry) -> Unit,
    onValidate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(storyObject.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (storyObject.isValidatedInCanon) {
                    Text("Canon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedButton(onClick = onValidate, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Valider", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Importance : ${storyObject.significance}", style = MaterialTheme.typography.bodySmall)
            if (storyObject.description.isNotBlank()) {
                Text("Description : ${storyObject.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyUniversePlaceholder(
    onGenerateClicked: () -> Unit,
    rateLimitCountdown: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
            Text("L'univers de ce webtoon n'a pas encore été forgé.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Générez la World Bible pour concevoir les règles, factions, lore et lieux.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGenerateClicked,
                enabled = rateLimitCountdown == 0
            ) {
                Text("Générer l'univers maintenant")
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

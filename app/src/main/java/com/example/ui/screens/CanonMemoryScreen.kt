package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.LocationEntity
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.CanonSnapshot
import com.example.domain.canon.Contradiction
import com.example.domain.canon.ContradictionSeverity
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Écran de visualisation et d'inspection manuelle du moteur CanonMemory (Phase 2).
 * Permet de :
 * 1. Voir en direct les éléments validés du canon (personnages, lieux, chapitres, mémoires).
 * 2. Tester manuellement la détection de contradiction via des scénarios injectables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonMemoryScreen(
    projectId: String,
    snapshot: CanonSnapshot,
    repository: CanonMemoryRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var detectedContradictions by remember { mutableStateOf<List<Contradiction>>(emptyList()) }
    var lastActionFeedback by remember { mutableStateOf<String?>(null) }

    val tabTitles = listOf("Vue d'ensemble", "Personnages", "Lieux & Univers", "Tests Contradiction")

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_canon_memory"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Canon Memory",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("canon_screen_title")
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("canon_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("canon_tab_$index")
                    )
                }
            }

            // Bandeau d'alerte des contradictions détectées
            if (detectedContradictions.isNotEmpty()) {
                ContradictionsBanner(
                    contradictions = detectedContradictions,
                    onDismiss = { detectedContradictions = emptyList() }
                )
            } else if (lastActionFeedback != null) {
                FeedbackBanner(
                    message = lastActionFeedback!!,
                    onDismiss = { lastActionFeedback = null }
                )
            }

            when (selectedTabIndex) {
                0 -> CanonOverviewTab(snapshot = snapshot)
                1 -> CanonCharactersTab(characters = snapshot.characters)
                2 -> CanonWorldTab(locations = snapshot.locations, memories = snapshot.canonMemories)
                3 -> CanonTestingTab(
                    projectId = projectId,
                    onValidateElement = { element ->
                        coroutineScope.launch {
                            val contradictions = repository.updateCanon(projectId, element)
                            detectedContradictions = contradictions
                            lastActionFeedback = if (contradictions.isEmpty()) {
                                "Élément validé et ajouté au canon avec succès."
                            } else {
                                "${contradictions.size} contradiction(s) détectée(s) !"
                            }
                        }
                    },
                    onTestElementWithoutSave = { element ->
                        coroutineScope.launch {
                            val contradictions = repository.detectContradictions(projectId, element)
                            detectedContradictions = contradictions
                            lastActionFeedback = if (contradictions.isEmpty()) {
                                "Aucune contradiction avec le canon actuel."
                            } else {
                                "${contradictions.size} contradiction(s) potentielle(s) identifiée(s)."
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CanonOverviewTab(snapshot: CanonSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "État Actuel du Canon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Le moteur CanonMemory garantit l'intégrité narrative en conservant uniquement les éléments explicitement validés.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Personnages",
                    count = snapshot.characters.size,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Lieux",
                    count = snapshot.locations.size,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Chapitres Canon",
                    count = snapshot.canonChapters.size,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section Packaging Éditorial & Couverture Canonique
        val hasMarketing = snapshot.project?.finalTitle != null || snapshot.canonCover != null
        if (hasMarketing) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Packaging Éditorial & Couverture (Phase 8)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        snapshot.project?.finalTitle?.let { title ->
                            Text(
                                text = "Titre validé : $title",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        snapshot.project?.finalSynopsis?.let { syn ->
                            Text(
                                text = "Synopsis validé : $syn",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        snapshot.canonCover?.let { cover ->
                            if (!cover.tagline.isNullOrBlank()) {
                                Text(
                                    text = "Accroche : \"${cover.tagline}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (cover.visualDescription.isNotBlank()) {
                                Text(
                                    text = "Description couverture (sans texte) : ${cover.visualDescription}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Mémoires & Règles Absolues (${snapshot.canonMemories.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (snapshot.canonMemories.isEmpty()) {
            item {
                Text(
                    text = "Aucune mémoire canonique enregistrée pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(snapshot.canonMemories, key = { it.id }) { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "[${memory.category}]",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = memory.fact,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonCharactersTab(characters: List<CharacterEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (characters.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aucun personnage canonique", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allez dans l'onglet 'Tests Contradiction' pour insérer un personnage de test.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(characters, key = { it.id }) { char ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = char.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = char.role,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (char.archetype.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Archétype: ${char.archetype}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (char.visualDescription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Visuel: ${char.visualDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonWorldTab(locations: List<LocationEntity>, memories: List<CanonMemoryEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Lieux Canoniques (${locations.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (locations.isEmpty()) {
            item {
                Text("Aucun lieu validé dans le canon.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(locations, key = { it.id }) { loc ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(loc.name, fontWeight = FontWeight.Bold)
                        Text(loc.description, style = MaterialTheme.typography.bodyMedium)
                        if (loc.atmosphere.isNotBlank()) {
                            Text("Atmosphère: ${loc.atmosphere}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonTestingTab(
    projectId: String,
    onValidateElement: (CanonElement) -> Unit,
    onTestElementWithoutSave: (CanonElement) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Validation & Détection Manuelle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Utilisez les boutons ci-dessous pour insérer des faits canoniques, puis tester des éléments contradictoires pour vérifier que le moteur les signale immédiatement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text("Étape 1 : Établir le Canon de base", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Button(
                onClick = {
                    val baseCharacter = CharacterEntity(
                        id = "char_aiden_canon",
                        projectId = projectId,
                        name = "Aiden",
                        role = "Protagoniste",
                        archetype = "Mage d'Élite Réticent",
                        visualDescription = "Jeune homme aux cheveux noirs ébouriffés et yeux bleus électriques",
                        personalityTraits = "Protecteur, méfiant"
                    )
                    onValidateElement(CanonElement.Character(baseCharacter, isUserValidated = true))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_insert_base_canon")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Valider dans le canon : Aiden (Protagoniste, yeux bleus)")
            }
        }

        item {
            Button(
                onClick = {
                    val location = LocationEntity(
                        id = "loc_sanctuaire",
                        projectId = projectId,
                        name = "Sanctuaire de l'Aube",
                        description = "Temple ancien perché sur la falaise",
                        atmosphere = "Sereine et mystique"
                    )
                    onValidateElement(CanonElement.Location(location, isUserValidated = true))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_insert_location_canon")
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Valider dans le canon : Sanctuaire de l'Aube (Serein)")
            }
        }

        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            Text("Étape 2 : Tester des Contradictions Flagrantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        item {
            OutlinedButton(
                onClick = {
                    // Contradiction : Même personnage 'Aiden', mais passé en Antagoniste avec des yeux rouges
                    val conflictingAiden = CharacterEntity(
                        id = "char_aiden_conflict",
                        projectId = projectId,
                        name = "Aiden",
                        role = "Antagoniste",
                        archetype = "Seigneur Sombre Cruel",
                        visualDescription = "Guerrier imposant aux cheveux noirs et yeux rouges sanglants"
                    )
                    onTestElementWithoutSave(CanonElement.Character(conflictingAiden, isUserValidated = false))
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_test_conflict_aiden")
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tester contradiction : Aiden (Antagoniste, yeux rouges)")
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    // Contradiction de lieu : Même lieu, atmosphère opposée
                    val conflictingLoc = LocationEntity(
                        id = "loc_sanctuaire_conflict",
                        projectId = projectId,
                        name = "Sanctuaire de l'Aube",
                        description = "Ruines hantées",
                        atmosphere = "Chaotique et angoissante"
                    )
                    onTestElementWithoutSave(CanonElement.Location(conflictingLoc, isUserValidated = false))
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_test_conflict_location")
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tester contradiction : Sanctuaire (Chaotique)")
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    // Élément cohérent : Nouvelle alliée sans conflit
                    val validAlly = CharacterEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        name = "Lyra",
                        role = "Alliée",
                        archetype = "Guérisseuse énergique",
                        visualDescription = "Cheveux roux tressés et yeux émeraudes"
                    )
                    onTestElementWithoutSave(CanonElement.Character(validAlly, isUserValidated = false))
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_test_coherent_element")
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tester élément cohérent : Lyra (Alliée)")
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContradictionsBanner(
    contradictions: List<Contradiction>,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("banner_contradictions"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contradiction(s) Détectée(s) (${contradictions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("OK", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            contradictions.forEach { contradiction ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "• [${contradiction.targetType}] ${contradiction.targetName} (${contradiction.fieldName})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = contradiction.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("banner_feedback"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Text("✕", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

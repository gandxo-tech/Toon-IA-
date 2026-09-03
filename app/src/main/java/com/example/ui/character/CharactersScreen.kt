package com.example.ui.character

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.example.domain.canon.ContradictionSeverity
import com.example.domain.model.CharacterBibleEntry

/**
 * Écran Character Bible (Phase 4 de ToonIA).
 * Affiche chaque personnage sous forme de fiche éditable.
 * Permet :
 * - Modification directe de n'importe quel champ avant validation
 * - Validation explicite avec bouton "Valider ce personnage" pour écrire dans CanonMemory
 * - Détection et affichage visible des contradictions avant écriture
 * - Ajout manuel de personnage sans IA
 * - Relance de génération IA pour un personnage précis sans toucher aux autres.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToNext: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CharactersViewModel = viewModel()
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
            .testTag("screen_characters_bible"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Character Bible",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("characters_title")
                        )
                        Text(
                            text = "Étape 2 : Création & validation canon des personnages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_characters")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showManualAddDialog(true) },
                        modifier = Modifier.testTag("btn_add_manual_character_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter manuellement",
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
            if (state.characters.isNotEmpty()) {
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
                                viewModel.validateAllCharacters()
                                onNavigateToNext()
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Tout valider & Continuer vers l'Univers", modifier = Modifier.padding(8.dp))
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
            // Bandeau de statut ou feedback
            state.generalFeedback?.let { feedback ->
                FeedbackBanner(
                    message = feedback,
                    onDismiss = { viewModel.dismissFeedback() }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section En-tête / Action globale IA
                item {
                    GlobalActionCard(
                        status = state.status,
                        characterCount = state.characters.size,
                        validatedCount = state.characters.count { it.entry.isValidatedInCanon },
                        onGenerateAll = { viewModel.generateCharacters() },
                        onAddManual = { viewModel.showManualAddDialog(true) }
                    )
                }

                // Liste des personnages
                if (state.characters.isEmpty() && state.status !is CharacterGenerationUiStatus.Running) {
                    item {
                        EmptyCharactersPlaceholder(
                            onGenerateClicked = { viewModel.generateCharacters() },
                            onAddManualClicked = { viewModel.showManualAddDialog(true) }
                        )
                    }
                } else {
                    items(state.characters, key = { it.entry.id }) { item ->
                        CharacterCard(
                            item = item,
                            onToggleExpand = { viewModel.toggleCharacterExpansion(item.entry.id) },
                            onFieldChange = { update -> viewModel.updateCharacterField(item.entry.id, update) },
                            onValidate = { viewModel.validateCharacter(item.entry.id, forceIgnoreContradictions = false) },
                            onOpenRegenDialog = { viewModel.showRegenDialog(item.entry.id, true) }
                        )
                    }
                }
            }
        }
    }

    // Dialogue d'ajout manuel de personnage
    if (state.showAddManualDialog) {
        AddManualCharacterDialog(
            name = state.manualName,
            role = state.manualRole,
            onNameChange = { viewModel.updateManualName(it) },
            onRoleChange = { viewModel.updateManualRole(it) },
            onDismiss = { viewModel.showManualAddDialog(false) },
            onConfirm = { viewModel.confirmAddManualCharacter() }
        )
    }

    // Dialogue d'alerte de contradiction détectée
    state.activeContradictionTarget?.let { targetItem ->
        ContradictionAlertDialog(
            characterName = targetItem.entry.name,
            contradictions = targetItem.contradictions,
            onDismiss = { viewModel.dismissContradictionDialog() },
            onIgnoreAndValidate = {
                viewModel.validateCharacter(targetItem.entry.id, forceIgnoreContradictions = true)
            }
        )
    }

    // Dialogue de régénération ciblée d'un seul personnage
    state.characters.firstOrNull { it.showRegenGuidanceDialog }?.let { targetItem ->
        SingleRegenGuidanceDialog(
            characterName = targetItem.entry.name,
            guidance = targetItem.singleRegenGuidance,
            onGuidanceChange = { viewModel.updateRegenGuidance(targetItem.entry.id, it) },
            onDismiss = { viewModel.showRegenDialog(targetItem.entry.id, false) },
            onConfirm = {
                viewModel.regenerateSingleCharacter(targetItem.entry.id, targetItem.singleRegenGuidance)
            }
        )
    }
}

@Composable
private fun GlobalActionCard(
    status: CharacterGenerationUiStatus,
    characterCount: Int,
    validatedCount: Int,
    onGenerateAll: () -> Unit,
    onAddManual: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_characters_global_action"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Casting du Webtoon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$validatedCount validé(s) au canon sur $characterCount au total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status is CharacterGenerationUiStatus.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onGenerateAll,
                    enabled = status !is CharacterGenerationUiStatus.Running,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_generate_all_characters")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer casting IA")
                }

                OutlinedButton(
                    onClick = onAddManual,
                    modifier = Modifier.testTag("btn_add_manual_character")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajouter manuellement")
                }
            }

            if (status is CharacterGenerationUiStatus.Failed) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    item: CharacterUiItem,
    onToggleExpand: () -> Unit,
    onFieldChange: ((CharacterBibleEntry) -> CharacterBibleEntry) -> Unit,
    onValidate: () -> Unit,
    onOpenRegenDialog: () -> Unit
) {
    val char = item.entry
    val isValidated = char.isValidatedInCanon

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("character_card_${char.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isValidated) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // En-tête de la carte avec indicateur Canon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isValidated) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isValidated) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = char.name.ifBlank { "Nouveau personnage" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isValidated) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "CANON VALIDÉ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = char.role.ifBlank { "Rôle non défini" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isRegenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (item.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (item.isExpanded) "Réduire" else "Développer"
                        )
                    }
                }
            }

            // Corps éditable complet
            AnimatedVisibility(visible = item.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    // Ligne 1 : Nom et Rôle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = char.name,
                            onValueChange = { newName -> onFieldChange { it.copy(name = newName) } },
                            label = { Text("Nom du personnage") },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("input_char_name_${char.id}"),
                            singleLine = true
                        )

                        RoleDropdown(
                            currentRole = char.role,
                            onRoleSelected = { newRole -> onFieldChange { it.copy(role = newRole) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_char_role_${char.id}")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Personnalité
                    OutlinedTextField(
                        value = char.personality,
                        onValueChange = { newPerso -> onFieldChange { it.copy(personality = newPerso) } },
                        label = { Text("Personnalité & Tempérament") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_personality_${char.id}"),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Apparence physique détaillée
                    OutlinedTextField(
                        value = char.physicalAppearance,
                        onValueChange = { newApp -> onFieldChange { it.copy(physicalAppearance = newApp) } },
                        label = { Text("Apparence physique détaillée (visage, cheveux, yeux...)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_appearance_${char.id}"),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Traits distinctifs & Vêtements caractéristiques
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = char.distinctiveFeatures,
                            onValueChange = { newFeatures -> onFieldChange { it.copy(distinctiveFeatures = newFeatures) } },
                            label = { Text("Traits distinctifs (cicatrice, marque...)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_char_features_${char.id}"),
                            minLines = 2
                        )

                        OutlinedTextField(
                            value = char.characteristicClothing,
                            onValueChange = { newClothing -> onFieldChange { it.copy(characteristicClothing = newClothing) } },
                            label = { Text("Vêtements caractéristiques") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_char_clothing_${char.id}"),
                            minLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Capacités
                    OutlinedTextField(
                        value = char.abilities,
                        onValueChange = { newAbilities -> onFieldChange { it.copy(abilities = newAbilities) } },
                        label = { Text("Capacités & Pouvoirs") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_abilities_${char.id}"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Relations avec les autres personnages
                    OutlinedTextField(
                        value = char.relationships,
                        onValueChange = { newRel -> onFieldChange { it.copy(relationships = newRel) } },
                        label = { Text("Relations avec les autres personnages") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_relationships_${char.id}"),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Arc narratif prévu
                    OutlinedTextField(
                        value = char.plannedArc,
                        onValueChange = { newArc -> onFieldChange { it.copy(plannedArc = newArc) } },
                        label = { Text("Arc narratif prévu au cours du webtoon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_arc_${char.id}"),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // État actuel au début de l'histoire
                    OutlinedTextField(
                        value = char.currentState,
                        onValueChange = { newState -> onFieldChange { it.copy(currentState = newState) } },
                        label = { Text("État initial au début de l'histoire") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_state_${char.id}"),
                        singleLine = true
                    )

                    // Avertissement de contradictions non résolues sur ce personnage
                    if (item.contradictions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Attention : ${item.contradictions.size} contradiction(s) avec le canon !",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                item.contradictions.forEach { c ->
                                    Text(
                                        text = "• ${c.fieldName} : ${c.description}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Boutons d'action sur le personnage : Validation Canon & Régénération Ciblée
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onValidate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValidated) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_validate_char_${char.id}")
                        ) {
                            Icon(
                                imageVector = if (isValidated) Icons.Default.CheckCircle else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isValidated) "Mettre à jour le canon" else "Valider ce personnage"
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenRegenDialog,
                            enabled = !item.isRegenerating,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_regen_char_${char.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Régénérer")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(
    currentRole: String,
    onRoleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val roles = listOf("Protagoniste", "Antagoniste", "Allié", "Mentor", "Rival", "Secondaire", "Neutre")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentRole,
            onValueChange = {},
            readOnly = true,
            label = { Text("Rôle") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            roles.forEach { roleOption ->
                DropdownMenuItem(
                    text = { Text(roleOption) },
                    onClick = {
                        onRoleSelected(roleOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AddManualCharacterDialog(
    name: String,
    role: String,
    onNameChange: (String) -> Unit,
    onRoleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un personnage (manuel)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Créez un nouveau personnage manuellement sans appel IA. Vous pourrez ensuite renseigner tous ses traits.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nom") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_manual_char_name"),
                    singleLine = true
                )
                RoleDropdown(
                    currentRole = role,
                    onRoleSelected = onRoleChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("btn_confirm_manual_char")
            ) {
                Text("Ajouter")
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
private fun ContradictionAlertDialog(
    characterName: String,
    contradictions: List<Contradiction>,
    onDismiss: () -> Unit,
    onIgnoreAndValidate: () -> Unit
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
                text = "Contradiction détectée avec le Canon !",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Le personnage '$characterName' présente une divergence avec les faits canoniques déjà validés :",
                    style = MaterialTheme.typography.bodyMedium
                )
                contradictions.forEach { c ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Champ : ${c.fieldName} (${if (c.severity == ContradictionSeverity.HIGH) "Critique" else "Moyen"})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = c.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Voulez-vous corriger la fiche ou forcer l'inscription en ignorant cette alerte ?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onIgnoreAndValidate,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("btn_force_validate_char")
            ) {
                Text("Ignorer et valider dans le canon")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_conflict_char")
            ) {
                Text("Corriger")
            }
        }
    )
}

@Composable
private fun SingleRegenGuidanceDialog(
    characterName: String,
    guidance: String,
    onGuidanceChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("Régénérer '$characterName'")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Vous pouvez donner des directives précises à l'IA pour orienter la régénération de ce personnage (ex: 'Rends-le plus sarcastique', 'Change sa tenue pour une armure légère') :",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = guidance,
                    onValueChange = onGuidanceChange,
                    label = { Text("Directives optionnelles") },
                    placeholder = { Text("Ex: accentuer son côté protecteur...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_single_regen_guidance"),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("btn_confirm_single_regen")
            ) {
                Text("Régénérer ce personnage")
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
private fun EmptyCharactersPlaceholder(
    onGenerateClicked: () -> Unit,
    onAddManualClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_characters_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Aucun personnage pour le moment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Générez un casting complet avec l'IA Gemini ou ajoutez vos personnages manuellement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onGenerateClicked) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Générer avec l'IA")
                }
                OutlinedButton(onClick = onAddManualClicked) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manuel")
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
            .testTag("characters_feedback_banner"),
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
                    imageVector = Icons.Default.CheckCircle,
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
                Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
            }
        }
    }
}

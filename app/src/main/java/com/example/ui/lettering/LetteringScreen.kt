package com.example.ui.lettering

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.DialogueEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetteringScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: LetteringViewModel = viewModel(
        factory = LetteringViewModel.Factory(
            projectId = projectId,
            database = ToonDatabase.getInstance(LocalContext.current)
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingDialogue by remember { mutableStateOf<DialogueEntity?>(null) }
    var editTextValue by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.testTag("screen_lettering"),
        topBar = {
            TopAppBar(
                title = { Text("Lettering & Bulles (Phase 15)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        if (uiState.panelsWithDialogues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun panel ou dialogue disponible. Veuillez générer des panels et dialogues d'abord.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(uiState.panelsWithDialogues, key = { it.panel.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lettering_panel_${item.panel.id}"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Case #${item.panel.orderIndex}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${item.dialogues.size} bulle(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .background(Color.DarkGray)
                            ) {
                                val imageFile = item.panel.localImagePath?.let { File(it) }
                                if (imageFile != null && imageFile.exists()) {
                                    AsyncImage(
                                        model = imageFile,
                                        contentDescription = "Illustration case #${item.panel.orderIndex}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Image non générée",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item.dialogues
                                            .filter { it.placement.contains("TOP", ignoreCase = true) }
                                            .forEach { dialogue ->
                                                DialogueBubbleView(
                                                    dialogue = dialogue,
                                                    onEditClicked = {
                                                        editingDialogue = dialogue
                                                        editTextValue = dialogue.text
                                                    }
                                                )
                                            }
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        item.dialogues
                                            .filter { !it.placement.contains("TOP", ignoreCase = true) }
                                            .forEach { dialogue ->
                                                DialogueBubbleView(
                                                    dialogue = dialogue,
                                                    onEditClicked = {
                                                        editingDialogue = dialogue
                                                        editTextValue = dialogue.text
                                                    }
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingDialogue != null) {
        AlertDialog(
            onDismissRequest = { editingDialogue = null },
            title = { Text("Modifier le texte de la bulle") },
            text = {
                Column {
                    Text(
                        text = "Locuteur : ${editingDialogue?.speakerName ?: "Inconnu"} (${editingDialogue?.type})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editTextValue,
                        onValueChange = { editTextValue = it },
                        label = { Text("Texte du dialogue") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_dialogue_text")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingDialogue?.let {
                            viewModel.updateDialogueText(it, editTextValue)
                        }
                        editingDialogue = null
                    },
                    modifier = Modifier.testTag("btn_save_dialogue")
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDialogue = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun DialogueBubbleView(
    dialogue: DialogueEntity,
    onEditClicked: () -> Unit
) {
    val backgroundColor = when (dialogue.type.uppercase()) {
        "THOUGHT" -> Color(0xFFE3F2FD)
        "SHOUT" -> Color(0xFFFFF9C4)
        "WHISPER" -> Color(0xFFF5F5F5)
        "NARRATION" -> Color(0xFF212121)
        else -> Color.White
    }

    val textColor = if (dialogue.type.uppercase() == "NARRATION") Color.White else Color.Black

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable(onClick = onEditClicked)
            .testTag("dialogue_bubble_${dialogue.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (dialogue.speakerName.isNotBlank() && dialogue.type.uppercase() != "NARRATION") {
                    Text(
                        text = dialogue.speakerName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!dialogue.emotion.isNullOrBlank() && dialogue.type.uppercase() != "NARRATION") {
                    Text(
                        text = "(${dialogue.emotion})",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    text = dialogue.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (dialogue.type.uppercase() == "SHOUT") FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Modifier",
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

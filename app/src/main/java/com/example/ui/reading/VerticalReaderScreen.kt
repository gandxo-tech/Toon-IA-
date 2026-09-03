package com.example.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.ToonDatabase
import com.example.ui.lettering.DialogueBubbleView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalReaderScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: VerticalReaderViewModel = viewModel(
        factory = VerticalReaderViewModel.Factory(
            projectId = projectId,
            database = ToonDatabase.getInstance(LocalContext.current)
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.testTag("screen_lecture_verticale"),
        topBar = {
            TopAppBar(
                title = { Text("Lecture Verticale (Phase 16)") },
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
                    text = "Aucun panel disponible pour la lecture verticale.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.panelsWithDialogues, key = { it.panel.id }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp)
                            .background(Color.Black)
                            .testTag("reader_panel_${item.panel.id}")
                    ) {
                        val imageFile = item.panel.localImagePath?.let { File(it) }
                        if (imageFile != null && imageFile.exists()) {
                            AsyncImage(
                                model = imageFile,
                                contentDescription = "Panel #${item.panel.orderIndex}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Panel #${item.panel.orderIndex} (En attente d'illustration)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                item.dialogues
                                    .filter { it.placement.contains("TOP", ignoreCase = true) }
                                    .forEach { dialogue ->
                                        DialogueBubbleView(
                                            dialogue = dialogue,
                                            onEditClicked = {}
                                        )
                                    }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                item.dialogues
                                    .filter { !it.placement.contains("TOP", ignoreCase = true) }
                                    .forEach { dialogue ->
                                        DialogueBubbleView(
                                            dialogue = dialogue,
                                            onEditClicked = {}
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

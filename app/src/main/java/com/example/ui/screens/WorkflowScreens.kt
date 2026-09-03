package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UniverseScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Univers",
        screenTag = "univers",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun StoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Histoire",
        screenTag = "histoire",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun LegacyChapterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Chapitre",
        screenTag = "chapitre",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun DialoguesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Dialogues",
        screenTag = "dialogues",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun CoverScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Couverture",
        screenTag = "couverture",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun VerticalReaderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Lecture Verticale",
        screenTag = "lecture_verticale",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleSkeletonScreen(
        title = "Export",
        screenTag = "export",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

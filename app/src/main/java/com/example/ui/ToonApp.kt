package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.ToonDatabase
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.data.repository.ProjectRepositoryImpl
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.CanonSnapshot
import com.example.domain.repository.ProjectRepository
import com.example.ui.navigation.Screen
import com.example.ui.character.CharactersScreen
import com.example.ui.chapter.ChapterScreen
import com.example.ui.cover.MarketingCoverScreen
import com.example.ui.screens.CanonMemoryScreen
import com.example.ui.screens.CoverScreen
import com.example.ui.dialogues.DialoguesScreen
import com.example.ui.export.ExportScreen
import com.example.ui.settings.ApiKeyScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.project.NewProjectScreen
import com.example.ui.panels.PanelsScreen
import com.example.ui.storyboard.StoryboardScreen
import com.example.ui.story.StoryScreen
import com.example.ui.universe.UniverseScreen
import com.example.ui.art.VisualHumorScreen
import com.example.ui.lettering.LetteringScreen
import com.example.ui.reading.VerticalReaderScreen

/**
 * Composant racine de l'application ToonIA.
 * Gère la navigation Compose entre tous les écrans du squelette.
 */
@Composable
fun ToonApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    repository: ProjectRepository = remember { ProjectRepositoryImpl() },
    canonRepository: CanonMemoryRepository? = null
) {
    val context = LocalContext.current
    val effectiveCanonRepo = remember(canonRepository) {
        canonRepository ?: CanonMemoryRepositoryImpl(ToonDatabase.getInstance(context))
    }
    val projects by repository.getProjects().collectAsState(initial = emptyList())
    val activeProjectId = projects.firstOrNull()?.id ?: "default_proj_toonia"
    val canonSnapshot by effectiveCanonRepo.getCanonSnapshotFlow(activeProjectId)
        .collectAsState(initial = CanonSnapshot(projectId = activeProjectId))

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val db = ToonDatabase.getInstance(context)
        if (db.projectDao().findProjectById("default_proj_toonia") == null) {
            db.projectDao().insertProject(
                com.example.data.local.entity.ProjectEntity(
                    id = "default_proj_toonia",
                    title = "Projet Démo ToonIA",
                    synopsis = "Un projet par défaut pour tester les modules.",
                    genre = "Shonen",
                    tone = "Action/Aventure"
                )
            )
        }
    }

    val rateLimitCountdown by com.example.ai.ApiRateLimitManager.rateLimitCountdown.collectAsState(initial = 0)

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Accueil.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Accueil.route) {
                HomeScreen(
                    projects = projects,
                    onNavigateToNewProject = {
                        navController.navigate(Screen.NouveauProjet.route)
                    },
                    onNavigateToScreen = { route ->
                        navController.navigate(route)
                    },
                    onNavigateToApiKey = {
                        navController.navigate(Screen.ApiKey.route)
                    }
                )
            }
            composable(Screen.NouveauProjet.route) {
                NewProjectScreen(
                    onNavigateToCharacters = { projectId ->
                        navController.navigate(Screen.Personnages.route)
                    }
                )
            }
            composable(Screen.Personnages.route) {
                CharactersScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNext = { navController.navigate(Screen.Univers.route) }
                )
            }
            composable(Screen.Univers.route) {
                UniverseScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNext = { navController.navigate(Screen.Histoire.route) }
                )
            }
            composable(Screen.Histoire.route) {
                StoryScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVisualHumor = { navController.navigate(Screen.DirectionArtistique.route) }
                )
            }
            composable(Screen.DirectionArtistique.route) {
                VisualHumorScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCoverMarketing = { navController.navigate(Screen.Couverture.route) }
                )
            }
            composable(Screen.Chapitre.route) {
                ChapterScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStoryboard = { navController.navigate(Screen.Storyboard.route) },
                    onNavigateToCanonMemory = { navController.navigate(Screen.CanonMemory.route) }
                )
            }
            composable(Screen.Storyboard.route) {
                StoryboardScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPanels = { navController.navigate(Screen.Panels.route) },
                    onNavigateToCanonMemory = { navController.navigate(Screen.CanonMemory.route) }
                )
            }
            composable(Screen.Panels.route) {
                PanelsScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDialogues = { navController.navigate(Screen.Dialogues.route) },
                    onNavigateToCanonMemory = { navController.navigate(Screen.CanonMemory.route) }
                )
            }
            composable(Screen.Dialogues.route) {
                DialoguesScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLettering = { navController.navigate(Screen.Lettering.route) }
                )
            }
            composable(Screen.Lettering.route) {
                LetteringScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReader = { navController.navigate(Screen.LectureVerticale.route) }
                )
            }
            composable(Screen.Couverture.route) {
                MarketingCoverScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCanonMemory = { navController.navigate(Screen.CanonMemory.route) },
                    onNavigateToChapter = { navController.navigate(Screen.Chapitre.route) }
                )
            }
            composable(Screen.CanonMemory.route) {
                CanonMemoryScreen(
                    projectId = activeProjectId,
                    snapshot = canonSnapshot,
                    repository = effectiveCanonRepo,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.LectureVerticale.route) {
                VerticalReaderScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToExport = { navController.navigate(Screen.Export.route) }
                )
            }
            composable(Screen.Export.route) {
                ExportScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ApiKey.route) {
                ApiKeyScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        AnimatedVisibility(
            visible = rateLimitCountdown > 0,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Limite d'API atteinte. Revenez dans $rateLimitCountdown secondes.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

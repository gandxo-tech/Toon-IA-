package com.example.ui.navigation

/**
 * Définition des écrans et routes de navigation pour ToonIA (Phase 0).
 */
sealed class Screen(val route: String, val title: String) {
    object Accueil : Screen("accueil", "Accueil")
    object NouveauProjet : Screen("nouveau_projet", "Nouveau Projet")
    object Personnages : Screen("personnages", "Personnages")
    object Univers : Screen("univers", "Univers")
    object Histoire : Screen("histoire", "Histoire")
    object DirectionArtistique : Screen("direction_artistique", "Direction Artistique & Humour")
    object Chapitre : Screen("chapitre", "Chapitre")
    object Storyboard : Screen("storyboard", "Storyboard")
    object Panels : Screen("panels", "Panels")
    object Dialogues : Screen("dialogues", "Dialogues")
    object Lettering : Screen("lettering", "Lettering & Bulles")
    object Couverture : Screen("couverture", "Couverture")
    object CanonMemory : Screen("canon_memory", "Canon Memory")
    object LectureVerticale : Screen("lecture_verticale", "Lecture Verticale")
    object Export : Screen("export", "Export")
    object ApiKey : Screen("api_key", "Clé API Gemini")

    companion object {
        val workflowScreens = listOf(
            NouveauProjet,
            Personnages,
            Univers,
            Histoire,
            DirectionArtistique,
            Chapitre,
            Storyboard,
            Panels,
            Dialogues,
            Lettering,
            Couverture,
            CanonMemory,
            LectureVerticale,
            Export
        )
    }
}

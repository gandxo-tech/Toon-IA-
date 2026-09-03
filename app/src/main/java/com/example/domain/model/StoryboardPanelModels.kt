package com.example.domain.model

/**
 * Modèle pour l'enrichissement détaillé d'une scène au storyboard (Phase 11).
 * Précise le rythme, l'arc émotionnel et les micro-beats avant le découpage en panels.
 */
data class SceneDetailDraft(
    val sceneId: String,
    val enrichedSummary: String,
    val pacingNotes: String,
    val emotionalArc: String,
    val detailedNarrativeBeats: List<String>,
    val atmosphereAndSensory: String,
    val characterInteractions: String
)

/**
 * Modèle de panel (case de webtoon) issu du découpage d'une scène par l'IA (Phase 12).
 * Chaque panel contient tous les paramètres requis pour le cadrage, l'ambiance,
 * le prompt visuel autonome et la continuité narrative.
 */
data class PanelDraft(
    val orderIndex: Int,
    val charactersPresent: String,
    val location: String,
    val actionDescription: String,
    val dominantEmotion: String,
    val cameraAngle: String,
    val composition: String,
    val lightingAndAtmosphere: String,
    val backgroundDetails: String,
    val visualEffects: String,
    val visualPrompt: String,
    val continuityNotes: String,
    val soundEffects: String? = null
)

/**
 * Résultat du découpage complet d'une scène en plusieurs panels ordonnés.
 */
data class ScenePanelsDraft(
    val sceneId: String,
    val sceneTitle: String,
    val panels: List<PanelDraft>
)

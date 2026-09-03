package com.example.domain.model

/**
 * Modèle de données représentant la proposition de script générée par l'IA pour un chapitre.
 */
data class ChapterScriptDraft(
    val chapterNumber: Int = 1,
    val title: String,
    val synopsis: String,
    val scenes: List<SceneScriptDraft>
)

/**
 * Scène structurée dans le script d'un chapitre (Phase 10).
 * Précise le lieu, les personnages, le résumé d'action et les beats narratifs,
 * sans encore découper en panels (découpage prévu en Phase 12).
 */
data class SceneScriptDraft(
    val orderIndex: Int,
    val title: String,
    val locationName: String,
    val charactersPresent: List<String>,
    val summary: String,
    val narrativeBeats: List<String>
)

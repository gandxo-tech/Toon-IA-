package com.example.data.local.entity

/**
 * Types de tâches de génération IA pour ToonIA.
 */
enum class JobType {
    ANALYSE,
    PERSONNAGES,
    UNIVERS,
    HISTOIRE,
    CHAPITRE,
    STORYBOARD,
    VISUEL,
    HUMOUR,
    PANEL,
    DIALOGUE,
    COUVERTURE,
    EXPORT
}

/**
 * Statuts d'exécution pour un GenerationJob.
 */
enum class JobStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED
}

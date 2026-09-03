package com.example.domain.model

/**
 * Modèle de domaine représentant un projet Webtoon dans ToonIA.
 */
data class Project(
    val id: String,
    val title: String,
    val synopsis: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

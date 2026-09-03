package com.example.domain.repository

import com.example.domain.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Interface du dépôt pour la gestion des projets ToonIA.
 */
interface ProjectRepository {
    fun getProjects(): Flow<List<Project>>
    suspend fun createProject(title: String, synopsis: String): Project
}

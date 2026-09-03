package com.example.data.repository

import com.example.domain.model.Project
import com.example.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Implémentation du dépôt de projets.
 * Initialement vide conformément aux exigences de la Phase 0 : pas de fausse donnée d'exemple.
 */
class ProjectRepositoryImpl : ProjectRepository {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())

    override fun getProjects(): Flow<List<Project>> = _projects.asStateFlow()

    override suspend fun createProject(title: String, synopsis: String): Project {
        val newProject = Project(
            id = UUID.randomUUID().toString(),
            title = title,
            synopsis = synopsis
        )
        _projects.value = _projects.value + newProject
        return newProject
    }
}

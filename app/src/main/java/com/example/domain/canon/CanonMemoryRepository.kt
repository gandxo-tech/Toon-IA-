package com.example.domain.canon

import kotlinx.coroutines.flow.Flow

/**
 * Interface du dépôt pour le moteur CanonMemory de ToonIA.
 */
interface CanonMemoryRepository {

    /**
     * Retourne un snapshot structuré et réactif de tout le contenu validé/canon d'un projet.
     */
    fun getCanonSnapshotFlow(projectId: String): Flow<CanonSnapshot>

    /**
     * Récupère de façon ponctuelle le snapshot du canon actuel pour un projet.
     */
    suspend fun getCanonSnapshot(projectId: String): CanonSnapshot

    /**
     * Tente de mettre à jour le canon avec un nouvel élément.
     * Règle stricte : N'écrit en base QUE si l'élément est explicitement marqué comme validé par l'utilisateur.
     * Retourne la liste des contradictions détectées (vide si aucune).
     * Si l'élément est validé, il est persisté dans le canon de la base Room.
     */
    suspend fun updateCanon(projectId: String, element: CanonElement): List<Contradiction>

    /**
     * Compare un élément au canon actuel sans écrire en base.
     */
    suspend fun detectContradictions(projectId: String, element: CanonElement): List<Contradiction>
}

package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité représentant un projet ToonIA dans la base de données locale.
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val synopsis: String = "",
    val genre: String = "",
    val targetAudience: String = "",
    val tone: String = "",
    val finalTitle: String? = null,
    val finalSynopsis: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

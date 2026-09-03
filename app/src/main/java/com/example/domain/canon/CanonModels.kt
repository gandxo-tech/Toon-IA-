package com.example.domain.canon

import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.HumorDNAEntity
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.local.entity.StoryObjectEntity
import com.example.data.local.entity.TimelineEventEntity
import com.example.data.local.entity.VisualBibleEntity
import com.example.data.local.entity.WorldBibleEntity

/**
 * Snapshot immuable représentant l'ensemble de la mémoire canonique et des éléments validés d'un projet.
 */
data class CanonSnapshot(
    val projectId: String,
    val characters: List<CharacterEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val storyObjects: List<StoryObjectEntity> = emptyList(),
    val canonChapters: List<ChapterEntity> = emptyList(),
    val scenes: List<SceneEntity> = emptyList(),
    val panels: List<PanelEntity> = emptyList(),
    val dialogues: List<DialogueEntity> = emptyList(),
    val canonTimelineEvents: List<TimelineEventEntity> = emptyList(),
    val canonMemories: List<CanonMemoryEntity> = emptyList(),
    val storyBible: StoryBibleEntity? = null,
    val worldBible: WorldBibleEntity? = null,
    val visualBible: VisualBibleEntity? = null,
    val humorDna: HumorDNAEntity? = null,
    val canonCover: CoverEntity? = null,
    val project: ProjectEntity? = null
)

/**
 * Éléments pouvant être soumis à la validation canonique.
 */
sealed class CanonElement {
    data class Character(val entity: CharacterEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Location(val entity: LocationEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class StoryObject(val entity: StoryObjectEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Chapter(val entity: ChapterEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Scene(val entity: SceneEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Panel(val entity: PanelEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Dialogue(val entity: DialogueEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class TimelineEvent(val entity: TimelineEventEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Memory(val entity: CanonMemoryEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class WorldBible(val entity: WorldBibleEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class StoryBible(val entity: StoryBibleEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class VisualBible(val entity: VisualBibleEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class HumorDNA(val entity: HumorDNAEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class Cover(val entity: CoverEntity, val isUserValidated: Boolean = true) : CanonElement()
    data class ProjectMetadata(val finalTitle: String, val finalSynopsis: String, val isUserValidated: Boolean = true) : CanonElement()
}

/**
 * Gravité de la contradiction détectée.
 */
enum class ContradictionSeverity {
    HIGH,   // Incompatibilité directe sur des faits établis
    MEDIUM  // Évolution inexpliquée ou divergence de caractéristiques
}

/**
 * Représente une contradiction narrative détectée entre un nouvel élément et le canon existant.
 */
data class Contradiction(
    val targetType: String,
    val targetName: String,
    val fieldName: String,
    val canonicalValue: String,
    val conflictingValue: String,
    val description: String,
    val severity: ContradictionSeverity
)

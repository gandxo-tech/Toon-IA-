package com.example.data.repository

import com.example.data.local.ToonDatabase
import com.example.domain.canon.CanonContradictionDetector
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonMemoryRepository
import com.example.domain.canon.CanonSnapshot
import com.example.domain.canon.Contradiction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Implémentation concrète de CanonMemoryRepository s'appuyant sur Room et CanonContradictionDetector.
 */
class CanonMemoryRepositoryImpl(
    private val database: ToonDatabase,
    private val contradictionDetector: CanonContradictionDetector = CanonContradictionDetector()
) : CanonMemoryRepository {

    override fun getCanonSnapshotFlow(projectId: String): Flow<CanonSnapshot> {
        val charactersFlow = database.characterDao().getCharactersByProjectId(projectId)
        val locationsFlow = database.locationDao().getLocationsByProjectId(projectId)
        val objectsFlow = database.storyObjectDao().getObjectsByProjectId(projectId)
        val chaptersFlow = database.chapterDao().getCanonChaptersByProjectId(projectId)
        val timelineFlow = database.timelineEventDao().getCanonEventsByProjectId(projectId)
        val memoriesFlow = database.canonMemoryDao().getCanonMemoriesByProjectId(projectId)
        val storyBibleFlow = database.storyBibleDao().getStoryBibleByProjectId(projectId)
        val worldBibleFlow = database.worldBibleDao().getWorldBibleByProjectId(projectId)
        val visualBibleFlow = database.visualBibleDao().getVisualBibleByProjectId(projectId)
        val humorDnaFlow = database.humorDnaDao().getHumorDnaByProjectId(projectId)
        val coverFlow = database.coverDao().getCanonCoverByProjectId(projectId)
        val projectFlow = database.projectDao().getProjectById(projectId)
        val scenesFlow = database.sceneDao().getCanonScenesByProjectId(projectId)
        val panelsFlow = database.panelDao().getCanonPanelsByProjectId(projectId)
        val dialoguesFlow = database.dialogueDao().getCanonDialoguesByProjectId(projectId)

        // Combiner l'ensemble des flux du canon
        return combine(
            combine(charactersFlow, locationsFlow, objectsFlow) { chars, locs, objs -> Triple(chars, locs, objs) },
            combine(chaptersFlow, timelineFlow, memoriesFlow) { chaps, times, mems -> Triple(chaps, times, mems) },
            combine(storyBibleFlow, worldBibleFlow, visualBibleFlow) { sb, wb, vb -> Triple(sb, wb, vb) },
            combine(humorDnaFlow, coverFlow, projectFlow) { hd, cov, proj -> Triple(hd, cov, proj) },
            combine(scenesFlow, panelsFlow, dialoguesFlow) { scs, pns, dls -> Triple(scs, pns, dls) }
        ) { (chars, locs, objs), (chaps, times, mems), (sb, wb, vb), (hd, cov, proj), (scs, pns, dls) ->
            CanonSnapshot(
                projectId = projectId,
                characters = chars,
                locations = locs,
                storyObjects = objs,
                canonChapters = chaps,
                scenes = scs,
                panels = pns,
                dialogues = dls,
                canonTimelineEvents = times,
                canonMemories = mems,
                storyBible = sb,
                worldBible = wb,
                visualBible = vb,
                humorDna = hd,
                canonCover = cov,
                project = proj
            )
        }
    }

    override suspend fun getCanonSnapshot(projectId: String): CanonSnapshot {
        return getCanonSnapshotFlow(projectId).first()
    }

    override suspend fun detectContradictions(projectId: String, element: CanonElement): List<Contradiction> {
        val currentSnapshot = getCanonSnapshot(projectId)
        return contradictionDetector.detectContradiction(element, currentSnapshot)
    }

    override suspend fun updateCanon(projectId: String, element: CanonElement): List<Contradiction> {
        // 1. Détection préalable des contradictions contre le canon actuel
        val currentSnapshot = getCanonSnapshot(projectId)
        val contradictions = contradictionDetector.detectContradiction(element, currentSnapshot)

        // 2. Règle du prompt : n'écrit que si l'élément est validé par l'utilisateur
        val isUserValidated = when (element) {
            is CanonElement.Character -> element.isUserValidated
            is CanonElement.Location -> element.isUserValidated
            is CanonElement.StoryObject -> element.isUserValidated
            is CanonElement.Chapter -> element.isUserValidated
            is CanonElement.TimelineEvent -> element.isUserValidated
            is CanonElement.Memory -> element.isUserValidated
            is CanonElement.WorldBible -> element.isUserValidated
            is CanonElement.StoryBible -> element.isUserValidated
            is CanonElement.VisualBible -> element.isUserValidated
            is CanonElement.HumorDNA -> element.isUserValidated
            is CanonElement.Cover -> element.isUserValidated
            is CanonElement.Scene -> element.isUserValidated
            is CanonElement.Panel -> element.isUserValidated
            is CanonElement.Dialogue -> element.isUserValidated
            is CanonElement.ProjectMetadata -> element.isUserValidated
        }

        if (!isUserValidated) {
            // L'élément n'est pas validé par l'utilisateur : on renvoie les contradictions sans écriture
            return contradictions
        }

        // 3. Écriture en base Room pour les éléments validés
        when (element) {
            is CanonElement.Character -> {
                database.characterDao().insertCharacter(element.entity.copy(projectId = projectId))
            }
            is CanonElement.Location -> {
                database.locationDao().insertLocation(element.entity.copy(projectId = projectId))
            }
            is CanonElement.StoryObject -> {
                database.storyObjectDao().insertObject(element.entity.copy(projectId = projectId))
            }
            is CanonElement.Chapter -> {
                database.chapterDao().insertChapter(element.entity.copy(projectId = projectId, isCanon = true))
            }
            is CanonElement.Scene -> {
                database.sceneDao().insertScene(element.entity.copy(projectId = projectId, isCanon = true))
            }
            is CanonElement.Panel -> {
                database.panelDao().insertPanel(element.entity.copy(projectId = projectId, isCanon = true))
            }
            is CanonElement.Dialogue -> {
                database.dialogueDao().insertDialogue(element.entity.copy(projectId = projectId, isCanon = true))
            }
            is CanonElement.TimelineEvent -> {
                database.timelineEventDao().insertEvent(element.entity.copy(projectId = projectId, isCanon = true))
            }
            is CanonElement.Memory -> {
                database.canonMemoryDao().insertCanonMemory(element.entity.copy(projectId = projectId))
            }
            is CanonElement.WorldBible -> {
                database.worldBibleDao().insertOrUpdateWorldBible(element.entity.copy(projectId = projectId))
            }
            is CanonElement.StoryBible -> {
                database.storyBibleDao().insertOrUpdateStoryBible(element.entity.copy(projectId = projectId))
            }
            is CanonElement.VisualBible -> {
                database.visualBibleDao().insertOrUpdateVisualBible(element.entity.copy(projectId = projectId))
            }
            is CanonElement.HumorDNA -> {
                database.humorDnaDao().insertOrUpdateHumorDna(element.entity.copy(projectId = projectId))
            }
            is CanonElement.Cover -> {
                database.coverDao().insertCover(element.entity.copy(projectId = projectId, isCanon = true))
            }
            is CanonElement.ProjectMetadata -> {
                val currentProj = database.projectDao().findProjectById(projectId)
                if (currentProj != null) {
                    database.projectDao().updateProject(
                        currentProj.copy(
                            title = element.finalTitle.ifBlank { currentProj.title },
                            synopsis = element.finalSynopsis.ifBlank { currentProj.synopsis },
                            finalTitle = element.finalTitle,
                            finalSynopsis = element.finalSynopsis,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return contradictions
    }
}

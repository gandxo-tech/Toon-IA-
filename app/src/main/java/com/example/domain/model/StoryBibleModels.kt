package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Modèle de domaine représentant un événement chronologique (TimelineEvent) fondateur ou marquant.
 */
data class StoryTimelineEventEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val orderIndex: Int = 0,
    val title: String,
    val description: String = "",
    val dateOrEpoch: String = "",
    val isValidatedInCanon: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("projectId", projectId)
            put("orderIndex", orderIndex)
            put("title", title)
            put("description", description)
            put("dateOrEpoch", dateOrEpoch)
            put("isValidatedInCanon", isValidatedInCanon)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryTimelineEventEntry {
            return StoryTimelineEventEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                projectId = obj.optString("projectId", ""),
                orderIndex = obj.optInt("orderIndex", obj.optInt("ordreChronologique", 0)),
                title = obj.optString("title", obj.optString("titre", "Événement")),
                description = obj.optString("description", ""),
                dateOrEpoch = obj.optString("dateOrEpoch", obj.optString("epoqueOuDate", "")),
                isValidatedInCanon = obj.optBoolean("isValidatedInCanon", false)
            )
        }
    }
}

/**
 * Modèle de domaine représentant la Story Bible complète de ToonIA (Phase 6).
 * Conforme aux spécifications :
 * - Prémisse
 * - Genre confirmé
 * - Thèmes principaux
 * - Ton général
 * - Conflits principaux (interne, relationnel, sociétal/cosmique)
 * - Intrigue principale (arc narratif global)
 * - Intrigues secondaires possibles (subplots)
 * - Direction générale du récit (climax envisagé, promesse finale)
 * - Événements fondateurs antérieurs au chapitre 1 (TimelineEvents)
 */
data class StoryBibleResult(
    val projectId: String = "",
    val premise: String = "",
    val confirmedGenre: String = "",
    val mainThemes: String = "",
    val generalTone: String = "",
    val mainConflicts: String = "",
    val mainPlot: String = "",
    val subPlots: String = "",
    val narrativeDirection: String = "",
    val pacing: String = "",
    val timelineEvents: List<StoryTimelineEventEntry> = emptyList(),
    val isValidatedInCanon: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("projectId", projectId)
            put("premise", premise)
            put("confirmedGenre", confirmedGenre)
            put("mainThemes", mainThemes)
            put("generalTone", generalTone)
            put("mainConflicts", mainConflicts)
            put("mainPlot", mainPlot)
            put("subPlots", subPlots)
            put("narrativeDirection", narrativeDirection)
            put("pacing", pacing)
            put("isValidatedInCanon", isValidatedInCanon)

            val eventsArray = JSONArray()
            timelineEvents.forEach { eventsArray.put(it.toJson()) }
            put("timelineEvents", eventsArray)
        }
    }

    companion object {
        fun fromJson(jsonString: String, projectId: String): StoryBibleResult {
            val root = JSONObject(jsonString)
            return fromJsonObject(root, projectId)
        }

        fun fromJsonObject(root: JSONObject, projectId: String): StoryBibleResult {
            val premise = root.optString("premise", root.optString("premisse", ""))
            val confirmedGenre = root.optString("confirmedGenre", root.optString("genreConfirme", ""))
            val mainThemes = root.optString("mainThemes", root.optString("themesPrincipaux", ""))
            val generalTone = root.optString("generalTone", root.optString("tonGeneral", ""))
            val mainConflicts = root.optString("mainConflicts", root.optString("conflitsPrincipaux", ""))
            val mainPlot = root.optString("mainPlot", root.optString("intriguePrincipale", ""))
            val subPlots = root.optString("subPlots", root.optString("intriguesSecondaires", ""))
            val narrativeDirection = root.optString("narrativeDirection", root.optString("directionGenerale", ""))
            val pacing = root.optString("pacing", root.optString("rythmeNarratif", ""))

            val eventsList = mutableListOf<StoryTimelineEventEntry>()
            val eventsArray = root.optJSONArray("timelineEvents") ?: root.optJSONArray("evenementsFondateurs")
            if (eventsArray != null) {
                for (i in 0 until eventsArray.length()) {
                    val item = eventsArray.getJSONObject(i)
                    eventsList.add(StoryTimelineEventEntry.fromJson(item).copy(projectId = projectId, orderIndex = i + 1))
                }
            }

            return StoryBibleResult(
                projectId = projectId,
                premise = premise,
                confirmedGenre = confirmedGenre,
                mainThemes = mainThemes,
                generalTone = generalTone,
                mainConflicts = mainConflicts,
                mainPlot = mainPlot,
                subPlots = subPlots,
                narrativeDirection = narrativeDirection,
                pacing = pacing,
                timelineEvents = eventsList,
                isValidatedInCanon = false
            )
        }
    }
}

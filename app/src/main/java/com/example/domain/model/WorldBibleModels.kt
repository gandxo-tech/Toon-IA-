package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Modèle de domaine représentant un lieu dans l'univers du webtoon (World Bible).
 */
data class WorldLocationEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val name: String,
    val description: String,
    val atmosphere: String = "",
    val isValidatedInCanon: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("projectId", projectId)
            put("name", name)
            put("description", description)
            put("atmosphere", atmosphere)
            put("isValidatedInCanon", isValidatedInCanon)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): WorldLocationEntry {
            return WorldLocationEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                projectId = obj.optString("projectId", ""),
                name = obj.optString("name", obj.optString("nom", "Lieu inconnu")),
                description = obj.optString("description", ""),
                atmosphere = obj.optString("atmosphere", obj.optString("ambiance", "")),
                isValidatedInCanon = obj.optBoolean("isValidatedInCanon", false)
            )
        }
    }
}

/**
 * Modèle de domaine représentant un objet important ou artefact narratif.
 */
data class WorldObjectEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val name: String,
    val significance: String,
    val description: String = "",
    val ownerCharacterId: String? = null,
    val isValidatedInCanon: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("projectId", projectId)
            put("name", name)
            put("significance", significance)
            put("description", description)
            put("ownerCharacterId", ownerCharacterId)
            put("isValidatedInCanon", isValidatedInCanon)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): WorldObjectEntry {
            return WorldObjectEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                projectId = obj.optString("projectId", ""),
                name = obj.optString("name", obj.optString("nom", "Objet")),
                significance = obj.optString("significance", obj.optString("importance", "")),
                description = obj.optString("description", ""),
                ownerCharacterId = if (obj.has("ownerCharacterId") && !obj.isNull("ownerCharacterId")) obj.getString("ownerCharacterId") else null,
                isValidatedInCanon = obj.optBoolean("isValidatedInCanon", false)
            )
        }
    }
}

/**
 * Modèle de domaine représentant la World Bible complète de ToonIA (Phase 5).
 * Conforme aux règles d'univers, factions, lieux, objets, lore, terminologie et contraintes.
 */
data class WorldBibleResult(
    val projectId: String = "",
    val rules: String = "",
    val factions: String = "",
    val geography: String = "",
    val socialStructure: String = "",
    val historyLore: String = "",
    val terminology: String = "",
    val worldConstraints: String = "",
    val locations: List<WorldLocationEntry> = emptyList(),
    val objects: List<WorldObjectEntry> = emptyList(),
    val isValidatedInCanon: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("projectId", projectId)
            put("rules", rules)
            put("factions", factions)
            put("geography", geography)
            put("socialStructure", socialStructure)
            put("historyLore", historyLore)
            put("terminology", terminology)
            put("worldConstraints", worldConstraints)
            put("isValidatedInCanon", isValidatedInCanon)

            val locsArray = JSONArray()
            locations.forEach { locsArray.put(it.toJson()) }
            put("locations", locsArray)

            val objsArray = JSONArray()
            objects.forEach { objsArray.put(it.toJson()) }
            put("objects", objsArray)
        }
    }

    companion object {
        fun fromJson(jsonString: String, projectId: String): WorldBibleResult {
            val root = JSONObject(jsonString)
            return fromJsonObject(root, projectId)
        }

        fun fromJsonObject(root: JSONObject, projectId: String): WorldBibleResult {
            val rules = root.optString("rules", root.optString("reglesUnivers", ""))
            val factions = root.optString("factions", root.optString("groupesFactions", ""))
            val geography = root.optString("geography", root.optString("geographie", ""))
            val socialStructure = root.optString("socialStructure", root.optString("structureSociale", ""))
            val historyLore = root.optString("historyLore", root.optString("elementsHistoire", ""))
            val terminology = root.optString("terminology", root.optString("terminologie", ""))
            val worldConstraints = root.optString("worldConstraints", root.optString("contraintesMonde", ""))

            val locsList = mutableListOf<WorldLocationEntry>()
            val locsArray = root.optJSONArray("locations") ?: root.optJSONArray("lieuxPrincipaux")
            if (locsArray != null) {
                for (i in 0 until locsArray.length()) {
                    val item = locsArray.getJSONObject(i)
                    locsList.add(WorldLocationEntry.fromJson(item).copy(projectId = projectId))
                }
            }

            val objsList = mutableListOf<WorldObjectEntry>()
            val objsArray = root.optJSONArray("objects") ?: root.optJSONArray("objetsImportants")
            if (objsArray != null) {
                for (i in 0 until objsArray.length()) {
                    val item = objsArray.getJSONObject(i)
                    objsList.add(WorldObjectEntry.fromJson(item).copy(projectId = projectId))
                }
            }

            return WorldBibleResult(
                projectId = projectId,
                rules = rules,
                factions = factions,
                geography = geography,
                socialStructure = socialStructure,
                historyLore = historyLore,
                terminology = terminology,
                worldConstraints = worldConstraints,
                locations = locsList,
                objects = objsList,
                isValidatedInCanon = false
            )
        }
    }
}

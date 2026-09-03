package com.example.domain.model

import com.example.data.local.entity.HumorDNAEntity
import com.example.data.local.entity.VisualBibleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Modèle de domaine pour la Visual Bible (Phase 7).
 * Encapsule la direction artistique, palette, découpage et continuité visuelle.
 */
data class VisualBibleResult(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val artStyle: String = "",
    val colorPalette: String = "",
    val lightingMood: String = "",
    val panelingStyle: String = "",
    val recurringVisualElements: List<String> = emptyList(),
    val framingRules: List<String> = emptyList(),
    val continuityElements: List<String> = emptyList(),
    val isValidatedInCanon: Boolean = false
) {
    fun toEntity(): VisualBibleEntity {
        return VisualBibleEntity(
            id = id,
            projectId = projectId,
            artStyle = artStyle,
            colorPalette = colorPalette,
            panelingStyle = panelingStyle,
            lightingMood = lightingMood,
            recurringVisualElements = JSONArray(recurringVisualElements).toString(),
            framingRules = JSONArray(framingRules).toString(),
            continuityElements = JSONArray(continuityElements).toString(),
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromEntity(entity: VisualBibleEntity): VisualBibleResult {
            return VisualBibleResult(
                id = entity.id,
                projectId = entity.projectId,
                artStyle = entity.artStyle,
                colorPalette = entity.colorPalette,
                lightingMood = entity.lightingMood,
                panelingStyle = entity.panelingStyle,
                recurringVisualElements = parseJsonStringArray(entity.recurringVisualElements),
                framingRules = parseJsonStringArray(entity.framingRules),
                continuityElements = parseJsonStringArray(entity.continuityElements),
                isValidatedInCanon = true
            )
        }

        fun fromJson(json: JSONObject, projectId: String, id: String = UUID.randomUUID().toString()): VisualBibleResult {
            return VisualBibleResult(
                id = id,
                projectId = projectId,
                artStyle = json.optString("directionArtistique", json.optString("artStyle", "")),
                colorPalette = json.optString("paletteCouleurs", json.optString("colorPalette", "")),
                lightingMood = json.optString("ambianceLumineuse", json.optString("lightingMood", "")),
                panelingStyle = json.optString("styleDecoupage", json.optString("panelingStyle", "")),
                recurringVisualElements = parseJsonArray(json.optJSONArray("caracteristiquesVisuellesRecurrentes") ?: json.optJSONArray("recurringVisualElements")),
                framingRules = parseJsonArray(json.optJSONArray("reglesCadragePrivilegiees") ?: json.optJSONArray("framingRules")),
                continuityElements = parseJsonArray(json.optJSONArray("elementsVisuelsContinuite") ?: json.optJSONArray("continuityElements")),
                isValidatedInCanon = false
            )
        }

        private fun parseJsonArray(jsonArray: JSONArray?): List<String> {
            if (jsonArray == null) return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i, "").trim()
                if (item.isNotEmpty()) list.add(item)
            }
            return list
        }

        private fun parseJsonStringArray(rawString: String): List<String> {
            if (rawString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(rawString)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val item = array.optString(i, "").trim()
                    if (item.isNotEmpty()) list.add(item)
                }
                list
            } catch (_: Exception) {
                rawString.split("\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
}

/**
 * Accroche humoristique propre à un personnage canonique identifié.
 */
data class CharacterHumorHook(
    val characterName: String,
    val characterId: String? = null,
    val humorNote: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("nomPersonnage", characterName)
            put("noteHumour", humorNote)
            if (characterId != null) put("characterId", characterId)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CharacterHumorHook {
            return CharacterHumorHook(
                characterName = json.optString("nomPersonnage", json.optString("characterName", "Personnage")),
                characterId = if (json.has("characterId") && !json.isNull("characterId")) json.getString("characterId") else null,
                humorNote = json.optString("noteHumour", json.optString("humorNote", ""))
            )
        }
    }
}

/**
 * Modèle de domaine pour l'Humor DNA (Phase 7).
 * Encapsule le registre comique, running gags et manies des personnages.
 */
data class HumorDnaResult(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val humorStyle: String = "",
    val chibiMomentsAllowed: Boolean = true,
    val punchlineFrequency: String = "",
    val runningGags: List<String> = emptyList(),
    val characterHumorHooks: List<CharacterHumorHook> = emptyList(),
    val comicSituations: List<String> = emptyList(),
    val isValidatedInCanon: Boolean = false
) {
    fun toEntity(): HumorDNAEntity {
        val hooksArray = JSONArray()
        characterHumorHooks.forEach { hook ->
            hooksArray.put(hook.toJson())
        }

        return HumorDNAEntity(
            id = id,
            projectId = projectId,
            humorStyle = humorStyle,
            chibiMomentsAllowed = chibiMomentsAllowed,
            runningGags = JSONArray(runningGags).toString(),
            punchlineFrequency = punchlineFrequency,
            characterHumorHooks = hooksArray.toString(),
            comicSituations = JSONArray(comicSituations).toString(),
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromEntity(entity: HumorDNAEntity): HumorDnaResult {
            val hooks = parseCharacterHooks(entity.characterHumorHooks)

            return HumorDnaResult(
                id = entity.id,
                projectId = entity.projectId,
                humorStyle = entity.humorStyle,
                chibiMomentsAllowed = entity.chibiMomentsAllowed,
                punchlineFrequency = entity.punchlineFrequency,
                runningGags = parseJsonStringArray(entity.runningGags),
                characterHumorHooks = hooks,
                comicSituations = parseJsonStringArray(entity.comicSituations),
                isValidatedInCanon = true
            )
        }

        fun fromJson(json: JSONObject, projectId: String, id: String = UUID.randomUUID().toString()): HumorDnaResult {
            val hooksList = mutableListOf<CharacterHumorHook>()
            val hooksArray = json.optJSONArray("blaguesPersonnages") ?: json.optJSONArray("characterHumorHooks")
            if (hooksArray != null) {
                for (i in 0 until hooksArray.length()) {
                    val hookObj = hooksArray.optJSONObject(i)
                    if (hookObj != null) {
                        hooksList.add(CharacterHumorHook.fromJson(hookObj))
                    }
                }
            }

            return HumorDnaResult(
                id = id,
                projectId = projectId,
                humorStyle = json.optString("styleHumour", json.optString("humorStyle", "")),
                chibiMomentsAllowed = json.optBoolean("momentsChibiAutorises", json.optBoolean("chibiMomentsAllowed", true)),
                punchlineFrequency = json.optString("frequenceHumour", json.optString("punchlineFrequency", "")),
                runningGags = parseJsonArray(json.optJSONArray("runningGagsPossibles") ?: json.optJSONArray("runningGags")),
                characterHumorHooks = hooksList,
                comicSituations = parseJsonArray(json.optJSONArray("situationsComiquesRecurrentes") ?: json.optJSONArray("comicSituations")),
                isValidatedInCanon = false
            )
        }

        private fun parseCharacterHooks(rawString: String): List<CharacterHumorHook> {
            if (rawString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(rawString)
                val list = mutableListOf<CharacterHumorHook>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        list.add(CharacterHumorHook.fromJson(obj))
                    }
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun parseJsonArray(jsonArray: JSONArray?): List<String> {
            if (jsonArray == null) return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i, "").trim()
                if (item.isNotEmpty()) list.add(item)
            }
            return list
        }

        private fun parseJsonStringArray(rawString: String): List<String> {
            if (rawString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(rawString)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val item = array.optString(i, "").trim()
                    if (item.isNotEmpty()) list.add(item)
                }
                list
            } catch (_: Exception) {
                rawString.split("\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
}

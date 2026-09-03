package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Modèle de domaine représentant un personnage complet dans la Character Bible de ToonIA.
 * Conforme aux spécifications de la Phase 4 :
 * - nom
 * - rôle dans l'histoire (Protagoniste, Antagoniste, Allié, Mentor, Rival, etc.)
 * - personnalité (traits de caractère, tempérament, faiblesses)
 * - apparence physique détaillée (morphologie, visage, cheveux, yeux, silhouette)
 * - traits distinctifs (cicatrice, tatouage, marque distinctive, tic, accessoire)
 * - vêtements caractéristiques (tenue iconique, style vestimentaire récurrent)
 * - capacités (pouvoirs, compétences martiales, magie, talent intellectuel, etc.)
 * - relations avec les autres personnages (dynamiques, conflits, loyautés)
 * - arc narratif prévu (évolution envisagée, objectif personnel, dilemme)
 * - état actuel (au début de l'histoire : situation, humeur, statut)
 */
data class CharacterBibleEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val name: String,
    val role: String,
    val personality: String,
    val physicalAppearance: String,
    val distinctiveFeatures: String = "",
    val characteristicClothing: String = "",
    val abilities: String = "",
    val relationships: String = "",
    val plannedArc: String = "",
    val currentState: String = "",
    val isValidatedInCanon: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("projectId", projectId)
            put("name", name)
            put("role", role)
            put("personality", personality)
            put("physicalAppearance", physicalAppearance)
            put("distinctiveFeatures", distinctiveFeatures)
            put("characteristicClothing", characteristicClothing)
            put("abilities", abilities)
            put("relationships", relationships)
            put("plannedArc", plannedArc)
            put("currentState", currentState)
            put("isValidatedInCanon", isValidatedInCanon)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): CharacterBibleEntry {
            return CharacterBibleEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                projectId = obj.optString("projectId", ""),
                name = obj.optString("name", "Personnage sans nom"),
                role = obj.optString("role", "Protagoniste"),
                personality = obj.optString("personality", ""),
                physicalAppearance = obj.optString("physicalAppearance", ""),
                distinctiveFeatures = obj.optString("distinctiveFeatures", ""),
                characteristicClothing = obj.optString("characteristicClothing", ""),
                abilities = obj.optString("abilities", ""),
                relationships = obj.optString("relationships", ""),
                plannedArc = obj.optString("plannedArc", ""),
                currentState = obj.optString("currentState", ""),
                isValidatedInCanon = obj.optBoolean("isValidatedInCanon", false)
            )
        }
    }
}

/**
 * Conteneur pour la liste de personnages générés.
 */
data class CharacterBibleResult(
    val characters: List<CharacterBibleEntry>
) {
    fun toJsonString(): String {
        val array = JSONArray()
        characters.forEach { array.put(it.toJson()) }
        return JSONObject().put("characters", array).toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): CharacterBibleResult {
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("characters") ?: JSONArray()
            val list = mutableListOf<CharacterBibleEntry>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item != null) {
                    list.add(CharacterBibleEntry.fromJson(item))
                }
            }
            return CharacterBibleResult(list)
        }
    }
}

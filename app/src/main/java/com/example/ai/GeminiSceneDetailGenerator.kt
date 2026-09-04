package com.example.ai

import com.example.BuildConfig
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.SceneDetailDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Générateur IA pour la Phase 11 : Enrichissement détaillé d'une scène au Storyboard.
 *
 * RÈGLES DU PROMPT MAÎTRE :
 * 1. Modèle : gemini-1.5-flash avec schéma JSON contraint strict (responseSchema).
 * 2. Injection obligatoire du CanonSnapshot (personnages, univers, DA, ton, règles du monde).
 * 3. Approfondit le rythme (pacing), l'arc émotionnel et les micro-beats narratifs d'une scène précise,
 *    SANS encore descendre au niveau panel (réservé à la Phase 12).
 * 4. Vrai appel réseau Gemini, aucune donnée factice.
 */
class GeminiSceneDetailGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_NAME = "gemini-1.5-flash"
        private const val FALLBACK_MODEL_NAME = "gemini-1.5-flash"
    }

    /**
     * Enrichit une scène précise avec du détail de mise en scène, rythme et émotions.
     */
    suspend fun detailScene(
        scene: SceneEntity,
        canonSnapshot: CanonSnapshot,
        chapterSynopsis: String = ""
    ): SceneDetailDraft = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildSceneDetailPrompt(scene, canonSnapshot, chapterSynopsis)
        val requestBodyJson = buildRequestBodyJson(promptText)

        try {
            executeRequest(MODEL_NAME, apiKey, requestBodyJson, scene.id)
        } catch (e: Exception) {
            if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                executeRequest(FALLBACK_MODEL_NAME, apiKey, requestBodyJson, scene.id)
            } else {
                throw e
            }
        }
    }

    fun buildSceneDetailPrompt(
        scene: SceneEntity,
        canonSnapshot: CanonSnapshot,
        chapterSynopsis: String
    ): String {
        return buildString {
            appendLine("Tu es un Storyboarder et Directeur Narratif de haut niveau pour les Webtoons.")
            appendLine("Ta tâche est d'ENRICHIR ET PRÉCISER EN DÉTAIL une scène clé avant son découpage en panels.")
            appendLine()
            appendLine("--- RÈGLE ABSOLUE DE CONTINUITÉ CANONIQUE ---")
            appendLine("Respecte scrupuleusement la cohérence avec le Canon ci-dessous.")
            appendLine("1. Les personnages doivent agir selon leurs motivations et traits canoniques.")
            appendLine("2. Le lieu et l'ambiance doivent être visuellement évocateurs.")
            appendLine("3. Précise le rythme de lecture vertical (accélérations, silences visuels, tension).")
            appendLine("4. Détaille l'arc émotionnel de la scène du début à la fin.")
            appendLine("5. Décompose la scène en 4 à 8 micro-beats narratifs clairs.")
            appendLine("6. IMPORTANT : Ne génère pas encore de panels individuels (c'est l'étape suivante).")
            appendLine()
            appendLine("--- CONTEXTE DU PROJET & DU CANON ---")
            canonSnapshot.project?.let {
                appendLine("Titre du projet : ${it.finalTitle ?: it.title}")
                appendLine("Genre : ${it.genre} | Ton : ${it.tone}")
            }
            if (chapterSynopsis.isNotBlank()) {
                appendLine("Synopsis global du chapitre : $chapterSynopsis")
            }
            if (canonSnapshot.characters.isNotEmpty()) {
                appendLine()
                appendLine("--- PERSONNAGES CANONIQUES ---")
                canonSnapshot.characters.forEach { c ->
                    appendLine("• ${c.name} (${c.role}) : ${c.archetype}, ${c.personalityTraits}")
                    if (c.visualDescription.isNotBlank()) {
                        appendLine("  Visuel : ${c.visualDescription}")
                    }
                }
            }
            if (canonSnapshot.locations.isNotEmpty()) {
                appendLine()
                appendLine("--- LIEUX CANONIQUES ---")
                canonSnapshot.locations.forEach { l ->
                    appendLine("• ${l.name} (${l.atmosphere}) : ${l.description}")
                }
            }
            canonSnapshot.visualBible?.let { vb ->
                appendLine()
                appendLine("--- DIRECTION ARTISTIQUE ---")
                appendLine("Style visuel : ${vb.artStyle} | Palette : ${vb.colorPalette}")
            }
            canonSnapshot.humorDna?.let { hd ->
                if (hd.humorStyle.isNotBlank()) {
                    appendLine("ADN Humour/Ton : ${hd.humorStyle} | Gags récurrents : ${hd.runningGags}")
                }
            }
            appendLine()
            appendLine("--- SCÈNE ACTUELLE À ENRICHIR ---")
            appendLine("Titre : ${scene.title}")
            appendLine("Lieu mentionné : ${scene.locationName.ifBlank { "À préciser selon le canon" }}")
            appendLine("Personnages mentionnés : ${scene.charactersPresent.ifBlank { "À déterminer selon le canon" }}")
            appendLine("Résumé actuel : ${scene.narrativeSummary}")
            if (scene.atmosphere.isNotBlank()) {
                appendLine("Ambiance actuelle : ${scene.atmosphere}")
            }
            if (scene.narrativeBeats.isNotBlank()) {
                appendLine("Beats actuels :\n${scene.narrativeBeats}")
            }
            appendLine()
            appendLine("Fournis un enrichissement de haute qualité cinématographique et webtoon.")
        }
    }

    private fun buildRequestBodyJson(promptText: String): JSONObject {
        val contentPart = JSONObject().apply {
            put("text", promptText)
        }
        val userContent = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply { put(contentPart) })
        }

        val generationConfig = JSONObject().apply {
            put("temperature", 0.7)
            put("topP", 0.95)
            put("responseMimeType", "application/json")
            put("responseSchema", buildJsonSchema())
        }

        return JSONObject().apply {
            put("contents", JSONArray().apply { put(userContent) })
            put("generationConfig", generationConfig)
        }
    }

    private fun buildJsonSchema(): JSONObject {
        val schema = JSONObject()
        schema.put("type", "OBJECT")

        val properties = JSONObject()

        properties.put("enrichedSummary", JSONObject().apply {
            put("type", "STRING")
            put("description", "Résumé narratif approfondi et cinématographique de la scène.")
        })

        properties.put("pacingNotes", JSONObject().apply {
            put("type", "STRING")
            put("description", "Recommandations précises sur le rythme de défilement vertical webtoon (silences, accélérations, tensions).")
        })

        properties.put("emotionalArc", JSONObject().apply {
            put("type", "STRING")
            put("description", "Évolution des émotions des personnages au fil de la scène.")
        })

        properties.put("detailedNarrativeBeats", JSONObject().apply {
            put("type", "ARRAY")
            put("description", "Liste ordonnée des 4 à 8 micro-beats narratifs essentiels composant la scène.")
            put("items", JSONObject().apply { put("type", "STRING") })
        })

        properties.put("atmosphereAndSensory", JSONObject().apply {
            put("type", "STRING")
            put("description", "Description de l'atmosphère, de la lumière, des bruits d'ambiance et des détails sensoriels.")
        })

        properties.put("characterInteractions", JSONObject().apply {
            put("type", "STRING")
            put("description", "Détails des postures, tensions non-dites, dynamiques relationnelles et sous-textes entre personnages.")
        })

        schema.put("properties", properties)

        val requiredArray = JSONArray().apply {
            put("enrichedSummary")
            put("pacingNotes")
            put("emotionalArc")
            put("detailedNarrativeBeats")
            put("atmosphereAndSensory")
            put("characterInteractions")
        }
        schema.put("required", requiredArray)

        return schema
    }

    private fun executeRequest(
        model: String,
        apiKey: String,
        requestBodyJson: JSONObject,
        sceneId: String
    ): SceneDetailDraft {
        val url = "$BASE_URL/$model:generateContent?key=$apiKey"
        val requestBody = requestBodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorDetails = parseErrorMessage(responseBody, response.code)
                throw IOException(errorDetails)
            }

            return parseGeminiResponse(responseBody, sceneId)
        }
    }

    private fun parseErrorMessage(responseBody: String, httpCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: "Code HTTP $httpCode"
            "Erreur Gemini ($httpCode): $message"
        } catch (_: Exception) {
            "Erreur réseau Gemini (Code HTTP $httpCode)"
        }
    }

    private fun parseGeminiResponse(responseBody: String, sceneId: String): SceneDetailDraft {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Aucun candidat retourné par le modèle Gemini.")

        if (candidates.length() == 0) {
            throw IllegalStateException("Réponse Gemini vide.")
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
            ?: throw IllegalStateException("Structure de contenu manquante dans la réponse.")

        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("Parts manquants dans la réponse.")

        if (parts.length() == 0) {
            throw IllegalStateException("Aucune partie texte dans la réponse.")
        }

        val text = parts.getJSONObject(0).optString("text")
        if (text.isBlank()) {
            throw IllegalStateException("Contenu texte vide généré par l'IA.")
        }

        val json = JSONObject(text.trim())

        val enrichedSummary = json.optString("enrichedSummary", "")
        val pacingNotes = json.optString("pacingNotes", "")
        val emotionalArc = json.optString("emotionalArc", "")
        val atmosphereAndSensory = json.optString("atmosphereAndSensory", "")
        val characterInteractions = json.optString("characterInteractions", "")

        val beatsArray = json.optJSONArray("detailedNarrativeBeats") ?: JSONArray()
        val detailedNarrativeBeats = mutableListOf<String>()
        for (i in 0 until beatsArray.length()) {
            detailedNarrativeBeats.add(beatsArray.getString(i))
        }

        return SceneDetailDraft(
            sceneId = sceneId,
            enrichedSummary = enrichedSummary,
            pacingNotes = pacingNotes,
            emotionalArc = emotionalArc,
            detailedNarrativeBeats = detailedNarrativeBeats,
            atmosphereAndSensory = atmosphereAndSensory,
            characterInteractions = characterInteractions
        )
    }
}

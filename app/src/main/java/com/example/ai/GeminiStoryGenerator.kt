package com.example.ai

import com.example.BuildConfig
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.StoryBibleResult
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
 * Générateur IA pour la Story Bible de ToonIA (Phase 6).
 * Utilise gemini-3.1-flash-lite-preview avec un schéma contraint JSON strict (responseSchema).
 *
 * Prérequis et contraintes absolues :
 * - Canon complet actuel en contexte (personnages validés + univers validé : règles, factions, lieux, objets).
 * - Aucun nouveau personnage contredisant la Character Bible ne doit être inventé.
 * - Respect des règles du Prompt Maître (vrai appel réseau, gestion d'erreurs, schéma contraint).
 */
class GeminiStoryGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_NAME = "gemini-3.1-flash-lite-preview"
    }

    /**
     * Génère la Story Bible en s'appuyant obligatoirement sur le Canon complet (Personnages + Univers).
     */
    suspend fun generateStoryBible(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): StoryBibleResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildStoryGenerationPrompt(
            projectTitle = projectTitle,
            projectSynopsis = projectSynopsis,
            projectGenre = projectGenre,
            projectTone = projectTone,
            canonSnapshot = canonSnapshot
        )

        val requestBodyJson = buildRequestBodyJson(promptText)
        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeRequestAndParse(request, canonSnapshot.projectId)
    }

    /**
     * Construit le prompt envoyé à Gemini en intégrant tout le Canon (Personnages + Lieux + Univers).
     */
    fun buildStoryGenerationPrompt(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es le Showrunner et scénariste principal de ToonIA, application de création de webtoons professionnels.")
            appendLine("Ta mission est de construire la 'Story Bible' (Bible de l'Histoire) structurée et captivante pour ce webtoon.")
            appendLine("TITRE : $projectTitle")
            appendLine("SYNOPSIS : $projectSynopsis")
            if (!projectGenre.isNullOrBlank()) appendLine("GENRE D'ORIGINE : $projectGenre")
            if (!projectTone.isNullOrBlank()) appendLine("TON RECHERCHÉ : $projectTone")
            appendLine()

            appendLine("--- CANON COMPLET ACTUEL (IMPÉRATIF ABSOLU DE RESPECT) ---")
            // 1. Personnages canoniques
            appendLine("PERSONNAGES CANONIQUES (Ne PAS inventer de personnage contradictoire) :")
            if (canonSnapshot.characters.isNotEmpty()) {
                canonSnapshot.characters.forEach { c ->
                    appendLine("- ${c.name} [${c.role}] : Traits='${c.personalityTraits}', Arc/Backstory='${c.backstory}'")
                }
            } else {
                appendLine("- Aucun personnage canonique n'est disponible.")
            }
            appendLine()

            // 2. Univers canonique
            appendLine("UNIVERS ET LIEUX CANONIQUES :")
            val wb = canonSnapshot.worldBible
            if (wb != null) {
                if (wb.magicOrTechRules.isNotBlank()) appendLine("- Règles Univers : ${wb.magicOrTechRules}")
                if (wb.factions.isNotBlank()) appendLine("- Factions : ${wb.factions}")
                if (wb.geography.isNotBlank()) appendLine("- Géographie : ${wb.geography}")
                if (wb.historyLore.isNotBlank()) appendLine("- Lore / Passé : ${wb.historyLore}")
            }
            if (canonSnapshot.locations.isNotEmpty()) {
                appendLine("Lieux établis :")
                canonSnapshot.locations.forEach { l ->
                    appendLine("- ${l.name} (${l.atmosphere}) : ${l.description}")
                }
            }
            if (canonSnapshot.storyObjects.isNotEmpty()) {
                appendLine("Objets clés :")
                canonSnapshot.storyObjects.forEach { o ->
                    appendLine("- ${o.name} : ${o.significance}")
                }
            }
            appendLine("----------------------------------------------------------")
            appendLine()

            appendLine("EXIGENCES POUR LA STORY BIBLE :")
            appendLine("1. Prémisse : Le pitch dramatique percutant en 2-3 phrases.")
            appendLine("2. Genre confirmé : Le genre exact consolidé pour le format webtoon.")
            appendLine("3. Thèmes principaux : Les messages profonds explorés (ex: émancipation, survie, fraternité...).")
            appendLine("4. Ton général : L'ambiance émotionnelle et stylistique dominante.")
            appendLine("5. Conflits principaux : Le conflit central (personnel, relationnel et environnemental).")
            appendLine("6. Intrigue principale : La progression globale de l'histoire du début au climax.")
            appendLine("7. Intrigues secondaires : 2 ou 3 subplots pertinents renforçant l'histoire principale.")
            appendLine("8. Direction générale du récit : La fin ou trajectoire ultime visée.")
            appendLine("9. Rythme narratif : La dynamique de lecture webtoon (cliffhangers réguliers, montées en tension).")
            appendLine("10. Événements fondateurs antérieurs au chapitre 1 (TimelineEvents) : 2 à 4 événements cruciaux du passé ayant conduit à la situation initiale, positionnés chronologiquement.")
            appendLine()
            appendLine("Réponds STRICTEMENT selon le schéma JSON contraint fourni.")
        }
    }

    private fun executeRequestAndParse(request: Request, projectId: String): StoryBibleResult {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Erreur réseau lors de la communication avec Gemini pour la Story Bible : ${e.message}", e)
        }

        val responseBodyString = response.body?.string()
        if (!response.isSuccessful || responseBodyString == null) {
            val errorMsg = parseErrorMessage(responseBodyString) ?: "Code HTTP ${response.code}: ${response.message}"
            throw GeminiApiException(code = response.code, message = "Échec de l'API Gemini lors de la génération de l'histoire ($errorMsg)")
        }

        return parseStoryResponse(responseBodyString, projectId)
    }

    private fun parseStoryResponse(responseBody: String, projectId: String): StoryBibleResult {
        val rawJsonText = extractTextFromGeminiResponse(responseBody)
        return try {
            StoryBibleResult.fromJson(rawJsonText, projectId)
        } catch (e: Exception) {
            val sanitized = cleanJsonText(rawJsonText)
            try {
                StoryBibleResult.fromJson(sanitized, projectId)
            } catch (e2: Exception) {
                throw IllegalStateException("Impossible de parser le JSON de la Story Bible : ${e2.message}\nExtrait : ${rawJsonText.take(200)}")
            }
        }
    }

    private fun cleanJsonText(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json")
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```")
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```")
        }
        return text.trim()
    }

    private fun extractTextFromGeminiResponse(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val promptFeedback = root.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            throw GeminiApiException(
                code = 400,
                message = "Gemini a refusé la requête pour la Story Bible. Raison : ${blockReason ?: "filtre de sécurité"}"
            )
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            val finishReason = firstCandidate.optString("finishReason", "UNKNOWN")
            throw GeminiApiException(
                code = 500,
                message = "La réponse de l'IA pour la Story Bible est vide. Raison : $finishReason"
            )
        }

        return parts.getJSONObject(0).getString("text")
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody == null) return null
        return try {
            val root = JSONObject(errorBody)
            val errorObj = root.optJSONObject("error")
            errorObj?.optString("message")
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequestBodyJson(prompt: String): JSONObject {
        val contentsArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put(
                        "parts",
                        JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                    )
                }
            )
        }

        val generationConfig = JSONObject().apply {
            put("temperature", 0.7)
            put("topP", 0.95)
            put("responseMimeType", "application/json")
            put("responseSchema", buildStoryBibleResponseSchema())
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }
    }

    private fun buildStoryBibleResponseSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("premisse", JSONObject().apply { put("type", "STRING"); put("description", "Pitch dramatique et prémisse du webtoon") })
                    put("genreConfirme", JSONObject().apply { put("type", "STRING"); put("description", "Genre narratif confirmé") })
                    put("themesPrincipaux", JSONObject().apply { put("type", "STRING"); put("description", "Thématiques fondamentales explorées") })
                    put("tonGeneral", JSONObject().apply { put("type", "STRING"); put("description", "Tonalité dominante du récit") })
                    put("conflitsPrincipaux", JSONObject().apply { put("type", "STRING"); put("description", "Conflits dramatiques majeurs") })
                    put("intriguePrincipale", JSONObject().apply { put("type", "STRING"); put("description", "Fil conducteur et arc narratif principal") })
                    put("intriguesSecondaires", JSONObject().apply { put("type", "STRING"); put("description", "Arcs narratifs secondaires (subplots)") })
                    put("directionGenerale", JSONObject().apply { put("type", "STRING"); put("description", "Direction finale et climax vers lequel tend l'histoire") })
                    put("rythmeNarratif", JSONObject().apply { put("type", "STRING"); put("description", "Cadence, gestion des montées en tension et pauses") })
                    put(
                        "evenementsFondateurs",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("titre", JSONObject().apply { put("type", "STRING") })
                                            put("description", JSONObject().apply { put("type", "STRING") })
                                            put("epoqueOuDate", JSONObject().apply { put("type", "STRING") })
                                        }
                                    )
                                    put("required", JSONArray().apply { put("titre"); put("description"); put("epoqueOuDate") })
                                }
                            )
                        }
                    )
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("premisse")
                    put("genreConfirme")
                    put("themesPrincipaux")
                    put("tonGeneral")
                    put("conflitsPrincipaux")
                    put("intriguePrincipale")
                    put("intriguesSecondaires")
                    put("directionGenerale")
                    put("rythmeNarratif")
                    put("evenementsFondateurs")
                }
            )
        }
    }
}

package com.example.ai

import com.example.BuildConfig
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.WorldBibleResult
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
 * Générateur IA pour la World Bible de ToonIA (Phase 5).
 * Utilise gemini-3.6-flash avec un schéma contraint JSON strict (responseSchema).
 *
 * Règles absolues :
 * - Pas de simulation, vrai appel réseau.
 * - Le prompt envoyé à Gemini doit explicitement inclure le résumé canon actuel
 *   (récupéré via getCanonSnapshot) pour que les lieux et règles générés soient cohérents
 *   avec les personnages déjà validés.
 */
class GeminiWorldGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_NAME = "gemini-3.6-flash"
    }

    /**
     * Génère l'ensemble de la World Bible à partir du synopsis du projet et du canon actuel.
     */
    suspend fun generateWorldBible(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): WorldBibleResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildFullGenerationPrompt(
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
     * Construit le prompt envoyé à Gemini en intégrant explicitement le résumé canon actuel (personnages validés).
     */
    fun buildFullGenerationPrompt(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es l'architecte narratif et worldbuilder en chef de ToonIA, une application de création de webtoons professionnels.")
            appendLine("Ta tâche est de concevoir la 'World Bible' (Bible de l'Univers) complète, immersive et cohérente pour le projet suivant :")
            appendLine("TITRE DU PROJET : $projectTitle")
            appendLine("SYNOPSIS : $projectSynopsis")
            if (!projectGenre.isNullOrBlank()) appendLine("GENRE : $projectGenre")
            if (!projectTone.isNullOrBlank()) appendLine("TON : $projectTone")
            appendLine()

            // CRITIQUE : Résumé du Canon Actuel
            appendLine("--- MÉMOIRE CANONIQUE EXISTANTE (IMPÉRATIF DE COHÉRENCE) ---")
            if (canonSnapshot.characters.isNotEmpty()) {
                appendLine("Personnages déjà validés dans le canon :")
                canonSnapshot.characters.forEach { char ->
                    appendLine("- ${char.name} (Rôle: ${char.role}, Archétype: ${char.archetype}) :")
                    appendLine("  Traits: ${char.personalityTraits}")
                    appendLine("  Apparence: ${char.visualDescription}")
                    if (char.backstory.isNotBlank()) appendLine("  Backstory: ${char.backstory}")
                }
            } else {
                appendLine("Aucun personnage canonique n'est encore enregistré.")
            }

            if (canonSnapshot.canonMemories.isNotEmpty()) {
                appendLine("Faits et règles déjà gravés dans le canon :")
                canonSnapshot.canonMemories.forEach { mem ->
                    appendLine("- [${mem.category}] ${mem.fact} (Impact: ${mem.impact})")
                }
            }
            appendLine("------------------------------------------------------------")
            appendLine()

            appendLine("EXIGENCES POUR LA WORLD BIBLE :")
            appendLine("1. Règles de l'univers : lois magiques, technologiques, physiques ou métaphysiques qui régissent ce monde.")
            appendLine("2. Factions ou groupes : les organisations, guildes, clans ou alliances en présence et leurs rivalités.")
            appendLine("3. Géographie : les régions clés, territoires, royaumes ou zones urbaines.")
            appendLine("4. Structure sociale : hiérarchie, politique, économie et conditions de vie.")
            appendLine("5. Éléments d'histoire (Lore) : les événements fondateurs passés qui ont façonné le présent.")
            appendLine("6. Terminologie : vocabulaire, expressions, titres ou concepts propres au récit.")
            appendLine("7. Contraintes du monde : règles intangibles et limites strictes à respecter dans toute génération future.")
            appendLine("8. Lieux principaux : nom, description visuelle détaillée, ambiance/atmosphère. Chaque lieu doit être en harmonie avec les personnages établis.")
            appendLine("9. Objets importants : armes légendaires, artefacts, symboles ou objets clés liés à l'intrigue ou aux personnages.")
            appendLine()
            appendLine("Réponds STRICTEMENT selon le schéma JSON contraint fourni.")
        }
    }

    private fun executeRequestAndParse(request: Request, projectId: String): WorldBibleResult {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Erreur réseau lors de la communication avec Gemini pour la World Bible : ${e.message}", e)
        }

        val responseBodyString = response.body?.string()
        if (!response.isSuccessful || responseBodyString == null) {
            val errorMsg = parseErrorMessage(responseBodyString) ?: "Code HTTP ${response.code}: ${response.message}"
            throw GeminiApiException(code = response.code, message = "Échec de l'API Gemini lors de la génération de l'univers ($errorMsg)")
        }

        return parseWorldResponse(responseBodyString, projectId)
    }

    private fun parseWorldResponse(responseBody: String, projectId: String): WorldBibleResult {
        val rawJsonText = extractTextFromGeminiResponse(responseBody)
        return try {
            WorldBibleResult.fromJson(rawJsonText, projectId)
        } catch (e: Exception) {
            val sanitized = cleanJsonText(rawJsonText)
            try {
                WorldBibleResult.fromJson(sanitized, projectId)
            } catch (e2: Exception) {
                throw IllegalStateException("Impossible d'interpréter la réponse JSON de la World Bible : ${e2.message}\nExtrait : ${rawJsonText.take(200)}")
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
                message = "Gemini a refusé la requête ou aucun candidat n'a été retourné. Raison : ${blockReason ?: "filtre de sécurité"}"
            )
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            val finishReason = firstCandidate.optString("finishReason", "UNKNOWN")
            throw GeminiApiException(
                code = 500,
                message = "La réponse de l'IA pour la World Bible est vide. Raison : $finishReason"
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
            put("responseSchema", buildWorldBibleResponseSchema())
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }
    }

    private fun buildWorldBibleResponseSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("reglesUnivers", JSONObject().apply { put("type", "STRING"); put("description", "Règles magiques, scientifiques et lois fondamentales du monde") })
                    put("groupesFactions", JSONObject().apply { put("type", "STRING"); put("description", "Factions, clans, guildes, alliances et rivalités") })
                    put("geographie", JSONObject().apply { put("type", "STRING"); put("description", "Géographie, régions et paysages principaux") })
                    put("structureSociale", JSONObject().apply { put("type", "STRING"); put("description", "Organisation politique, classes sociales et vie quotidienne") })
                    put("elementsHistoire", JSONObject().apply { put("type", "STRING"); put("description", "Histoire et événements marquants du passé (lore)") })
                    put("terminologie", JSONObject().apply { put("type", "STRING"); put("description", "Termes spécifiques, rangs, titres ou concepts propres au récit") })
                    put("contraintesMonde", JSONObject().apply { put("type", "STRING"); put("description", "Contraintes intangibles à toujours respecter lors des générations futures") })
                    put(
                        "lieuxPrincipaux",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("nom", JSONObject().apply { put("type", "STRING") })
                                            put("description", JSONObject().apply { put("type", "STRING") })
                                            put("ambiance", JSONObject().apply { put("type", "STRING") })
                                        }
                                    )
                                    put("required", JSONArray().apply { put("nom"); put("description"); put("ambiance") })
                                }
                            )
                        }
                    )
                    put(
                        "objetsImportants",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("nom", JSONObject().apply { put("type", "STRING") })
                                            put("importance", JSONObject().apply { put("type", "STRING") })
                                            put("description", JSONObject().apply { put("type", "STRING") })
                                        }
                                    )
                                    put("required", JSONArray().apply { put("nom"); put("importance"); put("description") })
                                }
                            )
                        }
                    )
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("reglesUnivers")
                    put("groupesFactions")
                    put("geographie")
                    put("structureSociale")
                    put("elementsHistoire")
                    put("terminologie")
                    put("contraintesMonde")
                    put("lieuxPrincipaux")
                    put("objetsImportants")
                }
            )
        }
    }
}

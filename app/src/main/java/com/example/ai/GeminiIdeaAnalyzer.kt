package com.example.ai

import com.example.BuildConfig
import com.example.domain.model.AdvancedIdeaDetails
import com.example.domain.model.IdeaAnalysisResult
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
 * Service d'analyse d'idée de webtoon via l'API Gemini réelle avec schéma contraint.
 * Respecte strictement la règle : pas de simulation, gestion d'erreurs réseau, HTTP et parsing.
 */
class GeminiIdeaAnalyzer(
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
    }

    /**
     * Analyse l'idée de webtoon fournie par l'utilisateur ainsi que les détails avancés.
     * @throws IllegalStateException Si la clé API est manquante
     * @throws IOException En cas de coupure ou d'erreur réseau
     * @throws GeminiApiException En cas d'erreur retournée par l'API Gemini
     * @throws Exception En cas de JSON corrompu ou invalide
     */
    suspend fun analyzeIdea(
        freeIdeaText: String,
        advancedDetails: AdvancedIdeaDetails? = null
    ): IdeaAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildPrompt(freeIdeaText, advancedDetails)
        val requestBodyJson = buildRequestBodyJson(promptText)

        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Erreur réseau : Impossible de contacter l'API Gemini (${e.localizedMessage ?: "connexion indisponible"}). Vérifiez votre connexion internet.", e)
        }

        response.use { resp ->
            val responseBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val errorDetails = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: responseBody
                } catch (_: Exception) {
                    responseBody
                }
                throw GeminiApiException(
                    code = resp.code,
                    message = "Erreur de l'API Gemini (${resp.code}) : $errorDetails"
                )
            }

            if (responseBody.isBlank()) {
                throw GeminiApiException(
                    code = resp.code,
                    message = "Réponse vide reçue de l'API Gemini."
                )
            }

            val rawStructuredJson = extractStructuredJson(responseBody)
            try {
                IdeaAnalysisResult.fromJsonString(rawStructuredJson)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Format de réponse JSON non conforme ou invalide retourné par l'IA : ${e.message}",
                    e
                )
            }
        }
    }

    private fun buildPrompt(freeIdeaText: String, details: AdvancedIdeaDetails?): String {
        val sb = StringBuilder()
        sb.append("Tu es le moteur narratif d'analyse de webtoon de ToonIA.\n")
        sb.append("Analyse l'idée de webtoon suivante écrite par l'utilisateur et décompose-la de manière rigoureuse.\n\n")
        sb.append("--- IDÉE LIBRE DE L'UTILISATEUR ---\n")
        sb.append(freeIdeaText.trim()).append("\n\n")

        if (details != null && details.hasAnyDetails()) {
            sb.append("--- DÉTAILS AVANCÉS OPTIONNELS FOURNIS PAR L'UTILISATEUR ---\n")
            if (details.genre.isNotBlank()) sb.append("- Genre souhaité : ${details.genre}\n")
            if (details.ambiance.isNotBlank()) sb.append("- Ambiance : ${details.ambiance}\n")
            if (details.epoque.isNotBlank()) sb.append("- Époque / Contexte temporel : ${details.epoque}\n")
            if (details.personnagesSouhaites.isNotBlank()) sb.append("- Personnages souhaités : ${details.personnagesSouhaites}\n")
            if (details.inspirations.isNotBlank()) sb.append("- Inspirations : ${details.inspirations}\n")
            if (details.longueurVisee.isNotBlank()) sb.append("- Longueur visée : ${details.longueurVisee}\n")
            if (details.publicCible.isNotBlank()) sb.append("- Public cible : ${details.publicCible}\n")
            if (details.contraintes.isNotBlank()) sb.append("- Contraintes : ${details.contraintes}\n")
            if (details.evenementsSouhaites.isNotBlank()) sb.append("- Événements souhaités : ${details.evenementsSouhaites}\n")
            sb.append("\n")
        }

        sb.append("CONSIGNES :\n")
        sb.append("1. Extrais avec clarté le genre principal (Action, Fantasy, Romance, Tranche de vie, Thriller, etc.).\n")
        sb.append("2. Identifie les sous-genres pertinents.\n")
        sb.append("3. Détermine le ton général (Épique, Sombre, Humoristique, Doux-amer, Mystérieux, etc.).\n")
        sb.append("4. Liste les thèmes majeurs détectés.\n")
        sb.append("5. Liste tous les éléments narratifs explicitement mentionnés par l'utilisateur (personnages, prémisses, lieux, incidents déclencheurs).\n")
        sb.append("6. Si l'idée est floue, trop brève ou incomplète, formule une liste de questions ciblées (ambiguïtés à clarifier). Si l'idée est très complète, liste les aspects narratifs restant à préciser pour le worldbuilding.\n")
        return sb.toString()
    }

    private fun buildRequestBodyJson(prompt: String): JSONObject {
        val root = JSONObject()

        // contents
        val contents = JSONArray()
        val contentObj = JSONObject()
        val parts = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        parts.put(partObj)
        contentObj.put("parts", parts)
        contents.put(contentObj)
        root.put("contents", contents)

        // generationConfig avec responseMimeType et responseSchema
        val generationConfig = JSONObject()
        generationConfig.put("responseMimeType", "application/json")

        val schema = JSONObject()
        schema.put("type", "OBJECT")

        val properties = JSONObject()

        val propGenre = JSONObject().apply {
            put("type", "STRING")
            put("description", "Genre principal du webtoon")
        }
        val propSousGenres = JSONObject().apply {
            put("type", "ARRAY")
            put("items", JSONObject().apply { put("type", "STRING") })
            put("description", "Sous-genres identifiés")
        }
        val propTon = JSONObject().apply {
            put("type", "STRING")
            put("description", "Ton global de l'histoire")
        }
        val propThemes = JSONObject().apply {
            put("type", "ARRAY")
            put("items", JSONObject().apply { put("type", "STRING") })
            put("description", "Thèmes narratifs détectés")
        }
        val propNarratifs = JSONObject().apply {
            put("type", "ARRAY")
            put("items", JSONObject().apply { put("type", "STRING") })
            put("description", "Éléments narratifs explicitement formulés par l'utilisateur")
        }
        val propAmbiguites = JSONObject().apply {
            put("type", "ARRAY")
            put("items", JSONObject().apply { put("type", "STRING") })
            put("description", "Questions ou ambiguïtés à clarifier avec l'auteur")
        }

        properties.put("genrePrincipal", propGenre)
        properties.put("sousGenres", propSousGenres)
        properties.put("ton", propTon)
        properties.put("themesDetectes", propThemes)
        properties.put("elementsNarratifsExplicites", propNarratifs)
        properties.put("ambiguitesAClarifier", propAmbiguites)

        val requiredList = JSONArray().apply {
            put("genrePrincipal")
            put("sousGenres")
            put("ton")
            put("themesDetectes")
            put("elementsNarratifsExplicites")
            put("ambiguitesAClarifier")
        }

        schema.put("properties", properties)
        schema.put("required", requiredList)

        generationConfig.put("responseSchema", schema)
        generationConfig.put("temperature", 0.4)

        root.put("generationConfig", generationConfig)
        return root
    }

    private fun extractStructuredJson(geminiResponseBody: String): String {
        val root = JSONObject(geminiResponseBody)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val promptFeedback = root.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            throw GeminiApiException(
                code = 400,
                message = "Aucun candidat de réponse retourné par Gemini. Raison possible : ${blockReason ?: "filtre de sécurité ou prompt rejeté"}"
            )
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            val finishReason = firstCandidate.optString("finishReason", "UNKNOWN")
            throw GeminiApiException(
                code = 500,
                message = "La réponse de l'IA ne contient aucune partie de texte. Raison de fin : $finishReason"
            )
        }

        return parts.getJSONObject(0).getString("text")
    }
}

package com.example.ai

import com.example.BuildConfig
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.CharacterBibleEntry
import com.example.domain.model.CharacterBibleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Service IA chargé de la génération de la Character Bible pour ToonIA.
 * Respecte strictement :
 * - Sortie JSON contrainte via responseSchema
 * - Ancrage dans le CanonSnapshot actuel (personnages déjà existants, mémoires, règles univers)
 * - Ne simule JAMAIS un appel IA : exceptions explicites pour réseau ou clé manquante
 * - Permet la génération complète ou ciblée d'un seul personnage (sans régénérer les autres).
 */
class GeminiCharacterGenerator(
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
     * Génère un ensemble de personnages pour le projet à partir de l'analyse validée et du snapshot du canon.
     */
    suspend fun generateCharacterBible(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): CharacterBibleResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
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
     * Régénère spécifiquement UN personnage précis sans toucher aux autres,
     * en s'appuyant sur son profil actuel et le canon existant.
     */
    suspend fun regenerateSingleCharacter(
        targetCharacter: CharacterBibleEntry,
        userGuidance: String?,
        projectTitle: String,
        projectSynopsis: String,
        canonSnapshot: CanonSnapshot
    ): CharacterBibleEntry = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildSingleRegenerationPrompt(
            targetCharacter = targetCharacter,
            userGuidance = userGuidance,
            projectTitle = projectTitle,
            projectSynopsis = projectSynopsis,
            canonSnapshot = canonSnapshot
        )

        val requestBodyJson = buildSingleCharacterRequestBodyJson(promptText)
        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeSingleRequestAndParse(request, targetCharacter.id, canonSnapshot.projectId)
    }

    private fun executeRequestAndParse(request: Request, projectId: String): CharacterBibleResult {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Erreur réseau lors de la génération des personnages : ${e.localizedMessage ?: "Vérifiez votre connexion internet."}", e)
        }

        val responseBodyString = response.body?.string()
        if (!response.isSuccessful || responseBodyString == null) {
            val errorMsg = parseErrorMessage(responseBodyString) ?: "Code HTTP ${response.code}: ${response.message}"
            throw GeminiApiException(code = response.code, message = "Échec de l'API Gemini lors de la génération des personnages ($errorMsg)")
        }

        return parseCharactersResponse(responseBodyString, projectId)
    }

    private fun executeSingleRequestAndParse(request: Request, characterId: String, projectId: String): CharacterBibleEntry {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Erreur réseau lors de la régénération du personnage : ${e.localizedMessage ?: "Vérifiez votre connexion."}", e)
        }

        val responseBodyString = response.body?.string()
        if (!response.isSuccessful || responseBodyString == null) {
            val errorMsg = parseErrorMessage(responseBodyString) ?: "Code HTTP ${response.code}: ${response.message}"
            throw GeminiApiException(code = response.code, message = "Échec de l'API Gemini lors de la régénération du personnage ($errorMsg)")
        }

        return parseSingleCharacterResponse(responseBodyString, characterId, projectId)
    }

    private fun parseCharactersResponse(jsonResponse: String, projectId: String): CharacterBibleResult {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException("Aucune réponse générée par l'API Gemini.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text")

            if (rawText.isNullOrBlank()) {
                throw IllegalStateException("Contenu de réponse Gemini vide.")
            }

            val structuredJson = JSONObject(rawText.trim())
            val charsArray = structuredJson.optJSONArray("characters") ?: JSONArray()
            val list = mutableListOf<CharacterBibleEntry>()

            for (i in 0 until charsArray.length()) {
                val item = charsArray.getJSONObject(i)
                list.add(
                    CharacterBibleEntry(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        name = item.optString("nom", "Sans nom").trim(),
                        role = item.optString("role", "Protagoniste").trim(),
                        personality = item.optString("personnalite", "").trim(),
                        physicalAppearance = item.optString("apparencePhysique", "").trim(),
                        distinctiveFeatures = item.optString("traitsDistinctifs", "").trim(),
                        characteristicClothing = item.optString("vetementsCaracteristiques", "").trim(),
                        abilities = item.optString("capacites", "").trim(),
                        relationships = item.optString("relations", "").trim(),
                        plannedArc = item.optString("arcNarratifPrevu", "").trim(),
                        currentState = item.optString("etatActuelDebut", "").trim(),
                        isValidatedInCanon = false
                    )
                )
            }

            if (list.isEmpty()) {
                throw IllegalStateException("La liste de personnages retournée par l'IA est vide.")
            }

            CharacterBibleResult(list)
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Format JSON non conforme retourné par le modèle d'IA : ${e.message}", e)
        }
    }

    private fun parseSingleCharacterResponse(jsonResponse: String, characterId: String, projectId: String): CharacterBibleEntry {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.getJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text")

            if (rawText.isNullOrBlank()) {
                throw IllegalStateException("Contenu de réponse Gemini vide.")
            }

            val item = JSONObject(rawText.trim())
            CharacterBibleEntry(
                id = characterId,
                projectId = projectId,
                name = item.optString("nom", "Sans nom").trim(),
                role = item.optString("role", "Protagoniste").trim(),
                personality = item.optString("personnalite", "").trim(),
                physicalAppearance = item.optString("apparencePhysique", "").trim(),
                distinctiveFeatures = item.optString("traitsDistinctifs", "").trim(),
                characteristicClothing = item.optString("vetementsCaracteristiques", "").trim(),
                abilities = item.optString("capacites", "").trim(),
                relationships = item.optString("relations", "").trim(),
                plannedArc = item.optString("arcNarratifPrevu", "").trim(),
                currentState = item.optString("etatActuelDebut", "").trim(),
                isValidatedInCanon = false
            )
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Format JSON du personnage non conforme : ${e.message}", e)
        }
    }

    private fun parseErrorMessage(responseBody: String?): String? {
        if (responseBody == null) return null
        return try {
            val errorObj = JSONObject(responseBody).optJSONObject("error")
            errorObj?.optString("message")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFullGenerationPrompt(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        val canonContext = buildCanonContextString(canonSnapshot)

        return """
Tu es le directeur narratif et créateur de personnages principal de l'application ToonIA, spécialisée dans la création de webtoons.
Ta mission est de générer la Character Bible (3 à 5 personnages principaux et secondaires clés) pour le projet webtoon ci-dessous.

=== INFORMATIONS DU PROJET ===
Titre : ${projectTitle.ifBlank { "Nouveau Webtoon" }}
Genre : ${projectGenre ?: "Non spécifié"}
Ton : ${projectTone ?: "Non spécifié"}
Synopsis / Idée validée :
$projectSynopsis

$canonContext

=== INSTRUCTIONS ET RÈGLES DE CONCEPTION ===
1. Crée un casting équilibré et complémentaire (ex: Protagoniste, Antagoniste ou Rival, Allié proche, Mentor ou Secondaire pivot).
2. Chaque personnage doit être immédiatement identifiable visuellement (règle webtoon : silhouette claire, couleurs fortes, traits marquants).
3. Pour chaque personnage, renseigne rigoureusement :
   - nom : nom complet ou surnom
   - role : Protagoniste, Antagoniste, Allié, Mentor, Rival, etc.
   - personnalite : traits dominants, défauts, motivations profondes
   - apparencePhysique : morphologie, visage, cheveux (couleur/forme), yeux (couleur), taille
   - traitsDistinctifs : cicatrice, marqueur visuel, accessoire distinctif
   - vetementsCaracteristiques : tenue récurrente facilement dessinable en webtoon
   - capacites : pouvoirs, compétences spécifiques, aptitudes de combat ou mentales
   - relations : dynamique avec les autres personnages du casting
   - arcNarratifPrevu : trajectoire dramatique dans l'histoire
   - etatActuelDebut : situation initiale au début du Chapitre 1
4. Ne contredis JAMAIS les faits déjà établis dans le canon.
5. Respecte impérativement le schéma JSON demandé.
        """.trimIndent()
    }

    private fun buildSingleRegenerationPrompt(
        targetCharacter: CharacterBibleEntry,
        userGuidance: String?,
        projectTitle: String,
        projectSynopsis: String,
        canonSnapshot: CanonSnapshot
    ): String {
        val canonContext = buildCanonContextString(canonSnapshot)

        return """
Tu es le directeur narratif de l'application ToonIA.
Ta mission est de régénérer et affiner spécifiquement ce personnage unique de la Character Bible, sans altérer les autres personnages.

=== PROJET ===
Titre : $projectTitle
Synopsis : $projectSynopsis

=== PERSONNAGE À RÉGÉNÉRER / AMÉLIORER ===
Nom actuel : ${targetCharacter.name}
Rôle actuel : ${targetCharacter.role}
Personnalité actuelle : ${targetCharacter.personality}
Apparence actuelle : ${targetCharacter.physicalAppearance}
Traits distinctifs : ${targetCharacter.distinctiveFeatures}
Vêtements : ${targetCharacter.characteristicClothing}
Capacités : ${targetCharacter.abilities}
Relations : ${targetCharacter.relationships}
Arc prévu : ${targetCharacter.plannedArc}
État initial : ${targetCharacter.currentState}

${if (!userGuidance.isNullOrBlank()) "=== DIRECTIVES PARTICULIÈRES DE L'AUTEUR ===\n$userGuidance" else ""}

$canonContext

=== INSTRUCTIONS ===
Régénère une version complète et peaufinée de ce personnage en prenant en compte les directives de l'auteur tout en restant cohérent avec le webtoon.
Renvoie un objet JSON unique conforme au schéma.
        """.trimIndent()
    }

    private fun buildCanonContextString(canonSnapshot: CanonSnapshot): String {
        val sb = StringBuilder()
        sb.append("=== ÉLÉMENTS VALIDÉS DANS LE CANON ACTUEL ===\n")

        if (canonSnapshot.characters.isNotEmpty()) {
            sb.append("Personnages déjà inscrits au canon :\n")
            canonSnapshot.characters.forEach {
                sb.append("- ${it.name} (${it.role}) : ${it.visualDescription}\n")
            }
        } else {
            sb.append("Aucun personnage préalablement validé au canon.\n")
        }

        if (canonSnapshot.canonMemories.isNotEmpty()) {
            sb.append("Faits et règles absolues du canon :\n")
            canonSnapshot.canonMemories.forEach {
                sb.append("- [${it.category}] ${it.fact}\n")
            }
        }

        return sb.toString()
    }

    private fun buildRequestBodyJson(prompt: String): JSONObject {
        val contentsArray = JSONArray().apply {
            put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        }

        val characterSchema = buildSingleCharacterSchema()

        val rootSchema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put(
                        "characters",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("description", "Liste des personnages créés pour le webtoon")
                            put("items", characterSchema)
                        }
                    )
                }
            )
            put("required", JSONArray().put("characters"))
        }

        val generationConfig = JSONObject().apply {
            put("responseMimeType", "application/json")
            put("responseSchema", rootSchema)
            put("temperature", 0.7)
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }
    }

    private fun buildSingleCharacterRequestBodyJson(prompt: String): JSONObject {
        val contentsArray = JSONArray().apply {
            put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        }

        val characterSchema = buildSingleCharacterSchema()

        val generationConfig = JSONObject().apply {
            put("responseMimeType", "application/json")
            put("responseSchema", characterSchema)
            put("temperature", 0.7)
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }
    }

    private fun buildSingleCharacterSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("nom", JSONObject().put("type", "STRING").put("description", "Nom du personnage"))
                    put("role", JSONObject().put("type", "STRING").put("description", "Rôle narratif : Protagoniste, Antagoniste, Allié, Mentor, Rival..."))
                    put("personnalite", JSONObject().put("type", "STRING").put("description", "Description de la personnalité, tempérament, qualités et défauts"))
                    put("apparencePhysique", JSONObject().put("type", "STRING").put("description", "Description physique détaillée : morphologie, visage, cheveux, yeux"))
                    put("traitsDistinctifs", JSONObject().put("type", "STRING").put("description", "Signes distinctifs, cicatrices, tatouages, marques, tics"))
                    put("vetementsCaracteristiques", JSONObject().put("type", "STRING").put("description", "Vêtements caractéristiques et style vestimentaire"))
                    put("capacites", JSONObject().put("type", "STRING").put("description", "Compétences, pouvoirs, techniques ou aptitudes spéciales"))
                    put("relations", JSONObject().put("type", "STRING").put("description", "Relations et liens avec les autres personnages du webtoon"))
                    put("arcNarratifPrevu", JSONObject().put("type", "STRING").put("description", "Évolution prévue du personnage au fil de l'histoire"))
                    put("etatActuelDebut", JSONObject().put("type", "STRING").put("description", "État psychologique et situation au début de l'histoire"))
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("nom")
                    put("role")
                    put("personnalite")
                    put("apparencePhysique")
                    put("traitsDistinctifs")
                    put("vetementsCaracteristiques")
                    put("capacites")
                    put("relations")
                    put("arcNarratifPrevu")
                    put("etatActuelDebut")
                }
            )
        }
    }
}

package com.example.ai

import com.example.BuildConfig
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.ChapterScriptDraft
import com.example.domain.model.SceneScriptDraft
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
 * Générateur IA pour la Phase 10 : Script du Chapitre 1 de ToonIA.
 *
 * RÈGLES DU PROMPT MAÎTRE :
 * 1. Modèle : gemini-3.5-flash avec schéma JSON contraint strict (responseSchema).
 * 2. Injection obligatoire de l'intégralité du Canon validé (getCanonSnapshot).
 * 3. Cohérence narrative stricte : respect des personnages, lieux et règles de la Story/World Bible.
 * 4. Pacing adapté au webtoon (rythme vertical, accroche percutante, cliffhanger de fin de chapitre).
 * 5. Pas de découpage en panels à ce stade (réservé à la Phase 12).
 * 6. Vrai appel réseau Gemini, aucune donnée factice.
 */
class GeminiChapterScriptGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val FALLBACK_MODEL_NAME = "gemini-2.5-flash"
    }

    /**
     * Génère le script structuré du Chapitre 1 en s'appuyant sur le canon complet.
     */
    suspend fun generateChapter1Script(
        userOrientation: String?,
        canonSnapshot: CanonSnapshot
    ): ChapterScriptDraft = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildChapterPrompt(
            chapterNumber = 1,
            userOrientation = userOrientation,
            canonSnapshot = canonSnapshot
        )

        val requestBodyJson = buildRequestBodyJson(promptText)

        try {
            executeRequest(MODEL_NAME, apiKey, requestBodyJson)
        } catch (e: Exception) {
            // Fallback sur gemini-2.5-flash si le modèle 3.5 renvoie 404
            if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                executeRequest(FALLBACK_MODEL_NAME, apiKey, requestBodyJson)
            } else {
                throw e
            }
        }
    }

    /**
     * Construit le prompt avec l'intégralité du canon (Personnages, Univers, Histoire, DA, Humour, Titre, Synopsis).
     */
    fun buildChapterPrompt(
        chapterNumber: Int,
        userOrientation: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es le Showrunner et Scénariste Principal d'une franchise majeure de Webtoons à succès.")
            appendLine("Ta mission est de rédiger le SCRIPT COMPLET ET STRUCTURÉ du Chapitre $chapterNumber.")
            appendLine()
            appendLine("--- RÈGLE ABSOLUE DE CONTINUITÉ CANONIQUE ---")
            appendLine("Tu dois t'appuyer OBLIGATOIREMENT et STRICTEMENT sur les éléments du canon validé ci-dessous.")
            appendLine("1. Utilise les personnages canoniques avec leurs personnalités, motivations et caractéristiques établies.")
            appendLine("2. Situe l'action dans les lieux canoniques ou dans une extension strictement logique de l'univers.")
            appendLine("3. Respecte les règles du monde (magie, technologie, interdits et lois) sans aucune contradiction.")
            appendLine("4. Respecte l'arc narratif et l'incident déclencheur définis dans la Story Bible.")
            appendLine("5. Intègre la tonalité et l'ADN humoristique (gags récurrents, punchlines) si approprié.")
            appendLine("6. Découpe ce chapitre en une suite ordonnée de 3 à 5 SCÈNES narratives majeures.")
            appendLine("7. IMPORTANT : Ne découpe PAS encore en panels individuels (cela sera fait dans une phase ultérieure).")
            appendLine()

            appendLine("--- CANON OFFICIEL DU PROJET ---")
            val title = canonSnapshot.project?.finalTitle ?: canonSnapshot.project?.title ?: "Webtoon"
            val synopsis = canonSnapshot.project?.finalSynopsis ?: canonSnapshot.project?.synopsis ?: ""
            appendLine("Titre de l'œuvre : $title")
            appendLine("Synopsis général : $synopsis")
            canonSnapshot.project?.genre?.let { if (it.isNotBlank()) appendLine("Genre : $it") }
            canonSnapshot.project?.tone?.let { if (it.isNotBlank()) appendLine("Tonalité : $it") }
            appendLine()

            // Personnages
            if (canonSnapshot.characters.isNotEmpty()) {
                appendLine("[PERSONNAGES VALIDÉS AU CANON] :")
                canonSnapshot.characters.forEach { char ->
                    appendLine("- ${char.name} (${char.role}) : Archetype='${char.archetype}', Traits='${char.personalityTraits}', Backstory='${char.backstory}'")
                }
                appendLine()
            }

            // Lieux
            if (canonSnapshot.locations.isNotEmpty()) {
                appendLine("[LIEUX VALIDÉS AU CANON] :")
                canonSnapshot.locations.forEach { loc ->
                    appendLine("- ${loc.name} : Atmosphère='${loc.atmosphere}', Description='${loc.description}'")
                }
                appendLine()
            }

            // Story Bible
            canonSnapshot.storyBible?.let { sb ->
                appendLine("[STORY BIBLE] :")
                if (sb.mainTheme.isNotBlank()) appendLine("- Thème central : ${sb.mainTheme}")
                if (sb.narrativeArc.isNotBlank()) appendLine("- Arc narratif : ${sb.narrativeArc}")
                if (sb.climaxDescription.isNotBlank()) appendLine("- Climax : ${sb.climaxDescription}")
                if (sb.narrativePacing.isNotBlank()) appendLine("- Rythme : ${sb.narrativePacing}")
                appendLine()
            }

            // World Bible
            canonSnapshot.worldBible?.let { wb ->
                appendLine("[WORLD BIBLE] :")
                if (wb.magicOrTechRules.isNotBlank()) appendLine("- Règles Magie/Tech : ${wb.magicOrTechRules}")
                if (wb.factions.isNotBlank()) appendLine("- Factions : ${wb.factions}")
                if (wb.geography.isNotBlank()) appendLine("- Géographie : ${wb.geography}")
                if (wb.socialStructure.isNotBlank()) appendLine("- Structure sociale : ${wb.socialStructure}")
                if (wb.historyLore.isNotBlank()) appendLine("- Histoire & Lore : ${wb.historyLore}")
                appendLine()
            }

            // Visual Bible & Humor DNA
            canonSnapshot.visualBible?.let { vb ->
                if (vb.artStyle.isNotBlank() || vb.panelingStyle.isNotBlank()) {
                    appendLine("[DIRECTION ARTISTIQUE & RYTHME] : Style='${vb.artStyle}', Paneling='${vb.panelingStyle}'")
                }
            }
            canonSnapshot.humorDna?.let { hd ->
                if (hd.humorStyle.isNotBlank()) {
                    appendLine("[ADN HUMORISTIQUE] : Style='${hd.humorStyle}', Gags='${hd.runningGags}', Chibi='${hd.chibiMomentsAllowed}'")
                }
            }
            appendLine()

            // Orientation optionnelle de l'utilisateur
            if (!userOrientation.isNullOrBlank()) {
                appendLine("--- ORIENTATION SPÉCIFIQUE DEMANDÉE PAR L'AUTEUR POUR LE CHAPITRE $chapterNumber ---")
                appendLine(userOrientation)
                appendLine()
            } else {
                appendLine("--- ORIENTATION NARRATIVE ---")
                appendLine("Rédige l'ouverture idéale du Chapitre 1 comme la mise en place saisissante de l'incident déclencheur de la Story Bible.")
                appendLine()
            }

            appendLine("--- FORMAT ATTENDU DU SCRIPT (JSON STRICT) ---")
            appendLine("Chaque scène doit comporter :")
            appendLine("1. orderIndex : index de la scène (commençant à 1)")
            appendLine("2. title : titre évocateur de la scène (ex: 'L'éveil des brumes')")
            appendLine("3. locationName : nom précis du lieu où la scène se déroule (doit correspondre ou être cohérent avec un lieu canon)")
            appendLine("4. charactersPresent : liste des noms des personnages présents dans cette scène")
            appendLine("5. summary : résumé narratif complet et détaillé des actions, révélations et émotions")
            appendLine("6. narrativeBeats : liste de 3 à 5 beats narratifs ordonnés chronologiquement (moments-clés / tournants)")
            appendLine("Le script doit se terminer par un cliffhanger ou une forte tension invitant à lire le Chapitre 2.")
            appendLine("Renvoie UNIQUEMENT le JSON conforme au schéma.")
        }
    }

    private suspend fun executeRequest(
        model: String,
        apiKey: String,
        requestBodyJson: JSONObject
    ): ChapterScriptDraft {
        val url = "$BASE_URL/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("Erreur réseau lors de la génération du script : ${e.localizedMessage}", e)
        }

        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            throw IOException(errorMsg)
        }

        return parseScriptResponse(responseBody)
    }

    private fun parseScriptResponse(responseBody: String): ChapterScriptDraft {
        val rootJson = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw IOException("Réponse JSON invalide reçue de Gemini : ${e.localizedMessage}")
        }

        val candidates = rootJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IOException("Aucun candidat retourné par le modèle Gemini.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            throw IOException("Contenu vide retourné par le modèle Gemini.")
        }

        val rawText = parts.getJSONObject(0).optString("text", "")
        if (rawText.isBlank()) {
            throw IOException("Texte vide dans la réponse de Gemini.")
        }

        val cleanJsonString = extractJsonFromResponse(rawText)
        val scriptJson = try {
            JSONObject(cleanJsonString)
        } catch (e: Exception) {
            throw IOException("Le texte retourné par Gemini n'est pas un JSON valide : $rawText")
        }

        val chapterTitle = scriptJson.optString("chapterTitle", "Chapitre 1")
        val chapterSynopsis = scriptJson.optString("chapterSynopsis", "")

        val scenesArray = scriptJson.optJSONArray("scenes") ?: JSONArray()
        val scenes = mutableListOf<SceneScriptDraft>()

        for (i in 0 until scenesArray.length()) {
            val sceneObj = scenesArray.getJSONObject(i)
            val orderIndex = sceneObj.optInt("orderIndex", i + 1)
            val title = sceneObj.optString("title", "Scène ${i + 1}")
            val locationName = sceneObj.optString("locationName", "Lieu inconnu")

            val charsArray = sceneObj.optJSONArray("charactersPresent") ?: JSONArray()
            val charactersPresent = mutableListOf<String>()
            for (j in 0 until charsArray.length()) {
                val c = charsArray.optString(j)
                if (c.isNotBlank()) charactersPresent.add(c)
            }

            val summary = sceneObj.optString("summary", "")

            val beatsArray = sceneObj.optJSONArray("narrativeBeats") ?: JSONArray()
            val narrativeBeats = mutableListOf<String>()
            for (k in 0 until beatsArray.length()) {
                val b = beatsArray.optString(k)
                if (b.isNotBlank()) narrativeBeats.add(b)
            }

            scenes.add(
                SceneScriptDraft(
                    orderIndex = orderIndex,
                    title = title,
                    locationName = locationName,
                    charactersPresent = charactersPresent,
                    summary = summary,
                    narrativeBeats = narrativeBeats
                )
            )
        }

        return ChapterScriptDraft(
            chapterNumber = 1,
            title = chapterTitle,
            synopsis = chapterSynopsis,
            scenes = scenes
        )
    }

    private fun buildRequestBodyJson(promptText: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptText)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
                put("responseSchema", buildScriptResponseSchema())
            })
        }
    }

    private fun buildScriptResponseSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("chapterTitle", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Titre du chapitre 1")
                })
                put("chapterSynopsis", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Résumé synthétique des événements majeurs de ce premier chapitre")
                })
                put("scenes", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("orderIndex", JSONObject().apply {
                                put("type", "INTEGER")
                                put("description", "Index d'ordre de la scène (1, 2, 3...)")
                            })
                            put("title", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Titre de la scène")
                            })
                            put("locationName", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Nom du lieu canonique dans lequel se passe la scène")
                            })
                            put("charactersPresent", JSONObject().apply {
                                put("type", "ARRAY")
                                put("items", JSONObject().apply { put("type", "STRING") })
                                put("description", "Liste des personnages canoniques présents")
                            })
                            put("summary", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Résumé narratif des événements et dialogues clés de la scène")
                            })
                            put("narrativeBeats", JSONObject().apply {
                                put("type", "ARRAY")
                                put("items", JSONObject().apply { put("type", "STRING") })
                                put("description", "Moments-clés chronologiques de la scène")
                            })
                        })
                        put("required", JSONArray().apply {
                            put("orderIndex")
                            put("title")
                            put("locationName")
                            put("charactersPresent")
                            put("summary")
                            put("narrativeBeats")
                        })
                    })
                })
            })
            put("required", JSONArray().apply {
                put("chapterTitle")
                put("chapterSynopsis")
                put("scenes")
            })
        }
    }

    private fun extractJsonFromResponse(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        return text
    }

    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")
            if (!message.isNullOrBlank()) {
                "Erreur Gemini ($statusCode) : $message"
            } else {
                "Erreur API Gemini HTTP $statusCode"
            }
        } catch (_: Exception) {
            "Erreur API Gemini HTTP $statusCode : $responseBody"
        }
    }
}

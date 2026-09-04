package com.example.ai

import com.example.BuildConfig
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.PanelDraft
import com.example.domain.model.ScenePanelsDraft
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
 * Générateur IA pour la Phase 12 : Découpage d'une scène en Panels (cases de webtoon).
 *
 * RÈGLES DU PROMPT MAÎTRE :
 * 1. Modèle : gemini-1.5-flash avec schéma JSON contraint strict (responseSchema).
 * 2. Découpe la scène en une séquence ordonnée de panels verticaux.
 * 3. Chaque panel contient : personnages avec état actuel, lieu, action, émotion dominante,
 *    angle/plan caméra, composition, éclairage, arrière-plan, effets visuels (SFX visuels),
 *    un PROMPT VISUEL AUTONOME sans texte incrusté prêt pour l'IA d'image (Phase 14),
 *    et des notes de continuité avec le panel précédent.
 * 4. Vrai appel réseau Gemini, sans fausse simulation.
 */
class GeminiPanelGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_NAME = "gemini-1.5-flash"
        private const val FALLBACK_MODEL_NAME = "gemini-1.5-flash"
    }

    /**
     * Découpe une scène en panels détaillés en respectant le canon.
     */
    suspend fun generatePanelsForScene(
        scene: SceneEntity,
        canonSnapshot: CanonSnapshot,
        previousSceneSummary: String? = null,
        targetPanelCount: Int = 5
    ): ScenePanelsDraft = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildPanelPrompt(
            scene = scene,
            canonSnapshot = canonSnapshot,
            previousSceneSummary = previousSceneSummary,
            targetPanelCount = targetPanelCount
        )

        val requestBodyJson = buildRequestBodyJson(promptText)

        try {
            executeRequest(MODEL_NAME, apiKey, requestBodyJson, scene.id, scene.title)
        } catch (e: Exception) {
            if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                executeRequest(FALLBACK_MODEL_NAME, apiKey, requestBodyJson, scene.id, scene.title)
            } else {
                throw e
            }
        }
    }

    fun buildPanelPrompt(
        scene: SceneEntity,
        canonSnapshot: CanonSnapshot,
        previousSceneSummary: String?,
        targetPanelCount: Int
    ): String {
        return buildString {
            appendLine("Tu es le Directeur Artistique et Découpeur Webtoon (Layout Artist) d'un studio d'animation.")
            appendLine("Ta mission est de DÉCOUPER CETTE SCÈNE EN UNE SÉQUENCE DE PANELS (cases de webtoon vertical).")
            appendLine("Génère environ $targetPanelCount panels équilibrés pour un rythme de lecture vertical optimal.")
            appendLine()
            appendLine("--- RÈGLES CRITIQUES DE DÉCOUPAGE ---")
            appendLine("1. RYTHME VERTICAL WEBTOON : varie les valeurs de plan (Plan d'ensemble pour poser le décor, Plan moyen pour l'interaction, Gros plan pour l'émotion intense, Plongée/Contre-plongée pour la tension).")
            appendLine("2. ÉTAT DES PERSONNAGES : précise la posture, la blessure éventuelle, l'état vestimentaire et l'expression faciale pour chaque personnage présent.")
            appendLine("3. PROMPT VISUEL AUTONOME (visualPrompt) : ce prompt textuel en anglais descriptif sera utilisé directement par le modèle d'image (Phase 14). Il doit être riche, précis et compréhensible à lui seul sans contexte.")
            appendLine("4. ZÉRO TEXTE DANS L'IMAGE (MANDATORY) : le prompt visuel ne doit JAMAIS demander d'inclure des bulles, du texte, des lettres ou des dialogues dans l'image. Tous les textes seront ajoutés par dessus par l'application.")
            appendLine("5. CONTINUITÉ NARRATIVE (continuityNotes) : décris l'axe caméra, la continuité d'action et d'éclairage par rapport au panel précédent pour éviter les sauts d'axe.")
            appendLine("6. ONOMATOPÉES (soundEffects) : suggère des effets sonores visuels (BAM, SWOOSH, CRACK...) si la scène est dynamique.")
            appendLine()
            appendLine("--- CANON VISUEL ET NARRATIF ---")
            canonSnapshot.project?.let {
                appendLine("Projet : ${it.finalTitle ?: it.title} (${it.genre} - ${it.tone})")
            }
            canonSnapshot.visualBible?.let { vb ->
                appendLine("Style artistique de référence : ${vb.artStyle} | Ambiance couleur : ${vb.colorPalette}")
                if (vb.framingRules.isNotBlank()) {
                    appendLine("Règles de cadrage : ${vb.framingRules}")
                }
                if (vb.recurringVisualElements.isNotBlank()) {
                    appendLine("Éléments récurrents : ${vb.recurringVisualElements}")
                }
            }
            if (canonSnapshot.characters.isNotEmpty()) {
                appendLine("Personnages canoniques :")
                canonSnapshot.characters.forEach { c ->
                    appendLine("• ${c.name} (${c.role}) : ${c.visualDescription}")
                }
            }
            if (previousSceneSummary != null && previousSceneSummary.isNotBlank()) {
                appendLine("Raccord scène précédente : $previousSceneSummary")
            }
            appendLine()
            appendLine("--- SCÈNE À DÉCOUPER (Scène ${scene.orderIndex} : ${scene.title}) ---")
            appendLine("Lieu : ${scene.locationName}")
            appendLine("Personnages : ${scene.charactersPresent}")
            appendLine("Résumé : ${scene.narrativeSummary}")
            if (scene.atmosphere.isNotBlank()) appendLine("Ambiance : ${scene.atmosphere}")
            if (scene.emotionalArc.isNotBlank()) appendLine("Arc émotionnel : ${scene.emotionalArc}")
            if (scene.pacingNotes.isNotBlank()) appendLine("Rythme souhaité : ${scene.pacingNotes}")
            if (scene.narrativeBeats.isNotBlank()) appendLine("Beats narratifs :\n${scene.narrativeBeats}")
            appendLine()
            appendLine("Génère maintenant les panels structurés.")
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

        val panelsArraySchema = JSONObject().apply {
            put("type", "ARRAY")
            put("description", "Liste chronologique des panels découpés pour cette scène de webtoon.")

            val panelObj = JSONObject().apply {
                put("type", "OBJECT")
                val pProps = JSONObject().apply {
                    put("orderIndex", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Numéro d'ordre chronologique du panel (1, 2, 3...).")
                    })
                    put("charactersPresent", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Personnages présents avec leur état actuel (ex: Aria, tenue déchirée, posture d'attaque tendue).")
                    })
                    put("location", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Lieu précis et cadrage du décor dans ce plan.")
                    })
                    put("actionDescription", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Description de l'action ou du micro-événement précis se déroulant dans cette case.")
                    })
                    put("dominantEmotion", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Émotion dominante exprimée (ex: Peur panique, Joie éclatante, Regard glacial).")
                    })
                    put("cameraAngle", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Type de cadrage et plan (Gros plan, Plan moyen, Plan d'ensemble, Plongée, Contre-plongée, Dutch angle).")
                    })
                    put("composition", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Disposition spatiale, diagonales, règle des tiers, équilibre des masses.")
                    })
                    put("lightingAndAtmosphere", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Éclairage, ombres, reflets et teintes chromatiques.")
                    })
                    put("backgroundDetails", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Détails visibles en arrière-plan.")
                    })
                    put("visualEffects", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Effets visuels stylisés (lignes de vitesse, auras énergétiques, particules, flou de mouvement).")
                    })
                    put("visualPrompt", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Prompt textuel descriptif complet en anglais destiné à la génération d'image IA, sans aucun texte/dialogue incrusté.")
                    })
                    put("continuityNotes", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Notes de continuité et de raccord par rapport au panel précédent (position des personnages, axe caméra).")
                    })
                    put("soundEffects", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Onomatopée suggérée pour le webtoon (ex: SHWACK, BOOM, TAP, silence).")
                    })
                }
                put("properties", pProps)
                put("required", JSONArray().apply {
                    put("orderIndex")
                    put("charactersPresent")
                    put("location")
                    put("actionDescription")
                    put("dominantEmotion")
                    put("cameraAngle")
                    put("composition")
                    put("lightingAndAtmosphere")
                    put("backgroundDetails")
                    put("visualEffects")
                    put("visualPrompt")
                    put("continuityNotes")
                })
            }
            put("items", panelObj)
        }

        properties.put("panels", panelsArraySchema)
        schema.put("properties", properties)
        schema.put("required", JSONArray().apply { put("panels") })

        return schema
    }

    private fun executeRequest(
        model: String,
        apiKey: String,
        requestBodyJson: JSONObject,
        sceneId: String,
        sceneTitle: String
    ): ScenePanelsDraft {
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

            return parseGeminiResponse(responseBody, sceneId, sceneTitle)
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

    private fun parseGeminiResponse(
        responseBody: String,
        sceneId: String,
        sceneTitle: String
    ): ScenePanelsDraft {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Aucun candidat retourné par Gemini.")

        if (candidates.length() == 0) {
            throw IllegalStateException("Réponse Gemini vide.")
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
            ?: throw IllegalStateException("Contenu manquant dans la réponse.")

        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("Parts manquants dans la réponse.")

        if (parts.length() == 0) {
            throw IllegalStateException("Aucune partie texte dans la réponse.")
        }

        val text = parts.getJSONObject(0).optString("text")
        if (text.isBlank()) {
            throw IllegalStateException("Texte vide retourné par l'IA.")
        }

        val json = JSONObject(text.trim())
        val panelsArray = json.optJSONArray("panels") ?: JSONArray()
        val panelsList = mutableListOf<PanelDraft>()

        for (i in 0 until panelsArray.length()) {
            val p = panelsArray.getJSONObject(i)
            panelsList.add(
                PanelDraft(
                    orderIndex = p.optInt("orderIndex", i + 1),
                    charactersPresent = p.optString("charactersPresent", ""),
                    location = p.optString("location", ""),
                    actionDescription = p.optString("actionDescription", ""),
                    dominantEmotion = p.optString("dominantEmotion", ""),
                    cameraAngle = p.optString("cameraAngle", "Plan moyen"),
                    composition = p.optString("composition", ""),
                    lightingAndAtmosphere = p.optString("lightingAndAtmosphere", ""),
                    backgroundDetails = p.optString("backgroundDetails", ""),
                    visualEffects = p.optString("visualEffects", ""),
                    visualPrompt = p.optString("visualPrompt", ""),
                    continuityNotes = p.optString("continuityNotes", ""),
                    soundEffects = p.optString("soundEffects").takeIf { !it.isNullOrBlank() && it != "null" }
                )
            )
        }

        return ScenePanelsDraft(
            sceneId = sceneId,
            sceneTitle = sceneTitle,
            panels = panelsList
        )
    }
}

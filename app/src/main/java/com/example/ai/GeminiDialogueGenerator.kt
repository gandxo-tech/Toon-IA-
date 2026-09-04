package com.example.ai

import com.example.BuildConfig
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.SceneEntity
import com.example.domain.canon.CanonSnapshot
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
 * Générateur de dialogues structurés par IA (Phase 13) pour ToonIA.
 *
 * RÈGLES MAÎTRES :
 * 1. Sortie IA JSON contrainte par schéma (responseSchema) — jamais de parsing libre fragile.
 * 2. Ancré sur le snapshot du canon (Character Bible : personnalités, tics de langage, arcs, relations, Running Gags).
 * 3. Textes séparés du rendu visuel de l'image (superposés nativement par Compose/Canvas en aval).
 * 4. Vrai appel réseau Gemini, clé via BuildConfig.
 */
class GeminiDialogueGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val PRIMARY_MODEL = "gemini-3.1-flash-lite-preview"
    }

    /**
     * Génère les bulles de dialogue pour un panel spécifique.
     */
    suspend fun generateDialoguesForPanel(
        projectId: String,
        panel: PanelEntity,
        scene: SceneEntity?,
        chapter: ChapterEntity?,
        canonSnapshot: CanonSnapshot
    ): List<DialogueEntity> = withContext(Dispatchers.IO) {
        val prompt = buildDialoguePromptForPanel(panel, scene, chapter, canonSnapshot)
        return@withContext executeDialogueGeneration(projectId, listOf(panel), prompt, canonSnapshot)
    }

    /**
     * Génère les bulles de dialogue pour tous les panels d'une scène.
     */
    suspend fun generateDialoguesForScene(
        projectId: String,
        scene: SceneEntity,
        panels: List<PanelEntity>,
        chapter: ChapterEntity?,
        canonSnapshot: CanonSnapshot
    ): List<DialogueEntity> = withContext(Dispatchers.IO) {
        val prompt = buildDialoguePromptForScene(scene, panels, chapter, canonSnapshot)
        return@withContext executeDialogueGeneration(projectId, panels, prompt, canonSnapshot)
    }

    /**
     * Exécute l'appel API Gemini avec schéma JSON strict.
     */
    private suspend fun executeDialogueGeneration(
        projectId: String,
        panels: List<PanelEntity>,
        promptText: String,
        canonSnapshot: CanonSnapshot
    ): List<DialogueEntity> {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Clé API Gemini non configurée. Veuillez renseigner votre clé API dans le panneau Secrets.")
        }

        val requestJson = JSONObject().apply {
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
                put("responseSchema", buildDialogueResponseSchema())
            })
        }

        val url = "$BASE_URL/$PRIMARY_MODEL:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IOException("Erreur API Gemini (${response.code}): $responseBody")
        }

        val rootJson = JSONObject(responseBody)
        val candidates = rootJson.optJSONArray("candidates")
            ?: throw IOException("Aucun candidat retourné par l'API Gemini.")

        val firstCandidate = candidates.optJSONObject(0)
            ?: throw IOException("Candidat Gemini vide.")

        val textResponse = firstCandidate
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: throw IOException("Contenu texte JSON introuvable dans la réponse Gemini.")

        val parsedJson = JSONObject(textResponse)
        val dialoguesArray = parsedJson.optJSONArray("dialogues") ?: JSONArray()

        val panelMap = panels.associateBy { it.id }
        val charMapByName = canonSnapshot.characters.associateBy { it.name.trim().lowercase() }

        val resultList = mutableListOf<DialogueEntity>()
        for (i in 0 until dialoguesArray.length()) {
            val item = dialoguesArray.optJSONObject(i) ?: continue
            val rawPanelId = item.optString("panelId", "")
            val speaker = item.optString("speaker", "").trim()
            val text = item.optString("text", "").trim()
            val type = item.optString("type", "SPEECH")
            val emotion = item.optString("emotion", "Neutre").trim()
            val orderIndex = item.optInt("orderIndex", i + 1)
            val placement = item.optString("placement", "TOP_LEFT")
            val soundEffect = item.optString("soundEffect", "").trim().takeIf { it.isNotBlank() }

            val matchedChar = charMapByName[speaker.lowercase()]
            val targetPanelId = if (panelMap.containsKey(rawPanelId)) rawPanelId else (panels.firstOrNull()?.id ?: "")

            resultList.add(
                DialogueEntity(
                    id = UUID.randomUUID().toString(),
                    panelId = targetPanelId,
                    projectId = projectId,
                    characterId = matchedChar?.id,
                    speakerName = speaker,
                    orderIndex = if (orderIndex > 0) orderIndex else (i + 1),
                    type = normalizeBubbleType(type),
                    emotion = emotion,
                    placement = normalizePlacement(placement),
                    soundEffect = soundEffect,
                    text = text,
                    isCanon = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return resultList
    }

    private fun normalizeBubbleType(type: String): String {
        val upper = type.trim().uppercase()
        return when {
            upper.contains("THOUGHT") || upper.contains("PENS") -> "THOUGHT"
            upper.contains("SHOUT") || upper.contains("CRI") || upper.contains("HURL") -> "SHOUT"
            upper.contains("WHISPER") || upper.contains("CHUCHOT") -> "WHISPER"
            upper.contains("NARRAT") || upper.contains("REC") || upper.contains("VOIX") -> "NARRATION"
            else -> "SPEECH"
        }
    }

    private fun normalizePlacement(placement: String): String {
        val upper = placement.trim().uppercase()
        return when {
            upper.contains("TOP_RIGHT") || upper.contains("DROITE_HAUT") -> "TOP_RIGHT"
            upper.contains("BOTTOM_LEFT") || upper.contains("GAUCHE_BAS") -> "BOTTOM_LEFT"
            upper.contains("BOTTOM_RIGHT") || upper.contains("DROITE_BAS") -> "BOTTOM_RIGHT"
            upper.contains("CENTER") || upper.contains("CENTRE") -> "CENTER"
            else -> "TOP_LEFT"
        }
    }

    private fun buildDialoguePromptForPanel(
        panel: PanelEntity,
        scene: SceneEntity?,
        chapter: ChapterEntity?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es un scénariste et dialoguiste expert de Webtoons.")
            appendLine("Génère des bulles de dialogue et/ou de narration percutantes pour le panel suivant, en respectant scrupuleusement la bible des personnages et le canon.")
            appendLine()
            appendLine("--- CANON & PERSONNAGES ---")
            if (canonSnapshot.characters.isNotEmpty()) {
                canonSnapshot.characters.forEach { char ->
                    appendLine("- ${char.name} (${char.role}) : ${char.personalityTraits}. Archétype : ${char.archetype}. Tics de langage & voix : ${char.backstory.take(120)}")
                }
            } else {
                appendLine("- Aucun personnage explicite défini.")
            }
            appendLine()
            if (canonSnapshot.humorDna != null) {
                appendLine("--- ADN HUMOUR & RUNNING GAGS ---")
                appendLine("Ton comique : ${canonSnapshot.humorDna.humorStyle}")
                appendLine("Running Gags : ${canonSnapshot.humorDna.runningGags}")
                appendLine()
            }
            if (chapter != null) {
                appendLine("Chapitre : ${chapter.title} (N°${chapter.number})")
            }
            if (scene != null) {
                appendLine("Scène : ${scene.title} - Ambiance : ${scene.atmosphere} - Arc émotionnel : ${scene.emotionalArc}")
            }
            appendLine()
            appendLine("--- PANEL CIBLE ---")
            appendLine("Panel ID : ${panel.id}")
            appendLine("Ordre dans la scène : Case #${panel.orderIndex}")
            appendLine("Angle / Cadrage : ${panel.cameraAngle}")
            appendLine("Action en cours : ${panel.actionDescription}")
            appendLine("Personnages présents & état : ${panel.charactersPresent}")
            appendLine("Émotion dominante : ${panel.dominantEmotion}")
            appendLine("Effets visuels / SFX : ${panel.soundEffects ?: "Aucun"}")
            appendLine("Description visuelle : ${panel.visualDescription}")
            appendLine()
            appendLine("--- RÈGLES DE DIALOGUES WEBTOON ---")
            appendLine("1. Chaque bulle doit être vivante, dynamique et adaptée à la lecture mobile verticale.")
            appendLine("2. Choisis le type de bulle exact : 'SPEECH' (parole normale), 'THOUGHT' (pensée intérieure), 'SHOUT' (cri / exclamation vive), 'WHISPER' (chuchotement / secret), ou 'NARRATION' (voix off / récitatif narratif).")
            appendLine("3. Indique l'ordre de lecture dans la case (orderIndex: 1, 2...) et un emplacement suggéré ('TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT', 'CENTER').")
            appendLine("4. Si la case est purement contemplative ou d'action muette, renvoie 0 ou 1 dialogue pertinent.")
            appendLine("5. Le speaker doit correspondre au prénom exact du personnage, ou 'Narration', ou 'Voix off'.")
        }
    }

    private fun buildDialoguePromptForScene(
        scene: SceneEntity,
        panels: List<PanelEntity>,
        chapter: ChapterEntity?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es un scénariste et dialoguiste expert de Webtoons.")
            appendLine("Génère l'ensemble des dialogues et narrations pour les cases de la scène suivante de manière cohérente, fluide et rythmée.")
            appendLine()
            appendLine("--- CANON & PERSONNAGES ---")
            canonSnapshot.characters.forEach { char ->
                appendLine("- ${char.name} (${char.role}) : ${char.personalityTraits}. Tics de langage & comportement : ${char.backstory.take(100)}")
            }
            appendLine()
            if (chapter != null) {
                appendLine("Chapitre : ${chapter.title} (N°${chapter.number})")
            }
            appendLine("Scène : ${scene.title} (Ordre: ${scene.orderIndex})")
            appendLine("Résumé : ${scene.narrativeSummary}")
            appendLine("Ambiance : ${scene.atmosphere}")
            appendLine("Arc émotionnel : ${scene.emotionalArc}")
            appendLine()
            appendLine("--- CASES (PANELS) DE LA SCÈNE ---")
            panels.sortedBy { it.orderIndex }.forEach { p ->
                appendLine("• Case #${p.orderIndex} (ID: ${p.id}) :")
                appendLine("   - Cadrage : ${p.cameraAngle}")
                appendLine("   - Action : ${p.actionDescription}")
                appendLine("   - Personnages présents : ${p.charactersPresent}")
                appendLine("   - Émotion : ${p.dominantEmotion}")
                if (!p.soundEffects.isNullOrBlank()) appendLine("   - SFX : ${p.soundEffects}")
            }
            appendLine()
            appendLine("--- CONSIGNES ---")
            appendLine("Pour chaque case nécessitant un dialogue ou une narration, génère les bulles avec l'ID de panel correspondant exact.")
            appendLine("Types possibles : SPEECH, THOUGHT, SHOUT, WHISPER, NARRATION.")
        }
    }

    private fun buildDialogueResponseSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("dialogues", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("panelId", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "L'identifiant ID exact du panel concerné")
                            })
                            put("speaker", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Le nom du personnage locuteur, ou 'Narration', ou 'Voix off'")
                            })
                            put("text", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Le texte exact de la réplique ou de la pensée")
                            })
                            put("type", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Type de bulle : SPEECH, THOUGHT, SHOUT, WHISPER, NARRATION")
                            })
                            put("emotion", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "L'émotion véhiculée par la réplique (ex: Colère, Détermination, Peur, Amusé, Neutre)")
                            })
                            put("orderIndex", JSONObject().apply {
                                put("type", "INTEGER")
                                put("description", "Ordre de lecture séquentiel au sein du panel (1, 2, 3...)")
                            })
                            put("placement", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Position recommandée dans la case : TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER")
                            })
                            put("soundEffect", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Bruitage ou onomatopée optionnel")
                            })
                        })
                        put("required", JSONArray().apply {
                            put("panelId")
                            put("speaker")
                            put("text")
                            put("type")
                            put("orderIndex")
                        })
                    })
                })
            })
            put("required", JSONArray().apply {
                put("dialogues")
            })
        }
    }
}

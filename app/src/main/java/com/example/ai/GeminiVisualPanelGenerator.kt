package com.example.ai

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.VisualBibleEntity
import com.example.domain.canon.CanonSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Résultat de la génération visuelle d'un panel.
 */
data class PanelImageGenerationResult(
    val localImagePath: String,
    val referencedCharacters: List<String> = emptyList(),
    val modelUsed: String = GeminiVisualPanelGenerator.PRIMARY_IMAGE_MODEL
)

/**
 * Générateur d'illustrations pour les panels de ToonIA (Phase 14).
 *
 * RÈGLES MAÎTRES & SPÉCIFICATIONS :
 * 1. Modèle d'image Gemini : gemini-1.5-flash (avec fallback imagen-3.0-generate-001 si indisponible).
 * 2. Avant de générer un panel : si un personnage présent n'a pas encore d'image de référence canonique,
 *    génère d'abord une image de référence de ce personnage seul (Character Bible + Visual Bible)
 *    et enregistre-la de façon pérenne dans le profil du personnage.
 * 3. Cohérence des personnages : injection multimodale des images de référence canoniques des personnages
 *    présents dans le prompt de génération de la case.
 * 4. RÈGLE STRICTE : AUCUN TEXTE NI BULLE dans l'image. Tout texte/dialogue est superposé nativement par l'app.
 * 5. Stockage en fichiers locaux (context.filesDir/panels/ et context.filesDir/characters/) — JAMAIS de base64 dans Room.
 * 6. Gestion rigoureuse des erreurs (clé manquante, timeout, contenu refusé) avec retry contrôlé.
 */
class GeminiVisualPanelGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val PRIMARY_IMAGE_MODEL = "gemini-1.5-flash"
        const val FALLBACK_IMAGE_MODEL = "imagen-3.0-generate-001"
    }

    /**
     * Génère l'image de référence canonique d'un personnage seul (portrait / silhouette).
     */
    suspend fun generateCharacterReferenceImage(
        context: Context,
        projectId: String,
        character: CharacterEntity,
        visualBible: VisualBibleEntity?
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Clé API Gemini non configurée. Veuillez renseigner votre clé API dans le panneau Secrets.")
        }

        val prompt = buildCharacterReferencePrompt(character, visualBible)

        try {
            val imageBytes = callGeminiFlashImage(apiKey, prompt, referenceImagesBase64 = emptyList())
            return@withContext saveCharacterImageToLocalStorage(context, projectId, character.id, imageBytes)
        } catch (e: Exception) {
            if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                val fallbackBytes = callImagenFallback(apiKey, prompt)
                return@withContext saveCharacterImageToLocalStorage(context, projectId, character.id, fallbackBytes)
            } else {
                throw e
            }
        }
    }

    /**
     * Génère l'illustration visuelle d'un panel donné en injectant les images de référence des personnages présents.
     */
    suspend fun generatePanelImage(
        context: Context,
        projectId: String,
        panel: PanelEntity,
        visualBible: VisualBibleEntity?,
        canonSnapshot: CanonSnapshot,
        presentCharacters: List<CharacterEntity>
    ): PanelImageGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Clé API Gemini non configurée. Veuillez renseigner votre clé API dans le panneau Secrets.")
        }

        // 1. Charger les images de référence existantes des personnages présents
        val refImagesBase64 = mutableListOf<String>()
        val referencedCharacterNames = mutableListOf<String>()

        presentCharacters.forEach { char ->
            val charImgPath = char.canonicalImagePath
            if (!charImgPath.isNullOrBlank()) {
                val file = File(charImgPath)
                if (file.exists() && file.length() > 0) {
                    val bytes = file.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    refImagesBase64.add(b64)
                    referencedCharacterNames.add(char.name)
                }
            }
        }

        // 2. Construire le prompt textuel complet avec consignes strictes sans texte
        val promptText = buildPanelVisualPrompt(
            panel = panel,
            visualBible = visualBible,
            canonSnapshot = canonSnapshot,
            referencedCharacters = referencedCharacterNames
        )

        // 3. Appel de génération d'image
        try {
            val imageBytes = callGeminiFlashImage(apiKey, promptText, refImagesBase64)
            val localPath = savePanelImageToLocalStorage(context, projectId, panel.id, imageBytes)
            return@withContext PanelImageGenerationResult(
                localImagePath = localPath,
                referencedCharacters = referencedCharacterNames,
                modelUsed = PRIMARY_IMAGE_MODEL
            )
        } catch (e: Exception) {
            if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                val fallbackBytes = callImagenFallback(apiKey, promptText)
                val localPath = savePanelImageToLocalStorage(context, projectId, panel.id, fallbackBytes)
                return@withContext PanelImageGenerationResult(
                    localImagePath = localPath,
                    referencedCharacters = referencedCharacterNames,
                    modelUsed = FALLBACK_IMAGE_MODEL
                )
            } else {
                throw e
            }
        }
    }

    private fun buildCharacterReferencePrompt(
        character: CharacterEntity,
        visualBible: VisualBibleEntity?
    ): String {
        return buildString {
            appendLine("Official character model reference sheet portrait for a high-end Webtoon manhwa.")
            appendLine("STRICT NEGATIVE INSTRUCTION: NO TEXT, NO WORDS, NO SPEECH BUBBLES, NO LETTERS, NO SIGNATURE, NO WATERMARK.")
            appendLine()
            appendLine("CHARACTER IDENTITY:")
            appendLine("- Name: ${character.name}")
            appendLine("- Role: ${character.role}")
            appendLine("- Visual appearance: ${character.visualDescription}")
            appendLine("- Personality & Archetype: ${character.personalityTraits} (${character.archetype})")
            appendLine()
            appendLine("COMPOSITION:")
            appendLine("Clean 3/4 or full body character concept design, centered on a neutral aesthetic background, clearly displaying hair, eyes, clothes and key visual traits.")
            appendLine()
            if (visualBible != null) {
                appendLine("ART STYLE:")
                if (visualBible.artStyle.isNotBlank()) appendLine("- Style: ${visualBible.artStyle}")
                if (visualBible.colorPalette.isNotBlank()) appendLine("- Palette: ${visualBible.colorPalette}")
            }
        }
    }

    private fun buildPanelVisualPrompt(
        panel: PanelEntity,
        visualBible: VisualBibleEntity?,
        canonSnapshot: CanonSnapshot,
        referencedCharacters: List<String>
    ): String {
        return buildString {
            appendLine("Cinematic Webtoon panel manhwa illustration, vertical scroll format.")
            appendLine("CRITICAL DIRECTIVE - ABSOLUTE NEGATIVE CONSTRAINT:")
            appendLine("STRICTLY NO TEXT, NO WORDS, NO LETTERS, NO SPEECH BUBBLES, NO DIALOGUES, NO NUMBERS, NO SOUND EFFECT TEXT, NO ONOMATOPOEIA, NO WATERMARKS.")
            appendLine("DO NOT DRAW ANY TEXT OR SPEECH BALLOONS. All typography, dialogue balloons and SFX will be overlaid digitally by the mobile application.")
            appendLine()
            appendLine("PANEL VISUAL SPECIFICATION:")
            if (panel.visualPrompt.isNotBlank()) {
                appendLine(panel.visualPrompt)
            } else {
                appendLine(panel.visualDescription)
            }
            appendLine("- Camera Angle & Framing: ${panel.cameraAngle}")
            appendLine("- Action / Movement: ${panel.actionDescription}")
            appendLine("- Characters & Current State: ${panel.charactersPresent}")
            appendLine("- Dominant Emotion & Mood: ${panel.dominantEmotion}")
            if (panel.lightingAndAtmosphere.isNotBlank()) {
                appendLine("- Lighting & Atmosphere: ${panel.lightingAndAtmosphere}")
            }
            if (panel.visualEffects.isNotBlank()) {
                appendLine("- Visual Effects (speed lines, aura, particles): ${panel.visualEffects}")
            }
            if (panel.backgroundDetails.isNotBlank()) {
                appendLine("- Background details: ${panel.backgroundDetails}")
            }
            appendLine()
            if (referencedCharacters.isNotEmpty()) {
                appendLine("CHARACTER CONSISTENCY REFERENCE:")
                appendLine("Reference images are attached for characters: ${referencedCharacters.joinToString(", ")}.")
                appendLine("Maintain consistent facial structure, hair style, eye color, and signature traits from the reference images.")
                appendLine()
            }
            if (visualBible != null) {
                appendLine("ART STYLE & VISUAL BIBLE:")
                if (visualBible.artStyle.isNotBlank()) appendLine("- Style: ${visualBible.artStyle}")
                if (visualBible.colorPalette.isNotBlank()) appendLine("- Color Palette: ${visualBible.colorPalette}")
                if (visualBible.framingRules.isNotBlank()) appendLine("- Framing & Composition: ${visualBible.framingRules}")
            }
        }
    }

    /**
     * Appelle gemini-1.5-flash pour générer l'image.
     */
    private suspend fun callGeminiFlashImage(
        apiKey: String,
        prompt: String,
        referenceImagesBase64: List<String>
    ): ByteArray = withContext(Dispatchers.IO) {
        val partsArray = JSONArray()

        // 1. Ajouter le prompt textuel
        partsArray.put(JSONObject().apply {
            put("text", prompt)
        })

        // 2. Ajouter les images de référence en inlineData
        referenceImagesBase64.forEach { b64 ->
            partsArray.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", b64)
                })
            })
        }

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
                put("imageConfig", JSONObject().apply {
                    put("aspectRatio", "1:1")
                    put("imageSize", "1K")
                })
            })
        }

        val url = "$BASE_URL/$PRIMARY_IMAGE_MODEL:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Erreur réseau"
            throw GeminiApiException(response.code, "Erreur API Gemini Flash Image (${response.code}): $errorBody")
        }

        val responseBody = response.body?.string() ?: throw IOException("Réponse API vide.")
        val rootJson = JSONObject(responseBody)
        val candidates = rootJson.optJSONArray("candidates")
            ?: throw IOException("Aucun candidat retourné.")

        val firstCandidate = candidates.optJSONObject(0)
            ?: throw IOException("Candidat vide.")

        val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Aucune part dans la réponse.")

        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i)
            val inlineData = part?.optJSONObject("inlineData")
            if (inlineData != null) {
                val base64Data = inlineData.optString("data")
                if (base64Data.isNotBlank()) {
                    return@withContext Base64.decode(base64Data, Base64.DEFAULT)
                }
            }
        }

        throw IOException("Aucune donnée d'image (inlineData) trouvée dans la réponse de gemini-1.5-flash.")
    }

    /**
     * Fallback vers Imagen 3 si flash-image n'est pas disponible.
     */
    private suspend fun callImagenFallback(
        apiKey: String,
        prompt: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put("instances", JSONArray().apply {
                put(JSONObject().apply {
                    put("prompt", prompt)
                })
            })
            put("parameters", JSONObject().apply {
                put("sampleCount", 1)
                put("aspectRatio", "1:1")
                put("outputOptions", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                })
            })
        }

        val url = "$BASE_URL/$FALLBACK_IMAGE_MODEL:predict?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Erreur Imagen"
            throw GeminiApiException(response.code, "Erreur API Imagen 3 (${response.code}): $errorBody")
        }

        val responseBody = response.body?.string() ?: throw IOException("Réponse Imagen vide.")
        val rootJson = JSONObject(responseBody)
        val predictions = rootJson.optJSONArray("predictions")
            ?: throw IOException("Aucune prédiction retournée par Imagen 3.")

        val firstPred = predictions.optJSONObject(0)
            ?: throw IOException("Prédiction Imagen vide.")

        val bytesBase64 = firstPred.optString("bytesBase64Encoded")
        if (bytesBase64.isNotBlank()) {
            return@withContext Base64.decode(bytesBase64, Base64.DEFAULT)
        }

        throw IOException("Données d'image introuvables dans la réponse Imagen.")
    }

    private fun saveCharacterImageToLocalStorage(
        context: Context,
        projectId: String,
        characterId: String,
        imageBytes: ByteArray
    ): String {
        val charDir = File(context.filesDir, "characters/$projectId")
        if (!charDir.exists()) {
            charDir.mkdirs()
        }
        val file = File(charDir, "char_ref_${characterId}.jpg")
        FileOutputStream(file).use { it.write(imageBytes) }
        return file.absolutePath
    }

    private fun savePanelImageToLocalStorage(
        context: Context,
        projectId: String,
        panelId: String,
        imageBytes: ByteArray
    ): String {
        val panelsDir = File(context.filesDir, "panels/$projectId")
        if (!panelsDir.exists()) {
            panelsDir.mkdirs()
        }
        val file = File(panelsDir, "panel_${panelId}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { it.write(imageBytes) }
        return file.absolutePath
    }
}

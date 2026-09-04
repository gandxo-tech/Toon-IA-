package com.example.ai

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CoverEntity
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
 * Générateur d'image pour la Phase 9 : Image de couverture de ToonIA.
 *
 * RÈGLES DU PROMPT MAÎTRE & SPÉCIFICATIONS :
 * 1. Modèle d'image Gemini : gemini-1.5-flash (spécifié dans gemini-api skill)
 *    avec fallback sur imagen-3.0-generate-001 si indisponible.
 * 2. Prompt construit à partir de la description de couverture validée en Phase 8 + Visual Bible.
 * 3. RÈGLE STRICTE : AUCUN TEXTE dans l'image. Tout texte (titre, auteur, tagline) est
 *    superposé nativement par Compose, jamais généré dans l'illustration.
 * 4. Image de référence canonique optionnelle : si un personnage possède une image de référence
 *    canonique (canonicalImagePath), elle est transmise en entrée multimodale pour la cohérence visuelle.
 * 5. Stockage en fichier local dans context.filesDir/covers/ (JAMAIS de base64 dans Room).
 * 6. Vrai appel réseau Gemini, gestion explicite des erreurs (clé manquante, quota, refus de sécurité, timeout).
 */
class GeminiCoverImageGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        // Modèle officiel Gemini pour la génération d'images (gemini-api skill)
        const val PRIMARY_IMAGE_MODEL = "gemini-1.5-flash"
        // Modèle de secours si gemini-1.5-flash n'est pas activé sur le projet
        const val FALLBACK_IMAGE_MODEL = "imagen-3.0-generate-001"
    }

    /**
     * Génère l'illustration de la couverture et la sauvegarde dans le stockage local de l'application.
     *
     * @return Le chemin absolu du fichier image local généré.
     */
    suspend fun generateCoverImage(
        context: Context,
        projectId: String,
        cover: CoverEntity,
        visualBible: VisualBibleEntity?,
        canonSnapshot: CanonSnapshot
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: com.example.util.ApiKeyManager.getApiKey()

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildCoverImagePrompt(
            cover = cover,
            visualBible = visualBible,
            canonSnapshot = canonSnapshot
        )

        // Récupérer d'éventuelles images de référence de personnages canoniques (Phase 14 optionnelle)
        val referenceImagesBase64 = loadCanonicalCharacterReferenceImages(canonSnapshot)

        // Tentative 1 : Modèle officiel Gemini gemini-1.5-flash
        try {
            val imageBytes = callGeminiFlashImage(
                apiKey = apiKey,
                prompt = promptText,
                referenceImagesBase64 = referenceImagesBase64
            )
            return@withContext saveImageToLocalStorage(context, projectId, cover.id, imageBytes)
        } catch (e: Exception) {
            // Si le modèle flash-image échoue (ex: 404 modèle non disponible ou non autorisé), tenter le fallback Imagen 3
            if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                try {
                    val fallbackBytes = callImagenFallback(
                        apiKey = apiKey,
                        prompt = promptText
                    )
                    return@withContext saveImageToLocalStorage(context, projectId, cover.id, fallbackBytes)
                } catch (fallbackError: Exception) {
                    throw IOException(
                        "Échec de la génération d'image : ${fallbackError.message ?: e.message}",
                        fallbackError
                    )
                }
            } else {
                throw e
            }
        }
    }

    /**
     * Construit le prompt d'illustration en combinant la description validée et la Visual Bible.
     * Impose explicitement l'absence totale de texte.
     */
    fun buildCoverImagePrompt(
        cover: CoverEntity,
        visualBible: VisualBibleEntity?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Masterpiece webtoon editorial cover illustration, premium vertical manhwa cover artwork.")
            appendLine("CRITICAL DIRECTIVE - ABSOLUTE NEGATIVE CONSTRAINT:")
            appendLine("STRICTLY NO TEXT, NO WORDS, NO LETTERS, NO NUMBERS, NO TITLES, NO WATERMARKS, NO SUBTITLES, NO LOGOS, NO SIGNATURES.")
            appendLine("DO NOT DRAW ANY TEXT OR TYPOGRAPHY ANYWHERE IN THE IMAGE. The cover text will be overlaid digitally by the mobile application.")
            appendLine()
            appendLine("VISUAL SCENE DESCRIPTION:")
            if (cover.visualDescription.isNotBlank()) {
                appendLine(cover.visualDescription)
            } else {
                appendLine("An iconic, dynamic illustration representing the webtoon universe '${cover.title}'.")
            }
            appendLine()

            if (visualBible != null) {
                appendLine("ARTISTIC DIRECTION & VISUAL BIBLE:")
                if (visualBible.artStyle.isNotBlank()) {
                    appendLine("- Art Style: ${visualBible.artStyle}")
                }
                if (visualBible.colorPalette.isNotBlank()) {
                    appendLine("- Color Palette: ${visualBible.colorPalette}")
                }
                if (visualBible.lightingMood.isNotBlank()) {
                    appendLine("- Lighting Mood & Atmosphere: ${visualBible.lightingMood}")
                }
                if (visualBible.recurringVisualElements.isNotBlank()) {
                    appendLine("- Signature Elements: ${visualBible.recurringVisualElements}")
                }
                if (visualBible.continuityElements.isNotBlank()) {
                    appendLine("- Continuity Rules: ${visualBible.continuityElements}")
                }
                appendLine()
            }

            // Personnages clés mentionnés
            if (canonSnapshot.characters.isNotEmpty()) {
                val mainChars = canonSnapshot.characters.take(2)
                appendLine("KEY CHARACTERS TO FEATURE:")
                mainChars.forEach { c ->
                    appendLine("- ${c.name} (${c.role}) : ${c.visualDescription.ifBlank { c.personalityTraits }}")
                }
                appendLine()
            }

            appendLine("FORMAT & COMPOSITION:")
            appendLine("Vertical composition with 3:4 aspect ratio optimized for smartphone webtoon cover display.")
            appendLine("Leave subtle breathing room at the top and bottom for native UI title overlay.")
            appendLine("Pure visual artwork only. No typography.")
        }
    }

    /**
     * Effectue l'appel à gemini-1.5-flash avec responseModalities = ["TEXT", "IMAGE"].
     */
    private suspend fun callGeminiFlashImage(
        apiKey: String,
        prompt: String,
        referenceImagesBase64: List<String>
    ): ByteArray {
        val url = "$BASE_URL/$PRIMARY_IMAGE_MODEL:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val partsArray = JSONArray()

            // Ajout des éventuelles images de référence pour la cohérence des personnages
            referenceImagesBase64.forEach { base64Data ->
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Data)
                    })
                })
            }

            // Prompt textuel
            partsArray.put(JSONObject().apply {
                put("text", prompt)
            })

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
                    put("aspectRatio", "3:4")
                    put("imageSize", "1K")
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return executeAndExtractImageBytes(request)
    }

    /**
     * Fallback sur imagen-3.0-generate-001 si le modèle flash-image n'est pas disponible.
     */
    private suspend fun callImagenFallback(
        apiKey: String,
        prompt: String
    ): ByteArray {
        val url = "$BASE_URL/$FALLBACK_IMAGE_MODEL:predict?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("instances", JSONArray().apply {
                put(JSONObject().apply {
                    put("prompt", prompt)
                })
            })
            put("parameters", JSONObject().apply {
                put("sampleCount", 1)
                put("aspectRatio", "3:4")
                put("outputOptions", JSONObject().apply {
                    put("mimeType", "image/png")
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return executeImagenAndExtractBytes(request)
    }

    private fun executeAndExtractImageBytes(request: Request): ByteArray {
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("Erreur réseau lors de la génération de couverture : ${e.localizedMessage}", e)
        }

        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            throw GeminiApiException(response.code, errorMsg)
        }

        val json = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw IOException("Réponse JSON invalide reçue de Gemini : ${e.localizedMessage}")
        }

        // Vérification des refus de sécurité
        val promptFeedback = json.optJSONObject("promptFeedback")
        val blockReason = promptFeedback?.optString("blockReason")
        if (!blockReason.isNullOrBlank() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
            throw IllegalStateException(
                "La génération de couverture a été bloquée par les filtres de sécurité ($blockReason). Modifiez la description et réessayez."
            )
        }

        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IOException("Aucun candidat retourné par le modèle d'image Gemini.")
        }

        val candidate = candidates.getJSONObject(0)
        val finishReason = candidate.optString("finishReason")
        if (finishReason.equals("SAFETY", ignoreCase = true)) {
            throw IllegalStateException(
                "L'image a été bloquée par les règles de sécurité (SAFETY). Modifiez la description de la couverture."
            )
        }

        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inlineData = part.optJSONObject("inlineData")
                if (inlineData != null) {
                    val base64Data = inlineData.optString("data")
                    if (base64Data.isNotBlank()) {
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            }
        }

        throw IOException("La réponse de Gemini ne contient aucune donnée d'image (inlineData introuvable).")
    }

    private fun executeImagenAndExtractBytes(request: Request): ByteArray {
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("Erreur réseau lors de l'appel Imagen 3 : ${e.localizedMessage}", e)
        }

        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            throw GeminiApiException(response.code, errorMsg)
        }

        val json = JSONObject(responseBody)
        val predictions = json.optJSONArray("predictions")
        if (predictions != null && predictions.length() > 0) {
            val first = predictions.getJSONObject(0)
            val bytesBase64 = first.optString("bytesBase64Encoded")
            if (bytesBase64.isNotBlank()) {
                return Base64.decode(bytesBase64, Base64.DEFAULT)
            }
        }

        throw IOException("Aucune image valide reçue d'Imagen 3.")
    }

    /**
     * Sauvegarde les octets de l'image dans le répertoire local context.filesDir/covers/.
     * Stocke strictement des fichiers sur disque, jamais de base64 en base Room.
     */
    private fun saveImageToLocalStorage(
        context: Context,
        projectId: String,
        coverId: String,
        imageBytes: ByteArray
    ): String {
        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) {
            coversDir.mkdirs()
        }

        val fileName = "cover_${projectId}_${coverId.take(8)}_${System.currentTimeMillis()}.png"
        val imageFile = File(coversDir, fileName)

        FileOutputStream(imageFile).use { output ->
            output.write(imageBytes)
            output.flush()
        }

        return imageFile.absolutePath
    }

    /**
     * Charge les images de référence canoniques des personnages validés s'ils en possèdent.
     */
    private fun loadCanonicalCharacterReferenceImages(canonSnapshot: CanonSnapshot): List<String> {
        val referenceList = mutableListOf<String>()
        canonSnapshot.characters.forEach { character ->
            val path = character.canonicalImagePath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    try {
                        val bytes = file.readBytes()
                        referenceList.add(Base64.encodeToString(bytes, Base64.NO_WRAP))
                    } catch (_: Exception) {
                        // Tolérance sur la lecture des fichiers de référence
                    }
                }
            }
        }
        return referenceList
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

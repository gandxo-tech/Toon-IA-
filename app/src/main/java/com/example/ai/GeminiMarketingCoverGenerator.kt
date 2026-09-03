package com.example.ai

import com.example.BuildConfig
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.CoverVisualProposal
import com.example.domain.model.MarketingCoverProposalsResult
import com.example.domain.model.SynopsisVariant
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
 * Générateur IA pour la Phase 8 : Titre, synopsis et description de couverture (Marketing).
 * Utilise gemini-3.6-flash avec schéma JSON contraint strict (responseSchema).
 *
 * RÈGLES D'OR DU PROMPT MAÎTRE :
 * 1. Utilisation de l'intégralité du canon actuel comme contexte (CanonSnapshot).
 * 2. Génération de 3 à 5 propositions de titres.
 * 3. Génération de 2 à 3 variantes de synopsis (court / moyen / long).
 * 4. Description textuelle détaillée de ce que la couverture visuelle doit représenter.
 * 5. RÈGLE STRICTE : SANS AUCUN TEXTE à intégrer dans l'image elle-même (tout texte est superposé nativement).
 * 6. Vrai appel réseau Gemini, aucune fausse donnée factice.
 */
class GeminiMarketingCoverGenerator(
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
     * Génère les propositions marketing (titres, synopsis, description couverture).
     */
    suspend fun generateMarketingAndCover(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): MarketingCoverProposalsResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildMarketingPrompt(
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
     * Construit le prompt complet en intégrant la totalité du canon validé.
     */
    fun buildMarketingPrompt(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es le Directeur Éditorial, Marketing et Artistique Senior d'une maison d'édition majeure de Webtoons.")
            appendLine("Ta mission est de concevoir le packaging marketing complet du webtoon : TITRES, VARIANTES DE SYNOPSIS et DESCRIPTION DÉTAILLÉE DE COUVERTURE.")
            appendLine()
            appendLine("--- RÈGLE ABSOLUE SUR LA COUVERTURE ---")
            appendLine("L'illustration de couverture sera générée par un modèle d'image puis traitée par l'application.")
            appendLine("Par conséquent : LA DESCRIPTION VISUELLE DE COUVERTURE NE DOIT CONTENIR AUCUN TEXTE, AUCUN TITRE, AUCUN LOGO NI TYPOGRAPHIE DANS L'IMAGE.")
            appendLine("Tout titre, nom d'auteur ou accroche sera superposé nativement par l'application Android en Jetpack Compose.")
            appendLine("La description doit se concentrer à 100% sur le visuel pur : cadrage, personnages, poses, expressions, éclairage et décors.")
            appendLine()
            appendLine("--- CONTEXTE ACTUEL DU PROJET ---")
            appendLine("Titre de travail initial : $projectTitle")
            if (projectSynopsis.isNotBlank()) appendLine("Idée / Synopsis initial : $projectSynopsis")
            if (!projectGenre.isNullOrBlank()) appendLine("Genre : $projectGenre")
            if (!projectTone.isNullOrBlank()) appendLine("Tonalité : $projectTone")
            appendLine()

            appendLine("--- INTÉGRALITÉ DU CANON VALIDÉ (RÉFÉRENCE ABSOLUE) ---")

            // Story Bible
            if (canonSnapshot.storyBible != null) {
                appendLine("STORY BIBLE :")
                appendLine("- Thèmes majeurs : ${canonSnapshot.storyBible.mainTheme}")
                appendLine("- Arc narratif : ${canonSnapshot.storyBible.narrativeArc}")
                appendLine("- Climax prévu : ${canonSnapshot.storyBible.climaxDescription}")
                appendLine("- Rythme narratif : ${canonSnapshot.storyBible.narrativePacing}")
            }

            // Personnages
            if (canonSnapshot.characters.isNotEmpty()) {
                appendLine("PERSONNAGES CANONIQUES :")
                canonSnapshot.characters.forEach { c ->
                    appendLine("- ${c.name} (${c.role}, ${c.archetype}) : ${c.personalityTraits}")
                    if (c.visualDescription.isNotBlank()) {
                        appendLine("  Description visuelle : ${c.visualDescription}")
                    }
                }
            }

            // Lieux
            if (canonSnapshot.locations.isNotEmpty()) {
                appendLine("LIEUX CANONIQUES :")
                canonSnapshot.locations.forEach { l ->
                    val extra = if (l.atmosphere.isNotBlank()) " [${l.atmosphere}]" else ""
                    appendLine("- ${l.name}$extra : ${l.description}")
                }
            }

            // Univers / Règles du monde
            if (canonSnapshot.worldBible != null) {
                appendLine("UNIVERS & RÈGLES DU MONDE :")
                if (canonSnapshot.worldBible.magicOrTechRules.isNotBlank()) {
                    appendLine("- Règles magiques/scientifiques : ${canonSnapshot.worldBible.magicOrTechRules}")
                }
                if (canonSnapshot.worldBible.factions.isNotBlank()) {
                    appendLine("- Factions & Géopolitique : ${canonSnapshot.worldBible.factions}")
                }
                if (canonSnapshot.worldBible.geography.isNotBlank()) {
                    appendLine("- Géographie : ${canonSnapshot.worldBible.geography}")
                }
            }

            // Direction Artistique (Visual Bible)
            if (canonSnapshot.visualBible != null) {
                appendLine("DIRECTION ARTISTIQUE (VISUAL BIBLE) :")
                appendLine("- Style graphique : ${canonSnapshot.visualBible.artStyle}")
                appendLine("- Palette de couleurs : ${canonSnapshot.visualBible.colorPalette}")
                appendLine("- Ambiance lumineuse : ${canonSnapshot.visualBible.lightingMood}")
                if (canonSnapshot.visualBible.continuityElements.isNotBlank()) {
                    appendLine("- Éléments de continuité : ${canonSnapshot.visualBible.continuityElements}")
                }
            }

            // Humor DNA
            if (canonSnapshot.humorDna != null) {
                appendLine("ADN HUMORISTIQUE :")
                appendLine("- Style : ${canonSnapshot.humorDna.humorStyle}")
            }

            appendLine()
            appendLine("--- ATTENTES PRÉCISES POUR LE RÉSULTAT ---")
            appendLine("1. TITRES : Génère entre 3 et 5 propositions de titres forts, mémorables, évocateurs du genre et du ton.")
            appendLine("2. VARIANTES DE SYNOPSIS :")
            appendLine("   - 'Court' : 1 à 2 phrases d'accroche très percutantes pour la bannière.")
            appendLine("   - 'Moyen' : 1 paragraphe équilibré (héros, incident, menace, enjeu).")
            appendLine("   - 'Long' : 2 paragraphes détaillant l'univers et le dilemme central sans spoiler la fin.")
            appendLine("   IMPORTANT : Les synopsis NE DOIVENT EN AUCUN CAS contredire les faits de la Story Bible ou les rôles des personnages.")
            appendLine("3. COUVERTURE VISUELLE :")
            appendLine("   - Composition portrait/verticale percutante (1 ou 2 personnages clés, pose forte, regard captivant).")
            appendLine("   - Harmonie totale avec la Direction Artistique (style, lumière, palettes).")
            appendLine("   - RAPPEL : ZÉRO texte dans l'image (aucune typographie ni logo).")
        }
    }

    private fun buildRequestBodyJson(promptText: String): JSONObject {
        val contentsArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "parts",
                        JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        }
                    )
                }
            )
        }

        val generationConfig = JSONObject().apply {
            put("responseMimeType", "application/json")
            put("responseSchema", buildResponseSchemaJson())
            put("temperature", 0.7)
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }
    }

    private fun buildResponseSchemaJson(): JSONObject {
        // Schéma pour les variantes de synopsis
        val synopsisVariantSchema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("typeLongueur", JSONObject().apply { put("type", "STRING"); put("description", "Court, Moyen ou Long") })
                    put("texte", JSONObject().apply { put("type", "STRING"); put("description", "Contenu textuel du synopsis") })
                    put("accroche", JSONObject().apply { put("type", "STRING"); put("description", "Phrase d'accroche marketing percutante") })
                }
            )
            put("required", JSONArray().apply { put("typeLongueur"); put("texte") })
        }

        // Schéma pour la couverture
        val coverSchema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("composition", JSONObject().apply { put("type", "STRING"); put("description", "Cadrage vertical, pose et disposition") })
                    put(
                        "personnagesPresents",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "Noms des personnages canoniques présents sur l'illustration")
                        }
                    )
                    put("ambianceEtLumiere", JSONObject().apply { put("type", "STRING"); put("description", "Éclairage, ombres et contrastes") })
                    put("paletteCouleurs", JSONObject().apply { put("type", "STRING"); put("description", "Dominantes chromatiques") })
                    put(
                        "descriptionCompleteSansTexte",
                        JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Description visuelle complète pour le modèle d'image, SANS AUCUN TEXTE NI LOGO")
                        }
                    )
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("composition")
                    put("personnagesPresents")
                    put("ambianceEtLumiere")
                    put("descriptionCompleteSansTexte")
                }
            )
        }

        return JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put(
                        "titresProposes",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "3 à 5 propositions de titre pour le webtoon")
                        }
                    )
                    put(
                        "variantesSynopsis",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", synopsisVariantSchema)
                            put("description", "2 à 3 variantes de synopsis (Court, Moyen, Long)")
                        }
                    )
                    put("descriptionVisuelleCouverture", coverSchema)
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("titresProposes")
                    put("variantesSynopsis")
                    put("descriptionVisuelleCouverture")
                }
            )
        }
    }

    private fun executeRequestAndParse(request: Request, projectId: String): MarketingCoverProposalsResult {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Erreur API Gemini (${response.code}) : $errorBody")
            }

            val bodyString = response.body?.string()
                ?: throw IOException("Réponse Gemini vide lors de la génération marketing.")

            val rootJson = JSONObject(bodyString)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IOException("Aucun candidat retourné par le modèle Gemini.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) {
                throw IOException("Contenu vide retourné par le modèle Gemini.")
            }

            val textPart = parts.getJSONObject(0).getString("text")
            val parsedResult = JSONObject(textPart)

            return parseMarketingResult(parsedResult, projectId)
        }
    }

    private fun parseMarketingResult(json: JSONObject, projectId: String): MarketingCoverProposalsResult {
        // Titres
        val titlesList = mutableListOf<String>()
        val titlesArray = json.optJSONArray("titresProposes")
        if (titlesArray != null) {
            for (i in 0 until titlesArray.length()) {
                val t = titlesArray.optString(i, "").trim()
                if (t.isNotBlank()) titlesList.add(t)
            }
        }

        // Variantes de synopsis
        val synopsisList = mutableListOf<SynopsisVariant>()
        val synArray = json.optJSONArray("variantesSynopsis")
        if (synArray != null) {
            for (i in 0 until synArray.length()) {
                val sObj = synArray.getJSONObject(i)
                synopsisList.add(
                    SynopsisVariant(
                        lengthType = sObj.optString("typeLongueur", "Moyen"),
                        text = sObj.optString("texte", ""),
                        tagline = sObj.optString("accroche", "")
                    )
                )
            }
        }

        // Description de couverture
        val coverObj = json.optJSONObject("descriptionVisuelleCouverture") ?: JSONObject()
        val charsList = mutableListOf<String>()
        val charsArray = coverObj.optJSONArray("personnagesPresents")
        if (charsArray != null) {
            for (i in 0 until charsArray.length()) {
                val c = charsArray.optString(i, "").trim()
                if (c.isNotBlank()) charsList.add(c)
            }
        }

        val coverProposal = CoverVisualProposal(
            composition = coverObj.optString("composition", ""),
            charactersPresent = charsList,
            atmosphereAndLighting = coverObj.optString("ambianceEtLumiere", ""),
            colorPalette = coverObj.optString("paletteCouleurs", ""),
            detailedDescriptionNoText = coverObj.optString("descriptionCompleteSansTexte", "")
        )

        return MarketingCoverProposalsResult(
            projectId = projectId,
            titleProposals = titlesList,
            synopsisVariants = synopsisList,
            coverProposal = coverProposal
        )
    }
}

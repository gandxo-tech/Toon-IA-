package com.example.ai

import com.example.BuildConfig
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.HumorDnaResult
import com.example.domain.model.VisualBibleResult
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
 * Générateur IA pour la Visual Bible et la Humor DNA de ToonIA (Phase 7).
 * Utilise gemini-3.5-flash avec un schéma JSON contraint strict (responseSchema).
 *
 * Contraintes absolues du Prompt Maître :
 * - Canon complet en contexte (Personnages validés, Univers validé, Story Bible validée).
 * - Alignement tonal strict : l'humour doit correspondre au ton établi dans la Story Bible.
 * - Les éléments d'humour sont expressément présentés comme des options disponibles, PAS des obligations pour chaque chapitre.
 * - Vrai appel réseau Gemini, aucune donnée factice inventée en dur.
 */
class GeminiVisualHumorGenerator(
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
    }

    /**
     * Génère la Visual Bible (Direction Artistique & Découpage) sous schéma JSON contraint.
     */
    suspend fun generateVisualBible(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): VisualBibleResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildVisualBiblePrompt(
            projectTitle = projectTitle,
            projectSynopsis = projectSynopsis,
            projectGenre = projectGenre,
            projectTone = projectTone,
            canonSnapshot = canonSnapshot
        )

        val requestBodyJson = buildVisualBibleRequestBodyJson(promptText)
        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeVisualRequestAndParse(request, canonSnapshot.projectId)
    }

    /**
     * Génère l'ADN Humoristique (Humor DNA) sous schéma JSON contraint.
     */
    suspend fun generateHumorDna(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): HumorDnaResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Clé API Gemini non configurée. Veuillez ajouter votre clé API Gemini dans le panneau Secrets d'AI Studio."
            )
        }

        val promptText = buildHumorDnaPrompt(
            projectTitle = projectTitle,
            projectSynopsis = projectSynopsis,
            projectGenre = projectGenre,
            projectTone = projectTone,
            canonSnapshot = canonSnapshot
        )

        val requestBodyJson = buildHumorDnaRequestBodyJson(promptText)
        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeHumorRequestAndParse(request, canonSnapshot.projectId)
    }

    /**
     * Construit le prompt pour la Visual Bible en incorporant tout le canon.
     */
    fun buildVisualBiblePrompt(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es le Directeur Artistique en chef de ToonIA, expert en webtoons verticaux de standard professionnel.")
            appendLine("Ta mission est de concevoir la 'Visual Bible' (Bible Visuelle) pour ce projet.")
            appendLine("TITRE DU WEBTOON : $projectTitle")
            appendLine("SYNOPSIS : $projectSynopsis")
            if (!projectGenre.isNullOrBlank()) appendLine("GENRE : $projectGenre")
            if (!projectTone.isNullOrBlank()) appendLine("TON RECHERCHÉ : $projectTone")
            appendLine()

            appendLine("--- CANON ET HISTOIRE ÉTABLIS (IMPÉRATIF DE CONTINUITÉ VISUELLE) ---")
            // Story Bible
            if (canonSnapshot.storyBible != null) {
                appendLine("THÈMES & ARCS DE LA STORY BIBLE :")
                appendLine("- Thèmes : ${canonSnapshot.storyBible.mainTheme}")
                appendLine("- Arc narratif : ${canonSnapshot.storyBible.narrativeArc}")
                appendLine("- Climax envisagé : ${canonSnapshot.storyBible.climaxDescription}")
                appendLine("- Rythme : ${canonSnapshot.storyBible.narrativePacing}")
            }

            // Personnages
            if (canonSnapshot.characters.isNotEmpty()) {
                appendLine("APPARENCES DES PERSONNAGES CANONIQUES :")
                canonSnapshot.characters.forEach { c ->
                    appendLine("- ${c.name} [${c.role}] : Visuel='${c.visualDescription}', Traits='${c.personalityTraits}'")
                }
            }

            // Lieux
            if (canonSnapshot.locations.isNotEmpty()) {
                appendLine("LIEUX MAJEURS :")
                canonSnapshot.locations.forEach { l ->
                    appendLine("- ${l.name} : Atmosphère='${l.atmosphere}', Description='${l.description}'")
                }
            }
            appendLine()

            appendLine("DIRECTIVES D'EXCELLENCE POUR LA VISUAL BIBLE WEBTOON :")
            appendLine("1. Direction artistique générale : style graphique précis (ex: Manhwa d'action dynamique aux lignes nettes et contours anguleux, Romance pastel lumineuse avec filtres doux, Dark Fantasy granuleux avec contrastes d'encrage accusés, Cyberpunk néon avec halos fluorescents).")
            appendLine("2. Caractéristiques visuelles récurrentes : détails d'ombrage, texture des effets magiques/technologiques, traitement des yeux et expressions fortes.")
            appendLine("3. Règles de cadrage privilégiées : utilisation experte du ratio vertical smartphone (plans larges verticaux pour l'ampleur, gouttières aérées avec fond noir pour la dramatisation ou blanc pour le quotidien, transitions fluides par dégradé).")
            appendLine("4. Palette de couleurs et ambiance lumineuse : dominantes chromatiques, contrastes symboliques, gestion des scènes de nuit ou de combat.")
            appendLine("5. éléments visuels de continuité : motifs clés qui doivent rester strictement constants d'un chapitre à l'autre (ex: éclat des yeux d'un pouvoir, couleur d'une aura, accessoire fétiche, traitement spécifique des flashbacks).")
            appendLine("Renvoie UNIQUEMENT le JSON conforme au schéma.")
        }
    }

    /**
     * Construit le prompt pour l'Humor DNA en insistant sur l'harmonie avec la Story Bible
     * et en indiquant que l'humour n'est JAMAIS forcé.
     */
    fun buildHumorDnaPrompt(
        projectTitle: String,
        projectSynopsis: String,
        projectGenre: String?,
        projectTone: String?,
        canonSnapshot: CanonSnapshot
    ): String {
        return buildString {
            appendLine("Tu es le Consultant Narratif & Spécialiste Humour de ToonIA pour webtoons.")
            appendLine("Ta mission est de définir l'ADN Humoristique ('Humor DNA') du projet.")
            appendLine("TITRE : $projectTitle")
            appendLine("SYNOPSIS : $projectSynopsis")
            if (!projectGenre.isNullOrBlank()) appendLine("GENRE : $projectGenre")
            if (!projectTone.isNullOrBlank()) appendLine("TON DU RÉCIT : $projectTone")
            appendLine()

            appendLine("--- CANON DU PROJET (PERSONNAGES & STORY BIBLE) ---")
            if (canonSnapshot.storyBible != null) {
                appendLine("STORY BIBLE VALIDÉE :")
                appendLine("- Climax & Direction : ${canonSnapshot.storyBible.climaxDescription}")
                appendLine("- Thèmes : ${canonSnapshot.storyBible.mainTheme}")
            }

            appendLine("PERSONNAGES VALIDES DANS LE CANON (Accrocher l'humour à leurs personnalités existantes) :")
            if (canonSnapshot.characters.isNotEmpty()) {
                canonSnapshot.characters.forEach { c ->
                    appendLine("- ${c.name} (${c.role}) : Traits='${c.personalityTraits}', Archétype='${c.archetype}'")
                }
            } else {
                appendLine("- Aucun personnage canonique encore enregistré.")
            }
            appendLine()

            appendLine("RÈGLES CRITIQUES ABSOLUES POUR L'HUMOR DNA :")
            appendLine("1. ALIGNEMENT TOTAL AVEC LE TON DE L'HISTOIRE : Si l'histoire est tragique, sombre ou intense, l'humour DOIT être mesuré, pince-sans-rire (deadpan), cynique, ironique ou servir de respiration salvatrice ponctuelle. N'invente JAMAIS d'humour burlesque déplacé qui détruirait la crédibilité du drame.")
            appendLine("2. DISPONIBILITÉ, PAS OBLIGATION : Précise des outils comiques utilisables quand la scène s'y prête. Ces éléments ne doivent PAS être forcés dans chaque chapitre. Ce sont des réserves d'idées pour l'auteur.")
            appendLine("3. BLAGUES PROPRES AUX PERSONNAGES : Définis des manies, réactions décalées ou tics comiques spécifiques pour les personnages validés du canon.")
            appendLine("4. RUNNING GAGS : Idées de gags récurrents légers et mémorables qui peuvent revenir avec parcimonie.")
            appendLine("Renvoie UNIQUEMENT le JSON conforme au schéma.")
        }
    }

    private fun buildVisualBibleRequestBodyJson(promptText: String): JSONObject {
        return JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
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
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", buildVisualBibleSchema())
                    put("temperature", 0.7)
                }
            )
        }
    }

    private fun buildHumorDnaRequestBodyJson(promptText: String): JSONObject {
        return JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
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
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", buildHumorDnaSchema())
                    put("temperature", 0.7)
                }
            )
        }
    }

    private fun buildVisualBibleSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("directionArtistique", JSONObject().apply { put("type", "STRING"); put("description", "Direction artistique et style graphique général du webtoon") })
                    put("paletteCouleurs", JSONObject().apply { put("type", "STRING"); put("description", "Palette de couleurs dominantes, teintes d'ambiance et symbolisme chromatique") })
                    put("ambianceLumineuse", JSONObject().apply { put("type", "STRING"); put("description", "Gestion de la lumière, ombres dures, contrastes et atmosphère") })
                    put("styleDecoupage", JSONObject().apply { put("type", "STRING"); put("description", "Style de découpage vertical adapté au smartphone (rythme, gouttières, respirations)") })
                    put(
                        "caracteristiquesVisuellesRecurrentes",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "Caractéristiques visuelles constantes (ex: yeux expressifs, effets magiques, encrage dynamique)")
                        }
                    )
                    put(
                        "reglesCadragePrivilegiees",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "Règles et plans de cadrage favorisés (gros plans émotionnels, panoramiques verticaux)")
                        }
                    )
                    put(
                        "elementsVisuelsContinuite",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "Éléments visuels stricts à conserver d'un chapitre à l'autre (auras, cicatrices, vêtements clés)")
                        }
                    )
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("directionArtistique")
                    put("paletteCouleurs")
                    put("ambianceLumineuse")
                    put("styleDecoupage")
                    put("caracteristiquesVisuellesRecurrentes")
                    put("reglesCadragePrivilegiees")
                    put("elementsVisuelsContinuite")
                }
            )
        }
    }

    private fun buildHumorDnaSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("styleHumour", JSONObject().apply { put("type", "STRING"); put("description", "Style d'humour en parfaite cohérence avec le ton du récit (ironique, situationnel, subtil...)") })
                    put("momentsChibiAutorises", JSONObject().apply { put("type", "BOOLEAN"); put("description", "Autoriser ou non les inserts graphiques chibi/SD selon le sérieux du ton") })
                    put("frequenceHumour", JSONObject().apply { put("type", "STRING"); put("description", "Fréquence suggérée des touches humoristiques (ex: rare et de respiration, modérée...)") })
                    put(
                        "runningGagsPossibles",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "Running gags récurrents possibles (à utiliser avec modération)")
                        }
                    )
                    put(
                        "blaguesPersonnages",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("nomPersonnage", JSONObject().apply { put("type", "STRING") })
                                            put("noteHumour", JSONObject().apply { put("type", "STRING") })
                                        }
                                    )
                                    put(
                                        "required",
                                        JSONArray().apply {
                                            put("nomPersonnage")
                                            put("noteHumour")
                                        }
                                    )
                                }
                            )
                            put("description", "Blagues, tics décalés ou manies propres à des personnages canoniques précis")
                        }
                    )
                    put(
                        "situationsComiquesRecurrentes",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                            put("description", "Types de quiproquos ou situations comiques récurrentes exploitables")
                        }
                    )
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("styleHumour")
                    put("momentsChibiAutorises")
                    put("frequenceHumour")
                    put("runningGagsPossibles")
                    put("blaguesPersonnages")
                    put("situationsComiquesRecurrentes")
                }
            )
        }
    }

    private fun executeVisualRequestAndParse(request: Request, projectId: String): VisualBibleResult {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Aucun détail"
                throw IOException("Échec de la requête Gemini (${response.code}) : $errorBody")
            }

            val bodyString = response.body?.string()
                ?: throw IOException("Réponse Gemini vide lors de la génération de la Visual Bible.")

            val rootJson = JSONObject(bodyString)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IOException("Gemini n'a retourné aucun candidat pour la Visual Bible.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val rawJsonText = parts.getJSONObject(0).getString("text").trim()

            val visualJson = JSONObject(rawJsonText)
            return VisualBibleResult.fromJson(visualJson, projectId)
        }
    }

    private fun executeHumorRequestAndParse(request: Request, projectId: String): HumorDnaResult {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Aucun détail"
                throw IOException("Échec de la requête Gemini (${response.code}) : $errorBody")
            }

            val bodyString = response.body?.string()
                ?: throw IOException("Réponse Gemini vide lors de la génération de l'Humor DNA.")

            val rootJson = JSONObject(bodyString)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IOException("Gemini n'a retourné aucun candidat pour l'Humor DNA.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val rawJsonText = parts.getJSONObject(0).getString("text").trim()

            val humorJson = JSONObject(rawJsonText)
            return HumorDnaResult.fromJson(humorJson, projectId)
        }
    }
}

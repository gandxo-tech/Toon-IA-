package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Détails optionnels et avancés renseignés par l'utilisateur lors de la soumission de l'idée.
 */
data class AdvancedIdeaDetails(
    val genre: String = "",
    val ambiance: String = "",
    val epoque: String = "",
    val personnagesSouhaites: String = "",
    val inspirations: String = "",
    val longueurVisee: String = "",
    val publicCible: String = "",
    val contraintes: String = "",
    val evenementsSouhaites: String = ""
) {
    fun hasAnyDetails(): Boolean {
        return genre.isNotBlank() || ambiance.isNotBlank() || epoque.isNotBlank() ||
            personnagesSouhaites.isNotBlank() || inspirations.isNotBlank() ||
            longueurVisee.isNotBlank() || publicCible.isNotBlank() ||
            contraintes.isNotBlank() || evenementsSouhaites.isNotBlank()
    }

    fun toJsonString(): String {
        val json = JSONObject()
        if (genre.isNotBlank()) json.put("genre", genre)
        if (ambiance.isNotBlank()) json.put("ambiance", ambiance)
        if (epoque.isNotBlank()) json.put("epoque", epoque)
        if (personnagesSouhaites.isNotBlank()) json.put("personnagesSouhaites", personnagesSouhaites)
        if (inspirations.isNotBlank()) json.put("inspirations", inspirations)
        if (longueurVisee.isNotBlank()) json.put("longueurVisee", longueurVisee)
        if (publicCible.isNotBlank()) json.put("publicCible", publicCible)
        if (contraintes.isNotBlank()) json.put("contraintes", contraintes)
        if (evenementsSouhaites.isNotBlank()) json.put("evenementsSouhaites", evenementsSouhaites)
        return json.toString()
    }
}

/**
 * Résultat structuré de l'analyse d'idée par Gemini via responseSchema.
 */
data class IdeaAnalysisResult(
    val genrePrincipal: String,
    val sousGenres: List<String>,
    val ton: String,
    val themesDetectes: List<String>,
    val elementsNarratifsExplicites: List<String>,
    val ambiguitesAClarifier: List<String>
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("genrePrincipal", genrePrincipal)
        json.put("sousGenres", JSONArray(sousGenres))
        json.put("ton", ton)
        json.put("themesDetectes", JSONArray(themesDetectes))
        json.put("elementsNarratifsExplicites", JSONArray(elementsNarratifsExplicites))
        json.put("ambiguitesAClarifier", JSONArray(ambiguitesAClarifier))
        return json.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): IdeaAnalysisResult {
            val json = JSONObject(jsonStr)
            val genrePrincipal = json.optString("genrePrincipal", "Webtoon")
            val ton = json.optString("ton", "Non spécifié")

            val sousGenres = json.optJSONArray("sousGenres")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            val themes = json.optJSONArray("themesDetectes")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            val narratifs = json.optJSONArray("elementsNarratifsExplicites")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            val ambiguites = json.optJSONArray("ambiguitesAClarifier")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            return IdeaAnalysisResult(
                genrePrincipal = genrePrincipal,
                sousGenres = sousGenres,
                ton = ton,
                themesDetectes = themes,
                elementsNarratifsExplicites = narratifs,
                ambiguitesAClarifier = ambiguites
            )
        }
    }
}

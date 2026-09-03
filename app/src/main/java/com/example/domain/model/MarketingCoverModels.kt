package com.example.domain.model

/**
 * Modèles pour la Phase 8 : Titre, synopsis et description de couverture (Marketing).
 */

/**
 * Variante de synopsis générée par l'IA.
 */
data class SynopsisVariant(
    val lengthType: String, // "Court" (1-2 phrases), "Moyen" (1 paragraphe), "Long" (2 paragraphes)
    val text: String,
    val tagline: String = "" // Phrase d'accroche percutante
)

/**
 * Description détaillée de ce que la couverture visuelle doit représenter.
 * RÈGLE ABSOLUE : SANS AUCUN TEXTE dans l'illustration (titre et sous-titre superposés nativement).
 */
data class CoverVisualProposal(
    val composition: String = "",
    val charactersPresent: List<String> = emptyList(),
    val atmosphereAndLighting: String = "",
    val colorPalette: String = "",
    val detailedDescriptionNoText: String = ""
)

/**
 * Ensemble complet des propositions générées pour la Phase 8.
 */
data class MarketingCoverProposalsResult(
    val projectId: String,
    val titleProposals: List<String> = emptyList(), // 3 à 5 propositions
    val synopsisVariants: List<SynopsisVariant> = emptyList(), // 2 à 3 variantes
    val coverProposal: CoverVisualProposal = CoverVisualProposal()
)

/**
 * État de sélection et de validation par l'utilisateur.
 */
data class MarketingSelection(
    val selectedTitle: String = "",
    val selectedSynopsis: String = "",
    val selectedLengthType: String = "Moyen",
    val selectedTagline: String = "",
    val selectedCoverDescription: String = "",
    val isCanonValidated: Boolean = false,
    val coverImagePath: String? = null,
    val coverId: String? = null
)

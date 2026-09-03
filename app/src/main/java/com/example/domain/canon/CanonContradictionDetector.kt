package com.example.domain.canon

import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.StoryObjectEntity

/**
 * Détecteur de contradictions canoniques.
 * Compare un nouvel élément candidat aux faits établis du canon.
 * Règle d'or : Ne corrige jamais automatiquement. Si aucune détection fiable n'est possible,
 * retourne une liste vide plutôt que de deviner.
 */
class CanonContradictionDetector {

    /**
     * Détecte les contradictions potentielles entre un élément et le snapshot du canon existant.
     */
    fun detectContradiction(element: CanonElement, snapshot: CanonSnapshot): List<Contradiction> {
        return when (element) {
            is CanonElement.Character -> checkCharacterContradictions(element.entity, snapshot)
            is CanonElement.Location -> checkLocationContradictions(element.entity, snapshot)
            is CanonElement.StoryObject -> checkStoryObjectContradictions(element.entity, snapshot)
            is CanonElement.Chapter -> checkChapterContradictions(element, snapshot)
            is CanonElement.TimelineEvent -> checkTimelineContradictions(element, snapshot)
            is CanonElement.Memory -> checkMemoryContradictions(element, snapshot)
            is CanonElement.WorldBible -> checkWorldBibleContradictions(element, snapshot)
            is CanonElement.StoryBible -> checkStoryBibleContradictions(element, snapshot)
            is CanonElement.VisualBible -> checkVisualBibleContradictions(element, snapshot)
            is CanonElement.HumorDNA -> checkHumorDnaContradictions(element, snapshot)
            is CanonElement.Cover -> checkCoverContradictions(element.entity, snapshot)
            is CanonElement.Scene -> emptyList()
            is CanonElement.Panel -> emptyList()
            is CanonElement.Dialogue -> emptyList()
            is CanonElement.ProjectMetadata -> checkProjectMetadataContradictions(element.finalTitle, element.finalSynopsis, snapshot)
        }
    }

    private fun checkCharacterContradictions(
        newChar: CharacterEntity,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val trimmedNewName = newChar.name.trim()

        // Recherche d'un personnage portant le même nom (ou même ID)
        val existingChar = snapshot.characters.firstOrNull {
            it.name.trim().equals(trimmedNewName, ignoreCase = true) || it.id == newChar.id
        }

        if (existingChar != null) {
            // 1. Rôle fondamental divergent (ex: Protagoniste vs Antagoniste)
            if (existingChar.role.isNotBlank() &&
                newChar.role.isNotBlank() &&
                !existingChar.role.equals(newChar.role, ignoreCase = true)
            ) {
                contradictions.add(
                    Contradiction(
                        targetType = "Personnage",
                        targetName = existingChar.name,
                        fieldName = "Rôle",
                        canonicalValue = existingChar.role,
                        conflictingValue = newChar.role,
                        description = "Le rôle de ${existingChar.name} dans le canon est '${existingChar.role}', mais le nouvel élément le définit comme '${newChar.role}'.",
                        severity = ContradictionSeverity.HIGH
                    )
                )
            }

            // 2. Archétype contradictoire
            if (existingChar.archetype.isNotBlank() &&
                newChar.archetype.isNotBlank() &&
                !existingChar.archetype.equals(newChar.archetype, ignoreCase = true)
            ) {
                contradictions.add(
                    Contradiction(
                        targetType = "Personnage",
                        targetName = existingChar.name,
                        fieldName = "Archétype",
                        canonicalValue = existingChar.archetype,
                        conflictingValue = newChar.archetype,
                        description = "L'archétype établi est '${existingChar.archetype}', différent de '${newChar.archetype}'.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }

            // 3. Description visuelle incompatible (ex: yeux bleus vs yeux rouges, cheveux blonds vs cheveux noirs)
            val visualMismatch = detectVisualDivergence(existingChar.visualDescription, newChar.visualDescription)
            if (visualMismatch != null) {
                contradictions.add(
                    Contradiction(
                        targetType = "Personnage",
                        targetName = existingChar.name,
                        fieldName = "Apparence Visuelle",
                        canonicalValue = existingChar.visualDescription,
                        conflictingValue = newChar.visualDescription,
                        description = visualMismatch,
                        severity = ContradictionSeverity.HIGH
                    )
                )
            }
        }

        // 4. Vérification par rapport aux règles absolues dans CanonMemories (ex: mort du personnage)
        val deathMemory = snapshot.canonMemories.firstOrNull { memory ->
            memory.fact.contains(trimmedNewName, ignoreCase = true) &&
                (memory.fact.contains("mort", ignoreCase = true) ||
                 memory.fact.contains("décédé", ignoreCase = true) ||
                 memory.fact.contains("tué", ignoreCase = true))
        }
        if (deathMemory != null && !newChar.backstory.contains("résurrection", ignoreCase = true)) {
            contradictions.add(
                Contradiction(
                    targetType = "Personnage",
                    targetName = newChar.name,
                    fieldName = "Statut Vital (Canon)",
                    canonicalValue = deathMemory.fact,
                    conflictingValue = "Personnage réintroduit actif",
                    description = "Une mémoire canonique établit que '${deathMemory.fact}'. La réintroduction de ${newChar.name} contredit ce fait absolu.",
                    severity = ContradictionSeverity.HIGH
                )
            )
        }

        return contradictions
    }

    private fun checkLocationContradictions(
        newLoc: LocationEntity,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existingLoc = snapshot.locations.firstOrNull {
            it.name.trim().equals(newLoc.name.trim(), ignoreCase = true) || it.id == newLoc.id
        }

        if (existingLoc != null) {
            // Divergence d'atmosphère ou description
            if (existingLoc.atmosphere.isNotBlank() &&
                newLoc.atmosphere.isNotBlank() &&
                !existingLoc.atmosphere.equals(newLoc.atmosphere, ignoreCase = true)
            ) {
                contradictions.add(
                    Contradiction(
                        targetType = "Lieu",
                        targetName = existingLoc.name,
                        fieldName = "Atmosphère",
                        canonicalValue = existingLoc.atmosphere,
                        conflictingValue = newLoc.atmosphere,
                        description = "L'atmosphère canonique de ${existingLoc.name} est '${existingLoc.atmosphere}', modifiée en '${newLoc.atmosphere}'.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }
        }
        return contradictions
    }

    private fun checkStoryObjectContradictions(
        newObj: StoryObjectEntity,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existingObj = snapshot.storyObjects.firstOrNull {
            it.name.trim().equals(newObj.name.trim(), ignoreCase = true) || it.id == newObj.id
        }

        if (existingObj != null) {
            // Propriétaire ou porteur différent sans explication
            if (!existingObj.ownerCharacterId.isNullOrBlank() &&
                !newObj.ownerCharacterId.isNullOrBlank() &&
                existingObj.ownerCharacterId != newObj.ownerCharacterId
            ) {
                val ownerName = snapshot.characters.find { it.id == existingObj.ownerCharacterId }?.name
                    ?: existingObj.ownerCharacterId
                val newOwnerName = snapshot.characters.find { it.id == newObj.ownerCharacterId }?.name
                    ?: newObj.ownerCharacterId

                contradictions.add(
                    Contradiction(
                        targetType = "Objet Narratif",
                        targetName = existingObj.name,
                        fieldName = "Possesseur",
                        canonicalValue = "Possédé par $ownerName",
                        conflictingValue = "Attribué à $newOwnerName",
                        description = "L'objet '${existingObj.name}' appartient canoniquement à $ownerName, mais est ici associé à $newOwnerName.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }
        }
        return contradictions
    }

    private fun checkChapterContradictions(
        element: CanonElement.Chapter,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val duplicateNum = snapshot.canonChapters.firstOrNull {
            it.number == element.entity.number && it.id != element.entity.id
        }
        if (duplicateNum != null) {
            contradictions.add(
                Contradiction(
                    targetType = "Chapitre",
                    targetName = "Chapitre ${element.entity.number}",
                    fieldName = "Numéro",
                    canonicalValue = "Chapitre existant: '${duplicateNum.title}'",
                    conflictingValue = "Nouveau chapitre: '${element.entity.title}'",
                    description = "Le numéro de chapitre ${element.entity.number} est déjà attribué au chapitre canonique '${duplicateNum.title}'.",
                    severity = ContradictionSeverity.HIGH
                )
            )
        }
        return contradictions
    }

    private fun checkTimelineContradictions(
        element: CanonElement.TimelineEvent,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val duplicateOrder = snapshot.canonTimelineEvents.firstOrNull {
            it.orderIndex == element.entity.orderIndex && it.id != element.entity.id
        }
        if (duplicateOrder != null && duplicateOrder.title != element.entity.title) {
            contradictions.add(
                Contradiction(
                    targetType = "Chronologie",
                    targetName = element.entity.title,
                    fieldName = "Ordre Temporel",
                    canonicalValue = "Position ${duplicateOrder.orderIndex}: '${duplicateOrder.title}'",
                    conflictingValue = "Position ${element.entity.orderIndex}: '${element.entity.title}'",
                    description = "Conflit d'ordonnancement temporel à l'index ${element.entity.orderIndex}.",
                    severity = ContradictionSeverity.MEDIUM
                )
            )
        }
        return contradictions
    }

    private fun checkMemoryContradictions(
        element: CanonElement.Memory,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existingFact = snapshot.canonMemories.firstOrNull {
            it.category.equals(element.entity.category, ignoreCase = true) &&
            it.fact.contains(element.entity.fact.take(20), ignoreCase = true) &&
            it.id != element.entity.id
        }
        if (existingFact != null && existingFact.fact != element.entity.fact) {
            contradictions.add(
                Contradiction(
                    targetType = "Mémoire Canonique",
                    targetName = element.entity.category,
                    fieldName = "Fait Établi",
                    canonicalValue = existingFact.fact,
                    conflictingValue = element.entity.fact,
                    description = "Un fait canonique similaire existe déjà avec une formulation différente.",
                    severity = ContradictionSeverity.MEDIUM
                )
            )
        }
        return contradictions
    }

    private fun checkWorldBibleContradictions(
        element: CanonElement.WorldBible,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existing = snapshot.worldBible
        if (existing != null && existing.magicOrTechRules.isNotBlank() && element.entity.magicOrTechRules.isNotBlank()) {
            if (!existing.magicOrTechRules.equals(element.entity.magicOrTechRules, ignoreCase = true)) {
                contradictions.add(
                    Contradiction(
                        targetType = "Bible Univers",
                        targetName = "Règles",
                        fieldName = "Règles Magiques / Technologiques",
                        canonicalValue = existing.magicOrTechRules,
                        conflictingValue = element.entity.magicOrTechRules,
                        description = "Modification des lois fondamentales du monde établies dans la Bible d'Univers.",
                        severity = ContradictionSeverity.HIGH
                    )
                )
            }
        }
        return contradictions
    }

    private fun checkStoryBibleContradictions(
        element: CanonElement.StoryBible,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existing = snapshot.storyBible
        if (existing != null && existing.mainTheme.isNotBlank() && element.entity.mainTheme.isNotBlank()) {
            if (!existing.mainTheme.equals(element.entity.mainTheme, ignoreCase = true)) {
                contradictions.add(
                    Contradiction(
                        targetType = "Bible Histoire",
                        targetName = "Thème",
                        fieldName = "Thème Principal",
                        canonicalValue = existing.mainTheme,
                        conflictingValue = element.entity.mainTheme,
                        description = "Le thème principal canonique '${existing.mainTheme}' est modifié en '${element.entity.mainTheme}'.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }
        }
        return contradictions
    }

    private fun checkVisualBibleContradictions(
        element: CanonElement.VisualBible,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existing = snapshot.visualBible
        if (existing != null && existing.artStyle.isNotBlank() && element.entity.artStyle.isNotBlank()) {
            if (!existing.artStyle.equals(element.entity.artStyle, ignoreCase = true)) {
                contradictions.add(
                    Contradiction(
                        targetType = "Direction Artistique",
                        targetName = "Style Graphique",
                        fieldName = "Direction Artistique Générale",
                        canonicalValue = existing.artStyle,
                        conflictingValue = element.entity.artStyle,
                        description = "La direction artistique canonique '${existing.artStyle}' est modifiée en '${element.entity.artStyle}'.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }
        }
        return contradictions
    }

    private fun checkHumorDnaContradictions(
        element: CanonElement.HumorDNA,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val existing = snapshot.humorDna
        if (existing != null && existing.humorStyle.isNotBlank() && element.entity.humorStyle.isNotBlank()) {
            if (!existing.humorStyle.equals(element.entity.humorStyle, ignoreCase = true)) {
                contradictions.add(
                    Contradiction(
                        targetType = "Humor DNA",
                        targetName = "Style d'humour",
                        fieldName = "Registre Comique",
                        canonicalValue = existing.humorStyle,
                        conflictingValue = element.entity.humorStyle,
                        description = "Le registre d'humour canonique '${existing.humorStyle}' est modifié en '${element.entity.humorStyle}'.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }
        }

        // Vérification de dissonance tonale flagrante avec la Story Bible
        val storyBible = snapshot.storyBible
        if (storyBible != null) {
            val isGrimStory = storyBible.climaxDescription.contains("sombre", ignoreCase = true) ||
                storyBible.climaxDescription.contains("tragique", ignoreCase = true) ||
                storyBible.mainTheme.contains("sombre", ignoreCase = true) ||
                storyBible.mainTheme.contains("mort", ignoreCase = true) ||
                storyBible.mainTheme.contains("tragédie", ignoreCase = true)

            val isBurlesque = element.entity.humorStyle.contains("burlesque", ignoreCase = true) ||
                element.entity.humorStyle.contains("slapstick", ignoreCase = true) ||
                element.entity.humorStyle.contains("bouffon", ignoreCase = true)

            if (isGrimStory && isBurlesque) {
                contradictions.add(
                    Contradiction(
                        targetType = "Humor DNA",
                        targetName = "Harmonie Tonale",
                        fieldName = "Dissonance Humour / Ton de l'Histoire",
                        canonicalValue = "Récit sombre ou tragique ('${storyBible.mainTheme}')",
                        conflictingValue = element.entity.humorStyle,
                        description = "Le registre comique '${element.entity.humorStyle}' risque d'affaiblir la tension dramatique majeure établie dans la Story Bible.",
                        severity = ContradictionSeverity.MEDIUM
                    )
                )
            }
        }

        return contradictions
    }

    /**
     * Détecte les divergences visuelles flagrantes entre deux descriptions (ex: couleurs d'yeux ou de cheveux opposées).
     */
    private fun detectVisualDivergence(canonDesc: String, newDesc: String): String? {
        if (canonDesc.isBlank() || newDesc.isBlank()) return null

        val hairColors = listOf("blond", "brun", "noir", "roux", "blanc", "argenté", "bleu", "vert", "rose")
        val eyeColors = listOf("bleu", "vert", "noir", "marron", "rouge", "doré", "ambre", "violet")

        for (color in hairColors) {
            val canonHasHair = canonDesc.contains("cheveux $color", ignoreCase = true) || canonDesc.contains("chevelure $color", ignoreCase = true)
            if (canonHasHair) {
                for (otherColor in hairColors) {
                    if (otherColor != color) {
                        val newHasOtherHair = newDesc.contains("cheveux $otherColor", ignoreCase = true) || newDesc.contains("chevelure $otherColor", ignoreCase = true)
                        if (newHasOtherHair) {
                            return "Incompatibilité capillaire : cheveux $color dans le canon, mais décrit avec des cheveux $otherColor dans le nouvel élément."
                        }
                    }
                }
            }
        }

        for (color in eyeColors) {
            val canonHasEyes = canonDesc.contains("yeux $color", ignoreCase = true)
            if (canonHasEyes) {
                for (otherColor in eyeColors) {
                    if (otherColor != color) {
                        val newHasOtherEyes = newDesc.contains("yeux $otherColor", ignoreCase = true)
                        if (newHasOtherEyes) {
                            return "Incompatibilité oculaire : yeux $color dans le canon, mais décrit avec des yeux $otherColor dans le nouvel élément."
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Vérifie la description de couverture visuelle contre les règles absolues et le canon existant.
     */
    fun checkCoverContradictions(
        cover: CoverEntity,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val visualDesc = cover.visualDescription

        // 1. Règle absolue ToonIA : Jamais de texte intégré dans l'illustration générée
        val textInImageKeywords = listOf(
            "avec le titre",
            "titre écrit",
            "texte écrit",
            "logo incrusté",
            "lettres dorées",
            "typographie sur l'image",
            "texte intégré",
            "titre en gros"
        )
        for (keyword in textInImageKeywords) {
            if (visualDesc.contains(keyword, ignoreCase = true)) {
                contradictions.add(
                    Contradiction(
                        targetType = "Couverture",
                        targetName = cover.title.ifBlank { "Couverture" },
                        fieldName = "Interdiction de texte intégré",
                        canonicalValue = "Génération sans aucun texte (le texte est superposé nativement par l'application)",
                        conflictingValue = keyword,
                        description = "La description de couverture demande '$keyword'. Règle absolue ToonIA : Les illustrations sont générées SANS texte ni logo. Tout texte est rendu nativement par l'application.",
                        severity = ContradictionSeverity.HIGH
                    )
                )
                break
            }
        }

        // 2. Vérification de cohérence visuelle avec les personnages canoniques
        for (character in snapshot.characters) {
            if (visualDesc.contains(character.name, ignoreCase = true)) {
                val visualConflict = detectVisualDivergence(character.visualDescription, visualDesc)
                if (visualConflict != null) {
                    contradictions.add(
                        Contradiction(
                            targetType = "Couverture",
                            targetName = character.name,
                            fieldName = "Continuité visuelle du personnage",
                            canonicalValue = character.visualDescription,
                            conflictingValue = visualDesc,
                            description = "Dans la couverture : $visualConflict",
                            severity = ContradictionSeverity.MEDIUM
                        )
                    )
                }
            }
        }

        return contradictions
    }

    /**
     * Vérifie que le titre et le synopsis proposés ne contredisent ni la Story Bible ni la Character Bible.
     */
    fun checkProjectMetadataContradictions(
        finalTitle: String,
        finalSynopsis: String,
        snapshot: CanonSnapshot
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()

        if (finalSynopsis.isBlank()) return contradictions

        // 1. Vérification contre la Story Bible (Ton & Conflit central)
        val storyBible = snapshot.storyBible
        if (storyBible != null) {
            val isGrimBible = storyBible.mainTheme.contains("sombre", ignoreCase = true) ||
                storyBible.mainTheme.contains("tragique", ignoreCase = true) ||
                storyBible.climaxDescription.contains("sombre", ignoreCase = true) ||
                storyBible.climaxDescription.contains("tragique", ignoreCase = true) ||
                storyBible.narrativeArc.contains("tragédie", ignoreCase = true)

            val isComedySynopsis = finalSynopsis.contains("comédie insouciante", ignoreCase = true) ||
                finalSynopsis.contains("gags burlesques", ignoreCase = true) ||
                finalSynopsis.contains("histoire légère et sans danger", ignoreCase = true)

            if (isGrimBible && isComedySynopsis) {
                contradictions.add(
                    Contradiction(
                        targetType = "Synopsis",
                        targetName = finalTitle.ifBlank { "Synopsis Final" },
                        fieldName = "Harmonie Tonale avec la Story Bible",
                        canonicalValue = "Story Bible sombre/tragique : ${storyBible.mainTheme}",
                        conflictingValue = finalSynopsis,
                        description = "Le synopsis présente une intrigue purement comique ou légère en contradiction directe avec les enjeux tragiques et sombres validés dans la Story Bible.",
                        severity = ContradictionSeverity.HIGH
                    )
                )
            }
        }

        // 2. Vérification contre la Character Bible (Rôles fondamentaux & Statut)
        for (character in snapshot.characters) {
            if (finalSynopsis.contains(character.name, ignoreCase = true)) {
                // Vérifier si un personnage décédé est décrit comme vivant ou partant dans de nouvelles aventures
                val isDeceasedInCanon = character.personalityTraits.contains("mort", ignoreCase = true) ||
                    character.personalityTraits.contains("décédé", ignoreCase = true) ||
                    character.backstory.contains("mort", ignoreCase = true) ||
                    character.backstory.contains("décédé", ignoreCase = true)

                if (isDeceasedInCanon && (finalSynopsis.contains("${character.name} part", ignoreCase = true) ||
                        finalSynopsis.contains("${character.name} explore", ignoreCase = true) ||
                        finalSynopsis.contains("${character.name} combat aux côtés", ignoreCase = true))) {
                    contradictions.add(
                        Contradiction(
                            targetType = "Synopsis",
                            targetName = character.name,
                            fieldName = "Statut Vital du Personnage",
                            canonicalValue = "${character.name} est canoniquement décédé ou absent",
                            conflictingValue = "Décrit en action dans le synopsis",
                            description = "Le personnage ${character.name} est validé comme décédé dans le canon, mais le synopsis le présente comme un protagoniste actif.",
                            severity = ContradictionSeverity.HIGH
                        )
                    )
                }

                // Inversion de rôle flagrante (ex: le héros principal qualifié d'antagoniste / traître)
                if (character.role.equals("Protagoniste", ignoreCase = true) &&
                    (finalSynopsis.contains("antagoniste ${character.name}", ignoreCase = true) ||
                        finalSynopsis.contains("le traître ${character.name}", ignoreCase = true) ||
                        finalSynopsis.contains("${character.name}, l'ennemi juré", ignoreCase = true))) {
                    contradictions.add(
                        Contradiction(
                            targetType = "Synopsis",
                            targetName = character.name,
                            fieldName = "Rôle Canonique du Protagoniste",
                            canonicalValue = "Protagoniste majeur (${character.name})",
                            conflictingValue = "Décrit comme antagoniste/traître dans le synopsis",
                            description = "${character.name} est le protagoniste du canon, mais le synopsis le qualifie d'antagoniste ou d'ennemi juré.",
                            severity = ContradictionSeverity.HIGH
                        )
                    )
                }

                // Inversion pour l'antagoniste
                if (character.role.equals("Antagoniste", ignoreCase = true) &&
                    (finalSynopsis.contains("vaillant héros ${character.name}", ignoreCase = true) ||
                        finalSynopsis.contains("notre héros ${character.name}", ignoreCase = true))) {
                    contradictions.add(
                        Contradiction(
                            targetType = "Synopsis",
                            targetName = character.name,
                            fieldName = "Rôle Canonique de l'Antagoniste",
                            canonicalValue = "Antagoniste (${character.name})",
                            conflictingValue = "Décrit comme vaillant héros dans le synopsis",
                            description = "${character.name} est l'antagoniste validé dans le canon, mais le synopsis le dépeint comme le héros.",
                            severity = ContradictionSeverity.HIGH
                        )
                    )
                }
            }
        }

        return contradictions
    }
}

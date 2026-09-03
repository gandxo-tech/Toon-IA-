package com.example.domain.canon

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

/**
 * Tests unitaires et d'intégration Robolectric pour le Canon de la Phase 8 :
 * Titre, synopsis et description de couverture (Marketing).
 */
@RunWith(RobolectricTestRunner::class)
class MarketingCoverCanonTest {

    private lateinit var db: ToonDatabase
    private lateinit var detector: CanonContradictionDetector
    private lateinit var repository: CanonMemoryRepository

    private val testProjectId = "proj-marketing-test"

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToonDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        db.projectDao().insertProject(
            ProjectEntity(
                id = testProjectId,
                title = "Titre de Travail Initial",
                synopsis = "Synopsis initial temporaire"
            )
        )

        detector = CanonContradictionDetector()
        repository = CanonMemoryRepositoryImpl(db, detector)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun rejectCoverWithEmbeddedTextRule() = runTest {
        val coverWithText = CoverEntity(
            id = UUID.randomUUID().toString(),
            projectId = testProjectId,
            title = "Projet Éther",
            visualDescription = "Illustration épique avec le titre écrit en lettres dorées au centre"
        )

        val snapshot = repository.getCanonSnapshot(testProjectId)
        val contradictions = detector.checkCoverContradictions(coverWithText, snapshot)

        assertFalse("Une contradiction doit être levée pour le texte dans l'image", contradictions.isEmpty())
        val textContradiction = contradictions.firstOrNull { it.fieldName.contains("texte", ignoreCase = true) }
        assertNotNull("La contradiction d'interdiction de texte doit être présente", textContradiction)
        assertEquals(ContradictionSeverity.HIGH, textContradiction?.severity)
    }

    @Test
    fun detectVisualDivergenceInCoverPrompt() = runTest {
        // Personnage canonique aux cheveux blancs
        repository.updateCanon(
            testProjectId,
            CanonElement.Character(
                entity = CharacterEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = testProjectId,
                    name = "Kaelen",
                    role = "Protagoniste",
                    visualDescription = "Un jeune homme aux cheveux blancs et aux yeux bleus perçants"
                ),
                isUserValidated = true
            )
        )

        val snapshot = repository.getCanonSnapshot(testProjectId)

        // Couverture décrivant le même personnage avec des cheveux bruns
        val conflictingCover = CoverEntity(
            id = UUID.randomUUID().toString(),
            projectId = testProjectId,
            title = "L'Héritier",
            visualDescription = "Kaelen au premier plan avec des cheveux bruns sous une pluie battante"
        )

        val contradictions = detector.checkCoverContradictions(conflictingCover, snapshot)
        assertFalse("Une contradiction capillaire doit être détectée pour Kaelen", contradictions.isEmpty())
        val visualContradiction = contradictions.firstOrNull { it.targetName == "Kaelen" }
        assertNotNull(visualContradiction)
        assertEquals(ContradictionSeverity.MEDIUM, visualContradiction?.severity)
    }

    @Test
    fun detectContradictionBetweenStoryBibleAndComedySynopsis() = runTest {
        // Story Bible sombre et tragique
        db.storyBibleDao().insertOrUpdateStoryBible(
            StoryBibleEntity(
                id = UUID.randomUUID().toString(),
                projectId = testProjectId,
                mainTheme = "Une tragédie sombre sur la déchéance et la mort inéluctable",
                narrativeArc = "Chute morale tragique",
                climaxDescription = "Un dénouement sombre sans espoir"
            )
        )

        val snapshot = repository.getCanonSnapshot(testProjectId)

        val comedySynopsis = "Une comédie insouciante pleine de gags burlesques où tout le monde fait la fête."
        val contradictions = detector.checkProjectMetadataContradictions("Gags & Fun", comedySynopsis, snapshot)

        assertFalse("Une contradiction de tonalité doit être détectée", contradictions.isEmpty())
        val toneConflict = contradictions.firstOrNull { it.fieldName.contains("Harmonie Tonale", ignoreCase = true) }
        assertNotNull(toneConflict)
        assertEquals(ContradictionSeverity.HIGH, toneConflict?.severity)
    }

    @Test
    fun detectContradictionWithDeceasedCharacterInSynopsis() = runTest {
        // Personnage canoniquement décédé
        repository.updateCanon(
            testProjectId,
            CanonElement.Character(
                entity = CharacterEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = testProjectId,
                    name = "Gareth",
                    role = "Mentor",
                    personalityTraits = "Sage, martyr, mort lors de la bataille inaugurale"
                ),
                isUserValidated = true
            )
        )

        val snapshot = repository.getCanonSnapshot(testProjectId)

        val synopsisWithDeceased = "Dans ce récit, Gareth part à l'aventure vers les terres oubliées pour trouver le trésor."
        val contradictions = detector.checkProjectMetadataContradictions("L'Odyssée", synopsisWithDeceased, snapshot)

        assertFalse("Une contradiction sur le statut vital doit être détectée", contradictions.isEmpty())
        val vitalConflict = contradictions.firstOrNull { it.targetName == "Gareth" }
        assertNotNull(vitalConflict)
        assertEquals(ContradictionSeverity.HIGH, vitalConflict?.severity)
    }

    @Test
    fun detectProtagonistRoleInversionInSynopsis() = runTest {
        repository.updateCanon(
            testProjectId,
            CanonElement.Character(
                entity = CharacterEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = testProjectId,
                    name = "Lyra",
                    role = "Protagoniste"
                ),
                isUserValidated = true
            )
        )

        val snapshot = repository.getCanonSnapshot(testProjectId)

        val invertedSynopsis = "Tous les héros doivent s'unir pour abattre le traître Lyra."
        val contradictions = detector.checkProjectMetadataContradictions("La Chute", invertedSynopsis, snapshot)

        assertFalse("L'inversion de rôle du protagoniste doit être signalée", contradictions.isEmpty())
        val roleConflict = contradictions.firstOrNull { it.targetName == "Lyra" }
        assertNotNull(roleConflict)
        assertEquals(ContradictionSeverity.HIGH, roleConflict?.severity)
    }

    @Test
    fun saveCoverAndProjectMetadataToCanonWhenValidated() = runTest {
        val finalTitle = "L'Ombre du Solstice"
        val finalSynopsis = "Dans un monde où le soleil vacille, une alchimiste doit ranimer la flamme primordiale."

        // Validation du ProjectMetadata
        val metaContradictions = repository.updateCanon(
            testProjectId,
            CanonElement.ProjectMetadata(
                finalTitle = finalTitle,
                finalSynopsis = finalSynopsis,
                isUserValidated = true
            )
        )
        assertTrue(metaContradictions.isEmpty())

        // Vérifier dans la base que le projet a bien ses champs finalTitle et finalSynopsis
        val projectInDb = db.projectDao().findProjectById(testProjectId)
        assertNotNull(projectInDb)
        assertEquals(finalTitle, projectInDb?.finalTitle)
        assertEquals(finalSynopsis, projectInDb?.finalSynopsis)
        assertEquals(finalTitle, projectInDb?.title)

        // Validation de la couverture
        val coverDesc = "Plan vertical montrant une silhouette alchimique tenant un orbe incandescent, contrastes marqués."
        val coverEntity = CoverEntity(
            id = UUID.randomUUID().toString(),
            projectId = testProjectId,
            title = finalTitle,
            tagline = "Quand la dernière étincelle devient l'unique espoir.",
            visualDescription = coverDesc,
            isCanon = true
        )

        val coverContradictions = repository.updateCanon(
            testProjectId,
            CanonElement.Cover(
                entity = coverEntity,
                isUserValidated = true
            )
        )
        assertTrue(coverContradictions.isEmpty())

        // Vérification dans le snapshot
        val updatedSnapshot = repository.getCanonSnapshot(testProjectId)
        assertNotNull(updatedSnapshot.canonCover)
        assertEquals(finalTitle, updatedSnapshot.canonCover?.title)
        assertEquals(coverDesc, updatedSnapshot.canonCover?.visualDescription)
        assertTrue(updatedSnapshot.canonCover?.isCanon == true)
        assertEquals(finalTitle, updatedSnapshot.project?.finalTitle)
    }

    @Test
    fun doNotPersistWhenNotUserValidated() = runTest {
        val initialProject = db.projectDao().findProjectById(testProjectId)

        // Tentative d'écriture sans validation explicite
        val contradictions = repository.updateCanon(
            testProjectId,
            CanonElement.ProjectMetadata(
                finalTitle = "Titre Non Validé",
                finalSynopsis = "Synopsis Non Validé",
                isUserValidated = false
            )
        )

        // Ne doit rien écrire en base
        val currentProject = db.projectDao().findProjectById(testProjectId)
        assertEquals(initialProject?.title, currentProject?.title)
        assertEquals(null, currentProject?.finalTitle)
    }
}

package com.example.domain.canon

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.StoryObjectEntity
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

@RunWith(RobolectricTestRunner::class)
class CanonMemoryEngineTest {

    private lateinit var db: ToonDatabase
    private lateinit var detector: CanonContradictionDetector
    private lateinit var repository: CanonMemoryRepository

    private val testProjectId = "proj-test-canon"

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Insérer le projet parent pour satisfaire la clé étrangère projectId
        db.projectDao().insertProject(
            com.example.data.local.entity.ProjectEntity(
                id = testProjectId,
                title = "Projet Canon Test"
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
    fun detectContradictionBetweenTwoConflictingCharacters() = runTest {
        // 1. Établir le canon initial validé par l'utilisateur
        val baseAiden = CharacterEntity(
            id = "char-aiden-1",
            projectId = testProjectId,
            name = "Aiden",
            role = "Protagoniste",
            archetype = "Mage d'Élite Réticent",
            visualDescription = "Cheveux noirs ébouriffés et yeux bleus électriques",
            personalityTraits = "Protecteur, loyal"
        )
        val initialContradictions = repository.updateCanon(
            testProjectId,
            CanonElement.Character(baseAiden, isUserValidated = true)
        )
        assertTrue("Le premier personnage validé ne doit pas avoir de contradiction", initialContradictions.isEmpty())

        // Vérifier qu'il est bien écrit dans le canon
        val snapshot = repository.getCanonSnapshot(testProjectId)
        assertEquals(1, snapshot.characters.size)
        assertEquals("Aiden", snapshot.characters[0].name)
        assertEquals("Protagoniste", snapshot.characters[0].role)

        // 2. Insérer à la main un second personnage contradictoire (même nom 'Aiden', mais rôle 'Antagoniste' et 'yeux rouges')
        val conflictingAiden = CharacterEntity(
            id = "char-aiden-2",
            projectId = testProjectId,
            name = "Aiden",
            role = "Antagoniste",
            archetype = "Seigneur Sombre Cruel",
            visualDescription = "Guerrier imposant avec cheveux noirs et yeux rouges sang",
            personalityTraits = "Froid et impitoyable"
        )

        // Détection de contradiction directe sans correction automatique
        val detected = repository.detectContradictions(
            testProjectId,
            CanonElement.Character(conflictingAiden, isUserValidated = false)
        )

        assertFalse("Des contradictions doivent être détectées !", detected.isEmpty())
        assertTrue("Au moins deux contradictions (Rôle + Yeux) doivent être relevées", detected.size >= 2)

        val roleContradiction = detected.firstOrNull { it.fieldName == "Rôle" }
        assertNotNull("Une contradiction sur le rôle doit être trouvée", roleContradiction)
        assertEquals("Protagoniste", roleContradiction?.canonicalValue)
        assertEquals("Antagoniste", roleContradiction?.conflictingValue)
        assertEquals(ContradictionSeverity.HIGH, roleContradiction?.severity)

        val visualContradiction = detected.firstOrNull { it.fieldName == "Apparence Visuelle" }
        assertNotNull("Une contradiction sur les yeux bleus vs rouges doit être trouvée", visualContradiction)
        assertTrue(visualContradiction!!.description.contains("yeux bleu", ignoreCase = true))
    }

    @Test
    fun unvalidatedElementIsNotPersistedToDatabase() = runTest {
        // Tentative d'écriture d'un lieu sans validation explicite de l'utilisateur
        val draftLocation = LocationEntity(
            id = "loc-draft-1",
            projectId = testProjectId,
            name = "Forêt Sombre",
            description = "Une forêt dense et menaçante",
            atmosphere = "Glaciale"
        )

        val contradictions = repository.updateCanon(
            testProjectId,
            CanonElement.Location(draftLocation, isUserValidated = false)
        )

        assertTrue(contradictions.isEmpty())

        // Vérifier que la base de données Room est restée vierge
        val snapshot = repository.getCanonSnapshot(testProjectId)
        assertTrue("Le lieu non validé ne doit pas avoir été écrit en base", snapshot.locations.isEmpty())
    }

    @Test
    fun detectContradictionAgainstCanonMemoriesRule() = runTest {
        // Établir une règle de mémoire canonique absolue : Le Grand Sage est mort
        val sageDeathFact = CanonMemoryEntity(
            id = "mem-1",
            projectId = testProjectId,
            category = "RÈGLE_ABSOLUE",
            fact = "Le Grand Sage Oron a été tué lors du siège de la capitale."
        )
        repository.updateCanon(testProjectId, CanonElement.Memory(sageDeathFact, isUserValidated = true))

        // Tentative d'introduction d'un personnage actif "Oron" sans mention de résurrection
        val activeOron = CharacterEntity(
            id = "char-oron",
            projectId = testProjectId,
            name = "Oron",
            role = "Mentor Actif",
            archetype = "Sage",
            visualDescription = "Longue barbe blanche",
            backstory = "Il enseigne toujours à l'académie des mages."
        )

        val contradictions = repository.detectContradictions(
            testProjectId,
            CanonElement.Character(activeOron, isUserValidated = false)
        )

        assertFalse(contradictions.isEmpty())
        val deathContradiction = contradictions.firstOrNull { it.fieldName == "Statut Vital (Canon)" }
        assertNotNull(deathContradiction)
        assertEquals(ContradictionSeverity.HIGH, deathContradiction?.severity)
    }

    @Test
    fun returnsEmptyListWhenNoReliableDetectionIsPossible() = runTest {
        // Un nouveau personnage complètement distinct, sans conflit de nom ni contradiction
        val baseCharacter = CharacterEntity(
            id = "char-1",
            projectId = testProjectId,
            name = "Aiden",
            role = "Protagoniste"
        )
        repository.updateCanon(testProjectId, CanonElement.Character(baseCharacter, isUserValidated = true))

        val newAlly = CharacterEntity(
            id = "char-2",
            projectId = testProjectId,
            name = "Mira",
            role = "Alliée",
            archetype = "Archère elfique",
            visualDescription = "Cheveux argentés et arc d'or"
        )

        val contradictions = repository.detectContradictions(
            testProjectId,
            CanonElement.Character(newAlly, isUserValidated = false)
        )

        // Règle du prompt : si aucune détection fiable n'est possible / aucun conflit, retourne une liste vide plutôt que de deviner
        assertTrue("Aucune contradiction ne doit être inventée pour un personnage cohérent", contradictions.isEmpty())
    }
}

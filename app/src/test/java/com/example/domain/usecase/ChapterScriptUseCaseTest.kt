package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiChapterScriptGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonContradictionDetector
import com.example.domain.canon.CanonElement
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChapterScriptUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-test-chapter"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToonDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        canonRepository = CanonMemoryRepositoryImpl(db)

        db.projectDao().insertProject(
            ProjectEntity(
                id = testProjectId,
                title = "ToonIA Aventure",
                synopsis = "Une histoire dans la cité céleste.",
                genre = "Action / Fantasy"
            )
        )

        // Ajout d'un personnage validé au Canon
        val hero = CharacterEntity(
            id = "hero-1",
            projectId = testProjectId,
            name = "Aria",
            role = "Protagoniste",
            archetype = "Guerrière céleste",
            personalityTraits = "Déterminée, loyale",
            visualDescription = "Armure blanche et ailes d'argent"
        )
        canonRepository.updateCanon(testProjectId, CanonElement.Character(hero, isUserValidated = true))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createMockOkHttpClient(responseJsonString: String, responseCode: Int = 200): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val geminiResponse = JSONObject().apply {
                val candidates = JSONArray().apply {
                    val candidate = JSONObject().apply {
                        val content = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", responseJsonString) })
                            }
                            put("parts", parts)
                        }
                        put("content", content)
                        put("finishReason", "STOP")
                    }
                    put(candidate)
                }
                put("candidates", candidates)
            }

            Response.Builder()
                .code(responseCode)
                .message(if (responseCode == 200) "OK" else "Error")
                .protocol(Protocol.HTTP_1_1)
                .request(chain.request())
                .body(geminiResponse.toString().toResponseBody("application/json".toMediaType()))
                .build()
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Test
    fun executeGenerateChapter1Script_createsDraftScenesAndDetectsContradictions() = runTest {
        // Script JSON avec 2 scènes : Scène 1 avec Aria (canon), Scène 2 avec "Malakor" (inconnu au canon)
        val mockScriptJson = JSONObject().apply {
            put("chapterTitle", "L'Éveil des Ailes")
            put("chapterSynopsis", "Aria découvre les ruines antiques et fait face à une menace inattendue.")
            put("scenes", JSONArray().apply {
                put(JSONObject().apply {
                    put("orderIndex", 1)
                    put("title", "L'Aube sur la Cité")
                    put("locationName", "Tour d'observation")
                    put("atmosphere", "Lumineuse et paisible")
                    put("charactersPresent", JSONArray().apply { put("Aria") })
                    put("summary", "Aria contemple l'horizon avant le départ de l'expédition.")
                    put("narrativeBeats", JSONArray().apply {
                        put("Aria vérifie son équipement.")
                        put("La cloche sonne.")
                    })
                    put("conflictOrHook", "Un sentiment d'inquiétude l'envahit.")
                })
                put(JSONObject().apply {
                    put("orderIndex", 2)
                    put("title", "La Rencontre Obscure")
                    put("locationName", "Ruines Oubliées")
                    put("atmosphere", "Brumeuse et angoissante")
                    put("charactersPresent", JSONArray().apply {
                        put("Aria")
                        put("Malakor L'Inconnu")
                    })
                    put("summary", "Un mystérieux assaillant surgit de l'ombre pour intercepter Aria.")
                    put("narrativeBeats", JSONArray().apply {
                        put("Le duel commence.")
                    })
                    put("conflictOrHook", "Aria doit fuir.")
                })
            })
        }.toString()

        val mockClient = createMockOkHttpClient(mockScriptJson)
        val generator = GeminiChapterScriptGenerator(client = mockClient, apiKeyOverride = "TEST")
        val useCase = ChapterScriptUseCase(
            projectDao = db.projectDao(),
            chapterDao = db.chapterDao(),
            sceneDao = db.sceneDao(),
            locationDao = db.locationDao(),
            characterDao = db.characterDao(),
            jobDao = db.generationJobDao(),
            canonRepository = canonRepository,
            contradictionDetector = CanonContradictionDetector(),
            generator = generator
        )

        val (job, result) = useCase.executeGenerateChapter1Script(testProjectId, userOrientation = null)

        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.CHAPITRE, job.type)
        assertTrue(result.isSuccess)

        val scriptResult = result.getOrNull()
        assertNotNull(scriptResult)
        assertEquals(1, scriptResult?.chapter?.number)
        assertEquals("L'Éveil des Ailes", scriptResult?.chapter?.title)
        assertFalse("Le chapitre doit être un brouillon isCanon = false", scriptResult?.chapter?.isCanon ?: true)

        // Vérification des scènes
        assertEquals(2, scriptResult?.scenes?.size)
        assertEquals(1, scriptResult?.scenes?.get(0)?.orderIndex)
        assertEquals(2, scriptResult?.scenes?.get(1)?.orderIndex)
        assertFalse(scriptResult?.scenes?.get(0)?.isCanon ?: true)

        // Vérification des contradictions détectées (Malakor n'est pas dans le Canon)
        val contradictions = scriptResult?.contradictions ?: emptyList()
        assertTrue("Une contradiction doit être détectée pour Malakor", contradictions.isNotEmpty())
        assertTrue(contradictions.any { it.conflictingValue.contains("Malakor") })

        // Vérification que les scènes sont bien stockées en base
        val storedScenes = db.sceneDao().getScenesByChapterId(scriptResult!!.chapter.id).firstOrNull() ?: emptyList()
        assertEquals(2, storedScenes.size)

        // Test de modification manuelle d'une scène (édition utilisateur)
        val sceneToEdit = storedScenes[0]
        val updatedScene = sceneToEdit.copy(title = "Titre Modifié par l'Utilisateur")
        useCase.updateSceneDraft(updatedScene)

        val retrievedScene = db.sceneDao().getSceneById(sceneToEdit.id).firstOrNull()
        assertEquals("Titre Modifié par l'Utilisateur", retrievedScene?.title)
    }
}

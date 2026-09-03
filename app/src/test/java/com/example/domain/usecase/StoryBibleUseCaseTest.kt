package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiStoryGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.WorldBibleEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonElement
import com.example.domain.model.StoryBibleResult
import com.example.domain.model.StoryTimelineEventEntry
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryBibleUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-test-story-bible"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToonDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        canonRepository = CanonMemoryRepositoryImpl(db)

        // Projet de base
        db.projectDao().insertProject(
            ProjectEntity(
                id = testProjectId,
                title = "Omniscient Reader Webtoon",
                synopsis = "Un lecteur ordinaire se retrouve dans le monde de son webnovel préféré.",
                genre = "Action / Fantasy",
                tone = "Épique et sombre"
            )
        )

        // Personnage canon
        val protagonist = CharacterEntity(
            id = "char-protagonist-1",
            projectId = testProjectId,
            name = "Kim Dokja",
            role = "Protagoniste",
            archetype = "Stratège",
            personalityTraits = "Calme, perspicace",
            visualDescription = "Manteau blanc",
            backstory = "Lecteur unique"
        )
        canonRepository.updateCanon(testProjectId, CanonElement.Character(protagonist, isUserValidated = true))

        // Univers canon
        val worldBible = WorldBibleEntity(
            id = "wb-1",
            projectId = testProjectId,
            magicOrTechRules = "Système de scénarios et pièces",
            factions = "Rois de Séoul",
            geography = "Séoul dévastée",
            socialStructure = "Dôme de survie",
            historyLore = "Fin du monde prophétisée",
            updatedAt = System.currentTimeMillis()
        )
        canonRepository.updateCanon(testProjectId, CanonElement.WorldBible(worldBible, isUserValidated = true))
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
    fun testGenerateStoryBibleSuccess_andExplicitValidationToCanon() = runTest {
        val storyJson = JSONObject().apply {
            put("premisse", "Le seul lecteur connaissant la fin du monde tente d'atteindre l'épilogue sans sacrifier ses alliés.")
            put("genreConfirme", "Action Fantasy Webtoon")
            put("themesPrincipaux", "Destin, sacrifice, fidélité et pouvoir de la lecture.")
            put("tonGeneral", "Épique, intense avec des pointes d'ironie.")
            put("conflitsPrincipaux", "Survie face aux constellations et rivalité avec le protagoniste d'origine.")
            put("intriguePrincipale", "Franchir les 100 scénarios un par un jusqu'à la Porte Finale.")
            put("intriguesSecondaires", "Révélation de l'identité de l'auteur du webnovel.")
            put("directionGenerale", "Sauver le monde sans devenir l'antagoniste des constellations.")
            put("rythmeNarratif", "Rythmé par des fins de chapitres sous tension extrême.")
            put(
                "evenementsFondateurs",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("titre", "Publication du chapitre 3149")
                            put("description", "Dernier chapitre lu dans la rame de métro.")
                            put("epoqueOuDate", "Il y a 1 heure")
                        }
                    )
                }
            )
        }.toString()

        val mockClient = createMockOkHttpClient(storyJson)
        val generator = GeminiStoryGenerator(client = mockClient, apiKeyOverride = "test-key")
        val useCase = StoryBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            storyBibleDao = db.storyBibleDao(),
            timelineEventDao = db.timelineEventDao(),
            canonRepository = canonRepository,
            generator = generator
        )

        // 1. Exécution du job
        val (job, result) = useCase.executeGenerateStoryJob(testProjectId)
        assertTrue(result.isSuccess)
        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.HISTOIRE, job.type)

        val story = result.getOrThrow()
        assertEquals("Action Fantasy Webtoon", story.confirmedGenre)
        assertEquals(1, story.timelineEvents.size)
        assertEquals("Publication du chapitre 3149", story.timelineEvents[0].title)
        assertFalse(story.isValidatedInCanon)

        // Rien dans la table StoryBible avant validation explicite
        assertNull(db.storyBibleDao().findStoryBibleByProjectId(testProjectId))

        // 2. Validation explicite au canon
        val (validatedStory, contradictions) = useCase.validateStoryBibleToCanon(testProjectId, story)
        assertTrue(validatedStory.isValidatedInCanon)
        assertTrue(contradictions.isEmpty())

        // Vérification de la persistance en Room
        val afterCanon = db.storyBibleDao().findStoryBibleByProjectId(testProjectId)
        assertNotNull(afterCanon)
        assertTrue(afterCanon?.mainTheme?.contains("Destin") == true)

        // Vérification des TimelineEvents au canon
        val canonEvents = canonRepository.getCanonSnapshot(testProjectId).canonTimelineEvents
        assertEquals(1, canonEvents.size)
        assertEquals("Publication du chapitre 3149", canonEvents[0].title)
    }
}

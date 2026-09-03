package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiVisualHumorGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonElement
import com.example.domain.canon.ContradictionSeverity
import com.example.domain.model.CharacterHumorHook
import com.example.domain.model.HumorDnaResult
import com.example.domain.model.VisualBibleResult
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
class VisualHumorUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-test-visual-humor"

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
                title = "Toon Odyssey",
                synopsis = "Une épopée spatiale cyberpunk où des pilotes d'astronefs s'affrontent.",
                genre = "Cyberpunk / Sci-Fi",
                tone = "Sombre, dramatique et intense"
            )
        )

        // Personnage canonique validé
        val hero = CharacterEntity(
            id = "char-hero-1",
            projectId = testProjectId,
            name = "Kaelen Vane",
            role = "Protagoniste",
            archetype = "Pilote rebelle",
            personalityTraits = "Calculateur, cynique, loyal envers son équipage",
            visualDescription = "Cheveux argentés, manteau long en cuir noir usé, yeux bleus néon augmentés"
        )
        canonRepository.updateCanon(testProjectId, CanonElement.Character(hero, isUserValidated = true))

        // Story Bible validée (ton sombre)
        val storyBible = StoryBibleEntity(
            id = "story-bible-1",
            projectId = testProjectId,
            mainTheme = "Sacrifice, survie face à une IA omnisciente. Récit sombre et oppressant",
            narrativeArc = "Kaelen découvre une arme antique capable d'anéantir la station orbitale.",
            climaxDescription = "Récit sombre, oppressant et tragique avec affrontement final",
            narrativePacing = "Rapide et intense"
        )
        canonRepository.updateCanon(testProjectId, CanonElement.StoryBible(storyBible, isUserValidated = true))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGenerateVisualBibleJob_Success() = runTest {
        val mockVisualJson = JSONObject().apply {
            put("directionArtistique", "Manhwa Cyberpunk néon avec encrage granuleux et contrastes marqués")
            put("paletteCouleurs", "Dominantes bleu cobalt (#0F1B38) et pourpre néon (#9D00FF), touches d'orange incandescent")
            put("ambianceLumineuse", "Contre-jours dramatiques, halos de néons et reflets métalliques sous pluie acide")
            put("styleDecoupage", "Défilement vertical énergique, grandes gouttières noires pour dramatiser les chutes")
            put(
                "caracteristiquesVisuellesRecurrentes",
                JSONArray().apply {
                    put("Lueur cyan électrique dans les yeux cybernétiques")
                    put("Traînées de vitesse floutées sur les impacts")
                }
            )
            put(
                "reglesCadragePrivilegiees",
                JSONArray().apply {
                    put("Plans serrés en plongée verticale sur les regards")
                    put("Panoramiques verticaux continus sur trois écrans pour les batailles spatiales")
                }
            )
            put(
                "elementsVisuelsContinuite",
                JSONArray().apply {
                    put("Le manteau noir usé au col déchiré de Kaelen")
                    put("L'éclat bleu constant de son œil bionique droit")
                }
            )
        }

        val geminiCandidate = JSONObject().apply {
            put(
                "candidates",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put(
                                "content",
                                JSONObject().apply {
                                    put(
                                        "parts",
                                        JSONArray().apply {
                                            put(JSONObject().apply { put("text", mockVisualJson.toString()) })
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(geminiCandidate.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val generator = GeminiVisualHumorGenerator(client = mockClient, apiKeyOverride = "FAKE_VALID_KEY")
        val useCase = VisualHumorUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            visualBibleDao = db.visualBibleDao(),
            humorDnaDao = db.humorDnaDao(),
            canonRepository = canonRepository,
            generator = generator
        )

        val (job, result) = useCase.executeGenerateVisualJob(testProjectId)

        assertEquals(JobType.VISUEL, job.type)
        assertEquals(JobStatus.SUCCESS, job.status)
        assertTrue(result.isSuccess)

        val visualBible = result.getOrThrow()
        assertEquals("Manhwa Cyberpunk néon avec encrage granuleux et contrastes marqués", visualBible.artStyle)
        assertEquals(2, visualBible.recurringVisualElements.size)
        assertEquals(2, visualBible.framingRules.size)
        assertEquals(2, visualBible.continuityElements.size)
        assertFalse("Ne doit pas être validé dans le canon avant accord explicite", visualBible.isValidatedInCanon)

        // Validation explicite au canon
        val (validated, contradictions) = useCase.validateVisualBibleToCanon(testProjectId, visualBible)
        assertTrue(validated.isValidatedInCanon)
        assertTrue(contradictions.isEmpty())

        // Vérification de la persistance en Room
        val saved = useCase.loadVisualBible(testProjectId)
        assertNotNull(saved)
        assertEquals(visualBible.artStyle, saved?.artStyle)
        assertEquals(2, saved?.continuityElements?.size)
    }

    @Test
    fun testGenerateHumorDnaJob_SuccessAndToneAlignment() = runTest {
        val mockHumorJson = JSONObject().apply {
            put("styleHumour", "Pince-sans-rire (deadpan), ironie cynique et comique de situation discret")
            put("momentsChibiAutorises", false)
            put("frequenceHumour", "Rare et mesurée : sert uniquement de respiration dramatique")
            put(
                "runningGagsPossibles",
                JSONArray().apply {
                    put("Kaelen qui calcule froidement le coût des dégâts de son vaisseau après chaque esquive")
                }
            )
            put(
                "blaguesPersonnages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("nomPersonnage", "Kaelen Vane")
                            put("noteHumour", "Garde un visage totalement impassible même quand une explosion détruit sa tasse de café préférée.")
                        }
                    )
                }
            )
            put(
                "situationsComiquesRecurrentes",
                JSONArray().apply {
                    put("Quiproquos sur les protocoles de négociation avec des marchands extraterrestres radins")
                }
            )
        }

        val geminiCandidate = JSONObject().apply {
            put(
                "candidates",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put(
                                "content",
                                JSONObject().apply {
                                    put(
                                        "parts",
                                        JSONArray().apply {
                                            put(JSONObject().apply { put("text", mockHumorJson.toString()) })
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(geminiCandidate.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val generator = GeminiVisualHumorGenerator(client = mockClient, apiKeyOverride = "FAKE_VALID_KEY")
        val useCase = VisualHumorUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            visualBibleDao = db.visualBibleDao(),
            humorDnaDao = db.humorDnaDao(),
            canonRepository = canonRepository,
            generator = generator
        )

        val (job, result) = useCase.executeGenerateHumorJob(testProjectId)

        assertEquals(JobType.HUMOUR, job.type)
        assertEquals(JobStatus.SUCCESS, job.status)
        assertTrue(result.isSuccess)

        val humorDna = result.getOrThrow()
        assertEquals("Pince-sans-rire (deadpan), ironie cynique et comique de situation discret", humorDna.humorStyle)
        assertFalse(humorDna.chibiMomentsAllowed)
        assertEquals(1, humorDna.characterHumorHooks.size)
        assertEquals("Kaelen Vane", humorDna.characterHumorHooks[0].characterName)
        assertFalse(humorDna.isValidatedInCanon)

        // Validation au canon
        val (validated, contradictions) = useCase.validateHumorDnaToCanon(testProjectId, humorDna)
        assertTrue(validated.isValidatedInCanon)
        assertTrue(contradictions.isEmpty())

        val saved = useCase.loadHumorDna(testProjectId)
        assertNotNull(saved)
        assertEquals(humorDna.humorStyle, saved?.humorStyle)
        assertEquals(1, saved?.characterHumorHooks?.size)
    }

    @Test
    fun testContradictionDetection_ToneDissonanceWithGrimStoryBible() = runTest {
        val mockClient = OkHttpClient.Builder().build()
        val generator = GeminiVisualHumorGenerator(client = mockClient, apiKeyOverride = "KEY")
        val useCase = VisualHumorUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            visualBibleDao = db.visualBibleDao(),
            humorDnaDao = db.humorDnaDao(),
            canonRepository = canonRepository,
            generator = generator
        )

        // Proposition d'un humour burlesque/slapstick sur une histoire sombre et tragique
        val conflictingHumor = HumorDnaResult(
            projectId = testProjectId,
            humorStyle = "Gags burlesques, pitreries slapstick et tartes à la crème en continu",
            chibiMomentsAllowed = true,
            punchlineFrequency = "Toutes les deux cases"
        )

        val contradictions = useCase.checkHumorContradictions(testProjectId, conflictingHumor)
        assertTrue("Une contradiction de dissonance tonale doit être détectée", contradictions.isNotEmpty())
        val toneContradiction = contradictions.firstOrNull { it.targetName == "Harmonie Tonale" }
        assertNotNull(toneContradiction)
        assertEquals(ContradictionSeverity.MEDIUM, toneContradiction?.severity)
    }

    @Test
    fun testPromptGeneration_IncludesOptionalNoticeAndStoryTone() = runTest {
        val generator = GeminiVisualHumorGenerator(apiKeyOverride = "KEY")
        val snapshot = canonRepository.getCanonSnapshot(testProjectId)

        val humorPrompt = generator.buildHumorDnaPrompt(
            projectTitle = "Toon Odyssey",
            projectSynopsis = "Une épopée spatiale cyberpunk",
            projectGenre = "Cyberpunk",
            projectTone = "Sombre et tragique",
            canonSnapshot = snapshot
        )

        // Vérification de la règle d'or du Prompt Maître
        assertTrue(humorPrompt.contains("ne doivent PAS être forcés dans chaque chapitre"))
        assertTrue(humorPrompt.contains("Kaelen Vane"))
        assertTrue(humorPrompt.contains("Récit sombre, oppressant et tragique avec affrontement final"))

        val visualPrompt = generator.buildVisualBiblePrompt(
            projectTitle = "Toon Odyssey",
            projectSynopsis = "Une épopée spatiale cyberpunk",
            projectGenre = "Cyberpunk",
            projectTone = "Sombre et tragique",
            canonSnapshot = snapshot
        )

        assertTrue(visualPrompt.contains("Kaelen Vane"))
        assertTrue(visualPrompt.contains("Cheveux argentés"))
        assertTrue(visualPrompt.contains("éléments visuels de continuité", ignoreCase = true))
    }
}

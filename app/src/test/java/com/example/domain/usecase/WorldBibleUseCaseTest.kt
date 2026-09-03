package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiWorldGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonElement
import com.example.domain.model.WorldBibleResult
import com.example.domain.model.WorldLocationEntry
import com.example.domain.model.WorldObjectEntry
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class WorldBibleUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-test-world-bible"

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

        // Préparer un personnage canon pour vérifier la transmission au prompt
        val protagonist = CharacterEntity(
            id = "char-protagonist-1",
            projectId = testProjectId,
            name = "Kim Dokja",
            role = "Protagoniste",
            archetype = "Stratège / Lecteur",
            personalityTraits = "Calme, perspicace, calculateur",
            visualDescription = "Cheveux noirs, manteau blanc iconique",
            backstory = "Unique lecteur d'un webnovel de 3000 chapitres."
        )
        canonRepository.updateCanon(testProjectId, CanonElement.Character(protagonist, isUserValidated = true))
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
    fun testGenerateWorldBibleSuccess_andExplicitValidationToCanon() = runTest {
        val worldJson = JSONObject().apply {
            put("reglesUnivers", "La Constellation et les Scénarios dictent la survie des incarnations.")
            put("groupesFactions", "Le Dôme de Séoul, la faction des Prophètes et l'Alliance des Rois.")
            put("geographie", "Séoul post-apocalyptique découpée en zones de scénarios.")
            put("structureSociale", "Hiérarchie basée sur les pièces et le patronage des constellations.")
            put("elementsHistoire", "La diffusion soudaine du premier scénario dans le métro.")
            put("terminologie", "Incarnation, Constellation, Scénario, Pièces.")
            put("contraintesMonde", "Aucun humain ne peut survivre sans participer aux scénarios.")
            put(
                "lieuxPrincipaux",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("nom", "Station de Chungmuro")
                            put("description", "Base souterraine fortifiée.")
                            put("ambiance", "Tendue, sombre, barricadée")
                        }
                    )
                }
            )
            put(
                "objetsImportants",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("nom", "Smartphone non connecté")
                            put("importance", "Contient l'unique copie du webnovel")
                            put("description", "Batterie miraculeusement préservée")
                        }
                    )
                }
            )
        }.toString()

        val mockClient = createMockOkHttpClient(worldJson)
        val generator = GeminiWorldGenerator(client = mockClient, apiKeyOverride = "test-key")
        val useCase = WorldBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            worldBibleDao = db.worldBibleDao(),
            locationDao = db.locationDao(),
            storyObjectDao = db.storyObjectDao(),
            canonRepository = canonRepository,
            generator = generator
        )

        // 1. Génération via job
        val (job, result) = useCase.executeGenerateWorldJob(testProjectId)
        assertTrue(result.isSuccess)
        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.UNIVERS, job.type)

        val world = result.getOrThrow()
        assertEquals("La Constellation et les Scénarios dictent la survie des incarnations.", world.rules)
        assertEquals(1, world.locations.size)
        assertEquals("Station de Chungmuro", world.locations[0].name)
        assertFalse("La world bible ne doit pas être au canon avant validation explicite", world.isValidatedInCanon)

        // Vérifier que rien n'est encore dans la table WorldBible
        val beforeCanon = db.worldBibleDao().findWorldBibleByProjectId(testProjectId)
        assertNull(beforeCanon)

        // 2. Validation explicite au canon
        val (validatedWorld, contradictions) = useCase.validateWorldBibleToCanon(testProjectId, world)
        assertTrue(validatedWorld.isValidatedInCanon)
        assertTrue(contradictions.isEmpty())

        // Vérifier la persistance dans Room
        val afterCanon = db.worldBibleDao().findWorldBibleByProjectId(testProjectId)
        assertNotNull(afterCanon)
        assertEquals(world.rules, afterCanon?.magicOrTechRules)

        // 3. Validation explicite d'un lieu individuel
        val loc = world.locations[0]
        val (validatedLoc, locContradictions) = useCase.validateLocationToCanon(testProjectId, loc)
        assertTrue(validatedLoc.isValidatedInCanon)
        assertTrue(locContradictions.isEmpty())

        val snapshot = canonRepository.getCanonSnapshot(testProjectId)
        assertEquals(1, snapshot.locations.size)
        assertEquals("Station de Chungmuro", snapshot.locations[0].name)
    }

    @Test
    fun testContradictionDetection_onConflictingLocationAtmosphere() = runTest {
        // Enregistrer d'abord un lieu canonique
        val existingLoc = LocationEntity(
            id = "loc-1",
            projectId = testProjectId,
            name = "Le Sanctuaire Céleste",
            description = "Temple ancestral",
            atmosphere = "Sereine et sacrée"
        )
        canonRepository.updateCanon(testProjectId, CanonElement.Location(existingLoc, isUserValidated = true))

        val useCase = WorldBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            worldBibleDao = db.worldBibleDao(),
            locationDao = db.locationDao(),
            storyObjectDao = db.storyObjectDao(),
            canonRepository = canonRepository
        )

        // Proposer un lieu avec le même nom mais une atmosphère divergente
        val conflictingLoc = WorldLocationEntry(
            id = "loc-conflict",
            projectId = testProjectId,
            name = "Le Sanctuaire Céleste",
            description = "Temple en ruine",
            atmosphere = "Lugubre et putride",
            isValidatedInCanon = false
        )

        val contradictions = useCase.checkLocationContradictions(testProjectId, conflictingLoc)
        assertFalse(contradictions.isEmpty())
        assertEquals("Atmosphère", contradictions[0].fieldName)
        assertEquals("Sereine et sacrée", contradictions[0].canonicalValue)
    }
}

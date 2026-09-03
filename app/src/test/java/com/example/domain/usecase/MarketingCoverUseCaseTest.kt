package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiMarketingCoverGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarketingCoverUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-test-marketing"

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
                title = "Projet Démo",
                synopsis = "Une histoire fantastique dans un monde magique.",
                genre = "Fantasy",
                tone = "Aventure"
            )
        )
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
    fun executeGenerateMarketingJob_createsJobAndSavesOptions() = runTest {
        val mockMarketingJson = JSONObject().apply {
            put("titresProposes", JSONArray().apply {
                put("L'Ombre du Monarque")
                put("Le Dernier Élu")
            })
            put("variantesSynopsis", JSONArray().apply {
                put(JSONObject().apply {
                    put("typeLongueur", "Court")
                    put("texte", "Quand la nuit tombe, seuls les braves s'élèvent.")
                    put("accroche", "Un voyage initiatique.")
                })
            })
            put("descriptionVisuelleCouverture", JSONObject().apply {
                put("composition", "Vertical webtoon ratio, dramatic lighting.")
                put("personnagesPresents", JSONArray().apply { put("Le Guerrier") })
                put("ambianceEtLumiere", "Epic, mysterious")
                put("paletteCouleurs", "Dark blue, gold")
                put("descriptionCompleteSansTexte", "A lone warrior on a cliff overlooking a glowing mystical valley.")
            })
        }.toString()

        val mockClient = createMockOkHttpClient(mockMarketingJson)
        val generator = GeminiMarketingCoverGenerator(client = mockClient, apiKeyOverride = "TEST_API_KEY")
        val useCase = MarketingCoverUseCase(
            projectDao = db.projectDao(),
            coverDao = db.coverDao(),
            jobDao = db.generationJobDao(),
            canonRepository = canonRepository,
            generator = generator
        )

        val (job, result) = useCase.executeGenerateMarketingJob(testProjectId)

        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.COUVERTURE, job.type)
        assertTrue(result.isSuccess)

        val marketingResult = result.getOrNull()
        assertNotNull(marketingResult)
        assertEquals(2, marketingResult?.titleProposals?.size)
        assertEquals("L'Ombre du Monarque", marketingResult?.titleProposals?.first())
        assertEquals(1, marketingResult?.synopsisVariants?.size)
        assertEquals("A lone warrior on a cliff overlooking a glowing mystical valley.", marketingResult?.coverProposal?.detailedDescriptionNoText)
    }

    @Test
    fun validateAndSaveToCanon_updatesProjectAndCanon() = runTest {
        val useCase = MarketingCoverUseCase(
            projectDao = db.projectDao(),
            coverDao = db.coverDao(),
            jobDao = db.generationJobDao(),
            canonRepository = canonRepository,
            generator = GeminiMarketingCoverGenerator(client = createMockOkHttpClient("{}"), apiKeyOverride = "TEST")
        )

        // Sauvegarder la sélection et valider au Canon
        val contradictions = useCase.validateAndSaveToCanon(
            projectId = testProjectId,
            chosenTitle = "L'Ombre du Monarque",
            chosenSynopsis = "Quand la nuit tombe...",
            chosenTagline = "Un voyage initiatique.",
            coverDescription = "A lone warrior on a cliff."
        )

        assertTrue(contradictions.isEmpty())

        // Vérifier la mise à jour du projet
        val updatedProject = db.projectDao().findProjectById(testProjectId)
        assertNotNull(updatedProject)
        assertEquals("L'Ombre du Monarque", updatedProject?.finalTitle)
        assertEquals("Quand la nuit tombe...", updatedProject?.finalSynopsis)

        // Vérifier dans CanonSnapshot
        val canon = canonRepository.getCanonSnapshot(testProjectId)
        assertNotNull(canon.canonCover)
        assertEquals("L'Ombre du Monarque", canon.canonCover?.title)
        assertEquals("Un voyage initiatique.", canon.canonCover?.tagline)
        assertEquals(true, canon.canonCover?.isCanon)
    }
}

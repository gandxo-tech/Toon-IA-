package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiIdeaAnalyzer
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.domain.model.AdvancedIdeaDetails
import com.example.domain.model.IdeaAnalysisResult
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
class AnalyzeIdeaUseCaseTest {

    private lateinit var db: ToonDatabase
    private val testProjectId = "proj-test-idea-analysis"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToonDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Insérer le projet parent
        db.projectDao().insertProject(
            ProjectEntity(
                id = testProjectId,
                title = "Projet Test Analyse",
                synopsis = "Synopsis initial"
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createMockOkHttpClient(
        statusCode: Int = 200,
        responseJsonText: String? = null,
        throwNetworkError: Boolean = false
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            if (throwNetworkError) {
                throw IOException("Simulation coupure réseau : Impossible de contacter l'API Gemini.")
            }

            val rawJsonContent = responseJsonText ?: createValidGeminiResponseJson(
                genrePrincipal = "Super-héros",
                sousGenres = listOf("Action", "Comédie"),
                ton = "Dynamique",
                themes = listOf("Justice", "Responsabilité"),
                narratifs = listOf("Un jeune héros avec super-pouvoirs combat des méchants"),
                ambiguites = listOf(
                    "Quelle est l'origine précise de ses pouvoirs ?",
                    "Qui dirige l'organisation ennemie et quels sont leurs objectifs ?"
                )
            )

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Error")
                .body(rawJsonContent.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun createValidGeminiResponseJson(
        genrePrincipal: String,
        sousGenres: List<String>,
        ton: String,
        themes: List<String>,
        narratifs: List<String>,
        ambiguites: List<String>
    ): String {
        val structuredPayload = JSONObject().apply {
            put("genrePrincipal", genrePrincipal)
            put("sousGenres", JSONArray(sousGenres))
            put("ton", ton)
            put("themesDetectes", JSONArray(themes))
            put("elementsNarratifsExplicites", JSONArray(narratifs))
            put("ambiguitesAClarifier", JSONArray(ambiguites))
        }.toString()

        val partObj = JSONObject().put("text", structuredPayload)
        val contentObj = JSONObject().put("parts", JSONArray().put(partObj))
        val candidateObj = JSONObject().put("content", contentObj)

        return JSONObject().put("candidates", JSONArray().put(candidateObj)).toString()
    }

    @Test
    fun testVagueIdeaAnalysisGeneratesClarificationQuestionsAndTransitionsJobStatus() = runTest {
        val mockClient = createMockOkHttpClient()
        val analyzer = GeminiIdeaAnalyzer(mockClient, apiKeyOverride = "test-api-key")
        val useCase = AnalyzeIdeaUseCase(db.projectDao(), db.generationJobDao(), analyzer)

        val vagueIdea = "C'est l'histoire d'un gars qui a des pouvoirs et qui combat des méchants dans une grande ville."

        val (job, result) = useCase.executeAnalysisJob(
            projectId = testProjectId,
            ideaText = vagueIdea,
            advancedDetails = null
        )

        // Vérifier que le résultat est un succès
        assertTrue("L'analyse d'une idée vague doit réussir", result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals("Super-héros", analysis?.genrePrincipal)
        assertEquals("Dynamique", analysis?.ton)

        // Pour une idée vague, des ambiguïtés à clarifier doivent être générées
        assertFalse("Des ambiguïtés à clarifier doivent être produites pour une idée vague", analysis?.ambiguitesAClarifier.isNullOrEmpty())
        assertEquals(2, analysis?.ambiguitesAClarifier?.size)

        // Vérifier le cycle de vie du GenerationJob dans Room
        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.ANALYSE, job.type)
        assertNull("Aucun message d'erreur pour un job réussi", job.errorMessage)
        assertNotNull("Le résultat brut doit être persisté", job.rawResult)

        // Vérifier dans la base de données Room
        val persistedJob = db.generationJobDao().findJobById(job.id)
        assertNotNull("Le job doit être enregistré dans Room", persistedJob)
        assertEquals(JobStatus.SUCCESS, persistedJob?.status)
    }

    @Test
    fun testDetailedIdeaAnalysisIncludesAdvancedDetailsAndSucceeds() = runTest {
        val detailedJson = createValidGeminiResponseJson(
            genrePrincipal = "Cyberpunk / Sci-Fi",
            sousGenres = listOf("Dystopie", "Thriller techno-militaire"),
            ton = "Sombre et anxiogène",
            themes = listOf("Transhumanisme", "Trahison corporatiste", "Limites du corps"),
            narratifs = listOf(
                "Kael répare des prothèses clandestines à Paris en 2088",
                "Un implant temporel de 5 secondes calcifie ses nerfs"
            ),
            ambiguites = listOf(
                "Quelle est la véritable nature du réseau Chimera Dynamics ?"
            )
        )

        val mockClient = createMockOkHttpClient(responseJsonText = detailedJson)
        val analyzer = GeminiIdeaAnalyzer(mockClient, apiKeyOverride = "test-api-key")
        val useCase = AnalyzeIdeaUseCase(db.projectDao(), db.generationJobDao(), analyzer)

        val detailedIdea = "Dans un Paris cyberpunk de 2088, Kael découvre un artefact temporel..."
        val advanced = AdvancedIdeaDetails(
            genre = "Cyberpunk",
            ambiance = "Sombre, pluvieux",
            epoque = "2088",
            personnagesSouhaites = "Kael, Mira",
            inspirations = "Akira, Blade Runner",
            longueurVisee = "60 chapitres",
            publicCible = "Jeunes adultes"
        )

        val (job, result) = useCase.executeAnalysisJob(
            projectId = testProjectId,
            ideaText = detailedIdea,
            advancedDetails = advanced
        )

        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals("Cyberpunk / Sci-Fi", analysis?.genrePrincipal)
        assertEquals("Sombre et anxiogène", analysis?.ton)
        assertTrue(analysis!!.themesDetectes.contains("Transhumanisme"))

        // Vérifier que le payload du job contient bien les détails avancés
        val persistedJob = db.generationJobDao().findJobById(job.id)
        assertNotNull(persistedJob)
        assertTrue(persistedJob!!.inputPayload.contains("advancedDetails"))
        assertTrue(persistedJob.inputPayload.contains("Cyberpunk"))
    }

    @Test
    fun testNetworkFailureProperlySetsJobToFailedWithoutHanging() = runTest {
        // Simuler une coupure de réseau complète (ex: mode avion ou perte de connexion)
        val offlineClient = createMockOkHttpClient(throwNetworkError = true)
        val analyzer = GeminiIdeaAnalyzer(offlineClient, apiKeyOverride = "test-api-key")
        val useCase = AnalyzeIdeaUseCase(db.projectDao(), db.generationJobDao(), analyzer)

        val (job, result) = useCase.executeAnalysisJob(
            projectId = testProjectId,
            ideaText = "Mon idée de webtoon en mode hors-ligne"
        )

        // Le cas d'utilisation ne doit JAMAIS prétendre au succès ou renvoyer des données fictives
        assertTrue("Le résultat doit être un échec suite à la coupure réseau", result.isFailure)
        assertEquals(JobStatus.FAILED, job.status)
        assertNotNull("Un message d'erreur clair doit être renseigné", job.errorMessage)
        assertTrue(
            "Le message doit mentionner l'erreur réseau",
            job.errorMessage!!.contains("Erreur réseau", ignoreCase = true)
        )
        assertNull("Aucun résultat ne doit être enregistré en cas d'erreur", job.rawResult)

        // Vérifier que le statut FAILED est bien persisté dans la base Room
        val persistedJob = db.generationJobDao().findJobById(job.id)
        assertNotNull(persistedJob)
        assertEquals(JobStatus.FAILED, persistedJob?.status)
        assertEquals(job.errorMessage, persistedJob?.errorMessage)
    }

    @Test
    fun testInvalidJsonResponseSetsJobToFailedWithExplicitMessage() = runTest {
        // Simuler une réponse corrompue (texte non JSON ou schéma incomplet)
        val corruptedResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Ceci n'est pas du JSON valide\"}]}}]}"
        val mockClient = createMockOkHttpClient(responseJsonText = corruptedResponse)
        val analyzer = GeminiIdeaAnalyzer(mockClient, apiKeyOverride = "test-api-key")
        val useCase = AnalyzeIdeaUseCase(db.projectDao(), db.generationJobDao(), analyzer)

        val (job, result) = useCase.executeAnalysisJob(
            projectId = testProjectId,
            ideaText = "Idée testant la robustesse du parser"
        )

        assertTrue("Le résultat doit échouer sur un JSON corrompu", result.isFailure)
        assertEquals(JobStatus.FAILED, job.status)
        assertTrue(
            "L'erreur doit mentionner le format JSON invalide",
            job.errorMessage!!.contains("JSON non conforme", ignoreCase = true)
        )
    }

    @Test
    fun testMissingApiKeyExplicitlyFailsJobWithConfigurationInstructions() = runTest {
        val mockClient = createMockOkHttpClient()
        val analyzer = GeminiIdeaAnalyzer(mockClient, apiKeyOverride = "MY_GEMINI_API_KEY")
        val useCase = AnalyzeIdeaUseCase(db.projectDao(), db.generationJobDao(), analyzer)

        val (job, result) = useCase.executeAnalysisJob(
            projectId = testProjectId,
            ideaText = "Idée sans clé API configurée"
        )

        assertTrue(result.isFailure)
        assertEquals(JobStatus.FAILED, job.status)
        assertNotNull(job.errorMessage)
        assertTrue(job.errorMessage!!.contains("Clé API Gemini non configurée", ignoreCase = true))
    }

    @Test
    fun testValidateAndSaveUpdatesProjectEntity() = runTest {
        val mockClient = createMockOkHttpClient()
        val analyzer = GeminiIdeaAnalyzer(mockClient)
        val useCase = AnalyzeIdeaUseCase(db.projectDao(), db.generationJobDao(), analyzer)

        val validatedAnalysis = IdeaAnalysisResult(
            genrePrincipal = "Dark Fantasy",
            sousGenres = listOf("Magie", "Intrigue de cour"),
            ton = "Épique et impitoyable",
            themesDetectes = listOf("Pouvoir", "Corruption"),
            elementsNarratifsExplicites = listOf("Guerre civile au royaume"),
            ambiguitesAClarifier = emptyList()
        )

        val updatedProject = useCase.validateAndSaveToProject(
            projectId = testProjectId,
            originalIdeaText = "Idée validée par l'auteur",
            validatedAnalysis = validatedAnalysis
        )

        assertEquals("Dark Fantasy", updatedProject.genre)
        assertEquals("Épique et impitoyable", updatedProject.tone)
        assertEquals("Idée validée par l'auteur", updatedProject.synopsis)

        // Vérifier dans Room
        val savedInDb = db.projectDao().findProjectById(testProjectId)
        assertNotNull(savedInDb)
        assertEquals("Dark Fantasy", savedInDb?.genre)
        assertEquals("Épique et impitoyable", savedInDb?.tone)
    }
}

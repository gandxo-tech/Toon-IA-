package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiCharacterGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.data.repository.CanonMemoryRepositoryImpl
import com.example.domain.canon.CanonElement
import com.example.domain.canon.CanonSnapshot
import com.example.domain.model.CharacterBibleEntry
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
class CharacterBibleUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-test-character-bible"

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
                tone = "Épique et mystérieux"
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

            val rawJsonContent = responseJsonText ?: createValidCharactersGeminiResponse()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val responseBody = rawJsonContent.toResponseBody(mediaType)

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Error")
                .body(responseBody)
                .build()
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun createValidCharactersGeminiResponse(): String {
        val charsArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("nom", "Kim Dokja")
                    put("role", "Protagoniste")
                    put("personnalite", "Calme, perspicace, sacrifice de soi caché sous un cynisme apparent")
                    put("apparencePhysique", "Taille moyenne, cheveux noirs courts et lisses, yeux sombres perçants, teint pâle")
                    put("traitsDistinctifs", "Regard observant, sourire en coin discret")
                    put("vetementsCaracteristiques", "Manteau blanc emblématique au-dessus d'une chemise sombre")
                    put("capacites", "Connaissance absolue de l'histoire, Quatrième Mur, Signet")
                    put("relations", "Compagnon d'armes et rival de Yoo Joonghyuk")
                    put("arcNarratifPrevu", "Survivre jusqu'au bout pour voir la fin du monde sauvée")
                    put("etatActuelDebut", "Employé de bureau ordinaire lisant le dernier chapitre dans le métro")
                }
            )
            put(
                JSONObject().apply {
                    put("nom", "Yoo Joonghyuk")
                    put("role", "Rival")
                    put("personnalite", "Froid, impitoyable mais protecteur envers ses compagnons")
                    put("apparencePhysique", "Grand, athlétique, traits ciselés, cheveux noirs mi-longs ébouriffés, yeux dorés perçants")
                    put("traitsDistinctifs", "Manteau noir, épée céleste noire")
                    put("vetementsCaracteristiques", "Long manteau de combat noir renforcé")
                    put("capacites", "Régression temporelle, Maîtrise absolue du sabre, Transcendance")
                    put("relations", "Méfiance envers Dokja qui sait trop de choses")
                    put("arcNarratifPrevu", "Apprendre à faire confiance pour briser le cycle éternel des régressions")
                    put("etatActuelDebut", "Régresseur à sa 3e vie, prêt au combat dans le premier wagon")
                }
            )
        }

        val structuredJson = JSONObject().put("characters", charsArray).toString()
        val contentObj = JSONObject().put(
            "parts",
            JSONArray().put(JSONObject().put("text", structuredJson))
        )
        val candidate = JSONObject().put("content", contentObj)

        return JSONObject().put("candidates", JSONArray().put(candidate)).toString()
    }

    @Test
    fun testGenerateCharactersSuccess_persistsJobAndReturnsStructuredBible() = runTest {
        val mockClient = createMockOkHttpClient()
        val fakeGenerator = GeminiCharacterGenerator(client = mockClient, apiKeyOverride = "test_valid_key")
        val useCase = CharacterBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            characterDao = db.characterDao(),
            characterStateDao = db.characterStateDao(),
            canonRepository = canonRepository,
            generator = fakeGenerator
        )

        val (job, result) = useCase.executeGenerateCharactersJob(testProjectId)

        assertTrue(result.isSuccess)
        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.PERSONNAGES, job.type)
        assertNotNull(job.rawResult)
        assertNull(job.errorMessage)

        val bibleResult = result.getOrNull()
        assertNotNull(bibleResult)
        assertEquals(2, bibleResult?.characters?.size)

        val protagonist = bibleResult?.characters?.first()
        assertEquals("Kim Dokja", protagonist?.name)
        assertEquals("Protagoniste", protagonist?.role)
        assertEquals("Manteau blanc emblématique au-dessus d'une chemise sombre", protagonist?.characteristicClothing)
        assertFalse(protagonist?.isValidatedInCanon ?: true)

        // Vérifier que le job est bien persisté dans la base Room
        val savedJob = db.generationJobDao().findJobById(job.id)
        assertNotNull(savedJob)
        assertEquals(JobStatus.SUCCESS, savedJob?.status)

        // Règle stricte : N'écrit en base QUE si validé, donc aucun personnage n'est encore dans Room
        val charsInDb = db.characterDao().findCharacterById(protagonist?.id ?: "")
        assertNull(charsInDb)
    }

    @Test
    fun testValidateAndWriteToCanon_persistsCharacterAndStateInRoom() = runTest {
        val useCase = CharacterBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            characterDao = db.characterDao(),
            characterStateDao = db.characterStateDao(),
            canonRepository = canonRepository
        )

        val candidate = CharacterBibleEntry(
            name = "Han Sooyoung",
            role = "Allié",
            personality = "Impertinente, écrivaine de génie, pragmatique",
            physicalAppearance = "Petite taille, cheveux noirs au carré avec frange, yeux noirs expressifs, grain de beauté sous l'œil",
            distinctiveFeatures = "Grain de beauté sous l'œil gauche",
            characteristicClothing = "Veste à capuche violette sombre, sucette à la bouche",
            abilities = "Clones d'avatar, Écriture prédictive",
            relationships = "Alliance temporaire avec Kim Dokja",
            plannedArc = "Révéler la véritable identité de l'auteur original",
            currentState = "Présente dans la zone du dôme"
        )

        val (validated, contradictions) = useCase.validateAndWriteToCanon(testProjectId, candidate)

        assertTrue(validated.isValidatedInCanon)
        assertTrue(contradictions.isEmpty())

        // Vérifier que le personnage est désormais présent dans le Canon de Room
        val charInDb = db.characterDao().findCharacterById(candidate.id)
        assertNotNull(charInDb)
        assertEquals("Han Sooyoung", charInDb?.name)
        assertEquals("Allié", charInDb?.role)

        // Vérifier via getCanonSnapshot
        val snapshot = canonRepository.getCanonSnapshot(testProjectId)
        assertEquals(1, snapshot.characters.size)
        assertEquals("Han Sooyoung", snapshot.characters.first().name)
    }

    @Test
    fun testContradictionDetected_whenVisualAppearanceContradictsCanon() = runTest {
        val useCase = CharacterBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            characterDao = db.characterDao(),
            characterStateDao = db.characterStateDao(),
            canonRepository = canonRepository
        )

        // 1. Inscrire un personnage au canon avec cheveux noirs et yeux noirs
        val originalChar = CharacterEntity(
            id = "char-dokja-canonical",
            projectId = testProjectId,
            name = "Kim Dokja",
            role = "Protagoniste",
            archetype = "Protagoniste",
            personalityTraits = "Calme",
            backstory = "Survivant",
            visualDescription = "Cheveux noirs courts, yeux noirs perçants",
            canonicalImagePath = null
        )
        canonRepository.updateCanon(testProjectId, CanonElement.Character(originalChar, isUserValidated = true))

        // 2. Tenter d'introduire un Kim Dokja avec yeux bleus et cheveux blonds (incompatible)
        val conflictingChar = CharacterBibleEntry(
            id = "char-dokja-candidate",
            name = "Kim Dokja",
            role = "Protagoniste",
            personality = "Calme",
            physicalAppearance = "Cheveux blonds éclatants, yeux bleus azur",
            distinctiveFeatures = "",
            characteristicClothing = "Costume"
        )

        val contradictions = useCase.checkContradictions(testProjectId, conflictingChar)
        assertFalse("Une contradiction visuelle aurait dû être détectée", contradictions.isEmpty())
        assertTrue(contradictions.any { it.fieldName == "Apparence Visuelle" })
    }

    @Test
    fun testNetworkError_jobTransitionsToFailedWithExplicitMessage() = runTest {
        val mockClient = createMockOkHttpClient(throwNetworkError = true)
        val fakeGenerator = GeminiCharacterGenerator(client = mockClient, apiKeyOverride = "test_valid_key")
        val useCase = CharacterBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            characterDao = db.characterDao(),
            characterStateDao = db.characterStateDao(),
            canonRepository = canonRepository,
            generator = fakeGenerator
        )

        val (job, result) = useCase.executeGenerateCharactersJob(testProjectId)

        assertTrue(result.isFailure)
        assertEquals(JobStatus.FAILED, job.status)
        assertNotNull(job.errorMessage)
        assertTrue(job.errorMessage!!.contains("réseau") || job.errorMessage!!.contains("Simulation coupure réseau"))

        // Vérifier l'état dans la base Room
        val savedJob = db.generationJobDao().findJobById(job.id)
        assertNotNull(savedJob)
        assertEquals(JobStatus.FAILED, savedJob?.status)
    }

    @Test
    fun testMissingApiKey_jobTransitionsToFailed() = runTest {
        // Clé API vide
        val generatorWithNoKey = GeminiCharacterGenerator(apiKeyOverride = "")
        val useCase = CharacterBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            characterDao = db.characterDao(),
            characterStateDao = db.characterStateDao(),
            canonRepository = canonRepository,
            generator = generatorWithNoKey
        )

        val (job, result) = useCase.executeGenerateCharactersJob(testProjectId)

        assertTrue(result.isFailure)
        assertEquals(JobStatus.FAILED, job.status)
        assertTrue(job.errorMessage!!.contains("Clé API Gemini non configurée"))
    }

    @Test
    fun testManualCharacterCreation_doesNotCallAIAndLeavesCanonUnwritten() = runTest {
        val useCase = CharacterBibleUseCase(
            projectDao = db.projectDao(),
            jobDao = db.generationJobDao(),
            characterDao = db.characterDao(),
            characterStateDao = db.characterStateDao(),
            canonRepository = canonRepository
        )

        val manualChar = useCase.createManualCharacter(testProjectId, "Lee Gilyoung", "Secondaire")

        assertEquals("Lee Gilyoung", manualChar.name)
        assertEquals("Secondaire", manualChar.role)
        assertFalse(manualChar.isValidatedInCanon)

        // Non écrit dans Room avant validation explicite
        val inDb = db.characterDao().findCharacterById(manualChar.id)
        assertNull(inDb)
    }
}

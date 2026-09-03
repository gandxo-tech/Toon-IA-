package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiSceneDetailGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SceneEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryboardUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-storyboard-test"
    private val testChapterId = "chap-1-test"

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
                title = "ToonIA Storyboard Saga",
                synopsis = "Une grande aventure verticale.",
                genre = "Action"
            )
        )

        db.chapterDao().insertChapter(
            ChapterEntity(
                id = testChapterId,
                projectId = testProjectId,
                number = 1,
                title = "L'Éveil de l'Aube",
                synopsis = "Aria découvre les ruines antiques.",
                isCanon = false
            )
        )

        val initialScenes = listOf(
            SceneEntity(
                id = "scene-1",
                chapterId = testChapterId,
                projectId = testProjectId,
                orderIndex = 1,
                title = "Découverte des ruines",
                narrativeSummary = "Aria explore la crypte sous une pluie battante.",
                charactersPresent = "Aria",
                locationName = "Crypte oubliée",
                isCanon = false
            ),
            SceneEntity(
                id = "scene-2",
                chapterId = testChapterId,
                projectId = testProjectId,
                orderIndex = 2,
                title = "L'apparition de l'ombre",
                narrativeSummary = "Une créature ténébreuse surgit du sol.",
                charactersPresent = "Aria, Ombre",
                locationName = "Crypte oubliée",
                isCanon = false
            ),
            SceneEntity(
                id = "scene-3",
                chapterId = testChapterId,
                projectId = testProjectId,
                orderIndex = 3,
                title = "Le duel aérien",
                narrativeSummary = "Aria déploie ses ailes et contre-attaque.",
                charactersPresent = "Aria, Ombre",
                locationName = "Crypte oubliée",
                isCanon = false
            )
        )
        db.sceneDao().insertScenes(initialScenes)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createMockOkHttpClient(responseJsonString: String): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val geminiResponse = JSONObject().apply {
                val candidate = JSONObject().apply {
                    val content = JSONObject().apply {
                        val part = JSONObject().apply {
                            put("text", responseJsonString)
                        }
                        put("parts", JSONArray().apply { put(part) })
                    }
                    put("content", content)
                }
                put("candidates", JSONArray().apply { put(candidate) })
            }

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(geminiResponse.toString().toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun testMoveSceneDownAndUp() = runTest {
        val mockGenerator = GeminiSceneDetailGenerator(createMockOkHttpClient("{}"), apiKeyOverride = "TEST_KEY")
        val useCase = StoryboardUseCase(db.chapterDao(), db.sceneDao(), db.generationJobDao(), canonRepository, mockGenerator)

        // Déplacer scène 1 vers le bas -> scène 1 doit passer en orderIndex 2
        useCase.moveScene(testChapterId, "scene-1", directionUp = false)

        val scenesAfterDown = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()?.sortedBy { it.orderIndex }
        assertNotNull(scenesAfterDown)
        assertEquals("scene-2", scenesAfterDown!![0].id)
        assertEquals("scene-1", scenesAfterDown[1].id)

        // Déplacer scène 1 vers le haut -> doit revenir en premier
        useCase.moveScene(testChapterId, "scene-1", directionUp = true)
        val scenesAfterUp = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()?.sortedBy { it.orderIndex }
        assertEquals("scene-1", scenesAfterUp!![0].id)
        assertEquals("scene-2", scenesAfterUp[1].id)
    }

    @Test
    fun testMergeScenes() = runTest {
        val mockGenerator = GeminiSceneDetailGenerator(createMockOkHttpClient("{}"), apiKeyOverride = "TEST_KEY")
        val useCase = StoryboardUseCase(db.chapterDao(), db.sceneDao(), db.generationJobDao(), canonRepository, mockGenerator)

        // Fusionner scène 1 et scène 2
        useCase.mergeScenes(testChapterId, "scene-1", "scene-2")

        val scenes = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()?.sortedBy { it.orderIndex }
        assertNotNull(scenes)
        assertEquals(2, scenes!!.size)

        val mergedScene = scenes[0]
        assertEquals("scene-1", mergedScene.id)
        assertTrue(mergedScene.title.contains("Découverte des ruines"))
        assertTrue(mergedScene.title.contains("L'apparition de l'ombre"))
        assertTrue(mergedScene.narrativeSummary.contains("Aria explore"))
        assertTrue(mergedScene.narrativeSummary.contains("Une créature ténébreuse"))

        // La scène 3 doit être réindexée en position 2
        assertEquals("scene-3", scenes[1].id)
        assertEquals(2, scenes[1].orderIndex)
    }

    @Test
    fun testSplitScene() = runTest {
        val mockGenerator = GeminiSceneDetailGenerator(createMockOkHttpClient("{}"), apiKeyOverride = "TEST_KEY")
        val useCase = StoryboardUseCase(db.chapterDao(), db.sceneDao(), db.generationJobDao(), canonRepository, mockGenerator)

        useCase.splitScene(
            chapterId = testChapterId,
            projectId = testProjectId,
            originalSceneId = "scene-1",
            splitTitle = "La stèle maudite",
            splitSummary = "Aria touche la stèle et libère le sceau.",
            splitBeats = "Activation magique\nChoc sismique",
            splitCharacters = "Aria"
        )

        val scenes = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()?.sortedBy { it.orderIndex }
        assertNotNull(scenes)
        assertEquals(4, scenes!!.size)
        assertEquals(1, scenes[0].orderIndex)
        assertEquals("scene-1", scenes[0].id)

        assertEquals(2, scenes[1].orderIndex)
        assertEquals("La stèle maudite", scenes[1].title)

        assertEquals(3, scenes[2].orderIndex)
        assertEquals("scene-2", scenes[2].id)

        assertEquals(4, scenes[3].orderIndex)
        assertEquals("scene-3", scenes[3].id)
    }

    @Test
    fun testDetailSceneWithAi() = runTest {
        val jsonResponse = JSONObject().apply {
            put("enrichedSummary", "Aria pénètre lentement dans la crypte, l'eau ruisselant le long des murs froids.")
            put("pacingNotes", "Rythme lent et contemplatif au début, montée soudaine de tension.")
            put("emotionalArc", "Curiosité prudente -> Malaise sensoriel -> Effroi mesuré.")
            put("detailedNarrativeBeats", JSONArray().apply {
                put("Entrée dans la crypte")
                put("Bruits suspects d'eau qui goutte")
                put("Découverte des glyphes anciens")
            })
            put("atmosphereAndSensory", "Brouillard froid, odeur de mousse et d'ozone magique.")
            put("characterInteractions", "Aria se parle à elle-même pour garder son sang-froid.")
        }.toString()

        val mockGenerator = GeminiSceneDetailGenerator(createMockOkHttpClient(jsonResponse), apiKeyOverride = "TEST_KEY")
        val useCase = StoryboardUseCase(db.chapterDao(), db.sceneDao(), db.generationJobDao(), canonRepository, mockGenerator)

        val scene1 = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()!!.first { it.id == "scene-1" }
        val (job, result) = useCase.detailSceneWithAi(testProjectId, scene1)

        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.STORYBOARD, job.type)
        assertTrue(result.isSuccess)

        val updatedScene = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()!!.first { it.id == "scene-1" }
        assertEquals("Rythme lent et contemplatif au début, montée soudaine de tension.", updatedScene.pacingNotes)
        assertEquals("Curiosité prudente -> Malaise sensoriel -> Effroi mesuré.", updatedScene.emotionalArc)
        assertTrue(updatedScene.narrativeBeats.contains("Entrée dans la crypte"))
        assertFalse(updatedScene.isCanon) // Toujours brouillon tant que non validé
    }

    @Test
    fun testValidateStoryboardToCanon() = runTest {
        val mockGenerator = GeminiSceneDetailGenerator(createMockOkHttpClient("{}"), apiKeyOverride = "TEST_KEY")
        val useCase = StoryboardUseCase(db.chapterDao(), db.sceneDao(), db.generationJobDao(), canonRepository, mockGenerator)

        useCase.validateStoryboardToCanon(testChapterId, testProjectId)

        val scenes = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()
        assertNotNull(scenes)
        assertTrue(scenes!!.all { it.isCanon })

        val canonSnapshot = canonRepository.getCanonSnapshot(testProjectId)
        assertTrue(canonSnapshot.scenes.isNotEmpty())
    }
}

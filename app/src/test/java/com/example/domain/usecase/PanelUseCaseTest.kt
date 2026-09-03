package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiPanelGenerator
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.PanelEntity
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
class PanelUseCaseTest {

    private lateinit var db: ToonDatabase
    private lateinit var canonRepository: CanonMemoryRepositoryImpl
    private val testProjectId = "proj-panel-test"
    private val testChapterId = "chap-1-panel-test"
    private val testSceneId = "scene-1-panel-test"

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
                title = "ToonIA Panel Saga",
                synopsis = "Une grande aventure verticale en cases.",
                genre = "Action"
            )
        )

        db.chapterDao().insertChapter(
            ChapterEntity(
                id = testChapterId,
                projectId = testProjectId,
                number = 1,
                title = "L'Aube des Héros",
                synopsis = "Aria affronte son destin.",
                isCanon = false
            )
        )

        db.sceneDao().insertScene(
            SceneEntity(
                id = testSceneId,
                chapterId = testChapterId,
                projectId = testProjectId,
                orderIndex = 1,
                title = "La Rencontre au Sommet",
                narrativeSummary = "Aria arrive au bord du gouffre et aperçoit l'antagoniste.",
                charactersPresent = "Aria, Kael",
                locationName = "Gouffre Céleste",
                atmosphere = "Vent violent, orage imminent",
                isCanon = false
            )
        )
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

    private fun samplePanelsJson(): String {
        return JSONObject().apply {
            val panelsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("orderIndex", 1)
                    put("charactersPresent", "Aria (manteau flottant, regard déterminé)")
                    put("location", "Bord de la falaise céleste")
                    put("actionDescription", "Aria marche vers le bord de la falaise en observant les nuages.")
                    put("dominantEmotion", "Concentration intense")
                    put("cameraAngle", "Plan d'ensemble vertical")
                    put("composition", "Personnage en bas à droite, immense gouffre à gauche")
                    put("lightingAndAtmosphere", "Lumière d'orage crépusculaire avec éclairs lointains")
                    put("backgroundDetails", "Flèches d'argent de la cité flottante en arrière-plan")
                    put("visualEffects", "Poussière soulevée par les bourrasques")
                    put("visualPrompt", "Webtoon wide shot, heroic female warrior on cliff edge looking at stormy sky, majestic floating city in background, detailed anime style")
                    put("continuityNotes", "Plan d'établissement initial de la scène")
                    put("soundEffects", "VWOOO")
                })
                put(JSONObject().apply {
                    put("orderIndex", 2)
                    put("charactersPresent", "Aria (regard surpris, pupilles dilatées)")
                    put("location", "Bord de la falaise")
                    put("actionDescription", "Gros plan sur les yeux d'Aria qui aperçoit une silhouette dans la brume.")
                    put("dominantEmotion", "Stupeur")
                    put("cameraAngle", "Gros plan")
                    put("composition", "Cadrage serré sur le visage, reflets d'éclairs dans les yeux")
                    put("lightingAndAtmosphere", "Éclair bleu illuminant vivement le visage")
                    put("backgroundDetails", "Brume sombre floutée")
                    put("visualEffects", "Lignes de choc subtiles")
                    put("visualPrompt", "Webtoon close up, warrior girl wide expressive eyes with lightning reflection, intense emotion, highly detailed anime linework")
                    put("continuityNotes", "Raccord dans l'axe suite au regard vers la brume")
                    put("soundEffects", "SHING")
                })
            }
            put("panels", panelsArray)
        }.toString()
    }

    @Test
    fun testGeneratePanelsForScene() = runTest {
        val mockGenerator = GeminiPanelGenerator(createMockOkHttpClient(samplePanelsJson()), apiKeyOverride = "TEST_KEY")
        val useCase = PanelUseCase(
            db.chapterDao(),
            db.sceneDao(),
            db.panelDao(),
            db.generationJobDao(),
            canonRepository,
            mockGenerator
        )

        val scene = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()!!.first()
        val (job, result) = useCase.generatePanelsForScene(testProjectId, scene, targetPanelCount = 2)

        assertEquals(JobStatus.SUCCESS, job.status)
        assertEquals(JobType.PANEL, job.type)
        assertTrue(result.isSuccess)

        val panels = db.panelDao().getPanelsBySceneId(testSceneId).firstOrNull()
        assertNotNull(panels)
        assertEquals(2, panels!!.size)

        val p1 = panels[0]
        assertEquals(1, p1.orderIndex)
        assertEquals("Plan d'ensemble vertical", p1.cameraAngle)
        assertEquals("VWOOO", p1.soundEffects)
        assertTrue(p1.visualPrompt.contains("Webtoon wide shot"))
        assertFalse(p1.isCanon)

        val p2 = panels[1]
        assertEquals(2, p2.orderIndex)
        assertEquals("Gros plan", p2.cameraAngle)
        assertEquals("Stupeur", p2.dominantEmotion)
    }

    @Test
    fun testMovePanelUpDown() = runTest {
        val mockGenerator = GeminiPanelGenerator(createMockOkHttpClient(samplePanelsJson()), apiKeyOverride = "TEST_KEY")
        val useCase = PanelUseCase(
            db.chapterDao(),
            db.sceneDao(),
            db.panelDao(),
            db.generationJobDao(),
            canonRepository,
            mockGenerator
        )

        val scene = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()!!.first()
        useCase.generatePanelsForScene(testProjectId, scene)

        val initialPanels = db.panelDao().getPanelsBySceneId(testSceneId).firstOrNull()!!
        val panel1 = initialPanels[0]
        val panel2 = initialPanels[1]

        // Déplacer le panel 1 vers le bas
        useCase.movePanel(testSceneId, panel1.id, directionUp = false)

        val afterMove = db.panelDao().getPanelsBySceneId(testSceneId).firstOrNull()?.sortedBy { it.orderIndex }!!
        assertEquals(panel2.id, afterMove[0].id)
        assertEquals(panel1.id, afterMove[1].id)
    }

    @Test
    fun testAddManualPanelAndDeletePanel() = runTest {
        val mockGenerator = GeminiPanelGenerator(createMockOkHttpClient(samplePanelsJson()), apiKeyOverride = "TEST_KEY")
        val useCase = PanelUseCase(
            db.chapterDao(),
            db.sceneDao(),
            db.panelDao(),
            db.generationJobDao(),
            canonRepository,
            mockGenerator
        )

        // Ajout d'un panel manuel
        useCase.addManualPanel(testSceneId, testChapterId, testProjectId, afterOrderIndex = 0)

        var panels = db.panelDao().getPanelsBySceneId(testSceneId).firstOrNull()!!
        assertEquals(1, panels.size)
        val addedPanel = panels[0]
        assertEquals(1, addedPanel.orderIndex)
        assertEquals("Nouveau panel", addedPanel.visualDescription)

        // Suppression du panel
        useCase.deletePanel(addedPanel)
        panels = db.panelDao().getPanelsBySceneId(testSceneId).firstOrNull()!!
        assertTrue(panels.isEmpty())
    }

    @Test
    fun testValidatePanelsToCanon() = runTest {
        val mockGenerator = GeminiPanelGenerator(createMockOkHttpClient(samplePanelsJson()), apiKeyOverride = "TEST_KEY")
        val useCase = PanelUseCase(
            db.chapterDao(),
            db.sceneDao(),
            db.panelDao(),
            db.generationJobDao(),
            canonRepository,
            mockGenerator
        )

        val scene = db.sceneDao().getScenesByChapterId(testChapterId).firstOrNull()!!.first()
        useCase.generatePanelsForScene(testProjectId, scene)

        useCase.validatePanelsToCanon(testChapterId)

        val panels = db.panelDao().getPanelsByChapterId(testChapterId).firstOrNull()!!
        assertTrue(panels.all { it.isCanon })
    }
}

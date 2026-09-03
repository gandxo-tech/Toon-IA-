package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.CanonMemoryEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.CharacterEntity
import com.example.data.local.entity.CharacterStateEntity
import com.example.data.local.entity.CoverEntity
import com.example.data.local.entity.DialogueEntity
import com.example.data.local.entity.ExportEntity
import com.example.data.local.entity.GenerationJobEntity
import com.example.data.local.entity.HumorDNAEntity
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType
import com.example.data.local.entity.LocationEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.local.entity.StoryBibleEntity
import com.example.data.local.entity.StoryObjectEntity
import com.example.data.local.entity.TimelineEventEntity
import com.example.data.local.entity.VisualBibleEntity
import com.example.data.local.entity.WorldBibleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class ToonDatabaseTest {

    private lateinit var db: ToonDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveProject() = runTest {
        val project = ProjectEntity(
            id = "proj-1",
            title = "L'Éveil du Mage",
            synopsis = "Un jeune apprenti découvre un pouvoir oublié.",
            genre = "Fantasy",
            targetAudience = "Shonen / Webtoon",
            tone = "Épique"
        )
        db.projectDao().insertProject(project)

        val retrieved = db.projectDao().findProjectById("proj-1")
        assertNotNull(retrieved)
        assertEquals("L'Éveil du Mage", retrieved?.title)
        assertEquals("Fantasy", retrieved?.genre)
    }

    @Test
    fun insertCharacterAndCharacterStateWithCanonicalImagePath() = runTest {
        val project = ProjectEntity(id = "proj-1", title = "Projet Test")
        db.projectDao().insertProject(project)

        val character = CharacterEntity(
            id = "char-1",
            projectId = "proj-1",
            name = "Aiden",
            role = "Protagoniste",
            archetype = "Héros réticent",
            canonicalImagePath = "/files/characters/aiden_canon.png"
        )
        db.characterDao().insertCharacter(character)

        val retrievedChar = db.characterDao().findCharacterById("char-1")
        assertNotNull(retrievedChar)
        assertEquals("Aiden", retrievedChar?.name)
        assertEquals("/files/characters/aiden_canon.png", retrievedChar?.canonicalImagePath)

        val charState = CharacterStateEntity(
            id = "state-1",
            characterId = "char-1",
            projectId = "proj-1",
            currentAppearance = "Armure noircie par le combat",
            currentRelationships = "Méfiance envers le roi",
            psychologicalState = "Déterminé"
        )
        db.characterStateDao().insertState(charState)

        val states = db.characterStateDao().getStatesByCharacterId("char-1").first()
        assertEquals(1, states.size)
        assertEquals("Armure noircie par le combat", states[0].currentAppearance)
    }

    @Test
    fun chapterWorkflowDifferentiatesCanonAndDraft() = runTest {
        val project = ProjectEntity(id = "proj-1", title = "Projet Test")
        db.projectDao().insertProject(project)

        val draftChapter = ChapterEntity(
            id = "chap-1",
            projectId = "proj-1",
            number = 1,
            title = "Chapitre 1 : Le Réveil",
            isCanon = false
        )
        val canonChapter = ChapterEntity(
            id = "chap-2",
            projectId = "proj-1",
            number = 2,
            title = "Chapitre 2 : L'Appel",
            isCanon = true
        )
        db.chapterDao().insertChapter(draftChapter)
        db.chapterDao().insertChapter(canonChapter)

        val allChapters = db.chapterDao().getChaptersByProjectId("proj-1").first()
        assertEquals(2, allChapters.size)

        val canonOnly = db.chapterDao().getCanonChaptersByProjectId("proj-1").first()
        assertEquals(1, canonOnly.size)
        assertEquals("chap-2", canonOnly[0].id)
        assertTrue(canonOnly[0].isCanon)

        val scene = SceneEntity(
            id = "scene-1",
            chapterId = "chap-2",
            projectId = "proj-1",
            orderIndex = 1,
            title = "Affrontement dans la ruelle",
            isCanon = true
        )
        db.sceneDao().insertScene(scene)

        val panel = PanelEntity(
            id = "panel-1",
            sceneId = "scene-1",
            chapterId = "chap-2",
            projectId = "proj-1",
            orderIndex = 1,
            cameraAngle = "Contre-plongée dramatique",
            visualDescription = "Aiden lève son bâton étincelant",
            localImagePath = "/files/panels/panel_1.webp",
            isCanon = true
        )
        db.panelDao().insertPanel(panel)

        val dialogue = DialogueEntity(
            id = "dial-1",
            panelId = "panel-1",
            projectId = "proj-1",
            characterId = "char-1",
            type = "SHOUT",
            text = "Reculez tous !",
            isCanon = true
        )
        db.dialogueDao().insertDialogue(dialogue)

        val retrievedPanel = db.panelDao().getPanelById("panel-1").first()
        assertNotNull(retrievedPanel)
        assertEquals("/files/panels/panel_1.webp", retrievedPanel?.localImagePath)
        assertTrue(retrievedPanel!!.isCanon)

        val dialogues = db.dialogueDao().getDialoguesByPanelId("panel-1").first()
        assertEquals(1, dialogues.size)
        assertEquals("Reculez tous !", dialogues[0].text)
    }

    @Test
    fun generationJobTrackingWithEnumsAndRetries() = runTest {
        val project = ProjectEntity(id = "proj-1", title = "Projet Test")
        db.projectDao().insertProject(project)

        val job = GenerationJobEntity(
            id = "job-101",
            projectId = "proj-1",
            type = JobType.PERSONNAGES,
            status = JobStatus.QUEUED,
            inputPayload = "{\"prompt\":\"Générer antagoniste froid et méthodique\"}",
            retryCount = 0
        )
        db.generationJobDao().insertJob(job)

        val queuedJobs = db.generationJobDao().getJobsByStatus("proj-1", JobStatus.QUEUED).first()
        assertEquals(1, queuedJobs.size)
        assertEquals(JobType.PERSONNAGES, queuedJobs[0].type)

        // Simulation de mise à jour d'état suite à exécution
        db.generationJobDao().updateJobOutcome(
            id = "job-101",
            status = JobStatus.SUCCESS,
            rawResult = "{\"characters\":[{\"name\":\"Kaelen\"}]}",
            errorMessage = null
        )

        val updatedJob = db.generationJobDao().findJobById("job-101")
        assertNotNull(updatedJob)
        assertEquals(JobStatus.SUCCESS, updatedJob?.status)
        assertNotNull(updatedJob?.rawResult)
        assertNull(updatedJob?.errorMessage)
    }

    @Test
    fun biblesAndWorldEntitiesStorage() = runTest {
        val project = ProjectEntity(id = "proj-1", title = "Projet Test")
        db.projectDao().insertProject(project)

        val worldBible = WorldBibleEntity(
            id = "wb-1",
            projectId = "proj-1",
            magicOrTechRules = "La magie consomme l'énergie vitale",
            factions = "L'Ordre Céleste vs La Rébellion d'Ombre",
            geography = "Archipel flottant d'Aethel"
        )
        db.worldBibleDao().insertOrUpdateWorldBible(worldBible)

        val storyBible = StoryBibleEntity(
            id = "sb-1",
            projectId = "proj-1",
            mainTheme = "Le fardeau de la vérité",
            narrativeArc = "Structure 3 actes webtoon",
            climaxDescription = "Duel au sommet de la flèche"
        )
        db.storyBibleDao().insertOrUpdateStoryBible(storyBible)

        val visualBible = VisualBibleEntity(
            id = "vb-1",
            projectId = "proj-1",
            artStyle = "Manhwa action haute dynamique",
            colorPalette = "Bleu néon et noir charbon"
        )
        db.visualBibleDao().insertOrUpdateVisualBible(visualBible)

        val humorDna = HumorDNAEntity(
            id = "hd-1",
            projectId = "proj-1",
            humorStyle = "Ironie et mimiques chibi expressives",
            chibiMomentsAllowed = true
        )
        db.humorDnaDao().insertOrUpdateHumorDna(humorDna)

        val location = LocationEntity(
            id = "loc-1",
            projectId = "proj-1",
            name = "La Forteresse Céleste",
            description = "Un château suspendu au-dessus des nuages"
        )
        db.locationDao().insertLocation(location)

        val storyObject = StoryObjectEntity(
            id = "obj-1",
            projectId = "proj-1",
            name = "Le Sceau de Cristal",
            significance = "Artefact de scellement antique"
        )
        db.storyObjectDao().insertObject(storyObject)

        val canonMemory = CanonMemoryEntity(
            id = "mem-1",
            projectId = "proj-1",
            category = "RÈGLE_ABSOLUE",
            fact = "Aucun mort ne peut être ressuscité sans sacrifier une cité"
        )
        db.canonMemoryDao().insertCanonMemory(canonMemory)

        val timelineEvent = TimelineEventEntity(
            id = "time-1",
            projectId = "proj-1",
            orderIndex = 1,
            title = "La Chute de l'Archonte",
            dateOrEpoch = "An 342 de l'Ère Stellaire",
            isCanon = true
        )
        db.timelineEventDao().insertEvent(timelineEvent)

        val cover = CoverEntity(
            id = "cov-1",
            projectId = "proj-1",
            title = "L'Éveil du Mage",
            subtitle = "Saison 1",
            localImagePath = "/files/covers/cover_s1.jpg",
            isCanon = true
        )
        db.coverDao().insertCover(cover)

        val export = ExportEntity(
            id = "exp-1",
            projectId = "proj-1",
            format = "PDF",
            localFilePath = "/files/exports/ch1.pdf",
            pageCount = 45,
            fileSizeBytes = 12450000L,
            isCanon = true
        )
        db.exportDao().insertExport(export)

        // Vérifications
        val savedWb = db.worldBibleDao().findWorldBibleByProjectId("proj-1")
        assertEquals("Archipel flottant d'Aethel", savedWb?.geography)

        val savedSb = db.storyBibleDao().findStoryBibleByProjectId("proj-1")
        assertEquals("Le fardeau de la vérité", savedSb?.mainTheme)

        val savedVb = db.visualBibleDao().findVisualBibleByProjectId("proj-1")
        assertEquals("Manhwa action haute dynamique", savedVb?.artStyle)

        val savedHd = db.humorDnaDao().findHumorDnaByProjectId("proj-1")
        assertTrue(savedHd?.chibiMomentsAllowed == true)

        val savedMem = db.canonMemoryDao().getCanonMemoriesByProjectId("proj-1").first()
        assertEquals(1, savedMem.size)
        assertEquals("RÈGLE_ABSOLUE", savedMem[0].category)

        val savedCover = db.coverDao().getCanonCoverByProjectId("proj-1").first()
        assertNotNull(savedCover)
        assertEquals("/files/covers/cover_s1.jpg", savedCover?.localImagePath)

        val savedExport = db.exportDao().getExportsByProjectId("proj-1").first()
        assertEquals(1, savedExport.size)
        assertEquals("PDF", savedExport[0].format)
    }
}

package com.example.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ToonDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ExportEntity
import com.example.data.local.entity.PanelEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.DialogueEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ExportUiState(
    val project: ProjectEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val panels: List<PanelEntity> = emptyList(),
    val dialogues: List<DialogueEntity> = emptyList(),
    val isExporting: Boolean = false,
    val lastExportFile: File? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ExportViewModel(
    private val projectId: String,
    private val database: ToonDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val project = database.projectDao().findProjectById(projectId)
            val chapters = database.chapterDao().getChaptersByProjectId(projectId).first()
            val panels = database.panelDao().getPanelsByProjectId(projectId).first()
            val dialogues = database.dialogueDao().getDialoguesByProjectId(projectId).first()

            _uiState.update {
                it.copy(
                    project = project,
                    chapters = chapters,
                    panels = panels,
                    dialogues = dialogues
                )
            }
        }
    }

    fun generatePdf(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null, statusMessage = "Génération du PDF en cours...") }
            try {
                val state = _uiState.value
                val project = state.project ?: throw IllegalStateException("Projet introuvable")
                val chapters = state.chapters
                val panels = state.panels
                val dialogues = state.dialogues

                val pdfDocument = PdfDocument()
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                    isAntiAlias = true
                }
                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 24f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val subtitlePaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 16f
                    isAntiAlias = true
                }

                // Page 1: Cover / Title Page
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var yPos = 80f
                canvas.drawText(project.title, 50f, yPos, titlePaint)
                yPos += 30f
                canvas.drawText("Synopsis :", 50f, yPos, subtitlePaint)
                yPos += 20f

                val words = project.synopsis.split(" ")
                var line = ""
                for (word in words) {
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(testLine) > 500f) {
                        canvas.drawText(line, 50f, yPos, paint)
                        yPos += 20f
                        line = word
                    } else {
                        line = testLine
                    }
                }
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 50f, yPos, paint)
                }

                pdfDocument.finishPage(page)

                // Pages for Chapters and Panels
                var pageIndex = 2
                for (chapter in chapters) {
                    val chapterPanels = panels.filter { it.chapterId == chapter.id }.sortedBy { it.orderIndex }
                    if (chapterPanels.isEmpty()) continue

                    val chapterPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex++).create()
                    val chapterPage = pdfDocument.startPage(chapterPageInfo)
                    val chapterCanvas = chapterPage.canvas
                    chapterCanvas.drawColor(Color.WHITE)

                    var cYPos = 50f
                    chapterCanvas.drawText("Chapitre : ${chapter.title}", 50f, cYPos, titlePaint)
                    cYPos += 30f

                    for (panel in chapterPanels) {
                        if (cYPos > 700f) break

                        chapterCanvas.drawText("Panel #${panel.orderIndex}", 50f, cYPos, subtitlePaint)
                        cYPos += 20f

                        val imgFile = panel.localImagePath?.let { File(it) }
                        if (imgFile != null && imgFile.exists()) {
                            try {
                                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                                if (bitmap != null) {
                                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 200, 150, true)
                                    chapterCanvas.drawBitmap(scaledBitmap, 50f, cYPos, null)
                                    cYPos += 160f
                                }
                            } catch (_: Exception) {
                            }
                        }

                        val panelDialogues = dialogues.filter { it.panelId == panel.id }
                        for (diag in panelDialogues) {
                            chapterCanvas.drawText("[${diag.speakerName}]: ${diag.text}", 70f, cYPos, paint)
                            cYPos += 18f
                        }
                        cYPos += 15f
                    }

                    pdfDocument.finishPage(chapterPage)
                }

                val fileName = "ToonIA_Export_${UUID.randomUUID().toString().take(8)}.pdf"
                val file = File(context.filesDir, fileName)
                val fos = FileOutputStream(file)
                pdfDocument.writeTo(fos)
                pdfDocument.close()
                fos.close()

                val exportEntity = ExportEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    format = "PDF",
                    localFilePath = file.absolutePath,
                    pageCount = pageIndex - 1,
                    fileSizeBytes = file.length(),
                    createdAt = System.currentTimeMillis()
                )
                database.exportDao().insertExport(exportEntity)

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportFile = file,
                        statusMessage = "PDF généré avec succès !"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Erreur lors de l'export PDF : ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun sharePdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Partager le Webtoon (PDF)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Impossible de partager le fichier : ${e.localizedMessage}") }
        }
    }

    class Factory(
        private val projectId: String,
        private val database: ToonDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExportViewModel(projectId, database) as T
        }
    }
}

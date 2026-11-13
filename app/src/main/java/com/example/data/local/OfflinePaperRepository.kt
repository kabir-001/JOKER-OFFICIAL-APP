package com.example.data.local

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.JokerOfficialDefaults
import com.example.data.model.PaperSerialization
import com.example.data.model.SupportConfig
import com.example.data.model.TargetPaper
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflinePaperRepository(private val context: Context) {

  private val appContext = context.applicationContext
  private val dao = AppDatabase.getInstance(appContext).downloadedPaperDao()
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun observeDownloadedPapers(): Flow<List<DownloadedPaperEntity>> = dao.observeAll()

  fun observeDownloadedIds(): Flow<Set<String>> = dao.observeDownloadedIds().map { it.toSet() }

  suspend fun getDownloadedPaper(paperId: String): DownloadedPaperEntity? = dao.getById(paperId)

  suspend fun downloadPaperForOffline(paper: TargetPaper): Result<DownloadedPaperEntity> =
    withContext(Dispatchers.IO) {
      try {
        require(paper.id.isNotBlank()) { "Paper ID cannot be blank" }
        val papersDir = File(appContext.filesDir, "papers").apply { mkdirs() }
        val pdfFile = File(papersDir, "${paper.id}.pdf")
        generatePrintablePdf(paper, pdfFile)

        val sectionsJson = PaperSerialization.sectionsToJson(paper.sections)
        val byteSize = pdfFile.length().coerceAtLeast(sectionsJson.toByteArray().size.toLong())

        val entity = DownloadedPaperEntity(
          paperId = paper.id,
          title = paper.title,
          subject = paper.subject,
          classLevel = paper.classLevel,
          examTerm = paper.examTerm,
          totalMarks = paper.totalMarks,
          durationText = paper.durationText,
          instructions = paper.instructions,
          externalPdfUrl = paper.externalPdfUrl,
          sectionsJson = sectionsJson,
          pdfFilePath = pdfFile.absolutePath,
          downloadedAtMillis = System.currentTimeMillis(),
          fileSizeBytes = byteSize,
        )
        dao.upsert(entity)
        Result.success(entity)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun removeOfflineDownload(paperId: String) =
    withContext(Dispatchers.IO) {
      val existing = dao.getById(paperId)
      if (existing != null && existing.pdfFilePath.isNotBlank()) {
        runCatching { File(existing.pdfFilePath).delete() }
      }
      dao.deleteById(paperId)
    }

  fun getShareablePdfUri(pdfFilePath: String): Uri? {
    val file = File(pdfFilePath)
    if (!file.exists()) return null
    return FileProvider.getUriForFile(
      appContext,
      "${appContext.packageName}.fileprovider",
      file,
    )
  }

  fun cacheSupportConfig(config: SupportConfig) {
    prefs.edit()
      .putString(KEY_WA_NAME, config.whatsappChannelName)
      .putString(KEY_WA_URL, config.whatsappChannelUrl)
      .putString(KEY_SSC_NAME, config.sscGroupName)
      .putString(KEY_SSC_URL, config.sscGroupUrl)
      .putString(KEY_HSC_NAME, config.hscGroupName)
      .putString(KEY_HSC_URL, config.hscGroupUrl)
      .putString(KEY_SUPPORT_NOTE, config.supportNote)
      .putString(KEY_CONTACTS_JSON, PaperSerialization.contactsToJson(config.helpContacts))
      .putBoolean(KEY_HAS_CACHED_SUPPORT, true)
      .apply()
  }

  fun getCachedSupportConfig(): SupportConfig {
    if (!prefs.getBoolean(KEY_HAS_CACHED_SUPPORT, false)) {
      return JokerOfficialDefaults.defaultSupportConfig()
    }
    return SupportConfig(
      id = "support_settings",
      isPublic = true,
      whatsappChannelName = prefs.getString(KEY_WA_NAME, JokerOfficialDefaults.CHANNEL_NAME) ?: JokerOfficialDefaults.CHANNEL_NAME,
      whatsappChannelUrl = prefs.getString(KEY_WA_URL, JokerOfficialDefaults.CHANNEL_URL) ?: JokerOfficialDefaults.CHANNEL_URL,
      sscGroupName = prefs.getString(KEY_SSC_NAME, JokerOfficialDefaults.SSC_GROUP_NAME) ?: JokerOfficialDefaults.SSC_GROUP_NAME,
      sscGroupUrl = prefs.getString(KEY_SSC_URL, JokerOfficialDefaults.SSC_GROUP_URL) ?: JokerOfficialDefaults.SSC_GROUP_URL,
      hscGroupName = prefs.getString(KEY_HSC_NAME, JokerOfficialDefaults.HSC_GROUP_NAME) ?: JokerOfficialDefaults.HSC_GROUP_NAME,
      hscGroupUrl = prefs.getString(KEY_HSC_URL, JokerOfficialDefaults.HSC_GROUP_URL) ?: JokerOfficialDefaults.HSC_GROUP_URL,
      supportNote = prefs.getString(KEY_SUPPORT_NOTE, JokerOfficialDefaults.SUPPORT_NOTE) ?: JokerOfficialDefaults.SUPPORT_NOTE,
      helpContacts = PaperSerialization.contactsFromJson(
        prefs.getString(KEY_CONTACTS_JSON, "[]") ?: "[]"
      ),
    )
  }

  private fun generatePrintablePdf(paper: TargetPaper, destination: File) {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 44f
    val contentWidth = pageWidth - (margin * 2)

    val pdf = PdfDocument()
    var pageNumber = 1
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    var y = 54f

    val titlePaint = Paint().apply {
      color = Color.parseColor("#0A6C6B")
      textSize = 18f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val metaPaint = Paint().apply {
      color = Color.parseColor("#334155")
      textSize = 11f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sectionPaint = Paint().apply {
      color = Color.parseColor("#0F172A")
      textSize = 13.5f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint().apply {
      color = Color.parseColor("#1E293B")
      textSize = 11f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val subtlePaint = Paint().apply {
      color = Color.parseColor("#64748B")
      textSize = 10f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }
    val dividerPaint = Paint().apply {
      color = Color.parseColor("#CBD5E1")
      strokeWidth = 1f
    }

    fun ensureSpace(neededHeight: Float) {
      if (y + neededHeight > pageHeight - margin) {
        pdf.finishPage(page)
        pageNumber += 1
        page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = 50f
      }
    }

    fun drawWrappedText(text: String, paint: Paint, xOffset: Float = margin, lineSpacing: Float = 16f) {
      if (text.isBlank()) return
      val maxW = contentWidth - (xOffset - margin)
      val paragraphs = text.split("\n")
      for (paragraph in paragraphs) {
        val words = paragraph.split(" ")
        var line = StringBuilder()
        for (word in words) {
          val test = if (line.isEmpty()) word else "$line $word"
          if (paint.measureText(test) <= maxW) {
            line = StringBuilder(test)
          } else {
            ensureSpace(lineSpacing)
            canvas.drawText(line.toString(), xOffset, y, paint)
            y += lineSpacing
            line = StringBuilder(word)
          }
        }
        if (line.isNotEmpty()) {
          ensureSpace(lineSpacing)
          canvas.drawText(line.toString(), xOffset, y, paint)
          y += lineSpacing
        }
      }
    }

    drawWrappedText(paper.title, titlePaint, lineSpacing = 22f)
    y += 4f
    val metaLine = buildString {
      append("${paper.classLevel}  •  ${paper.subject}")
      if (paper.examTerm.isNotBlank()) append("  •  ${paper.examTerm}")
      if (paper.totalMarks > 0) append("  •  ${paper.totalMarks} Marks")
      if (paper.durationText.isNotBlank()) append("  •  ${paper.durationText}")
    }
    drawWrappedText(metaLine, metaPaint, lineSpacing = 16f)
    y += 6f
    canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
    y += 14f

    if (paper.instructions.isNotBlank()) {
      drawWrappedText("Instructions: ${paper.instructions}", subtlePaint, lineSpacing = 15f)
      y += 8f
    }

    paper.sections.forEach { section ->
      ensureSpace(34f)
      val heading = buildString {
        append(section.heading)
        if (section.marksLabel.isNotBlank()) append(" (${section.marksLabel})")
      }
      drawWrappedText(heading, sectionPaint, lineSpacing = 19f)
      y += 4f

      section.questions.forEachIndexed { idx, q ->
        val num = if (q.number > 0) q.number else (idx + 1).toLong()
        val marksSuffix = if (q.marks > 0) " [${q.marks} Marks]" else ""
        drawWrappedText("Q$num. ${q.text}$marksSuffix", bodyPaint, xOffset = margin + 6f)
        if (q.options.isNotEmpty()) {
          val letters = listOf("A", "B", "C", "D", "E")
          q.options.forEachIndexed { optIdx, opt ->
            val label = letters.getOrElse(optIdx) { "${optIdx + 1}" }
            drawWrappedText("($label) $opt", subtlePaint, xOffset = margin + 22f, lineSpacing = 14f)
          }
        }
        y += 5f
      }
      y += 8f
    }

    pdf.finishPage(page)
    FileOutputStream(destination).use { out ->
      pdf.writeTo(out)
    }
    pdf.close()
  }

  companion object {
    private const val PREFS_NAME = "target_papers_offline_prefs"
    private const val KEY_WA_NAME = "wa_name"
    private const val KEY_WA_URL = "wa_url"
    private const val KEY_SSC_NAME = "ssc_name"
    private const val KEY_SSC_URL = "ssc_url"
    private const val KEY_HSC_NAME = "hsc_name"
    private const val KEY_HSC_URL = "hsc_url"
    private const val KEY_SUPPORT_NOTE = "support_note"
    private const val KEY_CONTACTS_JSON = "contacts_json"
    private const val KEY_HAS_CACHED_SUPPORT = "has_cached_support"
  }
}

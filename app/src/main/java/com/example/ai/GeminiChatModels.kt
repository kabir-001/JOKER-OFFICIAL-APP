package com.example.ai

import android.graphics.Bitmap
import com.example.data.model.TargetPaper

enum class GeminiModelOption(
  val modelId: String,
  val displayName: String,
  val subtitle: String,
) {
  FLASH_3_8(
    modelId = "gemini-3.8-flash",
    displayName = "Gemini 3.8 Flash",
    subtitle = "Default • Multimodal, fast & versatile",
  ),
  FLASH_LITE_3_5(
    modelId = "gemini-3.5-flash-lite",
    displayName = "Gemini 3.5 Flash-Lite",
    subtitle = "Ultra-fast everyday Q&A & translation",
  ),
  ARGON_4(
    modelId = "gemini-4-argon",
    displayName = "Gemini 4 Argon",
    subtitle = "Deep STEM, math & coding reasoning",
  ),
  NANO_BANANA_2_1(
    modelId = "gemini-nano-banana-2.1",
    displayName = "Nano Banana 2.1",
    subtitle = "AI image generation & photo editing",
  ),
}

enum class GeminiAssistantMode(
  val label: String,
  val description: String,
) {
  GENERAL(
    label = "General AI",
    description = "Ask anything: code, writing, translation, summarizing & image/PDF analysis",
  ),
  STUDY_TUTOR(
    label = "Exam Tutor",
    description = "Step-by-step SSC & HSC solutions, numericals & Urdu/English help",
  ),
  QUIZ_GENERATOR(
    label = "Quiz Builder",
    description = "Structured JSON MCQ practice quizzes you can solve interactively",
  ),
  IMAGE_STUDIO(
    label = "Image Studio",
    description = "Create or edit diagrams & artwork using Nano Banana 2.1",
  ),
}

sealed interface GeminiAttachment {
  val id: String
  val displayName: String

  data class ImageItem(
    override val id: String,
    override val displayName: String,
    val bitmap: Bitmap,
  ) : GeminiAttachment

  data class DocumentItem(
    override val id: String,
    override val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
  ) : GeminiAttachment
}

data class QuizQuestionItem(
  val question: String,
  val options: List<String>,
  val correctIndex: Int,
  val explanation: String,
)

enum class ChatRole {
  USER,
  MODEL,
}

data class GeminiChatMessage(
  val id: String,
  val role: ChatRole,
  val text: String = "",
  val imagePreviews: List<Bitmap> = emptyList(),
  val documentNames: List<String> = emptyList(),
  val generatedImage: Bitmap? = null,
  val quizItems: List<QuizQuestionItem> = emptyList(),
  val quizTitle: String = "",
  val modelLabel: String = "",
  val isStreaming: Boolean = false,
  val isError: Boolean = false,
)

data class ActivePaperContext(
  val title: String,
  val subject: String,
  val classLevel: String,
  val summaryText: String,
) {
  companion object {
    fun fromTargetPaper(paper: TargetPaper): ActivePaperContext {
      val contentText = buildString {
        appendLine("Title: ${paper.title}")
        appendLine("Class: ${paper.classLevel} | Subject: ${paper.subject}")
        if (paper.examTerm.isNotBlank()) appendLine("Session: ${paper.examTerm}")
        if (paper.totalMarks > 0) appendLine("Total Marks: ${paper.totalMarks}")
        if (paper.instructions.isNotBlank()) appendLine("Instructions: ${paper.instructions}")
        paper.sections.forEach { sec ->
          appendLine("\n${sec.heading} (${sec.marksLabel})")
          sec.questions.forEachIndexed { idx, q ->
            val num = if (q.number > 0) q.number else (idx + 1).toLong()
            appendLine("Q$num. ${q.text}")
            if (q.options.isNotEmpty()) {
              appendLine("Options: ${q.options.joinToString(" | ")}")
            }
          }
        }
      }.take(12_000)
      return ActivePaperContext(
        title = paper.title,
        subject = paper.subject,
        classLevel = paper.classLevel,
        summaryText = contentText,
      )
    }
  }
}

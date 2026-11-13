package com.example.ai

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TargetPaper
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class GeminiChatViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext

  // Lazily initialized Gemini models per supported model ID and mode
  private val generalFlashModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.FLASH_3_8.modelId,
      systemInstruction = content { text(GENERAL_SYSTEM_PROMPT) },
    )
  }

  private val generalFlashLiteModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.FLASH_LITE_3_5.modelId,
      systemInstruction = content { text(GENERAL_SYSTEM_PROMPT) },
    )
  }

  private val generalArgonModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.ARGON_4.modelId,
      systemInstruction = content { text(GENERAL_SYSTEM_PROMPT) },
    )
  }

  private val tutorFlashModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.FLASH_3_8.modelId,
      systemInstruction = content { text(TUTOR_SYSTEM_PROMPT) },
    )
  }

  private val tutorArgonModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.ARGON_4.modelId,
      systemInstruction = content { text(TUTOR_SYSTEM_PROMPT) },
    )
  }

  private val quizJsonModel: GenerativeModel by lazy {
    val quizSchema = Schema.obj(
      properties = mapOf(
        "quizTitle" to Schema.string(description = "Short title of the practice quiz"),
        "questions" to Schema.array(
          items = Schema.obj(
            properties = mapOf(
              "question" to Schema.string(description = "Clear question text"),
              "options" to Schema.array(
                items = Schema.string(),
                description = "Exactly 4 answer choices",
              ),
              "correctIndex" to Schema.integer(description = "Zero-based index (0 to 3) of the correct option"),
              "explanation" to Schema.string(description = "Concise explanation of why the answer is correct"),
            )
          ),
          description = "List of multiple-choice questions",
        ),
      )
    )
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.FLASH_3_8.modelId,
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = quizSchema
      },
      systemInstruction = content {
        text("You are an expert board exam MCQ generator. Generate accurate 4-option MCQs with zero-based correctIndex and clear explanations.")
      },
    )
  }

  private val imageSquareModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.NANO_BANANA_2_1.modelId,
      generationConfig = generationConfig {
        responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
      },
    )
  }

  private val imageLandscapeModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GeminiModelOption.NANO_BANANA_2_1.modelId,
      generationConfig = generationConfig {
        responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
        imageConfig = ImageConfig.builder()
          .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
          .setImageSize(ImageSize.SIZE_1K)
          .build()
      },
    )
  }

  private val _selectedModel = MutableStateFlow(GeminiModelOption.FLASH_3_8)
  val selectedModel: StateFlow<GeminiModelOption> = _selectedModel.asStateFlow()

  private val _selectedMode = MutableStateFlow(GeminiAssistantMode.GENERAL)
  val selectedMode: StateFlow<GeminiAssistantMode> = _selectedMode.asStateFlow()

  private val _useLandscapeImage = MutableStateFlow(false)
  val useLandscapeImage: StateFlow<Boolean> = _useLandscapeImage.asStateFlow()

  private val _messages = MutableStateFlow<List<GeminiChatMessage>>(emptyList())
  val messages: StateFlow<List<GeminiChatMessage>> = _messages.asStateFlow()

  private val _pendingAttachments = MutableStateFlow<List<GeminiAttachment>>(emptyList())
  val pendingAttachments: StateFlow<List<GeminiAttachment>> = _pendingAttachments.asStateFlow()

  private val _activePaperContext = MutableStateFlow<ActivePaperContext?>(null)
  val activePaperContext: StateFlow<ActivePaperContext?> = _activePaperContext.asStateFlow()

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

  private val _attachmentWarning = MutableStateFlow<String?>(null)
  val attachmentWarning: StateFlow<String?> = _attachmentWarning.asStateFlow()

  private var activeJob: Job? = null
  private var lastUserPromptText: String = ""
  private var lastUserAttachments: List<GeminiAttachment> = emptyList()

  fun selectModel(option: GeminiModelOption) {
    _selectedModel.value = option
    if (option == GeminiModelOption.NANO_BANANA_2_1) {
      _selectedMode.value = GeminiAssistantMode.IMAGE_STUDIO
    } else if (_selectedMode.value == GeminiAssistantMode.IMAGE_STUDIO) {
      _selectedMode.value = GeminiAssistantMode.GENERAL
    }
  }

  fun selectMode(mode: GeminiAssistantMode) {
    _selectedMode.value = mode
    if (mode == GeminiAssistantMode.IMAGE_STUDIO) {
      _selectedModel.value = GeminiModelOption.NANO_BANANA_2_1
    } else if (_selectedModel.value == GeminiModelOption.NANO_BANANA_2_1) {
      _selectedModel.value = GeminiModelOption.FLASH_3_8
    }
  }

  fun setUseLandscapeImage(landscape: Boolean) {
    _useLandscapeImage.value = landscape
  }

  fun attachPaperContext(paper: TargetPaper) {
    _activePaperContext.value = ActivePaperContext.fromTargetPaper(paper)
    if (_selectedMode.value == GeminiAssistantMode.IMAGE_STUDIO) {
      selectMode(GeminiAssistantMode.STUDY_TUTOR)
    }
  }

  fun clearPaperContext() {
    _activePaperContext.value = null
  }

  fun dismissAttachmentWarning() {
    _attachmentWarning.value = null
  }

  fun addImageFromUri(uri: Uri) {
    viewModelScope.launch {
      val currentImages = _pendingAttachments.value.filterIsInstance<GeminiAttachment.ImageItem>()
      if (currentImages.size >= 3) {
        _attachmentWarning.value = "You can attach up to 3 images per prompt."
        return@launch
      }
      val bitmap = withContext(Dispatchers.IO) { decodeScaledBitmap(uri) }
      if (bitmap == null) {
        _attachmentWarning.value = "Could not load selected image."
        return@launch
      }
      val name = queryDisplayName(uri) ?: "Image ${currentImages.size + 1}"
      _pendingAttachments.value = _pendingAttachments.value + GeminiAttachment.ImageItem(
        id = UUID.randomUUID().toString(),
        displayName = name,
        bitmap = bitmap,
      )
    }
  }

  fun addDocumentFromUri(uri: Uri) {
    viewModelScope.launch {
      val existingDocs = _pendingAttachments.value.filterIsInstance<GeminiAttachment.DocumentItem>()
      if (existingDocs.size >= 2) {
        _attachmentWarning.value = "You can attach up to 2 documents per prompt."
        return@launch
      }
      val doc = withContext(Dispatchers.IO) { readDocumentAttachment(uri) }
      if (doc == null) {
        _attachmentWarning.value = "File must be under 18 MB and readable (PDF or text)."
        return@launch
      }
      _pendingAttachments.value = _pendingAttachments.value + doc
    }
  }

  fun removeAttachment(id: String) {
    _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id }
  }

  fun clearChat() {
    activeJob?.cancel()
    _isGenerating.value = false
    _messages.value = emptyList()
    _pendingAttachments.value = emptyList()
  }

  fun stopGeneration() {
    activeJob?.cancel()
    activeJob = null
    _isGenerating.value = false
    _messages.value = _messages.value.map { msg ->
      if (msg.isStreaming) msg.copy(isStreaming = false) else msg
    }
  }

  fun retryLastPrompt() {
    if (lastUserPromptText.isBlank() && lastUserAttachments.isEmpty()) return
    sendPrompt(rawPrompt = lastUserPromptText, overrideAttachments = lastUserAttachments)
  }

  fun sendPrompt(
    rawPrompt: String,
    overrideAttachments: List<GeminiAttachment>? = null,
    overrideMode: GeminiAssistantMode? = null,
  ) {
    val prompt = rawPrompt.trim()
    val attachments = overrideAttachments ?: _pendingAttachments.value
    if (prompt.isBlank() && attachments.isEmpty()) return
    if (_isGenerating.value) return

    if (overrideMode != null) {
      selectMode(overrideMode)
    }

    lastUserPromptText = prompt
    lastUserAttachments = attachments
    if (overrideAttachments == null) {
      _pendingAttachments.value = emptyList()
    }

    val effectivePrompt = prompt.ifBlank { "Please analyze the attached content." }
    val paperCtx = _activePaperContext.value
    val activeMode = _selectedMode.value
    val activeModel = _selectedModel.value

    val userMsg = GeminiChatMessage(
      id = UUID.randomUUID().toString(),
      role = ChatRole.USER,
      text = effectivePrompt,
      imagePreviews = attachments.filterIsInstance<GeminiAttachment.ImageItem>().map { it.bitmap },
      documentNames = attachments.filterIsInstance<GeminiAttachment.DocumentItem>().map { it.displayName },
    )
    val modelMsgId = UUID.randomUUID().toString()
    val placeholderModelMsg = GeminiChatMessage(
      id = modelMsgId,
      role = ChatRole.MODEL,
      text = "",
      modelLabel = activeModel.displayName,
      isStreaming = true,
    )

    val historyBeforeTurn = _messages.value.filter { !it.isError && it.text.isNotBlank() }
    _messages.value = _messages.value + userMsg + placeholderModelMsg
    _isGenerating.value = true

    activeJob = viewModelScope.launch {
      try {
        when (activeMode) {
          GeminiAssistantMode.IMAGE_STUDIO -> {
            runImageGeneration(
              modelMsgId = modelMsgId,
              prompt = effectivePrompt,
              attachments = attachments,
            )
          }

          GeminiAssistantMode.QUIZ_GENERATOR -> {
            runStructuredQuizGeneration(
              modelMsgId = modelMsgId,
              prompt = effectivePrompt,
              attachments = attachments,
              paperCtx = paperCtx,
            )
          }

          GeminiAssistantMode.GENERAL,
          GeminiAssistantMode.STUDY_TUTOR -> {
            runStreamingChatTurn(
              modelMsgId = modelMsgId,
              prompt = effectivePrompt,
              attachments = attachments,
              paperCtx = paperCtx,
              historyMessages = historyBeforeTurn,
              mode = activeMode,
              modelOption = activeModel,
            )
          }
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Gemini request timed out", e)
        updateMessageError(
          modelMsgId,
          "Request timed out after 60 seconds. Please check your connection and try again.",
        )
      } catch (e: QuotaExceededException) {
        Log.e(TAG, "Gemini quota exceeded", e)
        updateMessageError(
          modelMsgId,
          "Gemini quota/rate limit reached (${e.localizedMessage ?: "Blaze plan or tier limit"}). Try again shortly or switch to Gemini 3.8 Flash.",
        )
      } catch (e: CancellationException) {
        // Preserve cooperative coroutine cancellation
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Gemini request failed", e)
        updateMessageError(
          modelMsgId,
          e.localizedMessage ?: "Gemini request failed. Verify network & Firebase AI Logic provisioning.",
        )
      } finally {
        _isGenerating.value = false
        activeJob = null
      }
    }
  }

  private suspend fun runStreamingChatTurn(
    modelMsgId: String,
    prompt: String,
    attachments: List<GeminiAttachment>,
    paperCtx: ActivePaperContext?,
    historyMessages: List<GeminiChatMessage>,
    mode: GeminiAssistantMode,
    modelOption: GeminiModelOption,
  ) {
    val model = resolveChatModel(mode, modelOption)
    val historyContents: List<Content> = historyMessages.takeLast(14).map { msg ->
      val roleName = if (msg.role == ChatRole.USER) "user" else "model"
      content(roleName) { text(msg.text) }
    }
    val chat = model.startChat(history = historyContents)

    val fullPrompt = buildString {
      if (paperCtx != null) {
        appendLine("[Attached Target Paper Context: ${paperCtx.title} (${paperCtx.classLevel} - ${paperCtx.subject})]")
        appendLine(paperCtx.summaryText)
        appendLine("--- User Request ---")
      }
      append(prompt)
    }

    val userContent = content("user") {
      attachments.filterIsInstance<GeminiAttachment.ImageItem>().forEach { img ->
        image(img.bitmap)
      }
      attachments.filterIsInstance<GeminiAttachment.DocumentItem>().forEach { doc ->
        inlineData(doc.bytes, doc.mimeType)
      }
      text(fullPrompt)
    }

    val accumulated = StringBuilder()
    withTimeout(AI_TIMEOUT_MS) {
      chat.sendMessageStream(userContent).collect { chunk ->
        val chunkText = chunk.text.orEmpty()
        if (chunkText.isNotEmpty()) {
          accumulated.append(chunkText)
          updateStreamingMessage(modelMsgId, accumulated.toString(), stillStreaming = true)
        }
      }
    }

    if (accumulated.isNotBlank()) {
      updateStreamingMessage(modelMsgId, accumulated.toString(), stillStreaming = false)
    } else {
      updateMessageError(modelMsgId, "Gemini returned an empty response.")
    }
  }

  private suspend fun runStructuredQuizGeneration(
    modelMsgId: String,
    prompt: String,
    attachments: List<GeminiAttachment>,
    paperCtx: ActivePaperContext?,
  ) {
    val fullPrompt = buildString {
      if (paperCtx != null) {
        appendLine("Generate an interactive practice MCQ quiz based on this Target Paper:")
        appendLine(paperCtx.summaryText)
        appendLine("\nAdditional instruction: $prompt")
      } else {
        append("Generate a high-quality interactive MCQ practice quiz for: $prompt")
      }
    }

    val requestContent = content("user") {
      attachments.filterIsInstance<GeminiAttachment.ImageItem>().forEach { img ->
        image(img.bitmap)
      }
      attachments.filterIsInstance<GeminiAttachment.DocumentItem>().forEach { doc ->
        inlineData(doc.bytes, doc.mimeType)
      }
      text(fullPrompt)
    }

    val response = withTimeout(AI_TIMEOUT_MS) {
      quizJsonModel.generateContent(requestContent)
    }
    val jsonString = response.text ?: throw IllegalStateException("Empty quiz JSON returned.")
    val root = JSONObject(jsonString)
    val quizTitle = root.optString("quizTitle", "Practice Quiz")
    val qArray = root.optJSONArray("questions") ?: throw IllegalStateException("No quiz questions returned.")
    val items = (0 until qArray.length()).map { i ->
      val obj = qArray.getJSONObject(i)
      val optsArr = obj.optJSONArray("options")
      val opts = if (optsArr != null) {
        (0 until optsArr.length()).map { idx -> optsArr.getString(idx) }
      } else {
        emptyList()
      }
      QuizQuestionItem(
        question = obj.getString("question"),
        options = opts,
        correctIndex = obj.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
        explanation = obj.optString("explanation", ""),
      )
    }

    _messages.value = _messages.value.map { msg ->
      if (msg.id == modelMsgId) {
        msg.copy(
          text = "Tap an option on each question below to test yourself:",
          quizTitle = quizTitle,
          quizItems = items,
          modelLabel = "Gemini 3.8 Flash • Structured JSON",
          isStreaming = false,
        )
      } else {
        msg
      }
    }
  }

  private suspend fun runImageGeneration(
    modelMsgId: String,
    prompt: String,
    attachments: List<GeminiAttachment>,
  ) {
    val model = if (_useLandscapeImage.value) imageLandscapeModel else imageSquareModel
    val attachedImages = attachments.filterIsInstance<GeminiAttachment.ImageItem>()

    val requestContent = content("user") {
      attachedImages.forEach { img -> image(img.bitmap) }
      text(prompt)
    }

    val response = withTimeout(AI_TIMEOUT_MS) {
      model.generateContent(requestContent)
    }
    val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
    val generatedBitmap = parts.filterIsInstance<ImagePart>().firstOrNull()?.image
    val accompanyingText = parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }.trim()

    if (generatedBitmap == null && accompanyingText.isBlank()) {
      updateMessageError(modelMsgId, "No image was returned by Nano Banana 2.1.")
      return
    }

    _messages.value = _messages.value.map { msg ->
      if (msg.id == modelMsgId) {
        msg.copy(
          text = accompanyingText.ifBlank {
            if (attachedImages.isNotEmpty()) "Edited image generated with Nano Banana 2.1"
            else "Generated image with Nano Banana 2.1"
          },
          generatedImage = generatedBitmap,
          modelLabel = GeminiModelOption.NANO_BANANA_2_1.displayName,
          isStreaming = false,
        )
      } else {
        msg
      }
    }
  }

  fun shareGeneratedBitmap(bitmap: Bitmap) {
    viewModelScope.launch {
      val uri = withContext(Dispatchers.IO) {
        runCatching {
          val dir = File(appContext.filesDir, "ai_images").apply { mkdirs() }
          val file = File(dir, "gemini_${System.currentTimeMillis()}.png")
          FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
          }
          FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        }.getOrNull()
      }
      if (uri != null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Gemini Image").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(chooser) }
      }
    }
  }

  private fun resolveChatModel(mode: GeminiAssistantMode, option: GeminiModelOption): GenerativeModel {
    return when (mode) {
      GeminiAssistantMode.STUDY_TUTOR -> when (option) {
        GeminiModelOption.ARGON_4 -> tutorArgonModel
        else -> tutorFlashModel
      }
      else -> when (option) {
        GeminiModelOption.FLASH_LITE_3_5 -> generalFlashLiteModel
        GeminiModelOption.ARGON_4 -> generalArgonModel
        else -> generalFlashModel
      }
    }
  }

  private fun updateStreamingMessage(id: String, text: String, stillStreaming: Boolean) {
    _messages.value = _messages.value.map { msg ->
      if (msg.id == id) msg.copy(text = text, isStreaming = stillStreaming, isError = false) else msg
    }
  }

  private fun updateMessageError(id: String, errorText: String) {
    _messages.value = _messages.value.map { msg ->
      if (msg.id == id) msg.copy(text = errorText, isStreaming = false, isError = true) else msg
    }
  }

  private fun decodeScaledBitmap(uri: Uri): Bitmap? {
    return runCatching {
      val resolver = appContext.contentResolver
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
      var sampleSize = 1
      while (maxDim / sampleSize > 1400) {
        sampleSize *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
  }

  private fun readDocumentAttachment(uri: Uri): GeminiAttachment.DocumentItem? {
    return runCatching {
      val resolver = appContext.contentResolver
      val mime = resolver.getType(uri) ?: "application/pdf"
      val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
      if (bytes.isEmpty() || bytes.size > MAX_INLINE_BYTES) return null
      val name = queryDisplayName(uri) ?: "Document"
      GeminiAttachment.DocumentItem(
        id = UUID.randomUUID().toString(),
        displayName = name,
        mimeType = mime,
        bytes = bytes,
      )
    }.getOrNull()
  }

  private fun queryDisplayName(uri: Uri): String? {
    return runCatching {
      appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) cursor.getString(idx) else null
        } else {
          null
        }
      }
    }.getOrNull()
  }

  companion object {
    private const val TAG = "GeminiChatVM"
    private const val AI_TIMEOUT_MS = 60_000L
    private const val MAX_INLINE_BYTES = 18 * 1024 * 1024

    private const val GENERAL_SYSTEM_PROMPT =
      "You are Gemini AI inside the Joker Official app. Deliver accurate, clear, well-structured answers across coding, math, science, writing, translation (including English and Urdu), summarization, and multimodal image/PDF analysis."

    private const val TUTOR_SYSTEM_PROMPT =
      "You are the Joker Official SSC (IX/X) & HSC (XI/XII) Exam Tutor powered by Gemini. Explain concepts step-by-step with formulas, derivations, definitions, key board-exam marking tips, and clear Urdu/English support when requested."
  }
}

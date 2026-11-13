package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.GeminiAssistantMode
import com.example.ai.GeminiAttachment
import com.example.ai.GeminiChatViewModel
import com.example.ai.GeminiModelOption
import com.example.ui.components.ExternalLinkHelpers
import com.example.ui.theme.ChampagneGold
import java.util.Locale

@Composable
fun GeminiChatScreen(
  viewModel: GeminiChatViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
  val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
  val useLandscapeImage by viewModel.useLandscapeImage.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val attachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val activePaper by viewModel.activePaperContext.collectAsStateWithLifecycle()
  val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
  val attachmentWarning by viewModel.attachmentWarning.collectAsStateWithLifecycle()

  var promptText by remember { mutableStateOf("") }
  var showModelPicker by remember { mutableStateOf(false) }
  var speakingMessageId by remember { mutableStateOf<String?>(null) }

  // Android TextToSpeech for instant read-aloud of any Gemini answer
  var ttsReady by remember { mutableStateOf(false) }
  val tts = remember {
    var instance: TextToSpeech? = null
    instance = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        instance?.language = Locale.US
        ttsReady = true
      }
    }
    instance
  }
  DisposableEffect(Unit) {
    onDispose {
      tts.stop()
      tts.shutdown()
    }
  }

  // Zero-permission Photo Picker
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) viewModel.addImageFromUri(uri)
  }

  // Zero-permission SAF Document / PDF Picker
  val documentPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) viewModel.addDocumentFromUri(uri)
  }

  // Zero-permission Voice-to-Text via system RecognizerIntent
  val voiceInputLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val spoken = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
      if (!spoken.isNullOrBlank()) {
        promptText = if (promptText.isBlank()) spoken else "$promptText $spoken"
      }
    }
  }

  val listState = rememberLazyListState()
  LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  if (showModelPicker) {
    AlertDialog(
      onDismissRequest = { showModelPicker = false },
      title = { Text("Select Gemini Model") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          GeminiModelOption.entries.forEach { option ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (selectedModel == option) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              },
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  viewModel.selectModel(option)
                  showModelPicker = false
                },
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                RadioButton(
                  selected = selectedModel == option,
                  onClick = {
                    viewModel.selectModel(option)
                    showModelPicker = false
                  },
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(option.displayName, style = MaterialTheme.typography.titleSmall)
                  Text(
                    option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          if (selectedMode == GeminiAssistantMode.IMAGE_STUDIO) {
            HorizontalDivider()
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                  selected = !useLandscapeImage,
                  onClick = { viewModel.setUseLandscapeImage(false) },
                  label = { Text("1:1 Square") },
                )
                FilterChip(
                  selected = useLandscapeImage,
                  onClick = { viewModel.setUseLandscapeImage(true) },
                  label = { Text("16:9 Wide") },
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showModelPicker = false }) { Text("Done") }
      },
    )
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 780.dp)
        .fillMaxSize(),
    ) {
      // Header controls: Model badge + Clear + Mode selector chips
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AssistChip(
            onClick = { showModelPicker = true },
            leadingIcon = {
              Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = ChampagneGold,
                modifier = Modifier.size(16.dp),
              )
            },
            trailingIcon = {
              Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = {
              Text(
                text = selectedModel.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier.testTag("gemini_model_selector_chip"),
          )

          if (messages.isNotEmpty()) {
            TextButton(
              onClick = {
                tts.stop()
                speakingMessageId = null
                viewModel.clearChat()
              },
              modifier = Modifier.testTag("gemini_clear_chat_button"),
            ) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("New Chat")
            }
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(GeminiAssistantMode.entries) { mode ->
            FilterChip(
              selected = selectedMode == mode,
              onClick = { viewModel.selectMode(mode) },
              label = { Text(mode.label) },
              leadingIcon = {
                Icon(
                  imageVector = when (mode) {
                    GeminiAssistantMode.GENERAL -> Icons.Rounded.AutoAwesome
                    GeminiAssistantMode.STUDY_TUTOR -> Icons.Rounded.Psychology
                    GeminiAssistantMode.QUIZ_GENERATOR -> Icons.Rounded.Quiz
                    GeminiAssistantMode.IMAGE_STUDIO -> Icons.Rounded.Image
                  },
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("gemini_mode_${mode.name.lowercase()}"),
            )
          }
        }

        if (activePaper != null) {
          ActivePaperBanner(
            paper = activePaper!!,
            onClear = viewModel::clearPaperContext,
            onQuickAction = { actionPrompt, targetMode ->
              viewModel.sendPrompt(actionPrompt, overrideMode = targetMode)
            },
          )
        }

        if (attachmentWarning != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = attachmentWarning!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = viewModel::dismissAttachmentWarning,
                modifier = Modifier.size(48.dp),
              ) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
              }
            }
          }
        }
      }

      // Main messages area or Welcome Starter Grid
      Box(modifier = Modifier.weight(1f)) {
        if (messages.isEmpty()) {
          GeminiWelcomeEmptyState(
            currentMode = selectedMode,
            onStarterClick = { starterPrompt, targetMode ->
              viewModel.sendPrompt(starterPrompt, overrideMode = targetMode)
            },
          )
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(messages, key = { it.id }) { msg ->
              GeminiMessageBubble(
                message = msg,
                isSpeaking = speakingMessageId == msg.id,
                onCopy = {
                  ExternalLinkHelpers.copyToClipboard(context, "Gemini Reply", msg.text)
                },
                onToggleSpeak = {
                  if (speakingMessageId == msg.id) {
                    tts.stop()
                    speakingMessageId = null
                  } else if (ttsReady && msg.text.isNotBlank()) {
                    tts.stop()
                    speakingMessageId = msg.id
                    tts.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, msg.id)
                  }
                },
                onShareImage = { bmp -> viewModel.shareGeneratedBitmap(bmp) },
                onRetry = viewModel::retryLastPrompt,
              )
            }
          }
        }
      }

      // Attachment previews + Input composer
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(attachments, key = { it.id }) { att ->
                when (att) {
                  is GeminiAttachment.ImageItem -> {
                    Box(modifier = Modifier.size(64.dp)) {
                      Image(
                        bitmap = att.bitmap.asImageBitmap(),
                        contentDescription = att.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .fillMaxSize()
                          .clip(RoundedCornerShape(10.dp)),
                      )
                      IconButton(
                        onClick = { viewModel.removeAttachment(att.id) },
                        modifier = Modifier
                          .align(Alignment.TopEnd)
                          .size(22.dp)
                          .clip(RoundedCornerShape(999.dp))
                          .background(Color.Black.copy(alpha = 0.65f)),
                      ) {
                        Icon(
                          Icons.Rounded.Close,
                          contentDescription = "Remove attachment",
                          tint = Color.White,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }
                  }

                  is GeminiAttachment.DocumentItem -> {
                    Surface(
                      shape = RoundedCornerShape(10.dp),
                      color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                          att.displayName,
                          style = MaterialTheme.typography.labelSmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                          Icons.Rounded.Close,
                          contentDescription = "Remove document",
                          modifier = Modifier
                            .size(16.dp)
                            .clickable { viewModel.removeAttachment(att.id) },
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            IconButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("gemini_attach_image_button"),
            ) {
              Icon(Icons.Rounded.Image, contentDescription = "Attach image")
            }

            IconButton(
              onClick = {
                documentPickerLauncher.launch(arrayOf("application/pdf", "text/*"))
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("gemini_attach_file_button"),
            ) {
              Icon(Icons.Rounded.AttachFile, contentDescription = "Attach PDF or text document")
            }

            IconButton(
              onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                  putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                  putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question...")
                }
                runCatching { voiceInputLauncher.launch(intent) }
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("gemini_voice_input_button"),
            ) {
              Icon(Icons.Rounded.Mic, contentDescription = "Voice prompt input")
            }

            OutlinedTextField(
              value = promptText,
              onValueChange = { promptText = it },
              placeholder = {
                Text(
                  text = when (selectedMode) {
                    GeminiAssistantMode.IMAGE_STUDIO -> "Describe an image to create or edit..."
                    GeminiAssistantMode.QUIZ_GENERATOR -> "Topic for MCQ quiz (e.g. Newton's Laws)..."
                    GeminiAssistantMode.STUDY_TUTOR -> "Ask SSC/HSC question or attach photo..."
                    GeminiAssistantMode.GENERAL -> "Ask Gemini anything..."
                  },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              maxLines = 4,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("gemini_prompt_input"),
            )

            Button(
              onClick = {
                if (isGenerating) {
                  viewModel.stopGeneration()
                } else {
                  val toSend = promptText
                  promptText = ""
                  viewModel.sendPrompt(toSend)
                }
              },
              enabled = isGenerating || promptText.isNotBlank() || attachments.isNotEmpty(),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(0.dp),
              modifier = Modifier
                .size(48.dp)
                .testTag("gemini_send_button"),
            ) {
              Icon(
                imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                contentDescription = if (isGenerating) "Stop generating" else "Send prompt",
              )
            }
          }
        }
      }
    }
  }
}

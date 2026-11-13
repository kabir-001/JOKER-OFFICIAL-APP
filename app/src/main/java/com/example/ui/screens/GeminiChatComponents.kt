package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.ActivePaperContext
import com.example.ai.ChatRole
import com.example.ai.GeminiAssistantMode
import com.example.ai.GeminiChatMessage
import com.example.ai.QuizQuestionItem
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.WhatsAppGreen

@Composable
internal fun ActivePaperBanner(
  paper: ActivePaperContext,
  onClear: () -> Unit,
  onQuickAction: (String, GeminiAssistantMode) -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Linked Target Paper Context",
            style = MaterialTheme.typography.labelSmall,
            color = ChampagneGold,
          )
          Text(
            text = "${paper.title} (${paper.classLevel})",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Rounded.Close, contentDescription = "Unlink paper")
        }
      }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          AssistChip(
            onClick = { onQuickAction("Summarize all key topics and important questions from this paper.", GeminiAssistantMode.STUDY_TUTOR) },
            label = { Text("Summarize Paper") },
          )
        }
        item {
          AssistChip(
            onClick = { onQuickAction("Provide step-by-step solved answers for the questions in this paper.", GeminiAssistantMode.STUDY_TUTOR) },
            label = { Text("Solve Questions") },
          )
        }
        item {
          AssistChip(
            onClick = { onQuickAction("Create a 5-question practice MCQ quiz based on this paper.", GeminiAssistantMode.QUIZ_GENERATOR) },
            label = { Text("5-MCQ Practice Quiz") },
          )
        }
      }
    }
  }
}

@Composable
internal fun GeminiWelcomeEmptyState(
  currentMode: GeminiAssistantMode,
  onStarterClick: (String, GeminiAssistantMode) -> Unit,
) {
  val starters = remember {
    listOf(
      Triple(
        Icons.Rounded.Psychology,
        "Solve SSC/HSC Numericals",
        "Explain Newton's 2nd law of motion with 2 solved board exam numerical examples." to GeminiAssistantMode.STUDY_TUTOR,
      ),
      Triple(
        Icons.Rounded.Quiz,
        "Interactive MCQ Quiz",
        "Physics Class 10 Current Electricity & Ohm's Law (5 MCQs)" to GeminiAssistantMode.QUIZ_GENERATOR,
      ),
      Triple(
        Icons.Rounded.Image,
        "Generate Study Diagram / Art",
        "Create a clean, labeled educational scientific diagram style illustration of a glowing solar system." to GeminiAssistantMode.IMAGE_STUDIO,
      ),
      Triple(
        Icons.Rounded.AutoAwesome,
        "Translate & Summarize",
        "Explain Photosynthesis clearly in both simple English and Roman Urdu bullet points." to GeminiAssistantMode.GENERAL,
      ),
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ChampagneGold)
            Text("Joker Official • Gemini AI", style = MaterialTheme.typography.titleLarge)
          }
          Text(
            text = currentMode.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Attach photos or PDFs, speak via microphone, listen via voice playback, generate interactive quizzes, or create images.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    items(starters) { (icon, title, promptAndMode) ->
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .clickable { onStarterClick(promptAndMode.first, promptAndMode.second) },
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
              promptAndMode.first,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun GeminiMessageBubble(
  message: GeminiChatMessage,
  isSpeaking: Boolean,
  onCopy: () -> Unit,
  onToggleSpeak: () -> Unit,
  onShareImage: (Bitmap) -> Unit,
  onRetry: () -> Unit,
) {
  val isUser = message.role == ChatRole.USER

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Card(
      shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp,
      ),
      colors = CardDefaults.cardColors(
        containerColor = when {
          message.isError -> MaterialTheme.colorScheme.errorContainer
          isUser -> MaterialTheme.colorScheme.primaryContainer
          else -> MaterialTheme.colorScheme.surface
        },
      ),
      border = if (!isUser && !message.isError) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
      } else {
        null
      },
      modifier = Modifier.widthIn(max = 620.dp),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (!isUser && message.modelLabel.isNotBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = ChampagneGold,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = message.modelLabel,
              style = MaterialTheme.typography.labelSmall,
              color = ChampagneGold,
            )
          }
        }

        if (message.imagePreviews.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(message.imagePreviews) { bmp ->
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(110.dp)
                  .clip(RoundedCornerShape(12.dp)),
              )
            }
          }
        }

        if (message.documentNames.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            message.documentNames.forEach { docName ->
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                  Text(docName, style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }

        if (message.isStreaming && message.text.isBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              Text(
                text = "Gemini is thinking...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
        } else if (message.text.isNotBlank()) {
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
              message.isError -> MaterialTheme.colorScheme.onErrorContainer
              isUser -> MaterialTheme.colorScheme.onPrimaryContainer
              else -> MaterialTheme.colorScheme.onSurface
            },
          )
        }

        if (message.generatedImage != null) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
              bitmap = message.generatedImage.asImageBitmap(),
              contentDescription = "Gemini generated image",
              contentScale = ContentScale.FillWidth,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
            )
            OutlinedButton(
              onClick = { onShareImage(message.generatedImage) },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            ) {
              Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share / Save Image")
            }
          }
        }

        if (message.quizItems.isNotEmpty()) {
          InteractiveQuizCard(
            title = message.quizTitle,
            items = message.quizItems,
          )
        }

        if (message.isError) {
          Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = RoundedCornerShape(10.dp),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
          }
        } else if (!isUser && !message.isStreaming && message.text.isNotBlank()) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onCopy,
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy reply",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = onToggleSpeak,
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (isSpeaking) "Stop reading aloud" else "Read aloud",
                modifier = Modifier.size(20.dp),
                tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InteractiveQuizCard(
  title: String,
  items: List<QuizQuestionItem>,
) {
  val selectedAnswers = remember(items) { mutableStateMapOf<Int, Int>() }
  val score = selectedAnswers.count { (qIdx, optIdx) ->
    items.getOrNull(qIdx)?.correctIndex == optIdx
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title.ifBlank { "Practice Quiz" },
        style = MaterialTheme.typography.titleMedium,
        color = ChampagneGold,
        modifier = Modifier.weight(1f),
      )
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Text(
          text = "Score: $score / ${items.size}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }

    items.forEachIndexed { qIdx, q ->
      val chosen = selectedAnswers[qIdx]
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Q${qIdx + 1}. ${q.question}",
            style = MaterialTheme.typography.titleSmall,
          )
          q.options.forEachIndexed { oIdx, optionText ->
            val isChosen = chosen == oIdx
            val isCorrect = oIdx == q.correctIndex
            val bgColor = when {
              chosen == null -> MaterialTheme.colorScheme.surface
              isCorrect -> WhatsAppGreen.copy(alpha = 0.18f)
              isChosen -> MaterialTheme.colorScheme.errorContainer
              else -> MaterialTheme.colorScheme.surface
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = bgColor,
              border = BorderStroke(
                1.dp,
                if (chosen != null && isCorrect) WhatsAppGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = chosen == null) { selectedAnswers[qIdx] = oIdx },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = optionText,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.weight(1f),
                )
                if (chosen != null && isCorrect) {
                  Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Correct",
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
          if (chosen != null && q.explanation.isNotBlank()) {
            Text(
              text = "Explanation: ${q.explanation}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

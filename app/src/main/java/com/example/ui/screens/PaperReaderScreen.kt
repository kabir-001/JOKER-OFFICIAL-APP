package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TargetPaper
import com.example.ui.components.ExternalLinkHelpers
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperReaderScreen(
  paper: TargetPaper,
  isDownloaded: Boolean,
  onBack: () -> Unit,
  onDownloadOffline: () -> Unit,
  onExportPdf: suspend () -> android.net.Uri?,
  onAskGemini: () -> Unit = {},
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var textScale by remember { mutableFloatStateOf(1f) }
  var showAnswers by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = paper.subject,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("reader_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = onAskGemini,
            modifier = Modifier.testTag("reader_ask_gemini_icon"),
          ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Ask Gemini AI about this paper")
          }
          IconButton(onClick = { textScale = (textScale - 0.1f).coerceAtLeast(0.85f) }) {
            Icon(Icons.Rounded.TextDecrease, contentDescription = "Decrease font size")
          }
          IconButton(onClick = { textScale = (textScale + 0.1f).coerceAtMost(1.45f) }) {
            Icon(Icons.Rounded.TextIncrease, contentDescription = "Increase font size")
          }
          IconButton(onClick = { showAnswers = !showAnswers }) {
            Icon(
              imageVector = if (showAnswers) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = if (showAnswers) "Hide answers" else "Show answers",
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 760.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    text = paper.classLevel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
                if (paper.examTerm.isNotBlank()) {
                  Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                  ) {
                    Text(
                      text = paper.examTerm,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                  }
                }
              }

              Text(
                text = paper.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )

              val summary = listOfNotNull(
                if (paper.totalMarks > 0) "Max Marks: ${paper.totalMarks}" else null,
                paper.durationText.takeIf { it.isNotBlank() }?.let { "Time: $it" },
              ).joinToString("  •  ")
              if (summary.isNotBlank()) {
                Text(
                  text = summary,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                )
              }

              if (paper.instructions.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Text(
                  text = "Instructions: ${paper.instructions}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = onDownloadOffline,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("reader_download_button"),
                ) {
                  Icon(
                    imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (isDownloaded) "Saved Offline" else "Download Offline")
                }

                OutlinedButton(
                  onClick = {
                    scope.launch {
                      val uri = onExportPdf()
                      if (uri != null) {
                        ExternalLinkHelpers.sharePdfUri(context, uri, paper.title)
                      }
                    }
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Share PDF")
                }
              }

              OutlinedButton(
                onClick = onAskGemini,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("reader_ask_gemini_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ask Gemini AI to Explain / Quiz This Paper")
              }

              if (paper.externalPdfUrl.isNotBlank()) {
                OutlinedButton(
                  onClick = { ExternalLinkHelpers.openExternalUrl(context, paper.externalPdfUrl) },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Open Attached Resource Link")
                }
              }
            }
          }
        }

        items(paper.sections) { section ->
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp),
                  )
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = section.heading,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f),
                )
                if (section.marksLabel.isNotBlank()) {
                  Text(
                    text = section.marksLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }

              section.questions.forEachIndexed { idx, q ->
                val qNum = if (q.number > 0) q.number else (idx + 1).toLong()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "Q$qNum. ${q.text}",
                      fontSize = (15 * textScale).sp,
                      lineHeight = (22 * textScale).sp,
                      fontWeight = FontWeight.Medium,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f),
                    )
                    if (q.marks > 0) {
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "[${q.marks}m]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                      )
                    }
                  }

                  if (q.options.isNotEmpty()) {
                    val letters = listOf("A", "B", "C", "D", "E")
                    Column(
                      modifier = Modifier.padding(start = 16.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      q.options.forEachIndexed { optIdx, option ->
                        val letter = letters.getOrElse(optIdx) { "${optIdx + 1}" }
                        Text(
                          text = "($letter) $option",
                          fontSize = (14 * textScale).sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }

                  if (showAnswers && q.answer.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = WhatsAppGreen.copy(alpha = 0.12f),
                      modifier = Modifier.padding(start = 12.dp),
                    ) {
                      Text(
                        text = "Answer: ${q.answer}",
                        fontSize = (13.5 * textScale).sp,
                        color = WhatsAppGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      )
                    }
                  }
                }
                if (idx < section.questions.lastIndex) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
              }
            }
          }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
      }
    }
  }
}

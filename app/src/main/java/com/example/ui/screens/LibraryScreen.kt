package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SupportConfig
import com.example.data.model.TargetPaper
import com.example.ui.components.ExternalLinkHelpers
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.UiState

@Composable
fun LibraryScreen(
  papersState: UiState<List<TargetPaper>>,
  allPublishedPapers: List<TargetPaper>,
  downloadedIds: Set<String>,
  downloadingIds: Set<String>,
  searchQuery: String,
  selectedClassFilter: String,
  selectedSubjectFilter: String,
  supportConfig: SupportConfig,
  onSearchQueryChange: (String) -> Unit,
  onClassFilterChange: (String) -> Unit,
  onSubjectFilterChange: (String) -> Unit,
  onOpenPaper: (TargetPaper) -> Unit,
  onDownloadPaper: (TargetPaper) -> Unit,
  onNavigateToHelp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val classOptions = remember(allPublishedPapers) {
    val dynamic = allPublishedPapers.map { it.classLevel }.filter { it.isNotBlank() }
    val presets = listOf("SSC-I (9th)", "SSC-II (10th)", "HSC-I (11th)", "HSC-II (12th)")
    listOf("All") + (dynamic + presets).distinct().sorted()
  }
  val subjectOptions = remember(allPublishedPapers) {
    listOf("All") + allPublishedPapers.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted()
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        JokerHeroBanner(
          totalPapers = allPublishedPapers.size,
          offlineCount = downloadedIds.size,
          supportConfig = supportConfig,
          onJoinChannel = {
            ExternalLinkHelpers.openExternalUrl(context, supportConfig.whatsappChannelUrl)
          },
          onJoinSscGroup = {
            ExternalLinkHelpers.openExternalUrl(context, supportConfig.sscGroupUrl)
          },
          onJoinHscGroup = {
            ExternalLinkHelpers.openExternalUrl(context, supportConfig.hscGroupUrl)
          },
          onOpenHelp = onNavigateToHelp,
        )
      }

      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search SSC / HSC papers by title or subject...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
          trailingIcon = {
            if (searchQuery.isNotBlank()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("library_search_input"),
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Class / Board Session",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(classOptions) { item ->
              FilterChip(
                selected = selectedClassFilter == item,
                onClick = { onClassFilterChange(item) },
                label = { Text(item) },
              )
            }
          }
        }
      }

      if (subjectOptions.size > 1) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Subject",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(subjectOptions) { item ->
                FilterChip(
                  selected = selectedSubjectFilter == item,
                  onClick = { onSubjectFilterChange(item) },
                  label = { Text(item) },
                )
              }
            }
          }
        }
      }

      when (papersState) {
        is UiState.Loading -> {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }

        is UiState.Error -> {
          item {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = papersState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }

        is UiState.Success -> {
          if (papersState.data.isEmpty()) {
            item {
              EmptyStateCard(
                icon = Icons.Rounded.AutoStories,
                title = stringResource(R.string.empty_papers_title),
                subtitle = stringResource(R.string.empty_papers_subtitle),
              )
            }
          } else {
            items(papersState.data, key = { it.id }) { paper ->
              TargetPaperCard(
                paper = paper,
                isDownloaded = paper.id in downloadedIds,
                isDownloading = paper.id in downloadingIds,
                onClick = { onOpenPaper(paper) },
                onDownloadClick = { onDownloadPaper(paper) },
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun JokerHeroBanner(
  totalPapers: Int,
  offlineCount: Int,
  supportConfig: SupportConfig,
  onJoinChannel: () -> Unit,
  onJoinSscGroup: () -> Unit,
  onJoinHscGroup: () -> Unit,
  onOpenHelp: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_joker_hero_banner),
        contentDescription = null,
        modifier = Modifier
          .fillMaxWidth()
          .height(216.dp),
        contentScale = ContentScale.Crop,
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.verticalGradient(
              listOf(
                Color(0x880B0F17),
                Color(0xEE0B0F17),
              )
            )
          )
      )
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = ChampagneGold,
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = "JOKER OFFICIAL",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF1F1600),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Text(
            text = "$totalPapers Papers • $offlineCount Offline",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
          )
        }

        Text(
          text = "Target Papers & Exam Sessions",
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            onClick = onJoinChannel,
            enabled = supportConfig.whatsappChannelUrl.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = WhatsAppGreen,
              contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("hero_whatsapp_channel_button"),
          ) {
            Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("WA Channel", maxLines = 1, style = MaterialTheme.typography.labelMedium)
          }

          OutlinedButton(
            onClick = onOpenHelp,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ChampagneGold),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("hero_help_numbers_button"),
          ) {
            Icon(Icons.Rounded.SupportAgent, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Help Numbers", maxLines = 1, style = MaterialTheme.typography.labelMedium)
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (supportConfig.sscGroupUrl.isNotBlank()) {
            OutlinedButton(
              onClick = onJoinSscGroup,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("hero_ssc_group_button"),
            ) {
              Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("SSC (IX/X) Group", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
          }
          if (supportConfig.hscGroupUrl.isNotBlank()) {
            OutlinedButton(
              onClick = onJoinHscGroup,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("hero_hsc_group_button"),
            ) {
              Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("HSC (XI/XII) Group", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}

@Composable
fun TargetPaperCard(
  paper: TargetPaper,
  isDownloaded: Boolean,
  isDownloading: Boolean,
  onClick: () -> Unit,
  onDownloadClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onClick)
      .testTag("paper_card_${paper.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f),
        ) {
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
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = paper.subject,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        if (isDownloaded) {
          Surface(
            color = WhatsAppGreen.copy(alpha = 0.16f),
            shape = RoundedCornerShape(999.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = WhatsAppGreen,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = stringResource(R.string.label_offline_ready),
                style = MaterialTheme.typography.labelSmall,
                color = WhatsAppGreen,
              )
            }
          }
        }
      }

      Text(
        text = paper.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (paper.totalMarks > 0) {
          MetaBadge(icon = Icons.Rounded.Star, text = "${paper.totalMarks} Marks")
        }
        if (paper.durationText.isNotBlank()) {
          MetaBadge(icon = Icons.Rounded.Schedule, text = paper.durationText)
        }
        MetaBadge(
          icon = Icons.Rounded.AutoStories,
          text = "${paper.sections.size} Sec • ${paper.totalQuestionCount} Qs",
        )
      }

      if (paper.examTerm.isNotBlank()) {
        Text(
          text = paper.examTerm,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onClick,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text("Read Paper")
        }
        Button(
          onClick = onDownloadClick,
          enabled = !isDownloading,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDownloaded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            contentColor = if (isDownloaded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("download_paper_${paper.id}"),
        ) {
          if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isDownloaded) "Update Offline" else "Download")
          }
        }
      }
    }
  }
}

@Composable
private fun MetaBadge(
  icon: ImageVector,
  text: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun EmptyStateCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(28.dp),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

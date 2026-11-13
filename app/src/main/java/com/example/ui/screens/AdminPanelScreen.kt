package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.HelpContact
import com.example.data.model.JokerOfficialDefaults
import com.example.data.model.SupportConfig
import com.example.data.model.TargetPaper
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
  adminPapersState: UiState<List<TargetPaper>>,
  supportConfig: SupportConfig,
  isSaving: Boolean,
  onCreatePaper: () -> Unit,
  onUploadPdfPaper: () -> Unit = {},
  onEditPaper: (TargetPaper) -> Unit,
  onDuplicatePaper: (TargetPaper) -> Unit,
  onToggleStatus: (TargetPaper) -> Unit,
  onDeletePaper: (String) -> Unit,
  onSaveSupportConfig: (SupportConfig) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSubTab by remember { mutableIntStateOf(0) }
  var pendingDeletePaper by remember { mutableStateOf<TargetPaper?>(null) }

  if (pendingDeletePaper != null) {
    val target = pendingDeletePaper!!
    AlertDialog(
      onDismissRequest = { pendingDeletePaper = null },
      title = { Text("Delete Target Paper?") },
      text = { Text("Are you sure you want to permanently delete \"${target.title}\"?") },
      confirmButton = {
        Button(
          onClick = {
            onDeletePaper(target.id)
            pendingDeletePaper = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeletePaper = null }) {
          Text("Cancel")
        }
      },
    )
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize(),
    ) {
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        SegmentedButton(
          selected = selectedSubTab == 0,
          onClick = { selectedSubTab = 0 },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
          Text("Target Papers")
        }
        SegmentedButton(
          selected = selectedSubTab == 1,
          onClick = { selectedSubTab = 1 },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
          Text("WhatsApp & Numbers")
        }
      }

      if (selectedSubTab == 0) {
        AdminPapersListSection(
          adminPapersState = adminPapersState,
          onCreatePaper = onCreatePaper,
          onUploadPdfPaper = onUploadPdfPaper,
          onEditPaper = onEditPaper,
          onDuplicatePaper = onDuplicatePaper,
          onToggleStatus = onToggleStatus,
          onRequestDelete = { pendingDeletePaper = it },
        )
      } else {
        AdminSupportSettingsSection(
          supportConfig = supportConfig,
          isSaving = isSaving,
          onSaveSupportConfig = onSaveSupportConfig,
        )
      }
    }
  }
}

@Composable
private fun AdminPapersListSection(
  adminPapersState: UiState<List<TargetPaper>>,
  onCreatePaper: () -> Unit,
  onUploadPdfPaper: () -> Unit,
  onEditPaper: (TargetPaper) -> Unit,
  onDuplicatePaper: (TargetPaper) -> Unit,
  onToggleStatus: (TargetPaper) -> Unit,
  onRequestDelete: (TargetPaper) -> Unit,
) {
  var adminSearch by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All") }
  val filterOptions = listOf("All", "Published", "Drafts", "SSC", "HSC")

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = onUploadPdfPaper,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("admin_upload_pdf_button"),
        ) {
          Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Upload PDF Target Paper", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
          onClick = onCreatePaper,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("admin_create_paper_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Build Structured Question Paper", style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    when (adminPapersState) {
      is UiState.Loading -> {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      }

      is UiState.Error -> {
        item {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Text(
              text = adminPapersState.message,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }

      is UiState.Success -> {
        val allPapers = adminPapersState.data
        val publishedCount = allPapers.count { it.isPublished }
        val draftCount = allPapers.count { !it.isPublished }
        val sscCount = allPapers.count {
          it.classLevel.contains("SSC", ignoreCase = true) ||
            it.classLevel.contains("9") ||
            it.classLevel.contains("10")
        }
        val hscCount = allPapers.count {
          it.classLevel.contains("HSC", ignoreCase = true) ||
            it.classLevel.contains("11") ||
            it.classLevel.contains("12")
        }

        if (allPapers.isNotEmpty()) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              AdminStatTile(label = "Published", count = publishedCount, modifier = Modifier.weight(1f))
              AdminStatTile(label = "Drafts", count = draftCount, modifier = Modifier.weight(1f))
              AdminStatTile(label = "SSC", count = sscCount, modifier = Modifier.weight(1f))
              AdminStatTile(label = "HSC", count = hscCount, modifier = Modifier.weight(1f))
            }
          }

          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = adminSearch,
                onValueChange = { adminSearch = it },
                placeholder = { Text("Filter uploaded papers...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                  if (adminSearch.isNotBlank()) {
                    IconButton(onClick = { adminSearch = "" }) {
                      Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterOptions) { option ->
                  FilterChip(
                    selected = selectedFilter == option,
                    onClick = { selectedFilter = option },
                    label = { Text(option) },
                  )
                }
              }
            }
          }
        }

        val q = adminSearch.trim().lowercase()
        val filtered = allPapers.filter { paper ->
          val matchesChip = when (selectedFilter) {
            "Published" -> paper.isPublished
            "Drafts" -> !paper.isPublished
            "SSC" -> paper.classLevel.contains("SSC", ignoreCase = true) ||
              paper.classLevel.contains("9") ||
              paper.classLevel.contains("10")
            "HSC" -> paper.classLevel.contains("HSC", ignoreCase = true) ||
              paper.classLevel.contains("11") ||
              paper.classLevel.contains("12")
            else -> true
          }
          val matchesQuery = q.isBlank() ||
            paper.title.lowercase().contains(q) ||
            paper.subject.lowercase().contains(q) ||
            paper.classLevel.lowercase().contains(q)
          matchesChip && matchesQuery
        }

        if (filtered.isEmpty()) {
          item {
            EmptyStateCard(
              icon = Icons.AutoMirrored.Rounded.NoteAdd,
              title = if (allPapers.isEmpty()) "No Uploaded Papers Yet" else "No Matching Papers",
              subtitle = if (allPapers.isEmpty()) {
                "Tap Create & Upload Target Paper above to build your first exam target paper."
              } else {
                "Try clearing the filter or search query."
              },
            )
          }
        } else {
          items(filtered, key = { it.id }) { paper ->
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier.fillMaxWidth(),
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
                  Text(
                    text = "${paper.classLevel} • ${paper.subject}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (paper.isPublished) {
                      WhatsAppGreen.copy(alpha = 0.14f)
                    } else {
                      MaterialTheme.colorScheme.secondaryContainer
                    },
                  ) {
                    Text(
                      text = if (paper.isPublished) "Published" else "Draft",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (paper.isPublished) WhatsAppGreen else MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }

                Text(
                  text = paper.title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )

                Text(
                  text = buildString {
                    if (paper.hasEmbeddedPdf || paper.pdfFileName.isNotBlank()) {
                      append("PDF: ${paper.pdfFileName.ifBlank { "Attached" }} • ")
                    }
                    append("${paper.sections.size} sections • ${paper.totalQuestionCount} questions • ${paper.totalMarks} marks")
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  OutlinedButton(
                    onClick = { onEditPaper(paper) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp),
                  ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                  }
                  OutlinedButton(
                    onClick = { onToggleStatus(paper) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp),
                  ) {
                    Icon(
                      imageVector = if (paper.isPublished) Icons.Rounded.VisibilityOff else Icons.Rounded.Public,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (paper.isPublished) "Hide" else "Publish")
                  }
                  IconButton(
                    onClick = { onDuplicatePaper(paper) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("duplicate_paper_${paper.id}"),
                  ) {
                    Icon(
                      Icons.Rounded.ContentCopy,
                      contentDescription = "Duplicate paper as draft",
                      tint = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  IconButton(
                    onClick = { onRequestDelete(paper) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      Icons.Rounded.DeleteOutline,
                      contentDescription = "Delete paper",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }
  }
}

@Composable
private fun AdminStatTile(
  label: String,
  count: Int,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AdminSupportSettingsSection(
  supportConfig: SupportConfig,
  isSaving: Boolean,
  onSaveSupportConfig: (SupportConfig) -> Unit,
) {
  var channelName by remember { mutableStateOf(supportConfig.whatsappChannelName) }
  var channelUrl by remember { mutableStateOf(supportConfig.whatsappChannelUrl) }
  var sscGroupName by remember { mutableStateOf(supportConfig.sscGroupName) }
  var sscGroupUrl by remember { mutableStateOf(supportConfig.sscGroupUrl) }
  var hscGroupName by remember { mutableStateOf(supportConfig.hscGroupName) }
  var hscGroupUrl by remember { mutableStateOf(supportConfig.hscGroupUrl) }
  var supportNote by remember { mutableStateOf(supportConfig.supportNote) }
  val contacts = remember {
    mutableStateListOf<HelpContact>().apply { addAll(supportConfig.helpContacts) }
  }

  LaunchedEffect(supportConfig) {
    channelName = supportConfig.whatsappChannelName
    channelUrl = supportConfig.whatsappChannelUrl
    sscGroupName = supportConfig.sscGroupName
    sscGroupUrl = supportConfig.sscGroupUrl
    hscGroupName = supportConfig.hscGroupName
    hscGroupUrl = supportConfig.hscGroupUrl
    supportNote = supportConfig.supportNote
    contacts.clear()
    contacts.addAll(supportConfig.helpContacts)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      OutlinedButton(
        onClick = {
          val defaults = JokerOfficialDefaults.defaultSupportConfig()
          channelName = defaults.whatsappChannelName
          channelUrl = defaults.whatsappChannelUrl
          sscGroupName = defaults.sscGroupName
          sscGroupUrl = defaults.sscGroupUrl
          hscGroupName = defaults.hscGroupName
          hscGroupUrl = defaults.hscGroupUrl
          supportNote = defaults.supportNote
          contacts.clear()
          contacts.addAll(defaults.helpContacts)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("admin_load_joker_defaults_button"),
      ) {
        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Load Joker Official Defaults")
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.Forum, contentDescription = null, tint = WhatsAppGreen)
            Text("Official WhatsApp Channel", style = MaterialTheme.typography.titleMedium)
          }

          OutlinedTextField(
            value = channelName,
            onValueChange = { channelName = it },
            label = { Text("Channel Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
            value = channelUrl,
            onValueChange = { channelUrl = it },
            label = { Text("WhatsApp Channel URL") },
            placeholder = { Text("https://whatsapp.com/channel/...") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_whatsapp_url_input"),
          )

          OutlinedTextField(
            value = supportNote,
            onValueChange = { supportNote = it },
            label = { Text("Help Message Note (Optional)") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text("SSC & HSC Session Groups", style = MaterialTheme.typography.titleMedium)
          }

          OutlinedTextField(
            value = sscGroupName,
            onValueChange = { sscGroupName = it },
            label = { Text("SSC (IX/X) Group Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
            value = sscGroupUrl,
            onValueChange = { sscGroupUrl = it },
            label = { Text("SSC (IX/X) WhatsApp Link") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_ssc_group_url_input"),
          )

          OutlinedTextField(
            value = hscGroupName,
            onValueChange = { hscGroupName = it },
            label = { Text("HSC (XI/XII) Group Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
            value = hscGroupUrl,
            onValueChange = { hscGroupUrl = it },
            label = { Text("HSC (XI/XII) WhatsApp Link") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_hsc_group_url_input"),
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Clickable Help Numbers", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
          onClick = {
            if (contacts.size < 15) {
              contacts.add(HelpContact(label = "", phoneNumber = "", allowCall = true, allowWhatsApp = true))
            }
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(48.dp),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add Number")
        }
      }
    }

    items(contacts.size) { index ->
      val contact = contacts[index]
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Contact #${index + 1}", style = MaterialTheme.typography.labelLarge)
            IconButton(
              onClick = { contacts.removeAt(index) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Remove contact",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }

          OutlinedTextField(
            value = contact.label,
            onValueChange = { contacts[index] = contact.copy(label = it) },
            label = { Text("Label (e.g. Joker Official Helpline)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
            value = contact.phoneNumber,
            onValueChange = { contacts[index] = contact.copy(phoneNumber = it) },
            label = { Text("Phone / WhatsApp Number (with country code)") },
            placeholder = { Text("+923143581887") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Switch(
                checked = contact.allowCall,
                onCheckedChange = { contacts[index] = contact.copy(allowCall = it) },
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Call Button", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Switch(
                checked = contact.allowWhatsApp,
                onCheckedChange = { contacts[index] = contact.copy(allowWhatsApp = it) },
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("WhatsApp Button", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }

    item {
      Button(
        onClick = {
          onSaveSupportConfig(
            SupportConfig(
              isPublic = true,
              whatsappChannelName = channelName.trim(),
              whatsappChannelUrl = channelUrl.trim(),
              sscGroupName = sscGroupName.trim(),
              sscGroupUrl = sscGroupUrl.trim(),
              hscGroupName = hscGroupName.trim(),
              hscGroupUrl = hscGroupUrl.trim(),
              supportNote = supportNote.trim(),
              helpContacts = contacts.toList().filter { it.phoneNumber.isNotBlank() },
            )
          )
        },
        enabled = !isSaving,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("admin_save_support_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save WhatsApp & Help Numbers", style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    item { Spacer(modifier = Modifier.height(20.dp)) }
  }
}

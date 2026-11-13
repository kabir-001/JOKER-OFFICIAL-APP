package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.PaperQuestion
import com.example.data.model.PaperSection
import com.example.data.model.TargetPaper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPaperEditorScreen(
  initialPaper: TargetPaper?,
  isSaving: Boolean,
  onBack: () -> Unit,
  onSavePaper: (TargetPaper) -> Unit,
) {
  BackHandler(onBack = onBack)

  val classSuggestions = listOf("SSC-I (9th)", "SSC-II (10th)", "HSC-I (11th)", "HSC-II (12th)", "MDCAT / ECAT", "University")
  val subjectSuggestions = listOf("Physics", "Chemistry", "Mathematics", "Biology", "English", "Computer Science", "Urdu")

  var title by remember { mutableStateOf(initialPaper?.title ?: "") }
  var classLevel by remember { mutableStateOf(initialPaper?.classLevel ?: "SSC-II (10th)") }
  var subject by remember { mutableStateOf(initialPaper?.subject ?: "Physics") }
  var examTerm by remember { mutableStateOf(initialPaper?.examTerm ?: "Annual Board 2026") }
  var totalMarksText by remember { mutableStateOf((initialPaper?.totalMarks ?: 75L).toString()) }
  var durationText by remember { mutableStateOf(initialPaper?.durationText ?: "3 Hours") }
  var instructions by remember { mutableStateOf(initialPaper?.instructions ?: "Attempt all MCQs. Attempt required questions from Section B and Section C.") }
  var externalPdfUrl by remember { mutableStateOf(initialPaper?.externalPdfUrl ?: "") }
  var publishImmediately by remember {
    mutableStateOf(initialPaper?.status != TargetPaper.STATUS_DRAFT)
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  val sections = remember {
    mutableStateListOf<PaperSection>().apply {
      if (initialPaper != null && initialPaper.sections.isNotEmpty()) {
        addAll(initialPaper.sections)
      } else {
        add(
          PaperSection(
            heading = "Section A: Important Questions",
            marksLabel = "30 Marks",
            questions = listOf(PaperQuestion(number = 1L, text = "", marks = 2L)),
          )
        )
      }
    }
  }

  var bulkPasteSectionIndex by remember { mutableStateOf<Int?>(null) }
  var bulkPasteText by remember { mutableStateOf("") }

  if (bulkPasteSectionIndex != null) {
    val sIdx = bulkPasteSectionIndex!!
    AlertDialog(
      onDismissRequest = { bulkPasteSectionIndex = null },
      title = { Text("Paste Questions (1 per line)") },
      text = {
        OutlinedTextField(
          value = bulkPasteText,
          onValueChange = { bulkPasteText = it },
          label = { Text("Paste lines here") },
          placeholder = { Text("Define Ohm's law.\nState Newton's second law.\nDerive kinetic energy formula.") },
          minLines = 5,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val lines = bulkPasteText
              .lineSequence()
              .map { it.trim().replace(Regex("^\\d+[.)]\\s*"), "") }
              .filter { it.isNotBlank() }
              .toList()
            if (lines.isNotEmpty() && sIdx in sections.indices) {
              val current = sections[sIdx]
              val cleanedExisting = current.questions.filter { it.text.isNotBlank() }
              val startNum = cleanedExisting.size + 1
              val appended = lines.mapIndexed { offset, qText ->
                PaperQuestion(number = (startNum + offset).toLong(), text = qText, marks = 0L)
              }
              sections[sIdx] = current.copy(questions = cleanedExisting + appended)
            }
            bulkPasteText = ""
            bulkPasteSectionIndex = null
          },
        ) {
          Text("Import")
        }
      },
      dismissButton = {
        TextButton(onClick = { bulkPasteSectionIndex = null }) {
          Text("Cancel")
        }
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (initialPaper == null) "Build Target Paper" else "Edit Target Paper") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text("Paper Details", style = MaterialTheme.typography.titleMedium)

              OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Target Paper Title *") },
                placeholder = { Text("e.g. Physics Class 10 Guess Paper 2026") },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("editor_title_input"),
              )

              Text("Class / Level *", style = MaterialTheme.typography.labelMedium)
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(classSuggestions) { preset ->
                  FilterChip(
                    selected = classLevel == preset,
                    onClick = { classLevel = preset },
                    label = { Text(preset) },
                  )
                }
              }
              OutlinedTextField(
                value = classLevel,
                onValueChange = { classLevel = it },
                label = { Text("Class / Grade") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Text("Subject *", style = MaterialTheme.typography.labelMedium)
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(subjectSuggestions) { preset ->
                  FilterChip(
                    selected = subject == preset,
                    onClick = { subject = preset },
                    label = { Text(preset) },
                  )
                }
              }
              OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              OutlinedTextField(
                value = examTerm,
                onValueChange = { examTerm = it },
                label = { Text("Exam Session / Board") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedTextField(
                  value = totalMarksText,
                  onValueChange = { totalMarksText = it.filter { ch -> ch.isDigit() } },
                  label = { Text("Total Marks") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                  value = durationText,
                  onValueChange = { durationText = it },
                  label = { Text("Duration") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }

              OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Paper Instructions") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
              )

              OutlinedTextField(
                value = externalPdfUrl,
                onValueChange = { externalPdfUrl = it },
                label = { Text("Optional External PDF / Drive Link") },
                placeholder = { Text("https://...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Publish Immediately", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = if (publishImmediately) "Visible to all app users" else "Saved privately as draft",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = publishImmediately,
                  onCheckedChange = { publishImmediately = it },
                )
              }
            }
          }
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Paper Sections & Questions", style = MaterialTheme.typography.titleMedium)
              OutlinedButton(
                onClick = {
                  if (sections.size < 20) {
                    val letter = ('A' + sections.size)
                    sections.add(
                      PaperSection(
                        heading = "Section $letter: Short / Long Questions",
                        marksLabel = "",
                        questions = listOf(PaperQuestion(number = 1L, text = "", marks = 0L)),
                      )
                    )
                  }
                },
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Section")
              }
            }

            OutlinedButton(
              onClick = {
                sections.clear()
                sections.addAll(
                  listOf(
                    PaperSection(
                      heading = "Section A: Multiple Choice Questions (MCQs)",
                      marksLabel = "15 Marks",
                      questions = listOf(
                        PaperQuestion(
                          number = 1L,
                          text = "Sample MCQ — replace with your question",
                          marks = 1L,
                          options = listOf("Option A", "Option B", "Option C", "Option D"),
                          answer = "Option A",
                        )
                      ),
                    ),
                    PaperSection(
                      heading = "Section B: Short-Answer Questions",
                      marksLabel = "36 Marks",
                      questions = listOf(
                        PaperQuestion(number = 1L, text = "", marks = 4L),
                      ),
                    ),
                    PaperSection(
                      heading = "Section C: Descriptive / Long Questions",
                      marksLabel = "24 Marks",
                      questions = listOf(
                        PaperQuestion(number = 1L, text = "", marks = 8L),
                      ),
                    ),
                  )
                )
                if (totalMarksText.isBlank() || totalMarksText == "0") totalMarksText = "75"
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("editor_load_board_pattern_button"),
            ) {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Load SSC/HSC Board Pattern (Sec A / B / C)")
            }
          }
        }

        items(sections.size) { sIdx ->
          val section = sections[sIdx]
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Section ${sIdx + 1}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  TextButton(
                    onClick = {
                      bulkPasteText = ""
                      bulkPasteSectionIndex = sIdx
                    },
                  ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bulk Paste")
                  }
                  if (sections.size > 1) {
                    IconButton(
                      onClick = { sections.removeAt(sIdx) },
                      modifier = Modifier.size(48.dp),
                    ) {
                      Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove section",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
              }

              OutlinedTextField(
                value = section.heading,
                onValueChange = { sections[sIdx] = section.copy(heading = it) },
                label = { Text("Section Heading") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              OutlinedTextField(
                value = section.marksLabel,
                onValueChange = { sections[sIdx] = section.copy(marksLabel = it) },
                label = { Text("Marks Label (e.g. 15 Marks)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

              section.questions.forEachIndexed { qIdx, q ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text("Question ${qIdx + 1}", style = MaterialTheme.typography.labelLarge)
                    if (section.questions.size > 1) {
                      IconButton(
                        onClick = {
                          val updated = section.questions.toMutableList().apply { removeAt(qIdx) }
                          sections[sIdx] = section.copy(questions = updated)
                        },
                        modifier = Modifier.size(48.dp),
                      ) {
                        Icon(
                          Icons.Rounded.DeleteOutline,
                          contentDescription = "Remove question",
                          tint = MaterialTheme.colorScheme.error,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }

                  OutlinedTextField(
                    value = q.text,
                    onValueChange = { newText ->
                      val updated = section.questions.toMutableList().apply {
                        this[qIdx] = q.copy(text = newText, number = (qIdx + 1).toLong())
                      }
                      sections[sIdx] = section.copy(questions = updated)
                    },
                    label = { Text("Question Text") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    OutlinedTextField(
                      value = if (q.marks > 0) q.marks.toString() else "",
                      onValueChange = { m ->
                        val digits = m.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        val updated = section.questions.toMutableList().apply {
                          this[qIdx] = q.copy(marks = digits)
                        }
                        sections[sIdx] = section.copy(questions = updated)
                      },
                      label = { Text("Marks") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      modifier = Modifier.weight(0.35f),
                    )
                    OutlinedTextField(
                      value = q.answer,
                      onValueChange = { ans ->
                        val updated = section.questions.toMutableList().apply {
                          this[qIdx] = q.copy(answer = ans)
                        }
                        sections[sIdx] = section.copy(questions = updated)
                      },
                      label = { Text("Answer / Key (Optional)") },
                      singleLine = true,
                      modifier = Modifier.weight(0.65f),
                    )
                  }

                  OutlinedTextField(
                    value = q.options.joinToString(" | "),
                    onValueChange = { opts ->
                      val parsed = if (opts.isBlank()) {
                        emptyList()
                      } else {
                        opts.split("|").map { it.trim() }.filter { it.isNotBlank() }
                      }
                      val updated = section.questions.toMutableList().apply {
                        this[qIdx] = q.copy(options = parsed)
                      }
                      sections[sIdx] = section.copy(questions = updated)
                    },
                    label = { Text("MCQ Options separated by | (Optional)") },
                    placeholder = { Text("Ampere | Coulomb | Volt | Ohm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }

              OutlinedButton(
                onClick = {
                  val updated = section.questions + PaperQuestion(
                    number = (section.questions.size + 1).toLong(),
                    text = "",
                    marks = 0L,
                  )
                  sections[sIdx] = section.copy(questions = updated)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Question to Section ${sIdx + 1}")
              }
            }
          }
        }

        if (validationError != null) {
          item {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = validationError!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }

        item {
          Button(
            onClick = {
              val cleanTitle = title.trim()
              if (cleanTitle.length < 2) {
                validationError = "Please enter a valid target paper title."
                return@Button
              }
              if (classLevel.isBlank() || subject.isBlank()) {
                validationError = "Class and Subject are required."
                return@Button
              }
              val cleanedSections = sections.mapNotNull { sec ->
                val cleanQuestions = sec.questions.filter { it.text.isNotBlank() }.mapIndexed { i, q ->
                  q.copy(number = (i + 1).toLong())
                }
                if (sec.heading.isBlank() && cleanQuestions.isEmpty()) null
                else sec.copy(
                  heading = sec.heading.ifBlank { "Section" },
                  questions = cleanQuestions,
                )
              }
              if (cleanedSections.isEmpty()) {
                validationError = "Add at least one section or question before uploading."
                return@Button
              }
              validationError = null
              onSavePaper(
                TargetPaper(
                  id = initialPaper?.id ?: "",
                  title = cleanTitle,
                  subject = subject.trim(),
                  classLevel = classLevel.trim(),
                  examTerm = examTerm.trim(),
                  totalMarks = totalMarksText.toLongOrNull() ?: 0L,
                  durationText = durationText.trim(),
                  instructions = instructions.trim(),
                  externalPdfUrl = externalPdfUrl.trim(),
                  sections = cleanedSections,
                  status = if (publishImmediately) TargetPaper.STATUS_PUBLISHED else TargetPaper.STATUS_DRAFT,
                  createdByUid = initialPaper?.createdByUid ?: "",
                  createdAt = initialPaper?.createdAt,
                  updatedAt = initialPaper?.updatedAt,
                )
              )
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("editor_upload_button"),
          ) {
            if (isSaving) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (publishImmediately) "Upload & Publish Paper" else "Save as Draft",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
      }
    }
  }
}

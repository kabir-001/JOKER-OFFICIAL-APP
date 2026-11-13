package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PaperQuestion
import com.example.data.model.PaperSection
import com.example.data.model.TargetPaper
import com.example.data.remote.TargetPapersRepository
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_INLINE_PDF_BYTES = 580_000
private const val MAX_DESCRIPTION_CHARS = 1500

data class SelectedPdfMetadata(
  val uri: Uri,
  val fileName: String,
  val sizeBytes: Long,
  val pageCount: Int,
  val base64Payload: String,
  val firstPageThumbnail: Bitmap?,
  val fitsInlineFirestore: Boolean,
)

fun isAdminEmailAuthorized(
  userEmail: String?,
  isEmailVerified: Boolean,
  allowedAdminEmails: Set<String> = TargetPapersRepository.BOOTSTRAPPED_ADMIN_EMAILS,
): Boolean {
  val normalized = userEmail?.trim()?.lowercase() ?: return false
  return isEmailVerified && normalized in allowedAdminEmails
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUploadScreen(
  userEmail: String?,
  isEmailVerified: Boolean,
  isSaving: Boolean,
  onBack: () -> Unit,
  onUploadPaper: (TargetPaper) -> Unit,
  allowedAdminEmails: Set<String> = TargetPapersRepository.BOOTSTRAPPED_ADMIN_EMAILS,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val isAuthorized = remember(userEmail, isEmailVerified, allowedAdminEmails) {
    isAdminEmailAuthorized(
      userEmail = userEmail,
      isEmailVerified = isEmailVerified,
      allowedAdminEmails = allowedAdminEmails,
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text("Upload PDF Target Paper") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("admin_upload_back_button"),
          ) {
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
      if (!isAuthorized) {
        AdminAccessRestrictedCard(
          userEmail = userEmail,
          allowedAdminEmails = allowedAdminEmails,
          onBack = onBack,
          modifier = Modifier
            .widthIn(max = 560.dp)
            .padding(24.dp),
        )
      } else {
        AdminUploadFormContent(
          verifiedEmail = userEmail.orEmpty(),
          isSaving = isSaving,
          onUploadPaper = onUploadPaper,
        )
      }
    }
  }
}

@Composable
private fun AdminAccessRestrictedCard(
  userEmail: String?,
  allowedAdminEmails: Set<String>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = modifier.testTag("admin_upload_access_denied"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.size(64.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Admin access restricted",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(32.dp),
          )
        }
      }

      Text(
        text = "Admin Access Required",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )

      Text(
        text = "Only verified Joker Official administrator accounts (${allowedAdminEmails.size} authorized emails) can upload PDF target papers.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = "Current signed-in account:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = userEmail?.takeIf { it.isNotBlank() } ?: "Not signed in",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Button(
        onClick = onBack,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("admin_upload_restricted_back_button"),
      ) {
        Text("Return to App")
      }
    }
  }
}

@Composable
private fun AdminUploadFormContent(
  verifiedEmail: String,
  isSaving: Boolean,
  onUploadPaper: (TargetPaper) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val classPresets = listOf("SSC-I (9th)", "SSC-II (10th)", "HSC-I (11th)", "HSC-II (12th)", "MDCAT / ECAT")
  val subjectPresets = listOf("Physics", "Chemistry", "Mathematics", "Biology", "English", "Computer Science", "Urdu")

  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var classLevel by remember { mutableStateOf("SSC-II (10th)") }
  var subject by remember { mutableStateOf("Physics") }
  var examTerm by remember { mutableStateOf("Annual Board 2026") }
  var totalMarksText by remember { mutableStateOf("75") }
  var durationText by remember { mutableStateOf("3 Hours") }
  var externalPdfUrl by remember { mutableStateOf("") }
  var publishImmediately by remember { mutableStateOf(true) }

  var selectedPdf by remember { mutableStateOf<SelectedPdfMetadata?>(null) }
  var isReadingPdf by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val pdfPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    errorMessage = null
    isReadingPdf = true
    scope.launch {
      val result = loadPdfMetadata(context, uri)
      isReadingPdf = false
      result.fold(
        onSuccess = { meta ->
          selectedPdf = meta
          if (title.isBlank()) {
            title = meta.fileName
              .removeSuffix(".pdf")
              .removeSuffix(".PDF")
              .replace('_', ' ')
              .replace('-', ' ')
              .trim()
              .take(140)
          }
        },
        onFailure = { err ->
          errorMessage = err.localizedMessage ?: "Unable to read the selected PDF file."
        },
      )
    }
  }

  Column(
    modifier = Modifier
      .widthIn(max = 760.dp)
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WhatsAppGreen.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.4f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("admin_upload_verified_badge"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = "Verified Admin",
            tint = WhatsAppGreen,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Authorized Admin Verified",
              style = MaterialTheme.typography.labelLarge,
              color = WhatsAppGreen,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = verifiedEmail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.35f)),
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
            Icon(
              imageVector = Icons.Rounded.PictureAsPdf,
              contentDescription = null,
              tint = ChampagneGold,
            )
            Text(
              text = "PDF Target Paper File",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }

          Text(
            text = "Pick a PDF target paper from your device. PDFs up to 580 KB are stored directly in the cloud for 1-tap offline download by students; larger PDFs can be linked via Drive URL below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          OutlinedButton(
            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
            enabled = !isReadingPdf && !isSaving,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("admin_upload_pick_pdf_button"),
          ) {
            if (isReadingPdf) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Reading PDF...")
            } else {
              Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (selectedPdf == null) "Choose PDF File" else "Replace PDF File")
            }
          }

          selectedPdf?.let { pdf ->
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
              border = BorderStroke(
                1.dp,
                if (pdf.fitsInlineFirestore) WhatsAppGreen.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_upload_selected_pdf_card"),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  if (pdf.firstPageThumbnail != null) {
                    Image(
                      bitmap = pdf.firstPageThumbnail.asImageBitmap(),
                      contentDescription = "Selected PDF first page preview",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(width = 58.dp, height = 78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    )
                  } else {
                    Surface(
                      shape = RoundedCornerShape(10.dp),
                      color = MaterialTheme.colorScheme.primaryContainer,
                      modifier = Modifier.size(52.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Rounded.PictureAsPdf,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                  }

                  Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                      text = pdf.fileName,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${formatFileSize(pdf.sizeBytes)} • ${pdf.pageCount} page(s)",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = if (pdf.fitsInlineFirestore) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (pdf.fitsInlineFirestore) WhatsAppGreen else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                      )
                      Text(
                        text = if (pdf.fitsInlineFirestore) {
                          "Ready for direct offline PDF distribution"
                        } else {
                          "Large PDF (> 580 KB): add Drive/PDF URL below"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pdf.fitsInlineFirestore) WhatsAppGreen else MaterialTheme.colorScheme.tertiary,
                      )
                    }
                  }

                  IconButton(
                    onClick = { selectedPdf = null },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "Remove selected PDF",
                    )
                  }
                }
              }
            }
          }

          OutlinedTextField(
            value = externalPdfUrl,
            onValueChange = { externalPdfUrl = it },
            label = { Text("Optional External PDF / Google Drive Link") },
            placeholder = { Text("https://drive.google.com/...") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_upload_external_url_input"),
          )
        }
      }

    Card(
        shape = RoundedCornerShape(20.dp),
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
            Icon(
              imageVector = Icons.Rounded.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Target Paper Description & Details",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }

          OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(140) },
            label = { Text("Paper Title *") },
            placeholder = { Text("e.g. HSC-II Physics Solved Target Paper 2026") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_upload_title_input"),
          )

          OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= MAX_DESCRIPTION_CHARS) description = it },
            label = { Text("Paper Description & Study Notes *") },
            placeholder = {
              Text("Describe chapters covered, important MCQs/numericals, marking scheme, and student instructions...")
            },
            minLines = 4,
            maxLines = 7,
            supportingText = { Text("${description.length} / $MAX_DESCRIPTION_CHARS") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_upload_description_input"),
          )

          Text("Class / Session *", style = MaterialTheme.typography.labelMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(classPresets) { preset ->
              FilterChip(
                selected = classLevel == preset,
                onClick = { classLevel = preset },
                label = { Text(preset) },
              )
            }
          }
          OutlinedTextField(
            value = classLevel,
            onValueChange = { classLevel = it.take(40) },
            label = { Text("Class / Level") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Text("Subject *", style = MaterialTheme.typography.labelMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(subjectPresets) { preset ->
              FilterChip(
                selected = subject == preset,
                onClick = { subject = preset },
                label = { Text(preset) },
              )
            }
          }
          OutlinedTextField(
            value = subject,
            onValueChange = { subject = it.take(60) },
            label = { Text("Subject Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = totalMarksText,
              onValueChange = { totalMarksText = it.filter { ch -> ch.isDigit() }.take(4) },
              label = { Text("Total Marks") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = durationText,
              onValueChange = { durationText = it.take(40) },
              label = { Text("Duration") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }

          OutlinedTextField(
            value = examTerm,
            onValueChange = { examTerm = it.take(80) },
            label = { Text("Exam Session / Board") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Publish to Students Immediately", style = MaterialTheme.typography.titleSmall)
              Text(
                text = if (publishImmediately) "Appears instantly in Target Papers Library" else "Saved privately as draft",
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

    if (errorMessage != null) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = errorMessage!!,
          color = MaterialTheme.colorScheme.onErrorContainer,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(14.dp),
        )
      }
    }

    Button(
        onClick = {
          val cleanTitle = title.trim()
          val cleanDesc = description.trim()
          val cleanUrl = externalPdfUrl.trim()
          val pdf = selectedPdf

          when {
            cleanTitle.length < 2 -> {
              errorMessage = "Please enter a paper title (at least 2 characters)."
            }
            cleanDesc.isBlank() -> {
              errorMessage = "Please enter a description for this target paper."
            }
            pdf == null && cleanUrl.isBlank() -> {
              errorMessage = "Please select a PDF target paper file or provide an external PDF link."
            }
            pdf != null && !pdf.fitsInlineFirestore && cleanUrl.isBlank() -> {
              errorMessage = "This PDF is larger than 580 KB for direct cloud embedding. Please paste a Google Drive / PDF link above."
            }
            classLevel.isBlank() || subject.isBlank() -> {
              errorMessage = "Class and Subject are required."
            }
            else -> {
              errorMessage = null
              val summaryLines = buildString {
                append(cleanDesc)
                if (pdf != null) {
                  append("\n\nAttached PDF: ${pdf.fileName} (${formatFileSize(pdf.sizeBytes)}, ${pdf.pageCount} pages)")
                }
              }
              val paper = TargetPaper(
                title = cleanTitle,
                subject = subject.trim(),
                classLevel = classLevel.trim(),
                examTerm = examTerm.trim(),
                totalMarks = totalMarksText.toLongOrNull() ?: 0L,
                durationText = durationText.trim(),
                instructions = cleanDesc,
                externalPdfUrl = cleanUrl,
                pdfFileName = pdf?.fileName.orEmpty(),
                pdfSizeBytes = pdf?.sizeBytes ?: 0L,
                pdfPageCount = (pdf?.pageCount ?: 0).toLong(),
                pdfBase64 = if (pdf?.fitsInlineFirestore == true) pdf.base64Payload else "",
                sections = listOf(
                  PaperSection(
                    heading = if (pdf != null) "PDF Paper Overview" else "Target Paper Description",
                    marksLabel = totalMarksText.takeIf { it.isNotBlank() }?.let { "$it Marks" } ?: "",
                    questions = listOf(
                      PaperQuestion(
                        number = 1L,
                        text = summaryLines,
                        marks = totalMarksText.toLongOrNull() ?: 0L,
                      )
                    ),
                  )
                ),
                status = if (publishImmediately) TargetPaper.STATUS_PUBLISHED else TargetPaper.STATUS_DRAFT,
              )
              onUploadPaper(paper)
            }
          }
        },
        enabled = !isSaving && !isReadingPdf,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("admin_upload_submit_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Uploading Target Paper...")
        } else {
          Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (publishImmediately) "Upload & Publish PDF Paper" else "Save PDF Paper as Draft",
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

private suspend fun loadPdfMetadata(context: Context, uri: Uri): Result<SelectedPdfMetadata> =
  withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      var fileName = "target_paper.pdf"
      var reportedSize = 0L

      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
          if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
        }
      }

      val mime = resolver.getType(uri).orEmpty()
      require(mime.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)) {
        "Please select a valid .pdf document."
      }

      val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Could not open selected PDF file.")
      val actualSize = bytes.size.toLong().coerceAtLeast(reportedSize)
      require(bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte()) {
        "Selected file does not appear to be a valid PDF document."
      }

      var pageCount = 0
      var thumbnail: Bitmap? = null
      resolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          pageCount = renderer.pageCount
          if (pageCount > 0) {
            renderer.openPage(0).use { page ->
              val scale = (240f / page.width.coerceAtLeast(1)).coerceAtMost(2f)
              val bmpW = (page.width * scale).toInt().coerceAtLeast(120)
              val bmpH = (page.height * scale).toInt().coerceAtLeast(160)
              val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(AndroidColor.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              thumbnail = bmp
            }
          }
        }
      }

      val fitsInline = bytes.size <= MAX_INLINE_PDF_BYTES
      val base64 = if (fitsInline) Base64.encodeToString(bytes, Base64.NO_WRAP) else ""

      SelectedPdfMetadata(
        uri = uri,
        fileName = fileName,
        sizeBytes = actualSize,
        pageCount = pageCount,
        base64Payload = base64,
        firstPageThumbnail = thumbnail,
        fitsInlineFirestore = fitsInline,
      )
    }
  }

private fun formatFileSize(bytes: Long): String {
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format("%.1f KB", kb)
  return String.format("%.2f MB", kb / 1024.0)
}

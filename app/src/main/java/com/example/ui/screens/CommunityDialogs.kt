package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CommunityRoom
import com.example.data.model.DecryptedStatus
import com.example.data.model.UsernameRecord
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.WhatsAppGreen

@Composable
fun ClaimUsernameDialog(
  initialUsername: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var handle by remember { mutableStateOf(initialUsername) }
  var bio by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Rounded.AlternateEmail, contentDescription = null, tint = WhatsAppGreen)
        Text("Claim @Username")
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "No phone numbers are used here. Pick a unique username so only people who know your @username can message you privately.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = handle,
          onValueChange = { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
          label = { Text("Username (e.g. ali_ssc26)") },
          prefix = { Text("@") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("claim_username_input"),
        )
        OutlinedTextField(
          value = bio,
          onValueChange = { bio = it.take(140) },
          label = { Text("Class / Note (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(handle, bio) },
        enabled = !isBusy && handle.length >= 3,
        modifier = Modifier.testTag("claim_username_save_button"),
      ) {
        if (isBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        else Text("Save Username")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun StartDmByUsernameDialog(
  lookupResult: UsernameRecord?,
  lookupError: String?,
  isBusy: Boolean,
  onSearch: (String) -> Unit,
  onStartChat: (UsernameRecord) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New E2EE Private Chat") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Enter an exact @username to start an end-to-end encrypted chat. Phone numbers are never shared.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it.lowercase().removePrefix("@").take(24) },
            label = { Text("Username") },
            prefix = { Text("@") },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("lookup_username_input"),
          )
          IconButton(
            onClick = { onSearch(query) },
            enabled = !isBusy && query.length >= 3,
            modifier = Modifier
              .size(48.dp)
              .testTag("lookup_username_button"),
          ) {
            if (isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Search, contentDescription = "Find username")
          }
        }

        if (!lookupError.isNullOrBlank()) {
          Text(
            text = lookupError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (lookupResult != null) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.5f)),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                Text(
                  text = "@${lookupResult.username}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text = "Key ID: ${lookupResult.keyFingerprint}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
              )
              if (lookupResult.bio.isNotBlank()) {
                Text(
                  text = lookupResult.bio,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (lookupResult != null) {
        Button(
          onClick = { onStartChat(lookupResult) },
          modifier = Modifier.testTag("start_dm_chat_button"),
        ) {
          Text("Start Encrypted Chat")
        }
      } else {
        Button(
          onClick = { onSearch(query) },
          enabled = !isBusy && query.length >= 3,
        ) {
          Text("Search")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}

@Composable
fun CreateRoomDialog(
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onCreate: (title: String, desc: String, roomType: String, category: String, adminsOnly: Boolean) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var roomType by remember { mutableStateOf(CommunityRoom.TYPE_GROUP) }
  var category by remember { mutableStateOf("SSC") }
  var adminsOnly by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create E2EE Group / Channel") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = roomType == CommunityRoom.TYPE_GROUP,
            onClick = {
              roomType = CommunityRoom.TYPE_GROUP
              adminsOnly = false
            },
            label = { Text("Study Group") },
          )
          FilterChip(
            selected = roomType == CommunityRoom.TYPE_CHANNEL,
            onClick = {
              roomType = CommunityRoom.TYPE_CHANNEL
              adminsOnly = true
            },
            label = { Text("Broadcast Channel") },
          )
        }
        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(80) },
          label = { Text("Title (e.g. HSC Physics Target Room)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it.take(300) },
          label = { Text("Description") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("Official", "SSC", "HSC", "General").forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat) },
            )
          }
        }
        if (roomType == CommunityRoom.TYPE_GROUP) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Admins Only Messaging", style = MaterialTheme.typography.bodySmall)
            Switch(checked = adminsOnly, onCheckedChange = { adminsOnly = it })
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(title, description, roomType, category, adminsOnly) },
        enabled = !isBusy && title.trim().length >= 2,
      ) {
        Text("Create Room")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun PostStatusDialog(
  isAdmin: Boolean,
  hasContacts: Boolean,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onPost: (text: String, imageUri: Uri?, accentHex: String, isPublicStatus: Boolean) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
  var accentHex by remember { mutableStateOf("#064E3B") }
  var isPublicStatus by remember { mutableStateOf(isAdmin || !hasContacts) }

  val photoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> selectedImageUri = uri }

  val palette = listOf(
    "#064E3B" to Color(0xFF064E3B),
    "#3F2E0A" to Color(0xFF3F2E0A),
    "#1E3A8A" to Color(0xFF1E3A8A),
    "#4C1D95" to Color(0xFF4C1D95),
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Post E2EE 24h Status") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = text,
          onValueChange = { text = it.take(500) },
          label = { Text("Status update or announcement...") },
          maxLines = 4,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("status_text_input"),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          palette.forEach { (hex, col) ->
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(col)
                .border(
                  width = if (accentHex == hex) 2.5.dp else 1.dp,
                  color = if (accentHex == hex) ChampagneGold else Color.White.copy(alpha = 0.4f),
                  shape = CircleShape,
                )
                .clickable { accentHex = hex },
            )
          }
          Spacer(modifier = Modifier.weight(1f))
          OutlinedButton(
            onClick = {
              photoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (selectedImageUri != null) "Photo ✓" else "Photo")
          }
        }
        Text(
          text = "Audience (End-to-End Encrypted):",
          style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = isPublicStatus,
            onClick = { isPublicStatus = true },
            label = { Text(if (isAdmin) "Official Broadcast" else "Anonymous Study") },
          )
          if (hasContacts) {
            FilterChip(
              selected = !isPublicStatus,
              onClick = { isPublicStatus = false },
              label = { Text("My @Contacts") },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onPost(text, selectedImageUri, accentHex, isPublicStatus) },
        enabled = !isBusy && (text.isNotBlank() || selectedImageUri != null),
        modifier = Modifier.testTag("post_status_confirm_button"),
      ) {
        if (isBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        else Text("Share Encrypted")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun StatusViewerDialog(
  status: DecryptedStatus,
  lowDataMode: Boolean,
  isAdmin: Boolean,
  decodeImage: (String) -> Bitmap?,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  var revealedLowDataImage by remember(status.raw.id) { mutableStateOf(!lowDataMode) }
  val bgColor = remember(status.raw.accentHex) {
    runCatching { Color(android.graphics.Color.parseColor(status.raw.accentHex)) }
      .getOrDefault(Color(0xFF064E3B))
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = bgColor,
      border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.5f)),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(Icons.Rounded.Lock, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(15.dp))
              Text(
                text = status.raw.authorLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              text = "End-to-End Encrypted • 24h Status",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.78f),
            )
          }
          if (status.isMine || isAdmin) {
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete status", tint = Color.White)
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close status", tint = Color.White)
          }
        }

        if (status.imageBase64.isNotBlank()) {
          if (revealedLowDataImage) {
            val bmp = remember(status.imageBase64) { decodeImage(status.imageBase64) }
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Status photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 320.dp)
                  .clip(RoundedCornerShape(16.dp)),
              )
            }
          } else {
            OutlinedButton(
              onClick = { revealedLowDataImage = true },
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            ) {
              Text(
                text = "Low-Data Mode: Tap to decrypt photo (${(status.raw.approxBytes / 1024).coerceAtLeast(1)} KB)",
                color = Color.White,
              )
            }
          }
        }

        if (status.text.isNotBlank()) {
          Text(
            text = status.text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            Text(
              text = "${status.raw.viewCount} views (viewer identities hidden)",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.8f),
            )
          }
        }
      }
    }
  }
}

@Composable
fun E2eeVerificationDialog(
  title: String,
  fingerprintOrSafetyNumber: String,
  subtitle: String,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhatsAppGreen)
        Text(title)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Cipher Suite: ECDH P-256 + HKDF-SHA256 + AES-256-GCM",
              style = MaterialTheme.typography.labelSmall,
              color = WhatsAppGreen,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = fingerprintOrSafetyNumber,
              style = MaterialTheme.typography.titleSmall,
              fontFamily = FontFamily.Monospace,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Verified") }
    },
  )
}

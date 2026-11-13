package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DecryptedDmMessage
import com.example.data.model.DecryptedRoom
import com.example.data.model.DecryptedRoomMessage
import com.example.data.model.UsernameRecord
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateDmChatScreen(
  peer: UsernameRecord,
  safetyNumber: String,
  messages: List<DecryptedDmMessage>,
  lowDataMode: Boolean,
  isBusy: Boolean,
  decodeImage: (String) -> Bitmap?,
  onBack: () -> Unit,
  onSendMessage: (String, Uri?) -> Unit,
) {
  BackHandler(onBack = onBack)

  var text by remember { mutableStateOf("") }
  var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
  var showVerify by remember { mutableStateOf(false) }

  val photoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> pendingPhoto = uri }

  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  if (showVerify) {
    E2eeVerificationDialog(
      title = "Private Chat Safety Number",
      fingerprintOrSafetyNumber = safetyNumber,
      subtitle = "Messages with @${peer.username} are encrypted on your device before leaving it. Only your two devices hold the ECDH keys.",
      onDismiss = { showVerify = false },
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text(
              text = "@${peer.username}",
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "End-to-End Encrypted • No phone number",
              style = MaterialTheme.typography.labelSmall,
              color = WhatsAppGreen,
            )
          }
        },
        actions = {
          IconButton(onClick = { showVerify = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.VerifiedUser, contentDescription = "Verify encryption", tint = WhatsAppGreen)
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      EncryptionNoticeBanner(
        text = "Zero-Number E2EE Chat • Encrypted on device (${if (lowDataMode) "Low-Data ON" else "Standard"})",
      )

      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (messages.isEmpty()) {
          item {
            EmptyStateCard(
              icon = Icons.Rounded.Lock,
              title = "Start Encrypted Conversation",
              subtitle = "Say Assalam-o-Alaikum to @${peer.username}. Every message is encrypted with ECDH P-256 + AES-256-GCM.",
            )
          }
        }
        items(messages, key = { it.raw.id }) { msg ->
          EncryptedMessageBubble(
            senderBadge = if (msg.isMine) "You" else "@${msg.raw.senderUsername}",
            isMine = msg.isMine,
            isAdminPost = false,
            text = msg.text,
            imageBase64 = msg.imageBase64,
            approxBytes = msg.raw.approxBytes,
            lowDataMode = lowDataMode,
            canDelete = false,
            decodeImage = decodeImage,
            onDelete = {},
          )
        }
      }

      ChatComposerBar(
        text = text,
        onTextChange = { text = it },
        pendingPhoto = pendingPhoto,
        onClearPhoto = { pendingPhoto = null },
        onPickPhoto = {
          photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        placeholder = "Encrypted message to @${peer.username}...",
        isBusy = isBusy,
        onSend = {
          val toSend = text
          val img = pendingPhoto
          text = ""
          pendingPhoto = null
          onSendMessage(toSend, img)
        },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnonymousRoomChatScreen(
  roomState: DecryptedRoom,
  messages: List<DecryptedRoomMessage>,
  isAdmin: Boolean,
  lowDataMode: Boolean,
  isBusy: Boolean,
  decodeImage: (String) -> Bitmap?,
  onBack: () -> Unit,
  onToggleJoin: () -> Unit,
  onTogglePostingLock: (Boolean) -> Unit,
  onSendMessage: (String, Uri?) -> Unit,
  onDeleteMessage: (String) -> Unit,
) {
  BackHandler(onBack = onBack)

  val room = roomState.room
  var text by remember { mutableStateOf("") }
  var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
  var showVerify by remember { mutableStateOf(false) }

  val photoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> pendingPhoto = uri }

  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  if (showVerify) {
    E2eeVerificationDialog(
      title = if (room.isChannel) "Channel Encryption" else "Group Encryption",
      fingerprintOrSafetyNumber = room.roomFingerprint,
      subtitle = "All posts in \"${room.title}\" are AES-256-GCM encrypted. Member lists and personal identities are strictly hidden — only total counts are visible.",
      onDismiss = { showVerify = false },
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text(
              text = room.title,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val countLabel = if (room.isChannel) "${room.memberCount} anonymous followers" else "${room.memberCount} anonymous members"
            Text(
              text = "$countLabel • E2EE",
              style = MaterialTheme.typography.labelSmall,
              color = WhatsAppGreen,
            )
          }
        },
        actions = {
          if (isAdmin && !room.isChannel) {
            IconButton(
              onClick = { onTogglePostingLock(!room.adminsOnlyPosting) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = if (room.adminsOnlyPosting) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = if (room.adminsOnlyPosting) "Unlock group chat" else "Restrict to admins only",
                tint = ChampagneGold,
              )
            }
          }
          IconButton(onClick = { showVerify = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.VerifiedUser, contentDescription = "Verify room encryption", tint = WhatsAppGreen)
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      EncryptionNoticeBanner(
        text = "Anonymous Mode • Identities hidden, only follower/member count shown",
      )

      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (messages.isEmpty()) {
          item {
            EmptyStateCard(
              icon = if (room.isChannel) Icons.Rounded.Campaign else Icons.Rounded.Groups,
              title = "No Encrypted Posts Yet",
              subtitle = room.description.ifBlank { "Encrypted updates posted here stay private inside Joker Official." },
            )
          }
        }
        items(messages, key = { it.raw.id }) { msg ->
          EncryptedMessageBubble(
            senderBadge = if (msg.raw.isAdminPost) "Joker Official (Admin)" else msg.raw.senderAlias,
            isMine = msg.isMine,
            isAdminPost = msg.raw.isAdminPost,
            text = msg.text,
            imageBase64 = msg.imageBase64,
            approxBytes = msg.raw.approxBytes,
            lowDataMode = lowDataMode,
            canDelete = isAdmin || msg.isMine,
            decodeImage = decodeImage,
            onDelete = { onDeleteMessage(msg.raw.id) },
          )
        }
      }

      val canPost = isAdmin || (!room.isChannel && !room.adminsOnlyPosting && roomState.isJoined)
      when {
        canPost -> {
          ChatComposerBar(
            text = text,
            onTextChange = { text = it },
            pendingPhoto = pendingPhoto,
            onClearPhoto = { pendingPhoto = null },
            onPickPhoto = {
              photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            placeholder = if (room.isChannel) "Broadcast encrypted post to followers..." else "Anonymous encrypted group message...",
            isBusy = isBusy,
            onSend = {
              val toSend = text
              val img = pendingPhoto
              text = ""
              pendingPhoto = null
              onSendMessage(toSend, img)
            },
          )
        }

        !roomState.isJoined -> {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
          ) {
            Button(
              onClick = onToggleJoin,
              colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(48.dp)
                .testTag("room_join_footer_button"),
            ) {
              Text(if (room.isChannel) "Follow Channel Anonymously" else "Join Group Anonymously")
            }
          }
        }

        else -> {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = if (room.isChannel) {
                "Channel broadcast: only Joker Official Admins can post updates."
              } else {
                "Admins-only mode enabled in this group."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EncryptionNoticeBanner(text: String) {
  Surface(
    color = WhatsAppGreen.copy(alpha = 0.14f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(Icons.Rounded.Lock, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(14.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = WhatsAppGreen,
      )
    }
  }
}

@Composable
private fun EncryptedMessageBubble(
  senderBadge: String,
  isMine: Boolean,
  isAdminPost: Boolean,
  text: String,
  imageBase64: String,
  approxBytes: Long,
  lowDataMode: Boolean,
  canDelete: Boolean,
  decodeImage: (String) -> Bitmap?,
  onDelete: () -> Unit,
) {
  var revealImage by remember(imageBase64) { mutableStateOf(!lowDataMode) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
  ) {
    Card(
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMine) 16.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 16.dp,
      ),
      colors = CardDefaults.cardColors(
        containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
      ),
      border = if (!isMine) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)) else null,
      modifier = Modifier.widthIn(max = 560.dp),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = senderBadge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isAdminPost) ChampagneGold else WhatsAppGreen,
          )
          if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
              Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete message",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        }

        if (imageBase64.isNotBlank()) {
          if (revealImage) {
            val bmp = remember(imageBase64) { decodeImage(imageBase64) }
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Decrypted photo",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp)),
              )
            }
          } else {
            OutlinedButton(
              onClick = { revealImage = true },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.height(48.dp),
            ) {
              Text("Tap to view photo (${(approxBytes / 1024).coerceAtLeast(1)} KB)")
            }
          }
        }

        if (text.isNotBlank()) {
          Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun ChatComposerBar(
  text: String,
  onTextChange: (String) -> Unit,
  pendingPhoto: Uri?,
  onClearPhoto: () -> Unit,
  onPickPhoto: () -> Unit,
  placeholder: String,
  isBusy: Boolean,
  onSend: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (pendingPhoto != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Rounded.Image, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
          Text("Photo ready (Low-Data compressed)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
          IconButton(onClick = onClearPhoto, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove photo", modifier = Modifier.size(16.dp))
          }
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = onPickPhoto,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(Icons.Rounded.Image, contentDescription = "Attach photo")
        }
        OutlinedTextField(
          value = text,
          onValueChange = onTextChange,
          placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          maxLines = 4,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("community_chat_input"),
        )
        Button(
          onClick = onSend,
          enabled = !isBusy && (text.isNotBlank() || pendingPhoto != null),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
          contentPadding = PaddingValues(0.dp),
          modifier = Modifier
            .size(48.dp)
            .testTag("community_chat_send_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send encrypted message")
        }
      }
    }
  }
}

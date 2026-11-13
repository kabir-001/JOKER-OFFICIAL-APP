package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CommunityRoom
import com.example.data.model.DecryptedDmThread
import com.example.data.model.DecryptedRoom
import com.example.data.model.DecryptedStatus
import com.example.data.model.UserIdentityState
import com.example.data.model.UsernameRecord
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityHubScreen(
  identity: UserIdentityState,
  isAdmin: Boolean,
  isBusy: Boolean,
  officialAdmins: List<UsernameRecord>,
  dmThreads: List<DecryptedDmThread>,
  rooms: List<DecryptedRoom>,
  statuses: List<DecryptedStatus>,
  lookupResult: UsernameRecord?,
  lookupError: String?,
  decodeImage: (String) -> Bitmap?,
  onClaimUsername: (String, String, () -> Unit) -> Unit,
  onToggleLowDataMode: () -> Unit,
  onSearchUsername: (String) -> Unit,
  onClearLookup: () -> Unit,
  onOpenDmWithRecord: (UsernameRecord) -> Unit,
  onOpenDmThread: (DecryptedDmThread) -> Unit,
  onOpenRoom: (DecryptedRoom) -> Unit,
  onToggleJoinRoom: (DecryptedRoom) -> Unit,
  onCreateRoom: (String, String, String, String, Boolean, () -> Unit) -> Unit,
  onSeedOfficialRooms: () -> Unit,
  onDeleteRoom: (String) -> Unit,
  onPostStatus: (String, Uri?, String, Boolean, () -> Unit) -> Unit,
  onStatusViewed: (DecryptedStatus) -> Unit,
  onDeleteStatus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSubTab by remember { mutableIntStateOf(0) } // 0=Chats, 1=Groups, 2=Channels, 3=Status
  var showClaimDialog by remember { mutableStateOf(false) }
  var showStartDmDialog by remember { mutableStateOf(false) }
  var showCreateRoomDialog by remember { mutableStateOf(false) }
  var showPostStatusDialog by remember { mutableStateOf(false) }
  var showIdentityKeyDialog by remember { mutableStateOf(false) }
  var activeStatusViewer by remember { mutableStateOf<DecryptedStatus?>(null) }

  if (showClaimDialog) {
    ClaimUsernameDialog(
      initialUsername = identity.claimedUsername,
      isBusy = isBusy,
      onDismiss = { showClaimDialog = false },
      onSave = { handle, bio ->
        onClaimUsername(handle, bio) { showClaimDialog = false }
      },
    )
  }

  if (showStartDmDialog) {
    StartDmByUsernameDialog(
      lookupResult = lookupResult,
      lookupError = lookupError,
      isBusy = isBusy,
      onSearch = onSearchUsername,
      onStartChat = { peer ->
        showStartDmDialog = false
        onClearLookup()
        onOpenDmWithRecord(peer)
      },
      onDismiss = {
        showStartDmDialog = false
        onClearLookup()
      },
    )
  }

  if (showCreateRoomDialog) {
    CreateRoomDialog(
      isBusy = isBusy,
      onDismiss = { showCreateRoomDialog = false },
      onCreate = { title, desc, roomType, cat, adminsOnly ->
        onCreateRoom(title, desc, roomType, cat, adminsOnly) {
          showCreateRoomDialog = false
        }
      },
    )
  }

  if (showPostStatusDialog) {
    PostStatusDialog(
      isAdmin = isAdmin,
      hasContacts = dmThreads.isNotEmpty(),
      isBusy = isBusy,
      onDismiss = { showPostStatusDialog = false },
      onPost = { text, uri, accent, isPub ->
        onPostStatus(text, uri, accent, isPub) {
          showPostStatusDialog = false
        }
      },
    )
  }

  if (showIdentityKeyDialog) {
    E2eeVerificationDialog(
      title = "Your E2EE Security Key",
      fingerprintOrSafetyNumber = identity.keyFingerprint.ifBlank { "Generating..." },
      subtitle = "Every private chat, group, channel, and status uses on-device encryption. Zero phone numbers or Google emails are ever exposed to students.",
      onDismiss = { showIdentityKeyDialog = false },
    )
  }

  if (activeStatusViewer != null) {
    val viewing = activeStatusViewer!!
    StatusViewerDialog(
      status = viewing,
      lowDataMode = identity.lowDataMode,
      isAdmin = isAdmin,
      decodeImage = decodeImage,
      onDelete = {
        onDeleteStatus(viewing.raw.id)
        activeStatusViewer = null
      },
      onDismiss = { activeStatusViewer = null },
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
      // Compact Identity + Low-Data + E2EE Status Strip
      LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          AssistChip(
            onClick = { showClaimDialog = true },
            leadingIcon = {
              Icon(Icons.Rounded.AlternateEmail, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
            },
            label = {
              Text(
                if (identity.claimedUsername.isNotBlank()) "@${identity.claimedUsername}"
                else "Set @username",
              )
            },
            modifier = Modifier.testTag("community_username_chip"),
          )
        }
        item {
          AssistChip(
            onClick = onToggleLowDataMode,
            leadingIcon = {
              Icon(
                Icons.Rounded.DataSaverOn,
                contentDescription = null,
                tint = if (identity.lowDataMode) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            },
            label = {
              Text(if (identity.lowDataMode) "Low-Data: ON" else "Low-Data: OFF")
            },
            modifier = Modifier.testTag("community_low_data_chip"),
          )
        }
        item {
          AssistChip(
            onClick = { showIdentityKeyDialog = true },
            leadingIcon = {
              Icon(Icons.Rounded.Shield, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(16.dp))
            },
            label = { Text("E2EE Keys") },
          )
        }
      }

      SingleChoiceSegmentedButtonRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp),
      ) {
        val tabs = listOf("Chats", "Groups", "Channels", "Status")
        tabs.forEachIndexed { idx, title ->
          SegmentedButton(
            selected = selectedSubTab == idx,
            onClick = { selectedSubTab = idx },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
            modifier = Modifier.testTag("community_subtab_${title.lowercase()}"),
          ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }

      when (selectedSubTab) {
        0 -> ChatsSection(
          identity = identity,
          officialAdmins = officialAdmins,
          threads = dmThreads,
          onClaimUsernameClick = { showClaimDialog = true },
          onStartNewDmClick = {
            if (identity.claimedUsername.isBlank()) showClaimDialog = true
            else showStartDmDialog = true
          },
          onOpenAdminDm = { admin ->
            if (identity.claimedUsername.isBlank()) showClaimDialog = true
            else onOpenDmWithRecord(admin)
          },
          onOpenThread = onOpenDmThread,
        )

        1 -> RoomsSection(
          rooms = rooms.filter { !it.room.isChannel },
          isChannelTab = false,
          isAdmin = isAdmin,
          onSeedDefaults = onSeedOfficialRooms,
          onCreateRoomClick = { showCreateRoomDialog = true },
          onOpenRoom = onOpenRoom,
          onToggleJoin = onToggleJoinRoom,
          onDeleteRoom = onDeleteRoom,
        )

        2 -> RoomsSection(
          rooms = rooms.filter { it.room.isChannel },
          isChannelTab = true,
          isAdmin = isAdmin,
          onSeedDefaults = onSeedOfficialRooms,
          onCreateRoomClick = { showCreateRoomDialog = true },
          onOpenRoom = onOpenRoom,
          onToggleJoin = onToggleJoinRoom,
          onDeleteRoom = onDeleteRoom,
        )

        3 -> StatusSection(
          statuses = statuses,
          onPostStatusClick = { showPostStatusDialog = true },
          onOpenStatus = { st ->
            onStatusViewed(st)
            activeStatusViewer = st
          },
        )
      }
    }
  }
}

@Composable
private fun ChatsSection(
  identity: UserIdentityState,
  officialAdmins: List<UsernameRecord>,
  threads: List<DecryptedDmThread>,
  onClaimUsernameClick: () -> Unit,
  onStartNewDmClick: () -> Unit,
  onOpenAdminDm: (UsernameRecord) -> Unit,
  onOpenThread: (DecryptedDmThread) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (identity.claimedUsername.isBlank()) {
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Create Your Private @Username", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "No phone numbers required. Claim a @username to send and receive end-to-end encrypted direct messages.",
              style = MaterialTheme.typography.bodySmall,
            )
            Button(
              onClick = onClaimUsernameClick,
              colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("banner_claim_username_button"),
            ) {
              Text("Claim @Username Now")
            }
          }
        }
      }
    }

    item {
      Button(
        onClick = onStartNewDmClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("new_e2ee_dm_button"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Message Any @Username (E2EE)")
      }
    }

    val otherAdmins = officialAdmins.filter { it.uid != identity.uid }
    if (otherAdmins.isNotEmpty()) {
      item {
        Text(
          text = "Message Official Admin",
          style = MaterialTheme.typography.labelLarge,
          color = ChampagneGold,
        )
      }
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(otherAdmins, key = { it.username }) { admin ->
            OutlinedButton(
              onClick = { onOpenAdminDm(admin) },
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.5f)),
              modifier = Modifier.height(48.dp),
            ) {
              Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("@${admin.username}")
            }
          }
        }
      }
    }

    if (threads.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Rounded.Chat,
          title = "No Private E2EE Chats Yet",
          subtitle = "Tap Message Any @Username above to start a zero-number, end-to-end encrypted chat.",
        )
      }
    } else {
      items(threads, key = { it.thread.id }) { thread ->
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenThread(thread) },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(WhatsAppGreen.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Rounded.Lock, contentDescription = null, tint = WhatsAppGreen)
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "@${thread.peerUsername}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = thread.lastPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RoomsSection(
  rooms: List<DecryptedRoom>,
  isChannelTab: Boolean,
  isAdmin: Boolean,
  onSeedDefaults: () -> Unit,
  onCreateRoomClick: () -> Unit,
  onOpenRoom: (DecryptedRoom) -> Unit,
  onToggleJoin: (DecryptedRoom) -> Unit,
  onDeleteRoom: (String) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (isAdmin) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onCreateRoomClick,
            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("admin_create_room_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isChannelTab) "New Channel" else "New Group")
          }
          OutlinedButton(
            onClick = onSeedDefaults,
            modifier = Modifier
              .height(48.dp)
              .testTag("admin_seed_rooms_button"),
          ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Init Joker")
          }
        }
      }
    }

    item {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = WhatsAppGreen.copy(alpha = 0.12f),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Rounded.Lock, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
          Text(
            text = if (isChannelTab) {
              "Channels are E2EE & 100% anonymous — only follower counts are shown."
            } else {
              "Groups are E2EE & anonymous — joining increases member count without showing your identity."
            },
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    if (rooms.isEmpty()) {
      item {
        EmptyStateCard(
          icon = if (isChannelTab) Icons.Rounded.Campaign else Icons.Rounded.Groups,
          title = if (isChannelTab) "No Channels Yet" else "No Study Groups Yet",
          subtitle = if (isAdmin) "Tap Init Joker or Create above to launch official E2EE rooms."
          else "Official Joker channels and study groups created by your instructor will appear here.",
        )
      }
    } else {
      items(rooms, key = { it.room.id }) { item ->
        val room = item.room
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenRoom(item) },
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${room.category} • E2EE",
                  style = MaterialTheme.typography.labelSmall,
                  color = ChampagneGold,
                )
                Text(
                  text = room.title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (isAdmin) {
                IconButton(onClick = { onDeleteRoom(room.id) }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete room", tint = MaterialTheme.colorScheme.error)
                }
              }
            }

            Text(
              text = item.lastPreview,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val countText = if (room.isChannel) {
                "${room.memberCount} Anonymous Followers"
              } else {
                "${room.memberCount} Anonymous Members"
              }
              Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = WhatsAppGreen,
              )

              if (item.isJoined) {
                OutlinedButton(
                  onClick = { onToggleJoin(item) },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.height(48.dp),
                ) {
                  Text(if (room.isChannel) "Following ✓" else "Joined ✓")
                }
              } else {
                Button(
                  onClick = { onToggleJoin(item) },
                  colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.height(48.dp),
                ) {
                  Text(if (room.isChannel) "Follow" else "Join")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatusSection(
  statuses: List<DecryptedStatus>,
  onPostStatusClick: () -> Unit,
  onOpenStatus: (DecryptedStatus) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Button(
        onClick = onPostStatusClick,
        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("post_e2ee_status_button"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Post End-to-End Encrypted Status (24h)")
      }
    }

    if (statuses.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Rounded.RadioButtonChecked,
          title = "No Active 24h Statuses",
          subtitle = "Official Joker announcements and encrypted updates from your @username contacts appear here.",
        )
      }
    } else {
      items(statuses, key = { it.raw.id }) { st ->
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(
            1.dp,
            if (st.raw.isOfficialBroadcast) ChampagneGold.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
          ),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenStatus(st) },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(2.5.dp, if (st.raw.isOfficialBroadcast) ChampagneGold else WhatsAppGreen, CircleShape)
                .background(WhatsAppGreen.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (st.raw.isOfficialBroadcast) ChampagneGold else WhatsAppGreen,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = st.raw.authorLabel,
                style = MaterialTheme.typography.titleMedium,
                color = if (st.raw.isOfficialBroadcast) ChampagneGold else MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = when {
                  !st.canDecrypt -> "Encrypted status update"
                  st.text.isNotBlank() -> st.text
                  else -> "Encrypted Photo Status • Tap to view"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text = "${st.raw.viewCount} views",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

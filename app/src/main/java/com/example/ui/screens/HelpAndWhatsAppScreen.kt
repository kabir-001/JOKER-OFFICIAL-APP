package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HelpContact
import com.example.data.model.SupportConfig
import com.example.ui.components.ExternalLinkHelpers
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.OnWhatsAppContainer
import com.example.ui.theme.WhatsAppContainer
import com.example.ui.theme.WhatsAppGreen

@Composable
fun HelpAndWhatsAppScreen(
  supportConfig: SupportConfig,
  userDisplayName: String,
  userEmail: String?,
  isAdmin: Boolean,
  onSignOut: () -> Unit,
  onOpenAdminPanel: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  listOf(WhatsAppGreen, Color(0xFF0A6C6B))
                )
              )
              .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Forum,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(26.dp),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = ChampagneGold,
                ) {
                  Text(
                    text = "OFFICIAL CHANNEL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1600),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = supportConfig.whatsappChannelName.ifBlank { "JOKER OFFICIAL Channel" },
                  style = MaterialTheme.typography.titleLarge,
                  color = Color.White,
                )
                Text(
                  text = "Instant alerts whenever new SSC & HSC target papers go live.",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.9f),
                )
              }
            }

            Button(
              onClick = { ExternalLinkHelpers.openExternalUrl(context, supportConfig.whatsappChannelUrl) },
              enabled = supportConfig.whatsappChannelUrl.isNotBlank(),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = WhatsAppGreen,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("whatsapp_channel_link_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (supportConfig.whatsappChannelUrl.isNotBlank()) {
                  stringResource(R.string.action_join_whatsapp_channel)
                } else {
                  "WhatsApp Channel Link Not Set Yet"
                },
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }

      if (supportConfig.sscGroupUrl.isNotBlank() || supportConfig.hscGroupUrl.isNotBlank()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "WhatsApp Study Sessions",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )

            if (supportConfig.sscGroupUrl.isNotBlank()) {
              WhatsAppSessionGroupCard(
                title = supportConfig.sscGroupName.ifBlank { "SSC (IX/X) Session" },
                subtitle = "9th & 10th Class target papers, guess notes & board updates",
                badgeText = "SSC • IX / X",
                url = supportConfig.sscGroupUrl,
                testTag = "help_ssc_group_button",
                onClick = { ExternalLinkHelpers.openExternalUrl(context, supportConfig.sscGroupUrl) },
              )
            }

            if (supportConfig.hscGroupUrl.isNotBlank()) {
              WhatsAppSessionGroupCard(
                title = supportConfig.hscGroupName.ifBlank { "HSC (XI/XII) Session" },
                subtitle = "11th & 12th Class target papers, practicals & exam guidance",
                badgeText = "HSC • XI / XII",
                url = supportConfig.hscGroupUrl,
                testTag = "help_hsc_group_button",
                onClick = { ExternalLinkHelpers.openExternalUrl(context, supportConfig.hscGroupUrl) },
              )
            }
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Help & Support Numbers",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = supportConfig.supportNote.ifBlank {
              "Tap any number below to call directly, chat on WhatsApp, or copy the number."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      val contacts = supportConfig.helpContacts
      if (contacts.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.SupportAgent,
            title = "No Help Numbers Configured Yet",
            subtitle = if (isAdmin) {
              "Open the Admin Panel to add your WhatsApp Channel link and clickable help phone numbers."
            } else {
              "Your instructor has not published help phone numbers yet. Please check back soon."
            },
          )
          if (isAdmin && onOpenAdminPanel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = onOpenAdminPanel,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            ) {
              Text("Configure Links in Admin Panel")
            }
          }
        }
      } else {
        itemsIndexed(contacts) { idx, contact ->
          HelpContactCard(index = idx, contact = contact)
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Signed In Account",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = buildString {
                append(userDisplayName)
                if (!userEmail.isNullOrBlank()) append(" ($userEmail)")
                if (isAdmin) append(" • Admin")
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = onSignOut,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign Out")
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun WhatsAppSessionGroupCard(
  title: String,
  subtitle: String,
  badgeText: String,
  url: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Button(
        onClick = onClick,
        enabled = url.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = WhatsAppContainer,
          contentColor = OnWhatsAppContainer,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag(testTag),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Join WhatsApp Group", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun HelpContactCard(
  index: Int,
  contact: HelpContact,
) {
  val context = LocalContext.current

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("help_contact_card_$index"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.SupportAgent,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = contact.label.ifBlank { "Help Line ${index + 1}" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = contact.phoneNumber,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        IconButton(
          onClick = {
            ExternalLinkHelpers.copyToClipboard(
              context = context,
              label = contact.label.ifBlank { "Phone Number" },
              text = contact.phoneNumber,
            )
          },
          modifier = Modifier
            .size(48.dp)
            .testTag("copy_contact_button_$index"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Copy ${contact.phoneNumber}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (contact.allowCall) {
          Button(
            onClick = { ExternalLinkHelpers.dialPhoneNumber(context, contact.phoneNumber) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("call_contact_button_$index"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Call,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("${stringResource(R.string.action_call_now)} Now")
          }
        }
        if (contact.allowWhatsApp) {
          Button(
            onClick = { ExternalLinkHelpers.openWhatsAppChat(context, contact.phoneNumber) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = WhatsAppContainer,
              contentColor = OnWhatsAppContainer,
            ),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("whatsapp_contact_button_$index"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Chat,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_whatsapp_chat))
          }
        }
      }
    }
  }
}

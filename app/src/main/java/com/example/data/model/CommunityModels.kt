package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONObject

@IgnoreExtraProperties
data class UsernameRecord(
  @DocumentId val id: String = "",
  val username: String = "",
  val uid: String = "",
  val publicKeyBase64: String = "",
  val keyFingerprint: String = "",
  val roleLabel: String = ROLE_STUDENT,
  val bio: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isAdmin: Boolean
    get() = roleLabel == ROLE_ADMIN

  companion object {
    const val ROLE_ADMIN = "admin"
    const val ROLE_STUDENT = "student"
  }
}

@IgnoreExtraProperties
data class DmThread(
  @DocumentId val id: String = "",
  val participantUids: List<String> = emptyList(),
  val participantUsernames: List<String> = emptyList(),
  val participantPublicKeys: List<String> = emptyList(),
  val lastMessageCiphertext: String = "",
  val lastMessageIv: String = "",
  val lastSenderUsername: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DmMessage(
  @DocumentId val id: String = "",
  val threadId: String = "",
  val participantUids: List<String> = emptyList(),
  val senderUid: String = "",
  val senderUsername: String = "",
  val senderPublicKey: String = "",
  val recipientPublicKey: String = "",
  val ciphertextBase64: String = "",
  val ivBase64: String = "",
  val payloadType: String = TYPE_TEXT,
  val approxBytes: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  companion object {
    const val TYPE_TEXT = "text"
    const val TYPE_IMAGE = "image"
  }
}

@IgnoreExtraProperties
data class CommunityRoom(
  @DocumentId val id: String = "",
  val title: String = "",
  val description: String = "",
  val roomType: String = TYPE_GROUP,
  val category: String = "SSC",
  @get:PropertyName("isPublic") val isPublic: Boolean = true,
  val adminsOnlyPosting: Boolean = false,
  val memberCount: Long = 0L,
  val roomKeyEnvelope: String = "",
  val roomFingerprint: String = "",
  val lastPostCiphertext: String = "",
  val lastPostIv: String = "",
  val lastPostAlias: String = "",
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isChannel: Boolean
    get() = roomType == TYPE_CHANNEL

  companion object {
    const val TYPE_CHANNEL = "channel"
    const val TYPE_GROUP = "group"
  }
}

@IgnoreExtraProperties
data class RoomMessage(
  @DocumentId val id: String = "",
  val roomId: String = "",
  @get:PropertyName("isPublicRoom") val isPublicRoom: Boolean = true,
  val senderUid: String = "",
  val senderAlias: String = "",
  @get:PropertyName("isAdminPost") val isAdminPost: Boolean = false,
  val ciphertextBase64: String = "",
  val ivBase64: String = "",
  val payloadType: String = DmMessage.TYPE_TEXT,
  val approxBytes: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StatusUpdate(
  @DocumentId val id: String = "",
  val authorUid: String = "",
  val authorLabel: String = "",
  @get:PropertyName("isOfficialBroadcast") val isOfficialBroadcast: Boolean = false,
  @get:PropertyName("isPublic") val isPublic: Boolean = true,
  val allowedUids: List<String> = emptyList(),
  val wrappedKeys: List<String> = emptyList(),
  val authorPublicKey: String = "",
  val ciphertextBase64: String = "",
  val ivBase64: String = "",
  val accentHex: String = "#064E3B",
  val hasImage: Boolean = false,
  val approxBytes: Long = 0L,
  val viewCount: Long = 0L,
  val expiresAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class E2eePayload(
  val text: String = "",
  val imageBase64: String = "",
) {
  fun toJson(): String = JSONObject().apply {
    put("t", text)
    if (imageBase64.isNotBlank()) put("img", imageBase64)
  }.toString()

  companion object {
    fun fromJson(json: String?): E2eePayload {
      if (json.isNullOrBlank()) return E2eePayload()
      return try {
        val obj = JSONObject(json)
        E2eePayload(
          text = obj.optString("t", ""),
          imageBase64 = obj.optString("img", ""),
        )
      } catch (_: Exception) {
        E2eePayload(text = json)
      }
    }
  }
}

data class UserIdentityState(
  val uid: String = "",
  val claimedUsername: String = "",
  val publicKeyBase64: String = "",
  val keyFingerprint: String = "",
  val lowDataMode: Boolean = true,
)

data class DecryptedDmThread(
  val thread: DmThread,
  val peerUid: String,
  val peerUsername: String,
  val peerPublicKey: String,
  val safetyNumber: String,
  val lastPreview: String,
)

data class DecryptedDmMessage(
  val raw: DmMessage,
  val isMine: Boolean,
  val text: String,
  val imageBase64: String,
  val decryptionFailed: Boolean = false,
)

data class DecryptedRoom(
  val room: CommunityRoom,
  val isJoined: Boolean,
  val lastPreview: String,
)

data class DecryptedRoomMessage(
  val raw: RoomMessage,
  val isMine: Boolean,
  val text: String,
  val imageBase64: String,
  val decryptionFailed: Boolean = false,
)

data class DecryptedStatus(
  val raw: StatusUpdate,
  val isMine: Boolean,
  val text: String,
  val imageBase64: String,
  val canDecrypt: Boolean,
)

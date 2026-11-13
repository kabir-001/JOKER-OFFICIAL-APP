package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.crypto.E2eeCryptoEngine
import com.example.data.model.CommunityRoom
import com.example.data.model.DmMessage
import com.example.data.model.DmThread
import com.example.data.model.E2eePayload
import com.example.data.model.RoomMessage
import com.example.data.model.StatusUpdate
import com.example.data.model.UserIdentityState
import com.example.data.model.UsernameRecord
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class CommunityRepository(
  private val db: FirebaseFirestore,
  val crypto: E2eeCryptoEngine,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    crypto = E2eeCryptoEngine(context.applicationContext),
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Community.")

  private fun <T> Flow<T>.handleErrors(operationType: OperationType, path: String): Flow<T> =
    catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val exceptionToLog = firestoreError ?: (error as? Exception)
      if (exceptionToLog != null) {
        handleFirestoreError(exceptionToLog, operationType, path)
      }
      throw error
    }

  suspend fun syncLocalIdentity(displayName: String, isAdmin: Boolean): Result<UserIdentityState> {
    val uid = requireUserId()
    val localKeys = crypto.getOrCreateIdentityKeys(uid)
    val userRef = db.collection(COLLECTION_USERS).document(uid)
    val safeName = displayName.trim().ifBlank { "Student" }.take(80)

    return try {
      val userSnap = userRef.get().await()
      val existingUsername = userSnap.getString("username").orEmpty().trim().lowercase()
      val lowDataMode = userSnap.getBoolean("lowDataMode") ?: true

      if (userSnap.exists()) {
        userRef.update(
          mapOf(
            "e2eePublicKey" to localKeys.publicKeyBase64,
            "keyFingerprint" to localKeys.keyFingerprint,
            "lowDataMode" to lowDataMode,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        userRef.set(
          mapOf(
            "userId" to uid,
            "displayName" to safeName,
            "preferredClass" to "",
            "username" to "",
            "e2eePublicKey" to localKeys.publicKeyBase64,
            "keyFingerprint" to localKeys.keyFingerprint,
            "lowDataMode" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }

      if (existingUsername.isNotBlank()) {
        val handleRef = db.collection(COLLECTION_USERNAMES).document(existingUsername)
        val handleSnap = handleRef.get().await()
        if (handleSnap.exists() && handleSnap.getString("uid") == uid) {
          handleRef.update(
            mapOf(
              "publicKeyBase64" to localKeys.publicKeyBase64,
              "keyFingerprint" to localKeys.keyFingerprint,
              "roleLabel" to if (isAdmin) UsernameRecord.ROLE_ADMIN else UsernameRecord.ROLE_STUDENT,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()
        }
      }

      Result.success(
        UserIdentityState(
          uid = uid,
          claimedUsername = existingUsername,
          publicKeyBase64 = localKeys.publicKeyBase64,
          keyFingerprint = localKeys.keyFingerprint,
          lowDataMode = lowDataMode,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun claimUsername(rawHandle: String, bio: String, isAdmin: Boolean): Result<UserIdentityState> {
    val uid = requireUserId()
    val handle = rawHandle.trim().lowercase().removePrefix("@")
    if (!USERNAME_REGEX.matches(handle)) {
      return Result.failure(IllegalArgumentException("Username must be 3-24 lowercase letters, numbers, or _ only."))
    }

    val localKeys = crypto.getOrCreateIdentityKeys(uid)
    val handleRef = db.collection(COLLECTION_USERNAMES).document(handle)
    val userRef = db.collection(COLLECTION_USERS).document(uid)

    return try {
      val existingHandle = handleRef.get().await()
      if (existingHandle.exists() && existingHandle.getString("uid") != uid) {
        return Result.failure(IllegalStateException("@$handle is already taken. Try another username."))
      }

      val userSnap = userRef.get().await()
      val oldHandle = userSnap.getString("username").orEmpty().trim().lowercase()
      val batch = db.batch()

      if (existingHandle.exists()) {
        batch.update(
          handleRef,
          mapOf(
            "publicKeyBase64" to localKeys.publicKeyBase64,
            "keyFingerprint" to localKeys.keyFingerprint,
            "roleLabel" to if (isAdmin) UsernameRecord.ROLE_ADMIN else UsernameRecord.ROLE_STUDENT,
            "bio" to bio.trim().take(140),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      } else {
        batch.set(
          handleRef,
          mapOf(
            "username" to handle,
            "uid" to uid,
            "publicKeyBase64" to localKeys.publicKeyBase64,
            "keyFingerprint" to localKeys.keyFingerprint,
            "roleLabel" to if (isAdmin) UsernameRecord.ROLE_ADMIN else UsernameRecord.ROLE_STUDENT,
            "bio" to bio.trim().take(140),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }

      if (oldHandle.isNotBlank() && oldHandle != handle) {
        batch.delete(db.collection(COLLECTION_USERNAMES).document(oldHandle))
      }

      if (userSnap.exists()) {
        batch.update(
          userRef,
          mapOf(
            "username" to handle,
            "e2eePublicKey" to localKeys.publicKeyBase64,
            "keyFingerprint" to localKeys.keyFingerprint,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      } else {
        batch.set(
          userRef,
          mapOf(
            "userId" to uid,
            "displayName" to "@$handle",
            "preferredClass" to "",
            "username" to handle,
            "e2eePublicKey" to localKeys.publicKeyBase64,
            "keyFingerprint" to localKeys.keyFingerprint,
            "lowDataMode" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }

      batch.commit().await()
      Result.success(
        UserIdentityState(
          uid = uid,
          claimedUsername = handle,
          publicKeyBase64 = localKeys.publicKeyBase64,
          keyFingerprint = localKeys.keyFingerprint,
          lowDataMode = userSnap.getBoolean("lowDataMode") ?: true,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, handleRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateLowDataMode(enabled: Boolean): Result<Unit> {
    val uid = requireUserId()
    val ref = db.collection(COLLECTION_USERS).document(uid)
    return try {
      ref.update(mapOf("lowDataMode" to enabled, "updatedAt" to FieldValue.serverTimestamp())).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun lookupUsername(rawHandle: String): Result<UsernameRecord?> {
    requireUserId()
    val handle = rawHandle.trim().lowercase().removePrefix("@")
    if (!USERNAME_REGEX.matches(handle)) {
      return Result.failure(IllegalArgumentException("Enter a valid username (3-24 letters, digits, or _)."))
    }
    val ref = db.collection(COLLECTION_USERNAMES).document(handle)
    return try {
      val snap = ref.get().await()
      Result.success(
        if (snap.exists()) snap.toObject(UsernameRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        else null
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, ref.path)
      Result.failure(e)
    }
  }

  fun observeOfficialAdminHandles(): Flow<List<UsernameRecord>> =
    db.collection(COLLECTION_USERNAMES)
      .whereEqualTo("roleLabel", UsernameRecord.ROLE_ADMIN)
      .snapshots()
      .map { snap ->
        snap.toObjects(UsernameRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleErrors(OperationType.LIST, COLLECTION_USERNAMES)

  fun observeMyDmThreads(): Flow<List<DmThread>> {
    val uid = requireUserId()
    return db.collection(COLLECTION_DM_THREADS)
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(DmThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
      }
      .handleErrors(OperationType.LIST, COLLECTION_DM_THREADS)
  }

  fun observeDmMessages(threadId: String): Flow<List<DmMessage>> {
    val uid = requireUserId()
    val path = "$COLLECTION_DM_THREADS/$threadId/$SUBCOLLECTION_MESSAGES"
    return db.collection(COLLECTION_DM_THREADS).document(threadId)
      .collection(SUBCOLLECTION_MESSAGES)
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(DmMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt ?: Timestamp(0, 0) }
      }
      .handleErrors(OperationType.LIST, path)
  }

  suspend fun sendDmMessage(
    peerUid: String,
    peerUsername: String,
    peerPublicKey: String,
    myUsername: String,
    text: String,
    imageBase64: String = "",
  ): Result<String> {
    val uid = requireUserId()
    if (peerUid == uid) return Result.failure(IllegalArgumentException("Cannot message yourself."))
    val cleanText = text.trim()
    if (cleanText.isBlank() && imageBase64.isBlank()) {
      return Result.failure(IllegalArgumentException("Message cannot be empty."))
    }

    val myKeys = crypto.getOrCreateIdentityKeys(uid)
    val sortedPair = listOf(uid to (myUsername to myKeys.publicKeyBase64), peerUid to (peerUsername to peerPublicKey))
      .sortedBy { it.first }
    val threadId = "${sortedPair[0].first}_${sortedPair[1].first}"
    val threadRef = db.collection(COLLECTION_DM_THREADS).document(threadId)
    val msgRef = threadRef.collection(SUBCOLLECTION_MESSAGES).document()

    val aesKey = crypto.deriveSharedAesKey(myKeys.privateKey, peerPublicKey, threadId)
    val payloadJson = E2eePayload(text = cleanText, imageBase64 = imageBase64).toJson()
    val envelope = crypto.encryptText(aesKey, payloadJson)
    val previewPlain = if (cleanText.isNotBlank()) cleanText.take(120) else "[Encrypted Photo]"
    val previewEnvelope = crypto.encryptText(aesKey, previewPlain)

    return try {
      val threadSnap = threadRef.get().await()
      val batch = db.batch()
      val pUids = sortedPair.map { it.first }
      val pNames = sortedPair.map { it.second.first }
      val pKeys = sortedPair.map { it.second.second }

      if (threadSnap.exists()) {
        batch.update(
          threadRef,
          mapOf(
            "participantPublicKeys" to pKeys,
            "lastMessageCiphertext" to previewEnvelope.ciphertextBase64,
            "lastMessageIv" to previewEnvelope.ivBase64,
            "lastSenderUsername" to myUsername,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      } else {
        batch.set(
          threadRef,
          mapOf(
            "participantUids" to pUids,
            "participantUsernames" to pNames,
            "participantPublicKeys" to pKeys,
            "lastMessageCiphertext" to previewEnvelope.ciphertextBase64,
            "lastMessageIv" to previewEnvelope.ivBase64,
            "lastSenderUsername" to myUsername,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }

      batch.set(
        msgRef,
        mapOf(
          "threadId" to threadId,
          "participantUids" to pUids,
          "senderUid" to uid,
          "senderUsername" to myUsername,
          "senderPublicKey" to myKeys.publicKeyBase64,
          "recipientPublicKey" to peerPublicKey,
          "ciphertextBase64" to envelope.ciphertextBase64,
          "ivBase64" to envelope.ivBase64,
          "payloadType" to if (imageBase64.isNotBlank()) DmMessage.TYPE_IMAGE else DmMessage.TYPE_TEXT,
          "approxBytes" to envelope.approxBytes,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      )
      batch.commit().await()
      Result.success(threadId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, msgRef.path)
      Result.failure(e)
    }
  }

  fun observePublicRooms(): Flow<List<CommunityRoom>> =
    db.collection(COLLECTION_ROOMS)
      .whereEqualTo("isPublic", true)
      .snapshots()
      .map { snap ->
        snap.toObjects(CommunityRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
      }
      .handleErrors(OperationType.LIST, COLLECTION_ROOMS)

  fun observeMyMembership(roomId: String): Flow<Boolean> {
    val uid = requireUserId()
    val docRef = db.collection(COLLECTION_ROOMS).document(roomId)
      .collection(SUBCOLLECTION_MEMBERSHIPS).document(uid)
    return docRef.snapshots()
      .map { it.exists() }
      .handleErrors(OperationType.GET, docRef.path)
  }

  suspend fun toggleRoomMembership(roomId: String, currentlyJoined: Boolean): Result<Boolean> {
    val uid = requireUserId()
    val roomRef = db.collection(COLLECTION_ROOMS).document(roomId)
    val memberRef = roomRef.collection(SUBCOLLECTION_MEMBERSHIPS).document(uid)
    return try {
      val batch = db.batch()
      if (currentlyJoined) {
        batch.delete(memberRef)
        batch.update(
          roomRef,
          mapOf(
            "memberCount" to FieldValue.increment(-1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      } else {
        batch.set(
          memberRef,
          mapOf(
            "userId" to uid,
            "joinedAt" to FieldValue.serverTimestamp(),
          )
        )
        batch.update(
          roomRef,
          mapOf(
            "memberCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      batch.commit().await()
      Result.success(!currentlyJoined)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, roomRef.path)
      Result.failure(e)
    }
  }

  fun observeRoomMessages(roomId: String): Flow<List<RoomMessage>> =
    db.collection(COLLECTION_ROOMS).document(roomId)
      .collection(SUBCOLLECTION_MESSAGES)
      .whereEqualTo("isPublicRoom", true)
      .snapshots()
      .map { snap ->
        snap.toObjects(RoomMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt ?: Timestamp(0, 0) }
      }
      .handleErrors(OperationType.LIST, "$COLLECTION_ROOMS/$roomId/$SUBCOLLECTION_MESSAGES")

  suspend fun sendRoomMessage(
    room: CommunityRoom,
    isAdmin: Boolean,
    text: String,
    imageBase64: String = "",
  ): Result<Unit> {
    val uid = requireUserId()
    val cleanText = text.trim()
    if (cleanText.isBlank() && imageBase64.isBlank()) {
      return Result.failure(IllegalArgumentException("Message cannot be empty."))
    }

    val roomRef = db.collection(COLLECTION_ROOMS).document(room.id)
    val msgRef = roomRef.collection(SUBCOLLECTION_MESSAGES).document()
    val aesKey = crypto.deriveRoomAesKey(room.id, room.roomKeyEnvelope)
    val payloadJson = E2eePayload(text = cleanText, imageBase64 = imageBase64).toJson()
    val envelope = crypto.encryptText(aesKey, payloadJson)
    val previewPlain = if (cleanText.isNotBlank()) cleanText.take(120) else "[Encrypted Photo]"
    val previewEnv = crypto.encryptText(aesKey, previewPlain)
    val alias = if (isAdmin) OFFICIAL_ADMIN_ALIAS else crypto.anonymousGroupAlias(uid, room.id)

    return try {
      val batch = db.batch()
      batch.set(
        msgRef,
        mapOf(
          "roomId" to room.id,
          "isPublicRoom" to room.isPublic,
          "senderUid" to uid,
          "senderAlias" to alias,
          "isAdminPost" to isAdmin,
          "ciphertextBase64" to envelope.ciphertextBase64,
          "ivBase64" to envelope.ivBase64,
          "payloadType" to if (imageBase64.isNotBlank()) DmMessage.TYPE_IMAGE else DmMessage.TYPE_TEXT,
          "approxBytes" to envelope.approxBytes,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      )
      batch.update(
        roomRef,
        mapOf(
          "lastPostCiphertext" to previewEnv.ciphertextBase64,
          "lastPostIv" to previewEnv.ivBase64,
          "lastPostAlias" to alias,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun createCommunityRoom(
    title: String,
    description: String,
    roomType: String,
    category: String,
    adminsOnlyPosting: Boolean,
  ): Result<String> {
    val uid = requireUserId()
    val cleanTitle = title.trim()
    if (cleanTitle.length < 2) return Result.failure(IllegalArgumentException("Title must be at least 2 characters."))
    val roomRef = db.collection(COLLECTION_ROOMS).document()
    val (envelope, fingerprint) = crypto.generateRandomRoomKeyEnvelope()

    return try {
      roomRef.set(
        mapOf(
          "title" to cleanTitle.take(80),
          "description" to description.trim().take(300),
          "roomType" to if (roomType == CommunityRoom.TYPE_CHANNEL) CommunityRoom.TYPE_CHANNEL else CommunityRoom.TYPE_GROUP,
          "category" to category.trim().ifBlank { "General" }.take(40),
          "isPublic" to true,
          "adminsOnlyPosting" to (roomType == CommunityRoom.TYPE_CHANNEL || adminsOnlyPosting),
          "memberCount" to 0L,
          "roomKeyEnvelope" to envelope,
          "roomFingerprint" to fingerprint,
          "lastPostCiphertext" to "",
          "lastPostIv" to "",
          "lastPostAlias" to "",
          "createdByUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(roomRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, roomRef.path)
      Result.failure(e)
    }
  }

  suspend fun seedDefaultJokerRooms(): Result<Unit> {
    requireUserId()
    val defaults = listOf(
      Triple("JOKER OFFICIAL Channel", CommunityRoom.TYPE_CHANNEL, "Official SSC & HSC target papers, guess updates & urgent board alerts (Anonymous Followers Only)."),
      Triple("SSC (IX/X) Study Group", CommunityRoom.TYPE_GROUP, "Anonymous encrypted discussion & help for Class 9 & 10 students."),
      Triple("HSC (XI/XII) Study Group", CommunityRoom.TYPE_GROUP, "Anonymous encrypted discussion & past paper help for Class 11 & 12 students."),
    )
    return try {
      val existing = db.collection(COLLECTION_ROOMS).whereEqualTo("isPublic", true).get().await()
      val existingTitles = existing.documents.mapNotNull { it.getString("title")?.lowercase() }.toSet()
      defaults.forEachIndexed { idx, (title, type, desc) ->
        if (title.lowercase() !in existingTitles) {
          createCommunityRoom(
            title = title,
            description = desc,
            roomType = type,
            category = when (idx) {
              0 -> "Official"
              1 -> "SSC"
              else -> "HSC"
            },
            adminsOnlyPosting = type == CommunityRoom.TYPE_CHANNEL,
          ).getOrThrow()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun toggleRoomPostingLock(roomId: String, adminsOnlyPosting: Boolean): Result<Unit> {
    requireUserId()
    val ref = db.collection(COLLECTION_ROOMS).document(roomId)
    return try {
      ref.update(
        mapOf(
          "adminsOnlyPosting" to adminsOnlyPosting,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun deleteCommunityRoom(roomId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection(COLLECTION_ROOMS).document(roomId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun deleteRoomMessage(roomId: String, messageId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection(COLLECTION_ROOMS).document(roomId)
      .collection(SUBCOLLECTION_MESSAGES).document(messageId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  fun observeStatuses(): Flow<List<StatusUpdate>> {
    val uid = requireUserId()
    val publicFlow = db.collection(COLLECTION_STATUS)
      .whereEqualTo("isPublic", true)
      .snapshots()
      .map { snap -> snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .handleErrors(OperationType.LIST, COLLECTION_STATUS)

    val privateFlow = db.collection(COLLECTION_STATUS)
      .whereArrayContains("allowedUids", uid)
      .snapshots()
      .map { snap -> snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .handleErrors(OperationType.LIST, COLLECTION_STATUS)

    return combine(publicFlow, privateFlow) { pub, priv ->
      val nowMs = System.currentTimeMillis()
      (pub + priv)
        .distinctBy { it.id }
        .filter { st ->
          val exp = st.expiresAt?.toDate()?.time
          exp == null || exp > nowMs
        }
        .sortedWith(
          compareByDescending<StatusUpdate> { it.isOfficialBroadcast }
            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
        )
    }
  }

  suspend fun postStatus(
    text: String,
    imageBase64: String,
    accentHex: String,
    isAdmin: Boolean,
    isPublicStatus: Boolean,
    senderUsername: String,
    contactRecipients: List<Pair<String, String>> = emptyList(),
  ): Result<Unit> {
    val uid = requireUserId()
    val cleanText = text.trim()
    if (cleanText.isBlank() && imageBase64.isBlank()) {
      return Result.failure(IllegalArgumentException("Status text or image is required."))
    }

    val statusRef = db.collection(COLLECTION_STATUS).document()
    val myKeys = crypto.getOrCreateIdentityKeys(uid)
    val payloadJson = E2eePayload(text = cleanText, imageBase64 = imageBase64).toJson()

    val isOfficial = isAdmin && isPublicStatus
    val authorLabel = when {
      isOfficial -> OFFICIAL_ADMIN_ALIAS
      senderUsername.isNotBlank() && !isPublicStatus -> "@$senderUsername"
      else -> "Anonymous Student"
    }

    val (envelope, wrappedKeys, allowedUids) = if (isPublicStatus) {
      val pubAesKey = crypto.deriveCommunityStatusKey(statusRef.id)
      val env = crypto.encryptText(pubAesKey, payloadJson)
      Triple(env, emptyList<String>(), listOf(uid))
    } else {
      val statusKey = crypto.generateRandomStatusAesKey()
      val env = crypto.encryptText(statusKey, payloadJson)
      val allRecipients = (contactRecipients + (uid to myKeys.publicKeyBase64)).distinctBy { it.first }.take(48)
      val wraps = allRecipients.mapNotNull { (rUid, rPub) ->
        crypto.wrapStatusKeyForRecipient(statusKey, myKeys.privateKey, rUid, rPub)
      }
      Triple(env, wraps, allRecipients.map { it.first })
    }

    val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 3600L * 1000L))
    return try {
      statusRef.set(
        mapOf(
          "authorUid" to uid,
          "authorLabel" to authorLabel.take(40),
          "isOfficialBroadcast" to isOfficial,
          "isPublic" to isPublicStatus,
          "allowedUids" to allowedUids,
          "wrappedKeys" to wrappedKeys,
          "authorPublicKey" to myKeys.publicKeyBase64,
          "ciphertextBase64" to envelope.ciphertextBase64,
          "ivBase64" to envelope.ivBase64,
          "accentHex" to accentHex,
          "hasImage" to imageBase64.isNotBlank(),
          "approxBytes" to envelope.approxBytes,
          "viewCount" to 0L,
          "expiresAt" to expiresAt,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, statusRef.path)
      Result.failure(e)
    }
  }

  suspend fun incrementStatusViewCount(statusId: String) {
    val uid = auth.currentUser?.uid ?: return
    if (uid.isBlank() || statusId.isBlank()) return
    val ref = db.collection(COLLECTION_STATUS).document(statusId)
    runCatching {
      ref.update("viewCount", FieldValue.increment(1L)).await()
    }
  }

  suspend fun deleteStatus(statusId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection(COLLECTION_STATUS).document(statusId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  companion object {
    const val COLLECTION_USERS = "users"
    const val COLLECTION_USERNAMES = "usernames"
    const val COLLECTION_DM_THREADS = "dm_threads"
    const val COLLECTION_ROOMS = "community_rooms"
    const val COLLECTION_STATUS = "status_updates"
    const val SUBCOLLECTION_MESSAGES = "messages"
    const val SUBCOLLECTION_MEMBERSHIPS = "memberships"
    const val OFFICIAL_ADMIN_ALIAS = "Joker Official"
    val USERNAME_REGEX = Regex("^[a-z0-9_]{3,24}$")
  }
}

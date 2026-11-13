package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CommunityRoom
import com.example.data.model.DecryptedDmMessage
import com.example.data.model.DecryptedDmThread
import com.example.data.model.DecryptedRoom
import com.example.data.model.DecryptedRoomMessage
import com.example.data.model.DecryptedStatus
import com.example.data.model.DmThread
import com.example.data.model.E2eePayload
import com.example.data.model.StatusUpdate
import com.example.data.model.UserIdentityState
import com.example.data.model.UsernameRecord
import com.example.data.remote.CommunityRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(
  private val repo: CommunityRepository,
  private val currentUserId: String,
  private val userDisplayName: String,
  private val isAdmin: Boolean,
) : ViewModel() {

  private val _identityState = MutableStateFlow(UserIdentityState(uid = currentUserId))
  val identityState: StateFlow<UserIdentityState> = _identityState.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

  private val _lookupResult = MutableStateFlow<UsernameRecord?>(null)
  val lookupResult: StateFlow<UsernameRecord?> = _lookupResult.asStateFlow()

  private val _lookupError = MutableStateFlow<String?>(null)
  val lookupError: StateFlow<String?> = _lookupError.asStateFlow()

  private val _activeDmPeer = MutableStateFlow<UsernameRecord?>(null)
  val activeDmPeer: StateFlow<UsernameRecord?> = _activeDmPeer.asStateFlow()

  private val _activeDmThreadId = MutableStateFlow<String?>(null)
  val activeDmThreadId: StateFlow<String?> = _activeDmThreadId.asStateFlow()

  private val _activeRoomId = MutableStateFlow<String?>(null)
  val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

  val officialAdmins: StateFlow<List<UsernameRecord>> = repo.observeOfficialAdminHandles()
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val dmThreads: StateFlow<List<DecryptedDmThread>> = repo.observeMyDmThreads()
    .map { list -> list.map { decryptThreadSummary(it) } }
    .catch { err ->
      val fs = err.unwrapFirestore()
      if (fs == null && err is CancellationException) throw err
      _statusMessages.tryEmit(fs?.message ?: "Unable to sync encrypted chats.")
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val activeDmMessages: StateFlow<List<DecryptedDmMessage>> = _activeDmThreadId
    .flatMapLatest { threadId ->
      if (threadId.isNullOrBlank()) flowOf(emptyList())
      else repo.observeDmMessages(threadId).map { msgs ->
        val myKeys = repo.crypto.getOrCreateIdentityKeys(currentUserId)
        msgs.map { m ->
          val isMine = m.senderUid == currentUserId
          val peerPub = if (isMine) m.recipientPublicKey else m.senderPublicKey
          val aes = runCatching {
            repo.crypto.deriveSharedAesKey(myKeys.privateKey, peerPub, threadId)
          }.getOrNull()
          val plain = aes?.let { repo.crypto.decryptText(it, m.ciphertextBase64, m.ivBase64) }
          if (plain != null) {
            val decoded = E2eePayload.fromJson(plain)
            DecryptedDmMessage(m, isMine, decoded.text, decoded.imageBase64, false)
          } else {
            DecryptedDmMessage(m, isMine, "Encrypted with a different safety key", "", true)
          }
        }
      }
    }
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val rawRooms: StateFlow<List<CommunityRoom>> = repo.observePublicRooms()
    .catch { err ->
      val fs = err.unwrapFirestore()
      if (fs == null && err is CancellationException) throw err
      _statusMessages.tryEmit(fs?.message ?: "Unable to load community groups & channels.")
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val joinedRoomIds: StateFlow<Set<String>> = rawRooms
    .flatMapLatest { rooms ->
      if (rooms.isEmpty()) flowOf(emptySet())
      else combine(rooms.map { r -> repo.observeMyMembership(r.id).map { joined -> r.id to joined } }) { pairs ->
        pairs.filter { it.second }.map { it.first }.toSet()
      }
    }
    .catch { emit(emptySet()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

  val rooms: StateFlow<List<DecryptedRoom>> = combine(rawRooms, joinedRoomIds) { list, joined ->
    list.map { room ->
      val preview = if (room.lastPostCiphertext.isNotBlank() && room.lastPostIv.isNotBlank()) {
        val key = repo.crypto.deriveRoomAesKey(room.id, room.roomKeyEnvelope)
        repo.crypto.decryptText(key, room.lastPostCiphertext, room.lastPostIv)
          ?: "Encrypted update"
      } else {
        room.description.ifBlank { "End-to-end encrypted ${room.roomType}" }
      }
      DecryptedRoom(
        room = room,
        isJoined = room.id in joined,
        lastPreview = preview,
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val activeRoom: StateFlow<DecryptedRoom?> = combine(_activeRoomId, rooms) { id, list ->
    if (id.isNullOrBlank()) null else list.firstOrNull { it.room.id == id }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val activeRoomMessages: StateFlow<List<DecryptedRoomMessage>> = combine(_activeRoomId, rawRooms) { id, allRooms ->
    id to allRooms.firstOrNull { it.id == id }
  }.flatMapLatest { (id, room) ->
    if (id.isNullOrBlank() || room == null) flowOf(emptyList())
    else repo.observeRoomMessages(id).map { msgs ->
      val aes = repo.crypto.deriveRoomAesKey(room.id, room.roomKeyEnvelope)
      msgs.map { m ->
        val plain = repo.crypto.decryptText(aes, m.ciphertextBase64, m.ivBase64)
        if (plain != null) {
          val decoded = E2eePayload.fromJson(plain)
          DecryptedRoomMessage(m, m.senderUid == currentUserId, decoded.text, decoded.imageBase64, false)
        } else {
          DecryptedRoomMessage(m, m.senderUid == currentUserId, "Unable to decrypt message", "", true)
        }
      }
    }
  }.catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val statuses: StateFlow<List<DecryptedStatus>> = repo.observeStatuses()
    .map { list -> list.map { decryptStatusItem(it) } }
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  init {
    viewModelScope.launch {
      repo.syncLocalIdentity(userDisplayName, isAdmin).onSuccess {
        _identityState.value = it
      }
    }
  }

  fun claimUsername(handle: String, bio: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      repo.claimUsername(handle, bio, isAdmin)
        .onSuccess {
          _identityState.value = it
          _statusMessages.tryEmit("Username @${it.claimedUsername} active with E2EE keys.")
          onSuccess()
        }
        .onFailure {
          _statusMessages.tryEmit(it.message ?: "Failed to save username.")
        }
      _isBusy.value = false
    }
  }

  fun toggleLowDataMode() {
    val next = !_identityState.value.lowDataMode
    _identityState.value = _identityState.value.copy(lowDataMode = next)
    viewModelScope.launch {
      repo.updateLowDataMode(next)
        .onSuccess {
          _statusMessages.tryEmit(
            if (next) "Low-Data Mode ON (WhatsApp-style ~40KB media cap)"
            else "Standard Quality Mode enabled"
          )
        }
    }
  }

  fun searchUsername(rawHandle: String) {
    viewModelScope.launch {
      _lookupError.value = null
      _lookupResult.value = null
      _isBusy.value = true
      repo.lookupUsername(rawHandle)
        .onSuccess { found ->
          if (found == null) _lookupError.value = "No user found with @$rawHandle"
          else _lookupResult.value = found
        }
        .onFailure { _lookupError.value = it.message ?: "Search failed" }
      _isBusy.value = false
    }
  }

  fun clearLookup() {
    _lookupResult.value = null
    _lookupError.value = null
  }

  fun openDmWithRecord(peer: UsernameRecord) {
    val threadId = listOf(currentUserId, peer.uid).sorted().joinToString("_")
    _activeDmPeer.value = peer
    _activeDmThreadId.value = threadId
  }

  fun openDmThread(thread: DecryptedDmThread) {
    _activeDmPeer.value = UsernameRecord(
      id = thread.peerUsername,
      username = thread.peerUsername,
      uid = thread.peerUid,
      publicKeyBase64 = thread.peerPublicKey,
      keyFingerprint = thread.safetyNumber,
    )
    _activeDmThreadId.value = thread.thread.id
  }

  fun closeDmChat() {
    _activeDmThreadId.value = null
    _activeDmPeer.value = null
  }

  fun sendDm(text: String, imageUri: Uri? = null) {
    val peer = _activeDmPeer.value ?: return
    val myUsername = _identityState.value.claimedUsername
    if (myUsername.isBlank()) {
      _statusMessages.tryEmit("Claim a @username first before sending private messages.")
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val imgB64 = if (imageUri != null) {
        repo.crypto.compressUriImageToLowDataBase64(imageUri, _identityState.value.lowDataMode).orEmpty()
      } else ""
      repo.sendDmMessage(
        peerUid = peer.uid,
        peerUsername = peer.username,
        peerPublicKey = peer.publicKeyBase64,
        myUsername = myUsername,
        text = text,
        imageBase64 = imgB64,
      ).onFailure {
        _statusMessages.tryEmit(it.message ?: "Failed to send encrypted message.")
      }
      _isBusy.value = false
    }
  }

  fun openRoom(roomId: String) {
    _activeRoomId.value = roomId
  }

  fun closeRoom() {
    _activeRoomId.value = null
  }

  fun toggleJoinRoom(room: DecryptedRoom) {
    viewModelScope.launch {
      repo.toggleRoomMembership(room.room.id, room.isJoined)
        .onSuccess { joinedNow ->
          val verb = if (room.room.isChannel) {
            if (joinedNow) "Following anonymously" else "Unfollowed"
          } else {
            if (joinedNow) "Joined anonymously" else "Left group"
          }
          _statusMessages.tryEmit("$verb (${room.room.title})")
        }
        .onFailure { _statusMessages.tryEmit(it.message ?: "Membership update failed.") }
    }
  }

  fun sendRoomMessage(text: String, imageUri: Uri? = null) {
    val current = activeRoom.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      val imgB64 = if (imageUri != null) {
        repo.crypto.compressUriImageToLowDataBase64(imageUri, _identityState.value.lowDataMode).orEmpty()
      } else ""
      repo.sendRoomMessage(
        room = current.room,
        isAdmin = isAdmin,
        text = text,
        imageBase64 = imgB64,
      ).onFailure {
        _statusMessages.tryEmit(it.message ?: "Could not post encrypted message.")
      }
      _isBusy.value = false
    }
  }

  fun createRoom(
    title: String,
    description: String,
    roomType: String,
    category: String,
    adminsOnlyPosting: Boolean,
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repo.createCommunityRoom(title, description, roomType, category, adminsOnlyPosting)
        .onSuccess {
          _statusMessages.tryEmit("Created E2EE ${if (roomType == CommunityRoom.TYPE_CHANNEL) "Channel" else "Group"}")
          onDone()
        }
        .onFailure { _statusMessages.tryEmit(it.message ?: "Creation failed") }
      _isBusy.value = false
    }
  }

  fun seedOfficialRooms() {
    viewModelScope.launch {
      _isBusy.value = true
      repo.seedDefaultJokerRooms()
        .onSuccess { _statusMessages.tryEmit("Official Joker Channel & SSC/HSC Groups ready.") }
        .onFailure { _statusMessages.tryEmit(it.message ?: "Failed to initialize official rooms.") }
      _isBusy.value = false
    }
  }

  fun toggleRoomPostingLock(roomId: String, adminsOnly: Boolean) {
    viewModelScope.launch {
      repo.toggleRoomPostingLock(roomId, adminsOnly)
        .onSuccess {
          _statusMessages.tryEmit(if (adminsOnly) "Group set to Admins Only" else "All members can now chat")
        }
    }
  }

  fun deleteRoom(roomId: String) {
    viewModelScope.launch {
      repo.deleteCommunityRoom(roomId)
        .onSuccess {
          if (_activeRoomId.value == roomId) _activeRoomId.value = null
          _statusMessages.tryEmit("Room deleted.")
        }
    }
  }

  fun deleteRoomMessage(roomId: String, messageId: String) {
    viewModelScope.launch {
      repo.deleteRoomMessage(roomId, messageId)
    }
  }

  fun postStatus(
    text: String,
    imageUri: Uri?,
    accentHex: String,
    isPublicStatus: Boolean,
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val imgB64 = if (imageUri != null) {
        repo.crypto.compressUriImageToLowDataBase64(imageUri, _identityState.value.lowDataMode).orEmpty()
      } else ""
      val contacts = dmThreads.value.map { it.peerUid to it.peerPublicKey }
      repo.postStatus(
        text = text,
        imageBase64 = imgB64,
        accentHex = accentHex,
        isAdmin = isAdmin,
        isPublicStatus = isPublicStatus,
        senderUsername = _identityState.value.claimedUsername,
        contactRecipients = contacts,
      ).onSuccess {
        _statusMessages.tryEmit("Encrypted 24h status posted.")
        onDone()
      }.onFailure {
        _statusMessages.tryEmit(it.message ?: "Failed to post status.")
      }
      _isBusy.value = false
    }
  }

  fun markStatusViewed(status: DecryptedStatus) {
    if (!status.isMine) {
      viewModelScope.launch { repo.incrementStatusViewCount(status.raw.id) }
    }
  }

  fun deleteStatus(statusId: String) {
    viewModelScope.launch {
      repo.deleteStatus(statusId)
        .onSuccess { _statusMessages.tryEmit("Status deleted.") }
    }
  }

  fun decodeBitmap(base64: String): Bitmap? = repo.crypto.decodeBase64ToBitmap(base64)

  fun computeSafetyNumber(peerPublicKey: String): String {
    val myPub = _identityState.value.publicKeyBase64
    return if (myPub.isBlank() || peerPublicKey.isBlank()) "N/A"
    else repo.crypto.computePairwiseSafetyNumber(myPub, peerPublicKey)
  }

  private fun decryptThreadSummary(thread: DmThread): DecryptedDmThread {
    val myIdx = thread.participantUids.indexOf(currentUserId)
    val peerIdx = if (myIdx == 0) 1 else 0
    val peerUid = thread.participantUids.getOrNull(peerIdx).orEmpty()
    val peerUsername = thread.participantUsernames.getOrNull(peerIdx).orEmpty().ifBlank { "user" }
    val peerPub = thread.participantPublicKeys.getOrNull(peerIdx).orEmpty()
    val myKeys = repo.crypto.getOrCreateIdentityKeys(currentUserId)

    val preview = if (thread.lastMessageCiphertext.isNotBlank() && peerPub.isNotBlank()) {
      runCatching {
        val aes = repo.crypto.deriveSharedAesKey(myKeys.privateKey, peerPub, thread.id)
        repo.crypto.decryptText(aes, thread.lastMessageCiphertext, thread.lastMessageIv)
      }.getOrNull() ?: "Encrypted message"
    } else "Encrypted chat"

    return DecryptedDmThread(
      thread = thread,
      peerUid = peerUid,
      peerUsername = peerUsername,
      peerPublicKey = peerPub,
      safetyNumber = if (peerPub.isNotBlank()) repo.crypto.computePairwiseSafetyNumber(myKeys.publicKeyBase64, peerPub) else "",
      lastPreview = preview,
    )
  }

  private fun decryptStatusItem(st: StatusUpdate): DecryptedStatus {
    val myKeys = repo.crypto.getOrCreateIdentityKeys(currentUserId)
    val aesKey = if (st.isPublic) {
      repo.crypto.deriveCommunityStatusKey(st.id)
    } else {
      repo.crypto.unwrapStatusKeyForMe(currentUserId, myKeys.privateKey, st.authorPublicKey, st.wrappedKeys)
    }
    val plain = aesKey?.let { repo.crypto.decryptText(it, st.ciphertextBase64, st.ivBase64) }
    val payload = E2eePayload.fromJson(plain)
    return DecryptedStatus(
      raw = st,
      isMine = st.authorUid == currentUserId,
      text = payload.text,
      imageBase64 = payload.imageBase64,
      canDecrypt = plain != null,
    )
  }

  private fun Throwable.unwrapFirestore(): FirebaseFirestoreException? =
    generateSequence(this) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
}

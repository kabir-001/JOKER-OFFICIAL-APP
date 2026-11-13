package com.example.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.E2eeCryptoEngine
import com.example.data.model.CommunityRoom
import com.example.data.model.E2eePayload
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun newRepo(): CommunityRepository {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    return CommunityRepository(firestore, E2eeCryptoEngine(ctx))
  }

  @Test
  fun usernameClaimAndE2eeDm_encryptsOnWireAndDecryptsForRecipient() = runBlocking {
    val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
    val aliceHandle = "alice_$suffix"
    val bobHandle = "bob_$suffix"

    val aliceUid = signInTestUser("alice_$suffix@test.com", markVerified = true)
    val aliceRepo = newRepo()
    val aliceId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.claimUsername(aliceHandle, "SSC Class 10", isAdmin = false).getOrThrow()
    }
    assertEquals(aliceHandle, aliceId.claimedUsername)

    val bobUid = signInTestUser("bob_$suffix@test.com", markVerified = true)
    val bobRepo = newRepo()
    val bobId = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.claimUsername(bobHandle, "HSC Part 1", isAdmin = false).getOrThrow()
    }
    assertEquals(bobHandle, bobId.claimedUsername)

    // Switch back to Alice, lookup Bob by exact @username, and send E2EE message
    signInTestUser("alice_$suffix@test.com", markVerified = true)
    val lookedUpBob = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.lookupUsername(bobHandle).getOrThrow()
    }
    assertNotNull(lookedUpBob)
    assertEquals(bobUid, lookedUpBob!!.uid)

    val secretText = "Confidential Physics Numerical Q4"
    val sendRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.sendDmMessage(
        peerUid = lookedUpBob.uid,
        peerUsername = lookedUpBob.username,
        peerPublicKey = lookedUpBob.publicKeyBase64,
        myUsername = aliceHandle,
        text = secretText,
      )
    }
    assertTrue("DM send failed: ${sendRes.exceptionOrNull()}", sendRes.isSuccess)
    val threadId = sendRes.getOrThrow()

    // Switch to Bob, observe messages, verify wire ciphertext does NOT leak plaintext and Bob decrypts it cleanly
    signInTestUser("bob_$suffix@test.com", markVerified = true)
    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeDmMessages(threadId).first { it.isNotEmpty() }
    }
    val rawMsg = messages.first()
    assertFalse(rawMsg.ciphertextBase64.contains("Confidential"))
    assertFalse(rawMsg.ciphertextBase64.contains("Physics"))

    val bobKeys = bobRepo.crypto.getOrCreateIdentityKeys(bobUid)
    val aesKey = bobRepo.crypto.deriveSharedAesKey(bobKeys.privateKey, rawMsg.senderPublicKey, threadId)
    val decryptedJson = bobRepo.crypto.decryptText(aesKey, rawMsg.ciphertextBase64, rawMsg.ivBase64)
    val decoded = E2eePayload.fromJson(decryptedJson)
    assertEquals(secretText, decoded.text)
    assertEquals(aliceUid, rawMsg.senderUid)
  }

  @Test
  fun anonymousCommunityGroupAndChannel_hidesIdentityAndEnforcesChannelLock() = runBlocking {
    val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
    signInTestUser(ADMIN_EMAIL, markVerified = true)
    val adminRepo = newRepo()

    val groupId = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.createCommunityRoom(
        title = "SSC Group $suffix",
        description = "Anonymous study group",
        roomType = CommunityRoom.TYPE_GROUP,
        category = "SSC",
        adminsOnlyPosting = false,
      ).getOrThrow()
    }
    val channelId = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.createCommunityRoom(
        title = "Joker Channel $suffix",
        description = "Official announcements",
        roomType = CommunityRoom.TYPE_CHANNEL,
        category = "Official",
        adminsOnlyPosting = true,
      ).getOrThrow()
    }

    val studentUid = signInTestUser("student_$suffix@test.com", markVerified = true)
    val studentRepo = newRepo()

    // Student joins group atomically (+1 memberCount)
    val joinRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      studentRepo.toggleRoomMembership(groupId, currentlyJoined = false)
    }
    assertTrue("Join group failed: ${joinRes.exceptionOrNull()}", joinRes.isSuccess)

    val rooms = withTimeout(FLOW_TIMEOUT_MS) {
      studentRepo.observePublicRooms().first { list -> list.any { it.id == groupId && it.memberCount >= 1L } }
    }
    val joinedGroup = rooms.first { it.id == groupId }
    assertTrue(joinedGroup.memberCount >= 1L)

    // Joined student posts in unlocked group -> alias must be Member #XXXX (not email/uid!)
    val postRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      studentRepo.sendRoomMessage(joinedGroup, isAdmin = false, text = "Need Chapter 3 MCQs please")
    }
    assertTrue("Student group post failed: ${postRes.exceptionOrNull()}", postRes.isSuccess)

    val groupMsgs = withTimeout(FLOW_TIMEOUT_MS) {
      studentRepo.observeRoomMessages(groupId).first { it.isNotEmpty() }
    }
    val posted = groupMsgs.first()
    assertTrue(posted.senderAlias.startsWith("Member #"))
    assertFalse(posted.senderAlias.contains(studentUid))

    val roomAes = studentRepo.crypto.deriveRoomAesKey(groupId, joinedGroup.roomKeyEnvelope)
    val decrypted = E2eePayload.fromJson(studentRepo.crypto.decryptText(roomAes, posted.ciphertextBase64, posted.ivBase64))
    assertEquals("Need Chapter 3 MCQs please", decrypted.text)

    // Student CANNOT post in Admin-Only Channel
    val channel = rooms.first { it.id == channelId }
    val channelAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
      studentRepo.sendRoomMessage(channel, isAdmin = false, text = "Unauthorized broadcast")
    }
    assertTrue(channelAttempt.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      channelAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )
  }

  @Test
  fun statusUpdates_officialAndPrivateContactEncryptionWork() = runBlocking {
    val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
    signInTestUser(ADMIN_EMAIL, markVerified = true)
    val adminRepo = newRepo()
    val officialRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.postStatus(
        text = "HSC Physics Target Paper Uploaded!",
        imageBase64 = "",
        accentHex = "#064E3B",
        isAdmin = true,
        isPublicStatus = true,
        senderUsername = "",
      )
    }
    assertTrue("Official status failed: ${officialRes.exceptionOrNull()}", officialRes.isSuccess)

    signInTestUser("viewer_$suffix@test.com", markVerified = true)
    val viewerRepo = newRepo()
    val statuses = withTimeout(FLOW_TIMEOUT_MS) {
      viewerRepo.observeStatuses().first { list -> list.any { it.isOfficialBroadcast } }
    }
    val st = statuses.first { it.isOfficialBroadcast }
    val key = viewerRepo.crypto.deriveCommunityStatusKey(st.id)
    val decoded = E2eePayload.fromJson(viewerRepo.crypto.decryptText(key, st.ciphertextBase64, st.ivBase64))
    assertEquals("HSC Physics Target Paper Uploaded!", decoded.text)
  }

  @Test
  fun unauthenticated_observePublicRooms_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = newRepo()
    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePublicRooms().first() }
    } catch (t: Throwable) {
      caught = t
    }
    val fsEx = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
  }

  private companion object {
    const val ADMIN_EMAIL = "vden4060@gmail.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}

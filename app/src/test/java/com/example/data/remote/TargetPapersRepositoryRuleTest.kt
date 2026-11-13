package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.HelpContact
import com.example.data.model.PaperQuestion
import com.example.data.model.PaperSection
import com.example.data.model.SupportConfig
import com.example.data.model.TargetPaper
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPapersRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun samplePaper(status: String = TargetPaper.STATUS_PUBLISHED): TargetPaper =
    TargetPaper(
      title = "Target Physics ${UUID.randomUUID().toString().take(6)}",
      subject = "Physics",
      classLevel = "10th Class",
      examTerm = "Annual Board 2026",
      totalMarks = 75L,
      durationText = "3 Hours",
      instructions = "Attempt all MCQs.",
      sections = listOf(
        PaperSection(
          heading = "Section A: Objective",
          marksLabel = "15 Marks",
          questions = listOf(
            PaperQuestion(
              number = 1L,
              text = "Unit of force is?",
              marks = 1L,
              options = listOf("Newton", "Joule", "Watt", "Pascal"),
              answer = "Newton",
            )
          ),
        )
      ),
      status = status,
    )

  @Test
  fun admin_createPublishAndObservePublishedPapers_succeeds() = runBlocking {
    val additionalAdmins = listOf(
      "karachieduofficial@gmail.com",
      "kbir94720@gmail.com",
      "joker94720@gmail.com",
    )
    for (extraAdmin in additionalAdmins) {
      signInTestUser(extraAdmin, markVerified = true)
      val extraRepo = TargetPapersRepository(firestore)
      val extraRes = withTimeout(DEFAULT_TIMEOUT_MS) { extraRepo.savePaper(samplePaper(TargetPaper.STATUS_DRAFT)) }
      assertTrue("Admin $extraAdmin savePaper failed: ${extraRes.exceptionOrNull()}", extraRes.isSuccess)
    }

    signInTestUser(ADMIN_EMAIL, markVerified = true)
    val repo = TargetPapersRepository(firestore)

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.savePaper(samplePaper(TargetPaper.STATUS_PUBLISHED)) }
    assertTrue("Admin savePaper failed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
    val paperId = createResult.getOrThrow()

    signInTestUser(STUDENT_EMAIL, markVerified = true)
    val studentRepo = TargetPapersRepository(firestore)
    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      studentRepo.observePublishedPapers().first { list -> list.any { it.id == paperId } }
    }
    assertTrue(observed.any { it.id == paperId })
  }

  @Test
  fun student_savePaper_failsWithPermissionDenied() = runBlocking {
    signInTestUser(STUDENT_EMAIL, markVerified = true)
    val repo = TargetPapersRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.savePaper(samplePaper()) }
    assertTrue(result.isFailure)
    val firestoreException = result.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun admin_saveSupportConfig_studentCanObserve() = runBlocking {
    signInTestUser(ADMIN_EMAIL, markVerified = true)
    val adminRepo = TargetPapersRepository(firestore)
    val config = SupportConfig(
      isPublic = true,
      whatsappChannelName = "JOKER OFFICIAL Channel",
      whatsappChannelUrl = "https://whatsapp.com/channel/0029VaZnOTr5vKA6nXxuWw3U",
      sscGroupName = "SSC (IX/X) Session",
      sscGroupUrl = "https://chat.whatsapp.com/C4IFHnqFt7dEojcnvGYHUK",
      hscGroupName = "HSC (XI/XII) Session",
      hscGroupUrl = "https://chat.whatsapp.com/KBZIZeWc2VXJoX8lY0bzCW",
      supportNote = "Contact us on WhatsApp or call.",
      helpContacts = listOf(
        HelpContact(
          label = "Joker Official Helpline 1",
          phoneNumber = "+923143581887",
          allowCall = true,
          allowWhatsApp = true,
        )
      ),
    )
    val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) { adminRepo.saveSupportConfig(config) }
    assertTrue("Admin saveSupportConfig failed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

    signInTestUser(STUDENT_EMAIL, markVerified = true)
    val studentRepo = TargetPapersRepository(firestore)
    val loaded = withTimeout(FLOW_TIMEOUT_MS) {
      studentRepo.observeSupportConfig().first { it != null }
    }
    assertEquals("JOKER OFFICIAL Channel", loaded?.whatsappChannelName)
    assertEquals("https://chat.whatsapp.com/C4IFHnqFt7dEojcnvGYHUK", loaded?.sscGroupUrl)
    assertEquals("https://chat.whatsapp.com/KBZIZeWc2VXJoX8lY0bzCW", loaded?.hscGroupUrl)
    assertEquals(1, loaded?.helpContacts?.size)
  }

  @Test
  fun unauthenticated_observePublishedPapers_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = TargetPapersRepository(firestore)
    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePublishedPapers().first() }
    } catch (t: Throwable) {
      caught = t
    }
    val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ADMIN_EMAIL = "vden4060@gmail.com"
    const val STUDENT_EMAIL = "student@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}

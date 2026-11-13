package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.SupportConfig
import com.example.data.model.TargetPaper
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TargetPapersRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observePublishedPapers(): Flow<List<TargetPaper>> =
    db.collection(COLLECTION_PAPERS)
      .whereEqualTo("status", TargetPaper.STATUS_PUBLISHED)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(TargetPaper::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_PAPERS)
        }
        throw error
      }

  fun observeAllPapersForAdmin(): Flow<List<TargetPaper>> =
    db.collection(COLLECTION_PAPERS)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(TargetPaper::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_PAPERS)
        }
        throw error
      }

  suspend fun getPublishedPaperById(paperId: String): Result<TargetPaper?> {
    val docRef = db.collection(COLLECTION_PAPERS).document(paperId)
    return try {
      val snapshot = docRef.get().await()
      val paper = snapshot.toObject(TargetPaper::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      Result.success(paper)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun savePaper(paper: TargetPaper): Result<String> {
    val uid = requireUserId()
    val isNew = paper.id.isBlank()
    val docRef = if (isNew) {
      db.collection(COLLECTION_PAPERS).document()
    } else {
      db.collection(COLLECTION_PAPERS).document(paper.id)
    }

    val sectionsList = paper.sections.map { section ->
      mapOf(
        "heading" to section.heading.trim(),
        "marksLabel" to section.marksLabel.trim(),
        "questions" to section.questions.mapIndexed { idx, q ->
          mapOf(
            "number" to (if (q.number > 0) q.number else (idx + 1).toLong()),
            "text" to q.text.trim(),
            "marks" to q.marks.coerceAtLeast(0L),
            "options" to q.options.map { it.trim() }.filter { it.isNotEmpty() },
            "answer" to q.answer.trim(),
          )
        },
      )
    }

    val payload = mutableMapOf<String, Any>(
      "title" to paper.title.trim(),
      "subject" to paper.subject.trim(),
      "classLevel" to paper.classLevel.trim(),
      "examTerm" to paper.examTerm.trim(),
      "totalMarks" to paper.totalMarks.coerceIn(0L, 1000L),
      "durationText" to paper.durationText.trim(),
      "instructions" to paper.instructions.trim(),
      "externalPdfUrl" to paper.externalPdfUrl.trim(),
      "sections" to sectionsList,
      "status" to if (paper.status == TargetPaper.STATUS_DRAFT) TargetPaper.STATUS_DRAFT else TargetPaper.STATUS_PUBLISHED,
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    if (paper.pdfFileName.isNotBlank() || paper.pdfBase64.isNotBlank() || paper.pdfSizeBytes > 0L || paper.pdfPageCount > 0L) {
      payload["pdfFileName"] = paper.pdfFileName.trim().take(180)
      payload["pdfSizeBytes"] = paper.pdfSizeBytes.coerceIn(0L, 52428800L)
      payload["pdfPageCount"] = paper.pdfPageCount.coerceIn(0L, 500L)
      payload["pdfBase64"] = paper.pdfBase64.take(850000)
    }

    return try {
      if (isNew) {
        payload["createdByUid"] = uid
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        docRef.update(payload).await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (isNew) OperationType.CREATE else OperationType.UPDATE,
        docRef.path,
      )
      Result.failure(e)
    }
  }

  suspend fun togglePaperStatus(paper: TargetPaper): Result<Unit> {
    require(paper.id.isNotBlank()) { "Paper ID is required" }
    val newStatus = if (paper.isPublished) TargetPaper.STATUS_DRAFT else TargetPaper.STATUS_PUBLISHED
    val docRef = db.collection(COLLECTION_PAPERS).document(paper.id)
    return try {
      docRef.update(
        mapOf(
          "status" to newStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deletePaper(paperId: String): Result<Unit> {
    require(paperId.isNotBlank()) { "Paper ID is required" }
    val docRef = db.collection(COLLECTION_PAPERS).document(paperId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeSupportConfig(): Flow<SupportConfig?> {
    val docRef = db.collection(COLLECTION_APP_CONFIG).document(DOC_SUPPORT_SETTINGS)
    return docRef.snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(SupportConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null) {
          handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
        }
        throw error
      }
  }

  suspend fun saveSupportConfig(config: SupportConfig): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection(COLLECTION_APP_CONFIG).document(DOC_SUPPORT_SETTINGS)
    val contactsList = config.helpContacts
      .filter { it.phoneNumber.isNotBlank() || it.label.isNotBlank() }
      .map { contact ->
        mapOf(
          "label" to contact.label.trim().ifBlank { "Help Line" },
          "phoneNumber" to contact.phoneNumber.trim(),
          "allowCall" to contact.allowCall,
          "allowWhatsApp" to contact.allowWhatsApp,
        )
      }

    val payload = mapOf(
      "isPublic" to true,
      "whatsappChannelName" to config.whatsappChannelName.trim(),
      "whatsappChannelUrl" to config.whatsappChannelUrl.trim(),
      "sscGroupName" to config.sscGroupName.trim(),
      "sscGroupUrl" to config.sscGroupUrl.trim(),
      "hscGroupName" to config.hscGroupName.trim(),
      "hscGroupUrl" to config.hscGroupUrl.trim(),
      "supportNote" to config.supportNote.trim(),
      "helpContacts" to contactsList,
      "updatedByUid" to uid,
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(payload).await()
      } else {
        docRef.set(payload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun checkIsAdmin(uid: String, email: String?, isEmailVerified: Boolean): Boolean {
    if (isEmailVerified && email != null && email.trim().lowercase() in BOOTSTRAPPED_ADMIN_EMAILS) {
      return true
    }
    return try {
      val snapshot = db.collection(COLLECTION_ADMINS).document(uid).get().await()
      snapshot.exists()
    } catch (_: Exception) {
      false
    }
  }

  suspend fun ensureUserProfile(displayName: String) {
    val uid = requireUserId()
    val docRef = db.collection(COLLECTION_USERS).document(uid)
    val safeName = displayName.trim().ifBlank { "Student" }.take(80)
    try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        docRef.update(
          mapOf(
            "displayName" to safeName,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "displayName" to safeName,
            "preferredClass" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
    }
  }

  companion object {
    const val COLLECTION_PAPERS = "target_papers"
    const val COLLECTION_APP_CONFIG = "app_config"
    const val COLLECTION_ADMINS = "admins"
    const val COLLECTION_USERS = "users"
    const val DOC_SUPPORT_SETTINGS = "support_settings"
    val BOOTSTRAPPED_ADMIN_EMAILS = setOf(
      "vden4060@gmail.com",
      "karachieduofficial@gmail.com",
      "kbir94720@gmail.com",
      "joker94720@gmail.com",
    )
  }
}

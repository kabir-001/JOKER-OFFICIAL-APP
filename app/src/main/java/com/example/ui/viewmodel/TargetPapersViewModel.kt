package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadedPaperEntity
import com.example.data.local.OfflinePaperRepository
import com.example.data.model.SupportConfig
import com.example.data.model.TargetPaper
import com.example.data.remote.TargetPapersRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
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

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class TargetPapersViewModel(
  private val remoteRepo: TargetPapersRepository,
  private val offlineRepo: OfflinePaperRepository,
  val currentUserId: String,
  val userEmail: String?,
  private val isEmailVerified: Boolean,
  val userDisplayName: String,
) : ViewModel() {

  private val _isAdmin = MutableStateFlow(false)
  val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedClassFilter = MutableStateFlow("All")
  val selectedClassFilter: StateFlow<String> = _selectedClassFilter.asStateFlow()

  private val _selectedSubjectFilter = MutableStateFlow("All")
  val selectedSubjectFilter: StateFlow<String> = _selectedSubjectFilter.asStateFlow()

  private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
  val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

  val publishedPapersState: StateFlow<UiState<List<TargetPaper>>> =
    remoteRepo.observePublishedPapers()
      .map<List<TargetPaper>, UiState<List<TargetPaper>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing published papers", rootError)
        emit(UiState.Error(rootError.message ?: "Unable to sync target papers"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val adminPapersState: StateFlow<UiState<List<TargetPaper>>> =
    _isAdmin
      .flatMapLatest { admin ->
        if (!admin) {
          flowOf(UiState.Success(emptyList()))
        } else {
          remoteRepo.observeAllPapersForAdmin()
            .map<List<TargetPaper>, UiState<List<TargetPaper>>> { UiState.Success(it) }
            .catch { error ->
              val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
              if (firestoreError == null && error is CancellationException) throw error
              val rootError = firestoreError ?: error
              Log.w(TAG, "Error observing admin papers", rootError)
              emit(UiState.Error(rootError.message ?: "Unable to load admin papers"))
            }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val downloadedPapers: StateFlow<List<DownloadedPaperEntity>> =
    offlineRepo.observeDownloadedPapers()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  val downloadedPaperIds: StateFlow<Set<String>> =
    offlineRepo.observeDownloadedIds()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptySet(),
      )

  val supportConfig: StateFlow<SupportConfig> =
    remoteRepo.observeSupportConfig()
      .map { remote ->
        if (remote != null) {
          offlineRepo.cacheSupportConfig(remote)
          remote
        } else {
          offlineRepo.getCachedSupportConfig()
        }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Using cached support config due to sync error", firestoreError ?: error)
        emit(offlineRepo.getCachedSupportConfig())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = offlineRepo.getCachedSupportConfig(),
      )

  val filteredPapers: StateFlow<UiState<List<TargetPaper>>> =
    combine(
      publishedPapersState,
      _searchQuery,
      _selectedClassFilter,
      _selectedSubjectFilter,
    ) { state, query, classFilter, subjectFilter ->
      when (state) {
        is UiState.Loading -> UiState.Loading
        is UiState.Error -> state
        is UiState.Success -> {
          val q = query.trim().lowercase()
          val filtered = state.data.filter { paper ->
            val matchesClass = classFilter == "All" || paper.classLevel.equals(classFilter, ignoreCase = true)
            val matchesSubject = subjectFilter == "All" || paper.subject.equals(subjectFilter, ignoreCase = true)
            val matchesQuery = q.isBlank() ||
              paper.title.lowercase().contains(q) ||
              paper.subject.lowercase().contains(q) ||
              paper.classLevel.lowercase().contains(q) ||
              paper.examTerm.lowercase().contains(q)
            matchesClass && matchesSubject && matchesQuery
          }
          UiState.Success(filtered)
        }
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  init {
    viewModelScope.launch {
      _isAdmin.value = remoteRepo.checkIsAdmin(currentUserId, userEmail, isEmailVerified)
      remoteRepo.ensureUserProfile(userDisplayName)
    }
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun updateClassFilter(classLevel: String) {
    _selectedClassFilter.value = classLevel
  }

  fun updateSubjectFilter(subject: String) {
    _selectedSubjectFilter.value = subject
  }

  fun downloadPaper(paper: TargetPaper) {
    if (paper.id.isBlank()) return
    viewModelScope.launch {
      _downloadingIds.value = _downloadingIds.value + paper.id
      val result = offlineRepo.downloadPaperForOffline(paper)
      _downloadingIds.value = _downloadingIds.value - paper.id
      if (result.isSuccess) {
        _statusMessages.tryEmit("Saved \"${paper.title}\" for offline reading")
      } else {
        _statusMessages.tryEmit("Download failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}")
      }
    }
  }

  fun removeDownload(paperId: String) {
    viewModelScope.launch {
      offlineRepo.removeOfflineDownload(paperId)
      _statusMessages.tryEmit("Removed from offline downloads")
    }
  }

  fun savePaper(paper: TargetPaper, onSaved: () -> Unit) {
    viewModelScope.launch {
      _isSaving.value = true
      val result = remoteRepo.savePaper(paper)
      _isSaving.value = false
      if (result.isSuccess) {
        val label = if (paper.isPublished) "Target paper uploaded & published!" else "Saved as draft."
        _statusMessages.tryEmit(label)
        onSaved()
      } else {
        _statusMessages.tryEmit("Upload failed: ${result.exceptionOrNull()?.localizedMessage ?: "Permission denied"}")
      }
    }
  }

  fun togglePaperStatus(paper: TargetPaper) {
    viewModelScope.launch {
      val result = remoteRepo.togglePaperStatus(paper)
      if (result.isSuccess) {
        val next = if (paper.isPublished) "Moved to Drafts" else "Published to all students"
        _statusMessages.tryEmit(next)
      } else {
        _statusMessages.tryEmit("Failed to update status")
      }
    }
  }

  fun deletePaper(paperId: String) {
    viewModelScope.launch {
      val result = remoteRepo.deletePaper(paperId)
      if (result.isSuccess) {
        offlineRepo.removeOfflineDownload(paperId)
        _statusMessages.tryEmit("Target paper deleted")
      } else {
        _statusMessages.tryEmit("Failed to delete paper")
      }
    }
  }

  fun saveSupportConfig(config: SupportConfig, onSaved: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      val result = remoteRepo.saveSupportConfig(config)
      _isSaving.value = false
      if (result.isSuccess) {
        offlineRepo.cacheSupportConfig(config)
        _statusMessages.tryEmit("WhatsApp Channel & Help Numbers updated!")
        onSaved()
      } else {
        _statusMessages.tryEmit("Save failed: ${result.exceptionOrNull()?.localizedMessage ?: "Permission denied"}")
      }
    }
  }

  suspend fun ensureOfflineAndGetPdfUri(paper: TargetPaper): Uri? {
    val existing = offlineRepo.getDownloadedPaper(paper.id)
    val entity = existing ?: offlineRepo.downloadPaperForOffline(paper).getOrNull()
    return entity?.let { offlineRepo.getShareablePdfUri(it.pdfFilePath) }
  }

  private companion object {
    const val TAG = "TargetPapersVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}

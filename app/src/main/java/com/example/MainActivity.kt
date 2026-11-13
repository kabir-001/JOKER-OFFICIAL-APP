package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiChatViewModel
import com.example.ai.configureAppCheck
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.crypto.E2eeCryptoEngine
import com.example.data.local.OfflinePaperRepository
import com.example.data.model.TargetPaper
import com.example.data.remote.CommunityRepository
import com.example.data.remote.TargetPapersRepository
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AdminPaperEditorScreen
import com.example.ui.screens.AdminUploadScreen
import com.example.ui.screens.AnonymousRoomChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CommunityHubScreen
import com.example.ui.screens.GeminiChatScreen
import com.example.ui.screens.HelpAndWhatsAppScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.OfflineDownloadsScreen
import com.example.ui.screens.PaperReaderScreen
import com.example.ui.screens.PrivateDmChatScreen
import com.example.ui.theme.TargetPapersTheme
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.TargetPapersViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    setContent {
      var isDarkTheme by remember { mutableStateOf(true) }
      TargetPapersTheme(darkTheme = isDarkTheme) {
        AppRootNavigation(
          isDarkTheme = isDarkTheme,
          onToggleTheme = { isDarkTheme = !isDarkTheme },
        )
      }
    }
  }
}

private enum class MainTab(val labelRes: Int, val icon: ImageVector, val testTag: String) {
  PAPERS(R.string.nav_papers, Icons.AutoMirrored.Rounded.MenuBook, "tab_papers"),
  CHATS(R.string.nav_chats, Icons.Rounded.Chat, "tab_chats"),
  GEMINI(R.string.nav_gemini, Icons.Rounded.AutoAwesome, "tab_gemini"),
  HELP(R.string.nav_help, Icons.Rounded.SupportAgent, "tab_help"),
  ADMIN(R.string.nav_admin, Icons.Rounded.AdminPanelSettings, "tab_admin"),
}

@Composable
fun AppRootNavigation(
  isDarkTheme: Boolean = true,
  onToggleTheme: () -> Unit = {},
) {
  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      MainAuthenticatedApp(
        currentUserId = user.uid,
        userEmail = user.email,
        isEmailVerified = user.isEmailVerified,
        userDisplayName = user.displayName ?: "Student",
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAuthenticatedApp(
  currentUserId: String,
  userEmail: String?,
  isEmailVerified: Boolean,
  userDisplayName: String,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val geminiViewModel: GeminiChatViewModel = viewModel(key = "gemini_$currentUserId")
  val viewModel: TargetPapersViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        TargetPapersViewModel(
          remoteRepo = TargetPapersRepository(db),
          offlineRepo = OfflinePaperRepository(app),
          currentUserId = currentUserId,
          userEmail = userEmail,
          isEmailVerified = isEmailVerified,
          userDisplayName = userDisplayName,
        )
      }
    },
  )

  val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
  val communityViewModel: CommunityViewModel = viewModel(
    key = "community_${currentUserId}_$isAdmin",
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        CommunityViewModel(
          repo = CommunityRepository(db, E2eeCryptoEngine(app)),
          currentUserId = currentUserId,
          userDisplayName = userDisplayName,
          isAdmin = isAdmin,
        )
      }
    },
  )

  val filteredPapersState by viewModel.filteredPapers.collectAsStateWithLifecycle()
  val publishedPapersState by viewModel.publishedPapersState.collectAsStateWithLifecycle()
  val adminPapersState by viewModel.adminPapersState.collectAsStateWithLifecycle()
  val downloadedPapers by viewModel.downloadedPapers.collectAsStateWithLifecycle()
  val downloadedIds by viewModel.downloadedPaperIds.collectAsStateWithLifecycle()
  val downloadingIds by viewModel.downloadingIds.collectAsStateWithLifecycle()
  val supportConfig by viewModel.supportConfig.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val classFilter by viewModel.selectedClassFilter.collectAsStateWithLifecycle()
  val subjectFilter by viewModel.selectedSubjectFilter.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

  val identityState by communityViewModel.identityState.collectAsStateWithLifecycle()
  val officialAdmins by communityViewModel.officialAdmins.collectAsStateWithLifecycle()
  val dmThreads by communityViewModel.dmThreads.collectAsStateWithLifecycle()
  val activeDmPeer by communityViewModel.activeDmPeer.collectAsStateWithLifecycle()
  val activeDmMessages by communityViewModel.activeDmMessages.collectAsStateWithLifecycle()
  val communityRooms by communityViewModel.rooms.collectAsStateWithLifecycle()
  val activeRoom by communityViewModel.activeRoom.collectAsStateWithLifecycle()
  val activeRoomMessages by communityViewModel.activeRoomMessages.collectAsStateWithLifecycle()
  val statuses by communityViewModel.statuses.collectAsStateWithLifecycle()
  val lookupResult by communityViewModel.lookupResult.collectAsStateWithLifecycle()
  val lookupError by communityViewModel.lookupError.collectAsStateWithLifecycle()
  val communityBusy by communityViewModel.isBusy.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.statusMessages.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }
  LaunchedEffect(communityViewModel) {
    communityViewModel.statusMessages.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }

  var selectedTabIndex by remember { mutableIntStateOf(0) }
  var papersSubTabIndex by remember { mutableIntStateOf(0) } // 0 = Library, 1 = Offline
  var openedPaper by remember { mutableStateOf<TargetPaper?>(null) }
  var editingPaper by remember { mutableStateOf<TargetPaper?>(null) }
  var showNewPaperEditor by remember { mutableStateOf(false) }
  var showPdfUploadScreen by remember { mutableStateOf(false) }

  val visibleTabs = remember(isAdmin) {
    if (isAdmin) listOf(MainTab.PAPERS, MainTab.CHATS, MainTab.GEMINI, MainTab.HELP, MainTab.ADMIN)
    else listOf(MainTab.PAPERS, MainTab.CHATS, MainTab.GEMINI, MainTab.HELP)
  }

  if (selectedTabIndex >= visibleTabs.size) selectedTabIndex = 0

  when {
    openedPaper != null -> {
      val paper = openedPaper!!
      PaperReaderScreen(
        paper = paper,
        isDownloaded = paper.id in downloadedIds,
        onBack = { openedPaper = null },
        onDownloadOffline = { viewModel.downloadPaper(paper) },
        onExportPdf = { viewModel.ensureOfflineAndGetPdfUri(paper) },
        onAskGemini = {
          geminiViewModel.attachPaperContext(paper)
          openedPaper = null
          selectedTabIndex = visibleTabs.indexOf(MainTab.GEMINI).coerceAtLeast(0)
        },
      )
    }

    showPdfUploadScreen -> {
      AdminUploadScreen(
        userEmail = userEmail,
        isEmailVerified = isEmailVerified,
        isSaving = isSaving,
        onBack = { showPdfUploadScreen = false },
        onUploadPaper = { paper ->
          viewModel.savePaper(paper) {
            showPdfUploadScreen = false
          }
        },
      )
    }

    showNewPaperEditor || editingPaper != null -> {
      AdminPaperEditorScreen(
        initialPaper = editingPaper,
        isSaving = isSaving,
        onBack = {
          showNewPaperEditor = false
          editingPaper = null
        },
        onSavePaper = { paper ->
          viewModel.savePaper(paper) {
            showNewPaperEditor = false
            editingPaper = null
          }
        },
      )
    }

    activeDmPeer != null -> {
      val peer = activeDmPeer!!
      PrivateDmChatScreen(
        peer = peer,
        safetyNumber = communityViewModel.computeSafetyNumber(peer.publicKeyBase64),
        messages = activeDmMessages,
        lowDataMode = identityState.lowDataMode,
        isBusy = communityBusy,
        decodeImage = communityViewModel::decodeBitmap,
        onBack = communityViewModel::closeDmChat,
        onSendMessage = communityViewModel::sendDm,
      )
    }

    activeRoom != null -> {
      val currentRoom = activeRoom!!
      AnonymousRoomChatScreen(
        roomState = currentRoom,
        messages = activeRoomMessages,
        isAdmin = isAdmin,
        lowDataMode = identityState.lowDataMode,
        isBusy = communityBusy,
        decodeImage = communityViewModel::decodeBitmap,
        onBack = communityViewModel::closeRoom,
        onToggleJoin = { communityViewModel.toggleJoinRoom(currentRoom) },
        onTogglePostingLock = { locked ->
          communityViewModel.toggleRoomPostingLock(currentRoom.room.id, locked)
        },
        onSendMessage = communityViewModel::sendRoomMessage,
        onDeleteMessage = { msgId ->
          communityViewModel.deleteRoomMessage(currentRoom.room.id, msgId)
        },
      )
    }

    else -> {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp
        val activeTab = visibleTabs[selectedTabIndex]

        Row(modifier = Modifier.fillMaxSize()) {
          if (useRail) {
            NavigationRail {
              visibleTabs.forEachIndexed { idx, tab ->
                NavigationRailItem(
                  selected = selectedTabIndex == idx,
                  onClick = { selectedTabIndex = idx },
                  icon = { TabIconWithBadge(tab, downloadedPapers.size, dmThreads.size) },
                  label = { Text(stringResource(tab.labelRes)) },
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }

          Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
              TopAppBar(
                title = {
                  Text(
                    text = when (activeTab) {
                      MainTab.PAPERS -> stringResource(R.string.app_name)
                      MainTab.CHATS -> "Joker Encrypted Connect"
                      MainTab.GEMINI -> "Gemini AI"
                      MainTab.HELP -> "WhatsApp & Help"
                      MainTab.ADMIN -> "Admin Panel"
                    },
                    style = MaterialTheme.typography.titleLarge,
                  )
                },
                actions = {
                  IconButton(
                    onClick = {
                      selectedTabIndex = visibleTabs.indexOf(MainTab.PAPERS).coerceAtLeast(0)
                      papersSubTabIndex = 1
                    },
                    modifier = Modifier.testTag("topbar_offline_shortcut"),
                  ) {
                    BadgedBox(
                      badge = {
                        if (downloadedPapers.isNotEmpty()) {
                          Badge { Text(downloadedPapers.size.toString()) }
                        }
                      },
                    ) {
                      Icon(Icons.Rounded.DownloadDone, contentDescription = "Offline Downloads")
                    }
                  }
                  IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.testTag("theme_toggle_button"),
                  ) {
                    Icon(
                      imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                      contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                    )
                  }
                },
              )
            },
            bottomBar = {
              if (!useRail) {
                NavigationBar {
                  visibleTabs.forEachIndexed { idx, tab ->
                    NavigationBarItem(
                      selected = selectedTabIndex == idx,
                      onClick = { selectedTabIndex = idx },
                      icon = { TabIconWithBadge(tab, downloadedPapers.size, dmThreads.size) },
                      label = { Text(stringResource(tab.labelRes)) },
                      modifier = Modifier.testTag(tab.testTag),
                    )
                  }
                }
              }
            },
          ) { innerPadding ->
            val allPublished = (publishedPapersState as? UiState.Success)?.data.orEmpty()
            when (activeTab) {
              MainTab.PAPERS -> {
                Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                  SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 6.dp),
                  ) {
                    SegmentedButton(
                      selected = papersSubTabIndex == 0,
                      onClick = { papersSubTabIndex = 0 },
                      shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) {
                      Text("All Target Papers")
                    }
                    SegmentedButton(
                      selected = papersSubTabIndex == 1,
                      onClick = { papersSubTabIndex = 1 },
                      shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) {
                      Text("Offline Saved (${downloadedPapers.size})")
                    }
                  }
                  if (papersSubTabIndex == 0) {
                    LibraryScreen(
                      papersState = filteredPapersState,
                      allPublishedPapers = allPublished,
                      downloadedIds = downloadedIds,
                      downloadingIds = downloadingIds,
                      searchQuery = searchQuery,
                      selectedClassFilter = classFilter,
                      selectedSubjectFilter = subjectFilter,
                      supportConfig = supportConfig,
                      onSearchQueryChange = viewModel::updateSearchQuery,
                      onClassFilterChange = viewModel::updateClassFilter,
                      onSubjectFilterChange = viewModel::updateSubjectFilter,
                      onOpenPaper = { openedPaper = it },
                      onDownloadPaper = viewModel::downloadPaper,
                      onNavigateToHelp = {
                        selectedTabIndex = visibleTabs.indexOf(MainTab.HELP).coerceAtLeast(0)
                      },
                      modifier = Modifier.weight(1f),
                    )
                  } else {
                    OfflineDownloadsScreen(
                      downloadedPapers = downloadedPapers,
                      onOpenPaper = { openedPaper = it },
                      onRemoveDownload = viewModel::removeDownload,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }

              MainTab.CHATS -> {
                CommunityHubScreen(
                  identity = identityState,
                  isAdmin = isAdmin,
                  isBusy = communityBusy,
                  officialAdmins = officialAdmins,
                  dmThreads = dmThreads,
                  rooms = communityRooms,
                  statuses = statuses,
                  lookupResult = lookupResult,
                  lookupError = lookupError,
                  decodeImage = communityViewModel::decodeBitmap,
                  onClaimUsername = communityViewModel::claimUsername,
                  onToggleLowDataMode = communityViewModel::toggleLowDataMode,
                  onSearchUsername = communityViewModel::searchUsername,
                  onClearLookup = communityViewModel::clearLookup,
                  onOpenDmWithRecord = communityViewModel::openDmWithRecord,
                  onOpenDmThread = communityViewModel::openDmThread,
                  onOpenRoom = { room -> communityViewModel.openRoom(room.room.id) },
                  onToggleJoinRoom = communityViewModel::toggleJoinRoom,
                  onCreateRoom = communityViewModel::createRoom,
                  onSeedOfficialRooms = communityViewModel::seedOfficialRooms,
                  onDeleteRoom = communityViewModel::deleteRoom,
                  onPostStatus = communityViewModel::postStatus,
                  onStatusViewed = communityViewModel::markStatusViewed,
                  onDeleteStatus = communityViewModel::deleteStatus,
                  modifier = Modifier.padding(innerPadding),
                )
              }

              MainTab.GEMINI -> {
                GeminiChatScreen(
                  viewModel = geminiViewModel,
                  modifier = Modifier.padding(innerPadding),
                )
              }

              MainTab.HELP -> {
                HelpAndWhatsAppScreen(
                  supportConfig = supportConfig,
                  userDisplayName = userDisplayName,
                  userEmail = userEmail,
                  isAdmin = isAdmin,
                  onSignOut = { signOut(credentialManager = credentialManager, scope = scope) },
                  onOpenAdminPanel = if (isAdmin) {
                    { selectedTabIndex = visibleTabs.indexOf(MainTab.ADMIN).coerceAtLeast(0) }
                  } else null,
                  modifier = Modifier.padding(innerPadding),
                )
              }

              MainTab.ADMIN -> {
                AdminPanelScreen(
                  adminPapersState = adminPapersState,
                  supportConfig = supportConfig,
                  isSaving = isSaving,
                  onCreatePaper = { showNewPaperEditor = true },
                  onUploadPdfPaper = { showPdfUploadScreen = true },
                  onEditPaper = { editingPaper = it },
                  onDuplicatePaper = { paper ->
                    editingPaper = paper.copy(
                      id = "",
                      title = "${paper.title} (Copy)",
                      status = TargetPaper.STATUS_DRAFT,
                      createdAt = null,
                      updatedAt = null,
                    )
                    showNewPaperEditor = true
                  },
                  onToggleStatus = viewModel::togglePaperStatus,
                  onDeletePaper = viewModel::deletePaper,
                  onSaveSupportConfig = { config -> viewModel.saveSupportConfig(config) },
                  modifier = Modifier.padding(innerPadding),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TabIconWithBadge(
  tab: MainTab,
  offlineCount: Int,
  dmCount: Int,
) {
  val count = when (tab) {
    MainTab.CHATS -> dmCount
    else -> 0
  }
  if (count > 0) {
    BadgedBox(badge = { Badge { Text(count.toString()) } }) {
      Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
    }
  } else {
    Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
  }
}

package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.billing.BillingViewModel
import dev.sriramcodessw.versesync.concert.DeleteSetlistDialog
import dev.sriramcodessw.versesync.concert.NewSetlistDialog
import dev.sriramcodessw.versesync.concert.ResumeInfo
import dev.sriramcodessw.versesync.concert.SetlistsViewModel
import dev.sriramcodessw.versesync.concert.setlistsSection
import dev.sriramcodessw.versesync.data.Folder
import dev.sriramcodessw.versesync.data.FreeLimit
import dev.sriramcodessw.versesync.data.OnboardingViewModel
import dev.sriramcodessw.versesync.data.Setlist
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.TrialViewModel
import dev.sriramcodessw.versesync.ui.components.*
import dev.sriramcodessw.versesync.ui.viewmodels.SortOrder
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    songs: List<Song>,
    onNavigateToAddSong: () -> Unit,
    onSongClick: (Int) -> Unit,
    onNavigateToTipJar: () -> Unit,
    onNavigateToRecognition: () -> Unit,
    onNavigateToSettings: () -> Unit,
    verseViewModel: VerseViewModel,
    onboardingViewModel: OnboardingViewModel,
    billingViewModel: BillingViewModel,
    trialViewModel: TrialViewModel,
    setlistsViewModel: SetlistsViewModel,
    onOpenSetlist: (Int) -> Unit,
    onStartConcert: (Int) -> Unit,
    onOpenFolder: (Int) -> Unit,
) {
    var showNewSetlistDialog by rememberSaveable { mutableStateOf(false) }
    var setlistToDelete by remember { mutableStateOf<Setlist?>(null) }
    val setlists by setlistsViewModel.setlists.collectAsState()
    val folderSongCounts by verseViewModel.folderSongCounts.collectAsState()
    // A new setlist opens in the editor. It arrives as a one-shot event, so a rotation neither repeats nor loses it.
    LaunchedEffect(setlistsViewModel) { setlistsViewModel.created.collect { onOpenSetlist(it) } }
    val resume by setlistsViewModel.resume.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val selectedSongs = remember { mutableStateListOf<Song>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val searchQuery by verseViewModel.searchQuery.collectAsState()
    val sortOrder by verseViewModel.sortOrder.collectAsState()
    val folders by verseViewModel.folders.collectAsState()
    val isMainTourCompleted by onboardingViewModel.isMainTourCompleted.collectAsState()
    val isPro by billingViewModel.isPro.collectAsState()
    val remainingTrials by trialViewModel.remainingTrials.collectAsState()
    val looseSongCount by verseViewModel.looseSongCount.collectAsState()

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var pendingProAction by remember { mutableStateOf<String?>(null) }

    // Pro Feature Gates
    if (pendingProAction != null) {
        ProFeatureTrialDialog(
            featureName = pendingProAction!!,
            remainingTrials = remainingTrials,
            onConfirm = {
                trialViewModel.useTrial()
                when (pendingProAction) {
                    "Folders" -> showAddFolderDialog = true
                    "Unlimited Library" -> onNavigateToAddSong()
                }
                pendingProAction = null
            },
            onDismiss = { pendingProAction = null },
            onUpgrade = {
                pendingProAction = null
                onNavigateToTipJar()
            },
        )
    }

    // Tour State
    var currentTourStep by remember { mutableIntStateOf(0) }
    var micIconAnchor by remember { mutableStateOf(Offset.Zero) }
    var micIconSize by remember { mutableStateOf(IntSize.Zero) }
    var addFabAnchor by remember { mutableStateOf(Offset.Zero) }
    var addFabSize by remember { mutableStateOf(IntSize.Zero) }
    var foldersTabAnchor by remember { mutableStateOf(Offset.Zero) }
    var foldersTabSize by remember { mutableStateOf(IntSize.Zero) }

    val tourSteps =
        listOf(
            TourStep("Welcome to VerseSync", "Manage your songs and folders here."),
            TourStep("Bulk Actions", "Long press on any song to select multiple items for deletion or moving."),
            TourStep(
                "Recognition",
                "Identify songs using Hybrid Engine (Pro).",
                anchor = micIconAnchor,
                size = micIconSize,
            ),
            TourStep("Add Music", "Create new lyrics.", anchor = addFabAnchor, size = addFabSize),
            TourStep(
                "Folders and setlists",
                "Folders also holds your setlists: an ordered list of songs for a show, with a big-text screen to sing along.",
                anchor = foldersTabAnchor,
                size = foldersTabSize,
            ),
        )

    if (songToDelete != null) {
        DeleteConfirmationDialog(
            onConfirm = {
                verseViewModel.deleteSong(songToDelete!!)
                songToDelete = null
            },
            onDismiss = { songToDelete = null },
        )
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder") },
            text = { Text("Are you sure you want to delete this folder? Songs inside will be moved to the main list.") },
            confirmButton = {
                TextButton(onClick = {
                    verseViewModel.deleteFolder(folderToDelete!!)
                    folderToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        verseViewModel.addFolder(newFolderName)
                        newFolderName = ""
                        showAddFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showMoveToFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMoveToFolderDialog = false },
            title = { Text("Move Selected to Folder") },
            text = {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("None (Main List)") },
                            modifier =
                                Modifier.clickable {
                                    verseViewModel.moveSongsToFolder(selectedSongs.toList(), null)
                                    selectedSongs.clear()
                                    isSelectionMode = false
                                    showMoveToFolderDialog = false
                                },
                        )
                    }
                    items(folders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier =
                                Modifier.clickable {
                                    verseViewModel.moveSongsToFolder(selectedSongs.toList(), folder.id)
                                    selectedSongs.clear()
                                    isSelectionMode = false
                                    showMoveToFolderDialog = false
                                },
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (isSelectionMode) {
                                Text("${selectedSongs.size} Selected")
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("VerseSync")
                                    if (!isPro) TrialBadge(remainingTrials)
                                }
                            }
                        },
                        navigationIcon = {
                            if (isSelectionMode) {
                                IconButton(onClick = {
                                    isSelectionMode = false
                                    selectedSongs.clear()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                                }
                            }
                        },
                        actions = {
                            if (isSelectionMode) {
                                IconButton(onClick = { showMoveToFolderDialog = true }) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move selected")
                                }
                                IconButton(onClick = {
                                    verseViewModel.deleteSongs(selectedSongs.toList())
                                    selectedSongs.clear()
                                    isSelectionMode = false
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                                }
                            } else {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort library")
                                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Title (A-Z)") },
                                            onClick = {
                                                verseViewModel.updateSortOrder(SortOrder.TITLE)
                                                showSortMenu = false
                                            },
                                            leadingIcon = { if (sortOrder == SortOrder.TITLE) Icon(Icons.Default.Check, null) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Artist") },
                                            onClick = {
                                                verseViewModel.updateSortOrder(SortOrder.ARTIST)
                                                showSortMenu = false
                                            },
                                            leadingIcon = { if (sortOrder == SortOrder.ARTIST) Icon(Icons.Default.Check, null) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Recently Added") },
                                            onClick = {
                                                verseViewModel.updateSortOrder(SortOrder.RECENT)
                                                showSortMenu = false
                                            },
                                            leadingIcon = { if (sortOrder == SortOrder.RECENT) Icon(Icons.Default.Check, null) },
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onNavigateToRecognition,
                                    modifier =
                                        Modifier.onGloballyPositioned {
                                            micIconAnchor = it.positionInRoot()
                                            micIconSize = it.size
                                        },
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = "Recognize Song")
                                }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                        showMenu = false
                                        onNavigateToSettings()
                                    })
                                    DropdownMenuItem(text = { Text("Tip Jar") }, onClick = {
                                        showMenu = false
                                        onNavigateToTipJar()
                                    })
                                }
                            }
                        },
                    )

                    if (!isSelectionMode) {
                        SearchBar(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            inputField = {
                                SearchBarDefaults.InputField(
                                    query = searchQuery,
                                    onQueryChange = { verseViewModel.updateSearchQuery(it) },
                                    onSearch = {},
                                    expanded = false,
                                    onExpandedChange = {},
                                    placeholder = { Text("Search songs...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                )
                            },
                            expanded = false,
                            onExpandedChange = {},
                        ) {}
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("Songs", modifier = Modifier.padding(16.dp))
                        }
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier =
                                Modifier.onGloballyPositioned {
                                    foldersTabAnchor = it.positionInRoot()
                                    foldersTabSize = it.size
                                },
                        ) {
                            Text("Folders", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedTab == 0) {
                                if (FreeLimit.canAddLooseSong(isPro, looseSongCount)) {
                                    onNavigateToAddSong()
                                } else if (remainingTrials > 0) {
                                    pendingProAction = "Unlimited Library"
                                } else {
                                    onNavigateToTipJar()
                                }
                            } else {
                                if (isPro) {
                                    showAddFolderDialog = true
                                } else if (remainingTrials > 0) {
                                    pendingProAction = "Folders"
                                } else {
                                    onNavigateToTipJar()
                                }
                            }
                        },
                        modifier =
                            Modifier.onGloballyPositioned {
                                addFabAnchor = it.positionInRoot()
                                addFabSize = it.size
                            },
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Add else Icons.Default.CreateNewFolder,
                            contentDescription = if (selectedTab == 0) "Add Song" else "Add Folder",
                        )
                    }
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if (selectedTab == 0) {
                    SongListContent(
                        songs = songs,
                        folders = folders,
                        selectedSongs = selectedSongs,
                        isSelectionMode = isSelectionMode,
                        onSongClick = {
                            if (isSelectionMode) {
                                if (selectedSongs.contains(it)) selectedSongs.remove(it) else selectedSongs.add(it)
                                if (selectedSongs.isEmpty()) isSelectionMode = false
                            } else {
                                onSongClick(it.id)
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedSongs.add(it)
                        },
                    )
                } else {
                    FoldersTabContent(
                        folders = folders,
                        onDeleteFolder = { folderToDelete = it },
                        onOpenFolder = onOpenFolder,
                        folderSongCounts = folderSongCounts,
                        setlists = setlists,
                        resume = resume,
                        onResume = onStartConcert,
                        onOpenSetlist = onOpenSetlist,
                        onStartSetlist = onStartConcert,
                        onDeleteSetlist = { setlistToDelete = it },
                        // Concert mode is free: a new setlist never checks Pro status or uses a trial.
                        onNewSetlist = { showNewSetlistDialog = true },
                    )
                }

                if (showNewSetlistDialog) {
                    NewSetlistDialog(
                        onCreate = { name ->
                            showNewSetlistDialog = false
                            setlistsViewModel.create(name)
                        },
                        onDismiss = { showNewSetlistDialog = false },
                    )
                }
                setlistToDelete?.let { setlist ->
                    DeleteSetlistDialog(
                        setlist = setlist,
                        onConfirm = {
                            setlistsViewModel.delete(setlist.id)
                            setlistToDelete = null
                        },
                        onDismiss = { setlistToDelete = null },
                    )
                }
            }
        }
        // Drawn over the whole screen, above the Scaffold's top bar, because the highlighted places are measured from the window.
        if (!isMainTourCompleted) {
            TourOverlay(
                steps = tourSteps,
                currentStepIndex = currentTourStep,
                onNext = {
                    if (currentTourStep < tourSteps.size - 1) {
                        currentTourStep++
                    } else {
                        onboardingViewModel.completeMainTour()
                    }
                },
                onSkip = { onboardingViewModel.completeMainTour() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListContent(
    songs: List<Song>,
    folders: List<Folder>,
    selectedSongs: List<Song>,
    isSelectionMode: Boolean,
    onSongClick: (Song) -> Unit,
    onLongClick: (Song) -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyState("No songs found.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                val isSelected = selectedSongs.contains(song)
                val hasStt = !song.lrcData.isNullOrBlank()
                val hasFingerprint = !song.fingerprint.isNullOrEmpty()

                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(song.title)
                            if (hasStt) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "STT Ready",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (hasFingerprint) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio Fingerprint Ready",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    },
                    supportingContent = {
                        val folderName = folders.find { it.id == song.folderId }?.name ?: "No Folder"
                        Text("${song.artist} • $folderName")
                    },
                    leadingContent =
                        if (isSelectionMode) {
                            { Checkbox(checked = isSelected, onCheckedChange = { onSongClick(song) }) }
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = { onSongClick(song) },
                                onLongClick = { onLongClick(song) },
                            ).background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                )
            }
        }
    }
}

/**
 * The Folders tab: the folders, then the setlists (ADR-20), in one scrolling list, so the main screen
 * has only two tabs, Songs and Folders.
 */
@Composable
fun FoldersTabContent(
    folders: List<Folder>,
    onDeleteFolder: (Folder) -> Unit,
    onOpenFolder: (Int) -> Unit,
    folderSongCounts: Map<Int, Int>,
    setlists: List<Setlist>,
    resume: ResumeInfo?,
    onResume: (Int) -> Unit,
    onOpenSetlist: (Int) -> Unit,
    onStartSetlist: (Int) -> Unit,
    onDeleteSetlist: (Setlist) -> Unit,
    onNewSetlist: () -> Unit,
) {
    // Room at the bottom, so the add button never covers the last row.
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "folders-header") {
            Text(
                "Folders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        if (folders.isEmpty()) {
            item(key = "folders-empty") {
                Text(
                    "No folders yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(folders, key = { "folder-${it.id}" }) { folder ->
            ListItem(
                headlineContent = { Text(folder.name) },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                supportingContent = { Text(songCountText(folderSongCounts[folder.id] ?: 0)) },
                trailingContent = {
                    IconButton(onClick = { onDeleteFolder(folder) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                modifier = Modifier.clickable { onOpenFolder(folder.id) },
            )
        }
        setlistsSection(
            setlists = setlists,
            resume = resume,
            onResume = onResume,
            onOpen = onOpenSetlist,
            onStart = onStartSetlist,
            onDelete = onDeleteSetlist,
            onNew = onNewSetlist,
        )
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "No songs", "1 song" or "3 songs", for a folder row. */
internal fun songCountText(count: Int): String =
    when (count) {
        0 -> "No songs"
        1 -> "1 song"
        else -> "$count songs"
    }

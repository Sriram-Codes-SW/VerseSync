package dev.sriramcodessw.versesync

import android.Manifest
import android.app.DownloadManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.sriramcodessw.versesync.billing.BillingViewModel
import dev.sriramcodessw.versesync.billing.BillingViewModelFactory
import dev.sriramcodessw.versesync.bluetooth.BluetoothViewModel
import dev.sriramcodessw.versesync.bluetooth.BluetoothViewModelFactory
import dev.sriramcodessw.versesync.concert.ConcertScreen
import dev.sriramcodessw.versesync.concert.ConcertViewModel
import dev.sriramcodessw.versesync.concert.ConcertViewModelFactory
import dev.sriramcodessw.versesync.concert.SetlistEditorScreen
import dev.sriramcodessw.versesync.concert.SetlistsViewModel
import dev.sriramcodessw.versesync.concert.SetlistsViewModelFactory
import dev.sriramcodessw.versesync.data.*
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModel
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModelFactory
import dev.sriramcodessw.versesync.fingerprinting.FingerprintCreationViewModel
import dev.sriramcodessw.versesync.fingerprinting.FingerprintCreationViewModelFactory
import dev.sriramcodessw.versesync.nfc.SecurityManager
import dev.sriramcodessw.versesync.ui.screens.*
import dev.sriramcodessw.versesync.ui.theme.VerseSyncTheme
import dev.sriramcodessw.versesync.ui.viewmodels.ShareViewModel
import dev.sriramcodessw.versesync.ui.viewmodels.ShareViewModelFactory
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModelFactory

object AppRoutes {
    const val MAIN_SCREEN = "main"
    const val ADD_SONG_SCREEN = "add_song"
    const val ADD_SONG_TO_SETLIST = "add_song_to_setlist/{setlistId}"
    const val SONG_DETAIL_SCREEN = "song_detail/{songId}"
    const val LYRIC_SYNC_SCREEN = "lyric_sync/{songId}"
    const val TIP_JAR_SCREEN = "tip_jar"
    const val RECOGNITION_SCREEN = "recognition"
    const val SHARE_SCREEN = "share"
    const val LANGUAGE_SELECTION = "language_selection"
    const val SETTINGS = "settings"
    const val SYNC_HEALTH = "sync_health"
    const val SETLIST_EDITOR = "setlist/{setlistId}"
    const val CONCERT = "concert/{setlistId}"
    const val FOLDER = "folder/{folderId}"
}

/**
 * The first screen, or null while the saved "first-run setup is done" value has not been read yet. Taking "not read yet" for
 * "first run" showed the first-run screen, and its location prompt, on every cold start (issue #79).
 */
internal fun startRouteFor(isSetupComplete: Boolean?): String? =
    when (isSetupComplete) {
        null -> null
        true -> AppRoutes.MAIN_SCREEN
        false -> AppRoutes.LANGUAGE_SELECTION
    }

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VerseSyncTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AuthWrapper(this)
                }
            }
        }
    }
}

@Composable
fun AuthWrapper(activity: AppCompatActivity) {
    val application = LocalContext.current.applicationContext as VerseSyncApp
    val securityViewModel: SecurityViewModel = viewModel(factory = SecurityViewModelFactory(application))
    val verseViewModel: VerseViewModel = viewModel(factory = VerseViewModelFactory(application, application.database.songDao()))
    val hybridViewModel: HybridRecognitionViewModel = viewModel(factory = HybridRecognitionViewModelFactory(application))

    // Null until the saved lock settings have been read. Treating "not loaded yet" as
    // "lock disabled" would unlock the app before the real setting arrives.
    val lockConfig by securityViewModel.lockConfig.collectAsState()
    val songs by verseViewModel.songs.collectAsState()

    // Held in the view model so a rotation does not ask for the lock again (issue #69). It is not saved state, so a cold start still locks.
    val isUnlocked by securityViewModel.isUnlocked.collectAsState()
    // Where the person is in the voice shortcut. Saved across a rotation, as the count of failed attempts must not reset.
    var attempts by rememberSaveable(stateSaver = VoiceShortcutAttempts.Saver) { mutableStateOf(VoiceShortcutAttempts()) }
    val securityManager = remember { SecurityManager(activity) }

    val syncReadySongs =
        remember(songs) {
            songs.filter { !it.fingerprint.isNullOrEmpty() }
        }
    // The song to say for the voice shortcut. Its id is saved across a rotation, as the attempts are, and is kept while the song
    // list changes, so the song does not change while the person is looking at it (issue #63). A new one is picked only if
    // it is gone. Saved state also survives the process being killed, which only keeps a song id and unlocks nothing.
    var targetSongId by rememberSaveable { mutableStateOf<Int?>(null) }
    val targetSong = remember(syncReadySongs, targetSongId) { chooseVoiceShortcutTarget(syncReadySongs, targetSongId) }
    LaunchedEffect(targetSong?.id) { targetSongId = targetSong?.id }

    fun promptDeviceCredential(subtitle: String) {
        securityManager.showBiometricPrompt(
            title = "Unlock VerseSync",
            subtitle = subtitle,
            onSuccess = { securityViewModel.markUnlocked() },
            onError = { },
        )
    }

    // Carries out what VoiceShortcutAttempts decided: the microphone and the device prompt are started and stopped here.
    fun carryOut(step: VoiceShortcutAttempts.Step) {
        attempts = step.next
        step.carryOut(
            startListening = { hybridViewModel.startRecognition(true) },
            stopListening = { hybridViewModel.stopRecognition() },
            promptDevice = { promptDeviceCredential(LockCopy.DEVICE_PROMPT_SUBTITLE) },
        )
        if (step.next.state == VoiceShortcutAttempts.State.Open) securityViewModel.markUnlocked()
    }

    // The microphone must not keep listening in the background. Going to the background is not a failed attempt (issue #62).
    val currentAttempts by rememberUpdatedState(attempts)
    val activityLifecycle = LocalLifecycleOwner.current
    DisposableEffect(activityLifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) carryOut(currentAttempts.interrupted())
            }
        activityLifecycle.lifecycle.addObserver(observer)
        onDispose { activityLifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lockConfig) {
        val config = lockConfig ?: return@LaunchedEffect
        if (!config.enabled) {
            securityViewModel.markUnlocked()
        } else if (config.type != LockType.VOCAL && !isUnlocked) {
            promptDeviceCredential("Access your library")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val config = lockConfig
        when {
            config == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            isUnlocked || !config.enabled -> {
                VerseSyncNavHost(application, securityViewModel, verseViewModel, hybridViewModel)
            }

            config.type == LockType.VOCAL && targetSong != null && attempts.state != VoiceShortcutAttempts.State.DeviceLock -> {
                VocalLockScreen(
                    targetSong = targetSong,
                    hybridViewModel = hybridViewModel,
                    isListening = attempts.state == VoiceShortcutAttempts.State.Listening,
                    tryNumber = attempts.tryNumber,
                    onMicTapped = { carryOut(attempts.micTapped()) },
                    onMatched = { carryOut(attempts.matched()) },
                    onTimedOut = { carryOut(attempts.timedOut()) },
                    onFallback = { carryOut(attempts.fallbackTapped()) },
                )
            }

            else -> {
                // Locked, and either the system prompt was dismissed or vocal lock has no
                // synced song to sing. Never unlock automatically; offer the device credential.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("VerseSync is locked")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { promptDeviceCredential("Access your library") }) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

@Composable
fun VerseSyncNavHost(
    application: VerseSyncApp,
    securityViewModel: SecurityViewModel,
    verseViewModel: VerseViewModel,
    hybridRecognitionViewModel: HybridRecognitionViewModel,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(BluetoothManager::class.java) }
    val bluetoothAdapter = remember { bluetoothManager?.adapter }

    // ViewModels
    val billingViewModel: BillingViewModel = viewModel(factory = BillingViewModelFactory(application))
    val fingerprintCreationViewModel: FingerprintCreationViewModel = viewModel(factory = FingerprintCreationViewModelFactory(application))
    val shareViewModel: ShareViewModel = viewModel(factory = ShareViewModelFactory(verseViewModel))
    val languageViewModel: LanguageViewModel = viewModel(factory = LanguageViewModelFactory(application))
    val trialViewModel: TrialViewModel = viewModel(factory = TrialViewModelFactory(application))
    val onboardingViewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory(application))
    val generalViewModel: GeneralViewModel = viewModel(factory = GeneralViewModelFactory(application))
    val setlistsViewModel: SetlistsViewModel =
        viewModel(factory = SetlistsViewModelFactory(application, application.database.setlistDao()))

    val bluetoothViewModel: BluetoothViewModel? =
        bluetoothAdapter?.let {
            viewModel(factory = BluetoothViewModelFactory(application, it))
        }

    // Google sends no callback when a subscription expires, so re-check it on every return to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, billingViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) billingViewModel.refreshPurchases()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Register Download Receiver
    DisposableEffect(Unit) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        // DownloadManager sends ACTION_DOWNLOAD_COMPLETE from its own process, so a non-exported
        // receiver never gets it (the model would download but never unpack). The handler
        // therefore trusts nothing in the intent: it looks the id up in our DownloadManager.
        ContextCompat.registerReceiver(
            context,
            languageViewModel.downloader.onDownloadComplete,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(languageViewModel.downloader.onDownloadComplete)
        }
    }

    val songs by verseViewModel.songs.collectAsState()
    val isSetupComplete by languageViewModel.isSetupComplete.collectAsState()
    var pendingSongIdForSync by remember { mutableIntStateOf(-1) }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _: Boolean ->
            languageViewModel.detectRegion()
        }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (isGranted) {
                if (pendingSongIdForSync != -1) {
                    navController.navigate("lyric_sync/$pendingSongIdForSync")
                    pendingSongIdForSync = -1
                } else {
                    navController.navigate(AppRoutes.RECOGNITION_SCREEN)
                }
            }
        }

    // Decided once, when the saved value arrives, and then kept: finishing first-run setup later must not rebuild the graph.
    val startDestination = remember(isSetupComplete == null) { startRouteFor(isSetupComplete) }
    if (startDestination == null) {
        // Nothing is shown, and no first-run effect runs, until the saved setup state is known (a few milliseconds).
        Box(Modifier.fillMaxSize())
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 300 }, animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -300 }, animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -300 }, animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 300 }, animationSpec = tween(300)) },
    ) {
        composable(AppRoutes.LANGUAGE_SELECTION) {
            // Approximate location only suggests languages during first-run setup, so ask here
            // rather than on every app start. Skip the prompt if it was already granted.
            LaunchedEffect(Unit) {
                val granted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    languageViewModel.detectRegion()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            LanguageSelectionScreen(
                viewModel = languageViewModel,
                onComplete = { navController.navigate(AppRoutes.MAIN_SCREEN) { popUpTo(AppRoutes.LANGUAGE_SELECTION) { inclusive = true } } },
            )
        }
        composable(AppRoutes.MAIN_SCREEN) {
            MainScreen(
                songs = songs,
                onNavigateToAddSong = { navController.navigate(AppRoutes.ADD_SONG_SCREEN) },
                onSongClick = { songId -> navController.navigate("song_detail/$songId") },
                onNavigateToTipJar = { navController.navigate(AppRoutes.TIP_JAR_SCREEN) },
                onNavigateToRecognition = {
                    pendingSongIdForSync = -1
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                verseViewModel = verseViewModel,
                onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onboardingViewModel = onboardingViewModel,
                billingViewModel = billingViewModel,
                trialViewModel = trialViewModel,
                setlistsViewModel = setlistsViewModel,
                onOpenSetlist = { id -> navController.navigate("setlist/$id") { launchSingleTop = true } },
                onStartConcert = { id -> navController.navigate("concert/$id") { launchSingleTop = true } },
                onOpenFolder = { id -> navController.navigate("folder/$id") { launchSingleTop = true } },
            )
        }
        composable(
            route = AppRoutes.SETLIST_EDITOR,
            arguments = listOf(navArgument("setlistId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getInt("setlistId") ?: 0
            val setlists by setlistsViewModel.setlists.collectAsState()
            val entriesFlow = remember(setlistId) { setlistsViewModel.entries(setlistId) }
            val entries by entriesFlow.collectAsState(initial = emptyList())
            val setlistName = setlists.firstOrNull { it.id == setlistId }?.name.orEmpty()
            SetlistEditorScreen(
                name = setlistName,
                entries = entries,
                library = songs,
                removed = setlistsViewModel.removed,
                onAddSong = { songId -> setlistsViewModel.addSong(setlistId, songId) },
                onNewSong = { navController.navigate("add_song_to_setlist/$setlistId") { launchSingleTop = true } },
                onMove = { entryId, by -> setlistsViewModel.move(setlistId, entryId, by) },
                onRemove = { row, index -> setlistsViewModel.remove(row, index) },
                onUndo = { gone -> setlistsViewModel.undoRemove(gone) },
                onRename = { newName -> setlistsViewModel.rename(setlistId, newName) },
                onStart = { navController.navigate("concert/$setlistId") { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.CONCERT,
            arguments = listOf(navArgument("setlistId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getInt("setlistId") ?: 0
            val concertViewModel: ConcertViewModel =
                viewModel(
                    key = "concert-$setlistId",
                    factory =
                        ConcertViewModelFactory(
                            application,
                            setlistId,
                            application.database.setlistDao(),
                            application.database.songDao(),
                        ),
                )
            ConcertScreen(viewModel = concertViewModel, onLeave = { if (navController.currentBackStackEntry == backStackEntry) navController.popBackStack() })
        }
        composable(
            route = AppRoutes.FOLDER,
            arguments = listOf(navArgument("folderId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getInt("folderId") ?: 0
            val folderSongsFlow = remember(folderId) { verseViewModel.songsInFolder(folderId) }
            val folderSongs by folderSongsFlow.collectAsState(initial = emptyList())
            val allFolders by verseViewModel.folders.collectAsState()
            FolderScreen(
                name = allFolders.firstOrNull { it.id == folderId }?.name.orEmpty(),
                songs = folderSongs,
                onSongClick = { songId -> navController.navigate("song_detail/$songId") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                languageViewModel = languageViewModel,
                verseViewModel = verseViewModel,
                securityViewModel = securityViewModel,
                generalViewModel = generalViewModel,
                onNavigateToHealthCheck = { navController.navigate(AppRoutes.SYNC_HEALTH) },
            )
        }
        composable(AppRoutes.SYNC_HEALTH) {
            SyncHealthScreen(
                onBack = { navController.popBackStack() },
                languageViewModel = languageViewModel,
                verseViewModel = verseViewModel,
            )
        }
        composable(AppRoutes.ADD_SONG_SCREEN) {
            AddSongScreen(
                onBack = { navController.popBackStack() },
                onSave = { title, artist, lyrics, language ->
                    verseViewModel.addSong(title, artist, lyrics, language)
                    navController.popBackStack()
                },
                languageViewModel = languageViewModel,
            )
        }
        // A new song for a setlist: saved into the library and at the end of that setlist together, in one transaction. It checks
        // no limit and no trial: concert use is free (ADR-20, decision 3), and a song in a setlist does not count.
        composable(
            route = AppRoutes.ADD_SONG_TO_SETLIST,
            arguments = listOf(navArgument("setlistId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getInt("setlistId") ?: 0
            AddSongScreen(
                onBack = { navController.popBackStack() },
                onSave = { title, artist, lyrics, language ->
                    setlistsViewModel.addNewSong(setlistId, title, artist, lyrics, language)
                    navController.popBackStack()
                },
                languageViewModel = languageViewModel,
            )
        }
        composable(
            route = AppRoutes.SONG_DETAIL_SCREEN,
            arguments = listOf(navArgument("songId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getInt("songId") ?: 0
            val songFlow = remember(songId) { verseViewModel.getSong(songId) }
            val song by songFlow.collectAsState(initial = null)
            song?.let {
                SongDetailScreen(
                    song = it,
                    onSyncLyrics = {
                        pendingSongIdForSync = songId
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onShare = {
                        navController.navigate(AppRoutes.SHARE_SCREEN)
                    },
                    onBack = { navController.popBackStack() },
                    billingViewModel = billingViewModel,
                )
            }
        }
        composable(
            route = AppRoutes.LYRIC_SYNC_SCREEN,
            arguments = listOf(navArgument("songId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getInt("songId") ?: 0
            LyricSyncScreen(
                verseViewModel = verseViewModel,
                fingerprintViewModel = fingerprintCreationViewModel,
                songId = songId,
                onNavigateBack = { navController.popBackStack() },
                onboardingViewModel = onboardingViewModel,
                generalViewModel = generalViewModel,
            )
        }
        composable(AppRoutes.TIP_JAR_SCREEN) {
            TipJarScreen(onBack = { navController.popBackStack() }, viewModel = billingViewModel)
        }
        composable(AppRoutes.RECOGNITION_SCREEN) {
            RecognitionScreen(
                onBack = { navController.popBackStack() },
                hybridViewModel = hybridRecognitionViewModel,
                billingViewModel = billingViewModel,
                languageViewModel = languageViewModel,
                trialViewModel = trialViewModel,
                onboardingViewModel = onboardingViewModel,
            )
        }
        composable(AppRoutes.SHARE_SCREEN) {
            if (bluetoothViewModel != null) {
                ShareScreen(
                    onBack = { navController.popBackStack() },
                    bluetoothViewModel = bluetoothViewModel,
                    shareViewModel = shareViewModel,
                    verseViewModel = verseViewModel,
                )
            }
        }
    }
}

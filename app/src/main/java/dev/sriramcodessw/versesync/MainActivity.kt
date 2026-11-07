package dev.sriramcodessw.versesync

import android.Manifest
import android.app.DownloadManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import dev.sriramcodessw.versesync.data.*
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModel
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModelFactory
import dev.sriramcodessw.versesync.fingerprinting.FingerprintCreationViewModel
import dev.sriramcodessw.versesync.fingerprinting.FingerprintCreationViewModelFactory
import dev.sriramcodessw.versesync.nfc.SecurityManager
import dev.sriramcodessw.versesync.ui.components.UpgradeDialog
import dev.sriramcodessw.versesync.ui.screens.*
import dev.sriramcodessw.versesync.ui.theme.VerseSyncTheme
import dev.sriramcodessw.versesync.ui.viewmodels.ShareViewModel
import dev.sriramcodessw.versesync.ui.viewmodels.ShareViewModelFactory
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModelFactory

object AppRoutes {
    const val MAIN_SCREEN = "main"
    const val ADD_SONG_SCREEN = "add_song"
    const val SONG_DETAIL_SCREEN = "song_detail/{songId}"
    const val LYRIC_SYNC_SCREEN = "lyric_sync/{songId}"
    const val TIP_JAR_SCREEN = "tip_jar"
    const val RECOGNITION_SCREEN = "recognition"
    const val SHARE_SCREEN = "share"
    const val LANGUAGE_SELECTION = "language_selection"
    const val SETTINGS = "settings"
    const val SYNC_HEALTH = "sync_health"
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
    
    val isAppLockEnabled by securityViewModel.isAppLockEnabled.collectAsState()
    val lockType by securityViewModel.lockType.collectAsState()
    val songs by verseViewModel.songs.collectAsState()
    
    var isUnlocked by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    val securityManager = remember { SecurityManager(activity) }

    val syncReadySongs = remember(songs) { 
        songs.filter { !it.fingerprint.isNullOrEmpty() } 
    }
    
    val targetSong = remember(syncReadySongs) { syncReadySongs.randomOrNull() }

    LaunchedEffect(isAppLockEnabled, lockType) {
        if (!isAppLockEnabled) {
            isUnlocked = true
        } else if (lockType != LockType.VOCAL && !isUnlocked) {
            securityManager.showBiometricPrompt(
                title = "Unlock VerseSync",
                subtitle = "Access your library",
                onSuccess = { isUnlocked = true },
                onError = { }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isUnlocked || !isAppLockEnabled) {
            VerseSyncNavHost(application, securityViewModel, verseViewModel, hybridViewModel)
        } else {
            if (lockType == LockType.VOCAL && targetSong != null) {
                VocalLockScreen(
                    targetSong = targetSong,
                    hybridViewModel = hybridViewModel,
                    attemptCount = failedAttempts + 1,
                    onSuccess = { isUnlocked = true },
                    onFailure = {
                        failedAttempts++
                        if (failedAttempts >= 2) {
                            securityManager.showBiometricPrompt(
                                title = "Unlock VerseSync",
                                subtitle = "Vocal auth failed. Use device lock.",
                                onSuccess = { isUnlocked = true },
                                onError = { }
                            )
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    if (lockType != LockType.VOCAL) {
                        securityManager.showBiometricPrompt(
                            title = "Unlock VerseSync",
                            subtitle = "Access library",
                            onSuccess = { isUnlocked = true },
                            onError = { }
                        )
                    } else if (songs.isNotEmpty() && targetSong == null) {
                        isUnlocked = true 
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
    hybridRecognitionViewModel: HybridRecognitionViewModel
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
    
    val bluetoothViewModel: BluetoothViewModel? = bluetoothAdapter?.let {
        viewModel(factory = BluetoothViewModelFactory(application, it))
    }

    // Register Download Receiver
    DisposableEffect(Unit) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(languageViewModel.downloader.onDownloadComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(languageViewModel.downloader.onDownloadComplete, filter)
        }
        onDispose {
            context.unregisterReceiver(languageViewModel.downloader.onDownloadComplete)
        }
    }

    val songs by verseViewModel.songs.collectAsState()
    val isSetupComplete by languageViewModel.isSetupComplete.collectAsState()
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var pendingSongIdForSync by remember { mutableIntStateOf(-1) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        languageViewModel.detectRegion()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
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

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    if (showUpgradeDialog) {
        UpgradeDialog(onDismiss = { showUpgradeDialog = false })
    }

    val startDestination = if (isSetupComplete) AppRoutes.MAIN_SCREEN else AppRoutes.LANGUAGE_SELECTION

    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 300 }, animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -300 }, animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -300 }, animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 300 }, animationSpec = tween(300)) }
    ) {
        composable(AppRoutes.LANGUAGE_SELECTION) {
            LanguageSelectionScreen(
                viewModel = languageViewModel,
                onComplete = { navController.navigate(AppRoutes.MAIN_SCREEN) { popUpTo(AppRoutes.LANGUAGE_SELECTION) { inclusive = true } } }
            )
        }
        composable(AppRoutes.MAIN_SCREEN) {
            MainScreen(
                songs = songs,
                onNavigateToAddSong = {
                    if (songs.size >= 10) {
                        showUpgradeDialog = true
                    } else {
                        navController.navigate(AppRoutes.ADD_SONG_SCREEN)
                    }
                },
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
                trialViewModel = trialViewModel
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                languageViewModel = languageViewModel,
                verseViewModel = verseViewModel,
                securityViewModel = securityViewModel,
                generalViewModel = generalViewModel,
                onNavigateToHealthCheck = { navController.navigate(AppRoutes.SYNC_HEALTH) }
            )
        }
        composable(AppRoutes.SYNC_HEALTH) {
            SyncHealthScreen(
                onBack = { navController.popBackStack() },
                languageViewModel = languageViewModel,
                verseViewModel = verseViewModel
            )
        }
        composable(AppRoutes.ADD_SONG_SCREEN) {
            AddSongScreen(
                onBack = { navController.popBackStack() },
                onSave = { title, artist, lyrics, language ->
                    verseViewModel.addSong(title, artist, lyrics, language)
                    navController.popBackStack()
                },
                languageViewModel = languageViewModel
            )
        }
        composable(
            route = AppRoutes.SONG_DETAIL_SCREEN,
            arguments = listOf(navArgument("songId") { type = NavType.IntType })
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
                    billingViewModel = billingViewModel
                )
            }
        }
        composable(
            route = AppRoutes.LYRIC_SYNC_SCREEN,
            arguments = listOf(navArgument("songId") { type = NavType.IntType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getInt("songId") ?: 0
            LyricSyncScreen(
                verseViewModel = verseViewModel,
                fingerprintViewModel = fingerprintCreationViewModel,
                songId = songId,
                onNavigateBack = { navController.popBackStack() },
                onboardingViewModel = onboardingViewModel,
                generalViewModel = generalViewModel
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
                onboardingViewModel = onboardingViewModel
            )
        }
        composable(AppRoutes.SHARE_SCREEN) {
            if (bluetoothViewModel != null) {
                ShareScreen(
                    onBack = { navController.popBackStack() },
                    bluetoothViewModel = bluetoothViewModel,
                    shareViewModel = shareViewModel,
                    verseViewModel = verseViewModel
                )
            }
        }
    }
}

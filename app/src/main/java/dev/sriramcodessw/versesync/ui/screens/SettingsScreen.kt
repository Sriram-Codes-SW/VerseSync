package dev.sriramcodessw.versesync.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.*
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    languageViewModel: LanguageViewModel,
    verseViewModel: VerseViewModel,
    securityViewModel: SecurityViewModel,
    generalViewModel: GeneralViewModel,
    onNavigateToHealthCheck: () -> Unit
) {
    val context = LocalContext.current
    val installedLanguages by languageViewModel.installedLanguages.collectAsState()
    val songs by verseViewModel.songs.collectAsState()
    val isAppLockEnabled by securityViewModel.isAppLockEnabled.collectAsState()
    val currentLockType by securityViewModel.lockType.collectAsState()
    
    val showPitchTrail by generalViewModel.showPitchTrail.collectAsState()
    val metronomeDefault by generalViewModel.metronomeDefaultOn.collectAsState()
    
    var showLockTypeDialog by remember { mutableStateOf(false) }
    val hasSyncIssues = remember(songs, installedLanguages) {
        songs.any { languageViewModel.needsSpeechModel(it.language) && !installedLanguages.contains(it.language) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val encodedData = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
            if (encodedData != null) {
                verseViewModel.importLibrary(encodedData) { success ->
                    if (success) Toast.makeText(context, "Library Imported!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showLockTypeDialog) {
        AlertDialog(
            onDismissRequest = { showLockTypeDialog = false },
            title = { Text("Select App Gatekeeper") },
            text = {
                Column {
                    LockOption(
                        title = "Device Lock",
                        description = "Standard PIN, Pattern, or Password",
                        selected = currentLockType == LockType.DEVICE,
                        onSelect = { 
                            securityViewModel.setLockType(LockType.DEVICE)
                            showLockTypeDialog = false 
                        }
                    )
                    LockOption(
                        title = "Strict Biometric",
                        description = "Fingerprint or Face scan",
                        selected = currentLockType == LockType.BIOMETRIC,
                        onSelect = { 
                            securityViewModel.setLockType(LockType.BIOMETRIC)
                            showLockTypeDialog = false 
                        }
                    )
                    LockOption(
                        title = "Vocal Lock",
                        description = "Unlock by singing",
                        selected = currentLockType == LockType.VOCAL,
                        enabled = songs.any { !it.fingerprint.isNullOrEmpty() },
                        onSelect = { 
                            securityViewModel.setLockType(LockType.VOCAL)
                            showLockTypeDialog = false 
                        }
                    )
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                Text("Security", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Enable App Lock") },
                    trailingContent = {
                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = { enable ->
                                // The lock never opens without a successful system authentication,
                                // so enabling it on a device with no screen lock or biometrics
                                // would lock the user out of their own library.
                                val canAuthenticate = BiometricManager.from(context).canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                ) == BiometricManager.BIOMETRIC_SUCCESS
                                if (enable && !canAuthenticate) {
                                    Toast.makeText(
                                        context,
                                        "Set a screen lock or biometrics in system settings first",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    securityViewModel.setAppLockEnabled(enable)
                                }
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) }
                )
                
                if (isAppLockEnabled) {
                    ListItem(
                        headlineContent = { Text("Gatekeeper Type") },
                        supportingContent = { Text(currentLockType.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.clickable { showLockTypeDialog = true },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Vocal Training & Performance", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Show Pitch Trail") },
                    supportingContent = { Text("Visual feedback of your voice stability") },
                    trailingContent = {
                        Switch(
                            checked = showPitchTrail,
                            onCheckedChange = { generalViewModel.setShowPitchTrail(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Metronome Default On") },
                    supportingContent = { Text("Start metronome automatically during sync") },
                    trailingContent = {
                        Switch(
                            checked = metronomeDefault,
                            onCheckedChange = { generalViewModel.setMetronomeDefaultOn(it) }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Data Management", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Export Library") },
                    supportingContent = { Text("Encrypted backup of your library") },
                    leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        verseViewModel.exportLibrary { encodedData ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, encodedData)
                            }
                            context.startActivity(Intent.createChooser(intent, "Save Backup"))
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Import Library") },
                    supportingContent = { Text("Restore library from backup") },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        importLauncher.launch("*/*")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            if (hasSyncIssues) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.padding(bottom = 16.dp),
                        onClick = onNavigateToHealthCheck
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Sync Health Warning", style = MaterialTheme.typography.titleMedium)
                                Text("Missing language models. Tap to fix.")
                            }
                        }
                    }
                }
            }

            item {
                Text("Installed Languages", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }

            items(languageViewModel.availableLanguages) { lang ->
                val isInstalled = installedLanguages.contains(lang.code)
                ListItem(
                    headlineContent = { Text(lang.name) },
                    supportingContent = {
                        Text(
                            when {
                                isInstalled -> "Ready for offline use"
                                !lang.hasSpeechModel -> "No speech model exists yet. Lyrics and pitch matching still work."
                                else -> "Not downloaded"
                            }
                        )
                    },
                    trailingContent = {
                        if (isInstalled) {
                            IconButton(onClick = { languageViewModel.removeLanguage(lang.code) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete model")
                            }
                        } else if (lang.hasSpeechModel) {
                            Button(onClick = { languageViewModel.downloadLanguage(lang) }) {
                                Text("Download")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LockOption(title: String, description: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

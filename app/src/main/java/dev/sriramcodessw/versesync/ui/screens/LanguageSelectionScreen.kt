package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.LanguageViewModel

@Composable
fun LanguageSelectionScreen(viewModel: LanguageViewModel, onComplete: () -> Unit) {
    val installedLanguages by viewModel.installedLanguages.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to VerseSync",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select the languages you intend to use for song recognition. Models will be downloaded for offline use.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.availableLanguages) { lang ->
                val isInstalled = installedLanguages.contains(lang.code)
                val status = downloadStatus[lang.code]

                ListItem(
                    headlineContent = { Text(lang.name) },
                    trailingContent = {
                        if (isInstalled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else if (status != null && status >= 0) {
                            CircularProgressIndicator(
                                progress = { status / 100f },
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(onClick = { viewModel.downloadLanguage(lang) }) {
                                Text("Download")
                            }
                        }
                    }
                )
            }
        }

        Button(
            onClick = {
                viewModel.markSetupComplete()
                onComplete()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(if (installedLanguages.isNotEmpty()) "Continue to App" else "Skip for Now / Continue")
        }
    }
}

package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.LanguageViewModel
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHealthScreen(
    onBack: () -> Unit,
    languageViewModel: LanguageViewModel,
    verseViewModel: VerseViewModel
) {
    val installedLanguages by languageViewModel.installedLanguages.collectAsState()
    val songs by verseViewModel.songs.collectAsState()
    
    // Filter songs that have missing models
    val affectedSongs = songs.filter { languageViewModel.needsSpeechModel(it.language) && !installedLanguages.contains(it.language) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Health") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (affectedSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("All systems healthy! All models are installed.")
            }
        } else {
            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                Text(
                    text = "The following songs require specific language models for recognition to work. Please download the missing models in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn {
                    items(affectedSongs) { song ->
                        val languageInfo = languageViewModel.availableLanguages.find { it.code == song.language }
                        ListItem(
                            headlineContent = { Text(song.title) },
                            supportingContent = { Text("Requires: ${languageInfo?.name ?: song.language}") }
                        )
                    }
                }
            }
        }
    }
}

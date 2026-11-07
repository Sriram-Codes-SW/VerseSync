package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.billing.BillingViewModel
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.utils.PitchPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    song: Song, 
    onSyncLyrics: () -> Unit, 
    onShare: () -> Unit, 
    onBack: () -> Unit, 
    billingViewModel: BillingViewModel
) {
    val isPro by billingViewModel.isPro.collectAsState()
    var showSyncedData by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val pitchPlayer = remember { PitchPlayer() }
    var isPlayingFingerprint by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { pitchPlayer.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(song.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (song.lrcData != null) {
                        IconButton(onClick = { showSyncedData = !showSyncedData }) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = if (showSyncedData) "Show raw lyrics" else "Show synced data",
                                tint = if (showSyncedData) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    if (isPro) {
                        IconButton(onClick = onShare) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share Song")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSyncLyrics) {
                Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync Lyrics")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(text = song.artist, style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(16.dp))

            if (showSyncedData && song.lrcData != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Synced Timing Data (LRC):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (!song.fingerprint.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                if (isPlayingFingerprint) {
                                    pitchPlayer.stop()
                                    isPlayingFingerprint = false
                                } else {
                                    isPlayingFingerprint = true
                                    scope.launch {
                                        pitchPlayer.playFingerprint(song.fingerprint) { progress ->
                                            playbackProgress = progress.toFloat() / song.fingerprint.size
                                        }
                                        isPlayingFingerprint = false
                                        playbackProgress = 0f
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingFingerprint) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPlayingFingerprint) "Stop Audio" else "Play Tune", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (isPlayingFingerprint) {
                    LinearProgressIndicator(
                        progress = { playbackProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        item {
                            Text(
                                text = song.lrcData,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Lyrics:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    item {
                        Text(text = song.lyrics, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

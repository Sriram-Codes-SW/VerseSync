package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModel
import dev.sriramcodessw.versesync.ui.components.PitchVisualizer

@Composable
fun VocalLockScreen(
    targetSong: Song,
    hybridViewModel: HybridRecognitionViewModel,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    attemptCount: Int,
) {
    var isListening by remember { mutableStateOf(false) }
    val recognitionResult by hybridViewModel.recognitionResult.collectAsState(initial = null)
    val instantPitch by hybridViewModel.instantPitch.collectAsState(initial = 0f)

    // Check for success
    LaunchedEffect(recognitionResult) {
        recognitionResult?.let { result ->
            if (result.candidates.any { it.id == targetSong.id }) {
                onSuccess()
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Vocal Authentication",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Attempt $attemptCount of 2",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sing a verse from:", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = targetSong.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(targetSong.artist, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(32.dp))

        if (isListening) {
            PitchVisualizer(pitch = instantPitch)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
            Text("Verifying your voice...", modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                isListening = !isListening
                if (isListening) {
                    hybridViewModel.startRecognition(true)
                } else {
                    hybridViewModel.stopRecognition()
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(80.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Listen", modifier = Modifier.size(32.dp))
        }

        if (!isListening) {
            Text(
                "Tap to Start Singing",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(48.dp))

        TextButton(onClick = onFailure) {
            Text("Use Device Lock Instead")
        }
    }
}

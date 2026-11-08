package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.GeneralViewModel
import dev.sriramcodessw.versesync.data.OnboardingViewModel
import dev.sriramcodessw.versesync.fingerprinting.FingerprintCreationViewModel
import dev.sriramcodessw.versesync.ui.components.*
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel
import dev.sriramcodessw.versesync.utils.MetronomeManager
import dev.sriramcodessw.versesync.utils.SoundManager
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun LyricSyncScreen(
    verseViewModel: VerseViewModel,
    fingerprintViewModel: FingerprintCreationViewModel,
    songId: Int,
    onNavigateBack: () -> Unit,
    onboardingViewModel: OnboardingViewModel,
    generalViewModel: GeneralViewModel,
) {
    val song by verseViewModel.getSong(songId).collectAsState(initial = null)
    val fingerprint by fingerprintViewModel.fingerprint.collectAsState()
    val instantPitch by fingerprintViewModel.instantPitch.collectAsState()
    val amplitude by fingerprintViewModel.amplitude.collectAsState()

    val isTourCompleted by onboardingViewModel.isSyncTourCompleted.collectAsState()
    val showPitchTrailPref by generalViewModel.showPitchTrail.collectAsState()
    val metronomeDefaultPref by generalViewModel.metronomeDefaultOn.collectAsState()

    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val metronomeManager = remember { MetronomeManager(context) }

    var isMetronomeEnabled by remember { mutableStateOf(false) }
    var bpm by remember { mutableFloatStateOf(120f) }

    // Sync state with preferences
    LaunchedEffect(metronomeDefaultPref) {
        isMetronomeEnabled = metronomeDefaultPref
    }

    // Tour State
    var currentTourStep by remember { mutableIntStateOf(0) }
    var playBtnAnchor by remember { mutableStateOf(Offset.Zero) }
    var playBtnSize by remember { mutableStateOf(IntSize.Zero) }
    var tapBtnAnchor by remember { mutableStateOf(Offset.Zero) }
    var tapBtnSize by remember { mutableStateOf(IntSize.Zero) }

    val tourSteps =
        listOf(
            TourStep(
                "Syncing your Lyrics",
                "This screen helps the app learn your song. First, play the actual track from your speaker.",
            ),
            TourStep(
                "Start Recording",
                "Tap 'Play' to start capturing your song's unique audio fingerprint.",
                anchor = playBtnAnchor,
                size = playBtnSize,
            ),
            TourStep(
                "Time the Lines",
                "Tap this button every time you hear the start of a new lyric line. This creates the perfect sync!",
                anchor = tapBtnAnchor,
                size = tapBtnSize,
            ),
        )

    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
            metronomeManager.release()
            fingerprintViewModel.stopRecording()
        }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        song?.let { currentSong ->
            val lyrics = currentSong.lyrics.lines()
            var currentLineIndex by remember { mutableIntStateOf(0) }
            val timestamps = remember { mutableStateListOf<Pair<Long, String>>() }
            var isPlaying by remember { mutableStateOf(false) }
            var startTime by remember { mutableLongStateOf(0L) }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(lyrics) { index, line ->
                        Text(
                            text = line,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (index == currentLineIndex) Color.Yellow else Color.Transparent)
                                    .padding(vertical = 8.dp)
                                    .semantics {
                                        contentDescription = if (index == currentLineIndex) "$line, current line" else line
                                    },
                        )
                    }
                }

                // Metronome and Visualization Section
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (isPlaying) {
                        AudioLevelMeter(amplitude = amplitude, modifier = Modifier.padding(bottom = 8.dp))
                        if (showPitchTrailPref) {
                            PitchVisualizer(pitch = instantPitch)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "Metronome settings. Status: ${if (isMetronomeEnabled) "Enabled" else "Disabled"}. BPM: ${bpm.toInt()}"
                            },
                    ) {
                        Text("Metronome", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = isMetronomeEnabled,
                            onCheckedChange = {
                                isMetronomeEnabled = it
                                if (it && isPlaying) {
                                    metronomeManager.start(bpm.toInt())
                                } else {
                                    metronomeManager.stop()
                                }
                            },
                            modifier = Modifier.scale(0.7f).padding(horizontal = 8.dp),
                        )
                        Text("${bpm.toInt()} BPM", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = bpm,
                            onValueChange = {
                                bpm = it
                                if (isMetronomeEnabled && isPlaying) metronomeManager.start(it.toInt())
                            },
                            valueRange = 40f..240f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(
                        onClick = {
                            isPlaying = !isPlaying
                            if (isPlaying) {
                                startTime = System.currentTimeMillis()
                                fingerprintViewModel.startRecording()
                                if (isMetronomeEnabled) metronomeManager.start(bpm.toInt())
                            } else {
                                fingerprintViewModel.stopRecording()
                                metronomeManager.stop()
                            }
                        },
                        modifier =
                            Modifier.onGloballyPositioned {
                                playBtnAnchor = it.positionInRoot()
                                playBtnSize = it.size
                            },
                    ) {
                        Text(text = if (isPlaying) "Pause" else "Play")
                    }
                    Button(
                        onClick = {
                            if (isPlaying && currentLineIndex < lyrics.size) {
                                val elapsedTime = System.currentTimeMillis() - startTime
                                timestamps.add(Pair(elapsedTime, lyrics[currentLineIndex]))
                                currentLineIndex++
                                soundManager.playTapSound()
                            }
                        },
                        enabled = isPlaying,
                        modifier =
                            Modifier.onGloballyPositioned {
                                tapBtnAnchor = it.positionInRoot()
                                tapBtnSize = it.size
                            },
                    ) {
                        Text(text = "Tap")
                    }
                    Button(
                        onClick = {
                            val lrcData =
                                timestamps.joinToString("\n") { timestampPair ->
                                    val time = timestampPair.first
                                    val line = timestampPair.second
                                    val minutes = TimeUnit.MILLISECONDS.toMinutes(time)
                                    val seconds = TimeUnit.MILLISECONDS.toSeconds(time) % 60
                                    val hundredths = (time % 1000) / 10
                                    String.format(Locale.US, "[%02d:%02d.%02d]%s", minutes, seconds, hundredths, line)
                                }
                            verseViewModel.updateSyncedData(currentSong, lrcData, fingerprint)
                            onNavigateBack()
                        },
                        enabled = timestamps.isNotEmpty(),
                    ) {
                        Text(text = "Save")
                    }
                }
            }
        }

        if (!isTourCompleted && song != null) {
            TourOverlay(
                steps = tourSteps,
                currentStepIndex = currentTourStep,
                onNext = {
                    if (currentTourStep < tourSteps.size - 1) {
                        currentTourStep++
                    } else {
                        onboardingViewModel.completeSyncTour()
                    }
                },
                onSkip = { onboardingViewModel.completeSyncTour() },
            )
        }
    }
}

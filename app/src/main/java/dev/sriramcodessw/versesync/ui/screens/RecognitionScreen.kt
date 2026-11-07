package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.billing.BillingViewModel
import dev.sriramcodessw.versesync.data.LanguageViewModel
import dev.sriramcodessw.versesync.data.OnboardingViewModel
import dev.sriramcodessw.versesync.data.TrialViewModel
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModel
import dev.sriramcodessw.versesync.ui.components.PitchVisualizer
import dev.sriramcodessw.versesync.ui.components.TourOverlay
import dev.sriramcodessw.versesync.ui.components.TourStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    onBack: () -> Unit, 
    hybridViewModel: HybridRecognitionViewModel, 
    billingViewModel: BillingViewModel,
    languageViewModel: LanguageViewModel,
    trialViewModel: TrialViewModel,
    onboardingViewModel: OnboardingViewModel
) {
    var isRecognizing by remember { mutableStateOf(false) }
    val recognitionResult by hybridViewModel.recognitionResult.collectAsState(initial = null)
    val isPro by billingViewModel.isPro.collectAsState()
    val isModelReady by hybridViewModel.isModelReady.collectAsState()
    val error by hybridViewModel.error.collectAsState()
    val instantPitch by hybridViewModel.instantPitch.collectAsState(initial = 0f)
    val sttText by hybridViewModel.sttText.collectAsState(initial = "")
    val installedLanguages by languageViewModel.installedLanguages.collectAsState()
    val remainingTrials by trialViewModel.remainingTrials.collectAsState()
    val isTourCompleted by onboardingViewModel.isRecognitionTourCompleted.collectAsState()

    val availableLanguages = languageViewModel.availableLanguages.filter { 
        lang -> installedLanguages.contains(lang.code) 
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { 
        mutableStateOf(availableLanguages.firstOrNull() ?: languageViewModel.availableLanguages[0]) 
    }

    // Tour State
    var currentTourStep by remember { mutableIntStateOf(0) }
    var langSelectorAnchor by remember { mutableStateOf(Offset.Zero) }
    var langSelectorSize by remember { mutableStateOf(IntSize.Zero) }
    var startBtnAnchor by remember { mutableStateOf(Offset.Zero) }
    var startBtnSize by remember { mutableStateOf(IntSize.Zero) }

    val tourSteps = listOf(
        TourStep(
            "Select Language",
            "Choose the language of the song you want to recognize. You can download more in Settings.",
            anchor = langSelectorAnchor,
            size = langSelectorSize
        ),
        TourStep(
            "Start Recognizing",
            "Tap this button and sing or play music. ${if (isPro) "Your Pro status enables real-time pitch matching!" else "Standard users use the STT engine."}",
            anchor = startBtnAnchor,
            size = startBtnSize
        )
    )

    LaunchedEffect(selectedLanguage) {
        hybridViewModel.loadLanguageModel(selectedLanguage.code)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recognize Song") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).systemBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (availableLanguages.isEmpty()) {
                    Text("No language models installed. Please go to Settings to download one.")
                } else {
                    if (!isPro) {
                        Text(
                            text = "Trial Usage: $remainingTrials/3 daily hybrid attempts remaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (!isRecognizing) expanded = !expanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                langSelectorAnchor = it.positionInRoot()
                                langSelectorSize = it.size
                            }
                    ) {
                        OutlinedTextField(
                            value = selectedLanguage.name,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isRecognizing,
                            label = { Text("Listening Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = !isRecognizing).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableLanguages.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.name) },
                                    onClick = {
                                        selectedLanguage = language
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRecognizing) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text(
                                text = "Heard: ${sttText.ifBlank { "..." }}",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        PitchVisualizer(
                            pitch = instantPitch,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (!isModelReady) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Loading ${selectedLanguage.name} Model...")
                    } else {
                        Button(
                            onClick = { 
                                if (!isRecognizing) {
                                    if (isPro || remainingTrials > 0) {
                                        if (!isPro) trialViewModel.useTrial()
                                        isRecognizing = true
                                        hybridViewModel.startRecognition(true)
                                    }
                                } else {
                                    isRecognizing = false
                                    hybridViewModel.stopRecognition()
                                }
                            },
                            modifier = Modifier
                                .onGloballyPositioned {
                                    startBtnAnchor = it.positionInRoot()
                                    startBtnSize = it.size
                                }
                                .semantics { 
                                    contentDescription = if (isRecognizing) "Stop recognition" else "Start recognition"
                                }
                        ) {
                            Text(text = if (isRecognizing) "Stop Recognizing" else "Start Recognizing")
                        }
                    }
                }

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isRecognizing || (recognitionResult?.candidates?.isNotEmpty() == true)) {
                    recognitionResult?.let {
                        if (it.candidates.isEmpty()) {
                            Text(text = "Listening...")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(it.candidates) { song ->
                                    ListItem(
                                        headlineContent = { Text(song.title) },
                                        supportingContent = { Text("${song.artist} (${song.language})") },
                                        modifier = Modifier.background(
                                            if (it.candidates.size == 1) Color.Green else Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isTourCompleted && availableLanguages.isNotEmpty()) {
                TourOverlay(
                    steps = tourSteps,
                    currentStepIndex = currentTourStep,
                    onNext = {
                        if (currentTourStep < tourSteps.size - 1) currentTourStep++
                        else onboardingViewModel.completeRecognitionTour()
                    },
                    onSkip = { onboardingViewModel.completeRecognitionTour() }
                )
            }
        }
    }
}

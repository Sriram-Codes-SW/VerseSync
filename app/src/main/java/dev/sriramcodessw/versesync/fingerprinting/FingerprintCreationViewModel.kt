package dev.sriramcodessw.versesync.fingerprinting

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.audio.AudioFrameSource
import dev.sriramcodessw.versesync.audio.AudioRecordSource
import dev.sriramcodessw.versesync.audio.PitchDetector
import dev.sriramcodessw.versesync.audio.tarsos.TarsosPitchDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "FingerprintViewModel"

/**
 * Records the pitch fingerprint of a song while its lyrics are synced.
 *
 * [audioSourceFactory] and [pitchDetectorFactory] decide where audio comes from and how its pitch
 * is found. The defaults are the microphone and TarsosDSP, and tests supply fakes. `@JvmOverloads`
 * keeps the single-argument constructor that the framework's view model factory looks for.
 */
@SuppressLint("MissingPermission")
class FingerprintCreationViewModel
    @JvmOverloads
    constructor(
        application: Application,
        private val audioSourceFactory: () -> AudioFrameSource = { AudioRecordSource() },
        private val pitchDetectorFactory: () -> PitchDetector = { TarsosPitchDetector() },
    ) : AndroidViewModel(application) {
        private var audioSource: AudioFrameSource? = null
        private var fingerprinter: AudioFingerprinter? = null
        private var collectionJob: Job? = null
        private var instantPitchJob: Job? = null
        private var amplitudeJob: Job? = null

        private val _fingerprint = MutableStateFlow<List<Float>>(emptyList())
        val fingerprint = _fingerprint.asStateFlow()

        private val _instantPitch = MutableStateFlow(0f)
        val instantPitch = _instantPitch.asStateFlow()

        private val _amplitude = MutableStateFlow(0f)
        val amplitude = _amplitude.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error = _error.asStateFlow()

        private fun initializeAudioComponents() {
            try {
                if (audioSource == null) {
                    audioSource = audioSourceFactory()
                }
                val newFingerprinter = AudioFingerprinter(pitchDetectorFactory())
                fingerprinter = newFingerprinter

                collectionJob?.cancel()
                collectionJob =
                    viewModelScope.launch {
                        newFingerprinter.fingerprint.collect {
                            _fingerprint.value = it
                        }
                    }

                instantPitchJob?.cancel()
                instantPitchJob =
                    viewModelScope.launch {
                        newFingerprinter.instantPitch.collect {
                            _instantPitch.value = it
                        }
                    }

                amplitudeJob?.cancel()
                amplitudeJob =
                    viewModelScope.launch {
                        newFingerprinter.amplitude.collect {
                            _amplitude.value = it
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize audio components", e)
                _error.value = "Microphone initialization failed. Please ensure no other app is using it."
            }
        }

        fun startRecording() {
            _error.value = null
            initializeAudioComponents()

            val currentFingerprinter = fingerprinter
            val currentSource = audioSource

            if (currentFingerprinter != null && currentSource != null) {
                try {
                    currentSource.start { frame -> currentFingerprinter.onFrame(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recording thread", e)
                    _error.value = "Could not start audio capture."
                }
            }
        }

        fun stopRecording() {
            collectionJob?.cancel()
            instantPitchJob?.cancel()
            amplitudeJob?.cancel()
            _instantPitch.value = 0f
            _amplitude.value = 0f
            try {
                audioSource?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio capture", e)
            }

            audioSource = null
            fingerprinter = null
        }

        override fun onCleared() {
            super.onCleared()
            stopRecording()
        }
    }

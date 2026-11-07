package dev.sriramcodessw.versesync.fingerprinting

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "FingerprintViewModel"

@SuppressLint("MissingPermission")
class FingerprintCreationViewModel(application: Application) : AndroidViewModel(application) {

    private var dispatcher: be.tarsos.dsp.AudioDispatcher? = null
    private var fingerprinter: AudioFingerprinter? = null
    private var recognitionThread: Thread? = null
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
            if (dispatcher == null) {
                dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(16000, 1024, 0)
            }
            val currentDispatcher = dispatcher ?: return
            val newFingerprinter = AudioFingerprinter(currentDispatcher)
            fingerprinter = newFingerprinter

            collectionJob?.cancel()
            collectionJob = viewModelScope.launch {
                newFingerprinter.fingerprint.collect { 
                    _fingerprint.value = it
                }
            }

            instantPitchJob?.cancel()
            instantPitchJob = viewModelScope.launch {
                newFingerprinter.instantPitch.collect {
                    _instantPitch.value = it
                }
            }

            amplitudeJob?.cancel()
            amplitudeJob = viewModelScope.launch {
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
        val currentDispatcher = dispatcher
        
        if (currentFingerprinter != null && currentDispatcher != null) {
            try {
                currentFingerprinter.attachPitchProcessor(sampleRate = 16000f, bufferSize = 1024)
                recognitionThread = Thread(currentDispatcher, "Audio Dispatcher")
                recognitionThread?.start()
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
            dispatcher?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping dispatcher", e)
        }
        recognitionThread?.interrupt()
        
        dispatcher = null
        fingerprinter = null
        recognitionThread = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }
}

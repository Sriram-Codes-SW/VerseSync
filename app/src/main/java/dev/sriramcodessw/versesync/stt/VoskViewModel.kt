package dev.sriramcodessw.versesync.stt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow

class VoskViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val voskVoiceRecognition = VoskVoiceRecognition(application)

    val recognitionResult: StateFlow<String> = voskVoiceRecognition.recognitionResult

    fun startListening() {
        voskVoiceRecognition.startListening()
    }

    fun stopListening() {
        voskVoiceRecognition.stopListening()
    }
}

class VoskViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoskViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

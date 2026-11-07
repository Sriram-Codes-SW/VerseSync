package dev.sriramcodessw.versesync.engine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.sriramcodessw.versesync.VerseSyncApp

class HybridRecognitionViewModel(application: Application) : AndroidViewModel(application) {

    private val hybridRecognitionEngine = HybridRecognitionEngine(application, (application as VerseSyncApp).database.songDao())

    val recognitionResult = hybridRecognitionEngine.recognitionResult
    val isModelReady = hybridRecognitionEngine.isModelReady
    val error = hybridRecognitionEngine.error
    val instantPitch = hybridRecognitionEngine.instantPitch
    val sttText = hybridRecognitionEngine.sttText

    fun loadLanguageModel(languageCode: String) {
        hybridRecognitionEngine.loadLanguageModel(languageCode)
    }

    fun startRecognition(isPro: Boolean) {
        hybridRecognitionEngine.startRecognition(isPro)
    }

    fun stopRecognition() {
        hybridRecognitionEngine.stopRecognition()
    }
}

class HybridRecognitionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HybridRecognitionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HybridRecognitionViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

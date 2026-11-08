package dev.sriramcodessw.versesync.fingerprinting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FingerprintingViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0)
    private val fingerprinter = AudioFingerprinter(dispatcher)

    private val _fingerprint = MutableStateFlow<List<Float>>(emptyList())
    val fingerprint = _fingerprint.asStateFlow()

    init {
        viewModelScope.launch {
            fingerprinter.fingerprint.collect {
                _fingerprint.value = it
            }
        }
    }

    fun start() {
        val thread = Thread(dispatcher, "Audio Dispatcher")
        thread.start()
    }

    fun stop() {
        dispatcher.stop()
    }
}

class FingerprintingViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FingerprintingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FingerprintingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

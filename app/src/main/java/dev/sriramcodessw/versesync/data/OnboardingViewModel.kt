package dev.sriramcodessw.versesync.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    application: Application,
    private val prefs: OnboardingPreferences = OnboardingPreferences(application)
) : AndroidViewModel(application) {

    val isMainTourCompleted: StateFlow<Boolean> = prefs.isMainTourCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isRecognitionTourCompleted: StateFlow<Boolean> = prefs.isRecognitionTourCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isSyncTourCompleted: StateFlow<Boolean> = prefs.isSyncTourCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun completeMainTour() {
        viewModelScope.launch { prefs.setMainTourCompleted(true) }
    }

    fun completeRecognitionTour() {
        viewModelScope.launch { prefs.setRecognitionTourCompleted(true) }
    }

    fun completeSyncTour() {
        viewModelScope.launch { prefs.setSyncTourCompleted(true) }
    }
}

class OnboardingViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

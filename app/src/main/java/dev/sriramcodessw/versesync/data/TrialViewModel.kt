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

class TrialViewModel(
    application: Application,
    private val prefs: TrialPreferences = TrialPreferences(application)
) : AndroidViewModel(application) {

    val remainingTrials: StateFlow<Int> = prefs.remainingTrials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    fun useTrial() {
        viewModelScope.launch {
            prefs.useTrial()
        }
    }
}

class TrialViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrialViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrialViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

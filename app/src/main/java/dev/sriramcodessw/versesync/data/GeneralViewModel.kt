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

class GeneralViewModel(
    application: Application,
    private val prefs: GeneralPreferences = GeneralPreferences(application),
) : AndroidViewModel(application) {
    val showPitchTrail: StateFlow<Boolean> =
        prefs.showPitchTrail
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val metronomeDefaultOn: StateFlow<Boolean> =
        prefs.metronomeDefaultOn
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShowPitchTrail(show: Boolean) {
        viewModelScope.launch {
            prefs.setShowPitchTrail(show)
        }
    }

    fun setMetronomeDefaultOn(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setMetronomeDefaultOn(enabled)
        }
    }
}

class GeneralViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GeneralViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GeneralViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

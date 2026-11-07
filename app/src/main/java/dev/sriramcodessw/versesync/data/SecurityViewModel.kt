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

class SecurityViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SecurityPreferences(application)

    val isAppLockEnabled: StateFlow<Boolean> = prefs.isAppLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockType: StateFlow<LockType> = prefs.lockType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockType.DEVICE)

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAppLockEnabled(enabled)
        }
    }

    fun setLockType(type: LockType) {
        viewModelScope.launch {
            prefs.setLockType(type)
        }
    }
}

class SecurityViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SecurityViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SecurityViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

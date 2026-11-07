package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LockType {
    NONE, DEVICE, BIOMETRIC, VOCAL
}

class SecurityPreferences(private val context: Context) {
    companion object {
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val SELECTED_LOCK_TYPE = stringPreferencesKey("selected_lock_type")
    }

    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val lockType: Flow<LockType> = context.dataStore.data.map { 
        LockType.valueOf(it[SELECTED_LOCK_TYPE] ?: LockType.DEVICE.name) 
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setLockType(type: LockType) {
        context.dataStore.edit { it[SELECTED_LOCK_TYPE] = type.name }
    }
}

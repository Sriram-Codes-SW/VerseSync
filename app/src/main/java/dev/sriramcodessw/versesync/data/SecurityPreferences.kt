package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LockType {
    NONE,
    DEVICE,
    BIOMETRIC,
    VOCAL,
}

class SecurityPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val SELECTED_LOCK_TYPE = stringPreferencesKey("selected_lock_type")
    }

    val isAppLockEnabled: Flow<Boolean> = dataStore.safeData.map { it[APP_LOCK_ENABLED] ?: false }
    val lockType: Flow<LockType> =
        dataStore.safeData.map {
            runCatching { LockType.valueOf(it[SELECTED_LOCK_TYPE] ?: LockType.DEVICE.name) }
                .getOrDefault(LockType.DEVICE)
        }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setLockType(type: LockType) {
        dataStore.edit { it[SELECTED_LOCK_TYPE] = type.name }
    }
}

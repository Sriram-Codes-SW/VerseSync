package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TransferProgress(val transferId: String, val bytesReceived: Int)

class TransferPreferences(private val context: Context) {
    companion object {
        private val CURRENT_TRANSFER_ID = stringPreferencesKey("current_transfer_id")
        private val BYTES_RECEIVED = intPreferencesKey("bytes_received")
    }

    val transferProgress: Flow<TransferProgress?> = context.dataStore.data.map { prefs ->
        val id = prefs[CURRENT_TRANSFER_ID]
        if (id != null) {
            TransferProgress(id, prefs[BYTES_RECEIVED] ?: 0)
        } else {
            null
        }
    }

    suspend fun updateProgress(transferId: String, bytes: Int) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_TRANSFER_ID] = transferId
            prefs[BYTES_RECEIVED] = bytes
        }
    }

    suspend fun clearProgress() {
        context.dataStore.edit { prefs ->
            prefs.remove(CURRENT_TRANSFER_ID)
            prefs.remove(BYTES_RECEIVED)
        }
    }
}

package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrialPreferences(private val context: Context) {
    companion object {
        private val REMAINING_TRIALS = intPreferencesKey("remaining_trials")
        const val MAX_TRIALS = 3
    }

    val remainingTrials: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[REMAINING_TRIALS] ?: MAX_TRIALS
    }

    suspend fun useTrial() {
        context.dataStore.edit { prefs ->
            val currentRemaining = prefs[REMAINING_TRIALS] ?: MAX_TRIALS
            if (currentRemaining > 0) {
                prefs[REMAINING_TRIALS] = currentRemaining - 1
            }
        }
    }
}

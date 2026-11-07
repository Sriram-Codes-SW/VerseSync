package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GeneralPreferences(private val context: Context) {
    companion object {
        private val SHOW_PITCH_TRAIL = booleanPreferencesKey("show_pitch_trail")
        private val METRONOME_DEFAULT_ON = booleanPreferencesKey("metronome_default_on")
    }

    val showPitchTrail: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PITCH_TRAIL] ?: true }
    val metronomeDefaultOn: Flow<Boolean> = context.dataStore.data.map { it[METRONOME_DEFAULT_ON] ?: false }

    suspend fun setShowPitchTrail(show: Boolean) {
        context.dataStore.edit { it[SHOW_PITCH_TRAIL] = show }
    }

    suspend fun setMetronomeDefaultOn(enabled: Boolean) {
        context.dataStore.edit { it[METRONOME_DEFAULT_ON] = enabled }
    }
}

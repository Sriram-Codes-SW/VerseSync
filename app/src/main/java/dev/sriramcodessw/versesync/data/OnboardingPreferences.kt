package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OnboardingPreferences(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        private val MAIN_TOUR_COMPLETED = booleanPreferencesKey("main_tour_completed")
        private val RECOGNITION_TOUR_COMPLETED = booleanPreferencesKey("recognition_tour_completed")
        private val SYNC_TOUR_COMPLETED = booleanPreferencesKey("sync_tour_completed")
    }

    val isMainTourCompleted: Flow<Boolean> = dataStore.data.map { it[MAIN_TOUR_COMPLETED] ?: false }
    val isRecognitionTourCompleted: Flow<Boolean> = dataStore.data.map { it[RECOGNITION_TOUR_COMPLETED] ?: false }
    val isSyncTourCompleted: Flow<Boolean> = dataStore.data.map { it[SYNC_TOUR_COMPLETED] ?: false }

    suspend fun setMainTourCompleted(completed: Boolean) {
        dataStore.edit { it[MAIN_TOUR_COMPLETED] = completed }
    }

    suspend fun setRecognitionTourCompleted(completed: Boolean) {
        dataStore.edit { it[RECOGNITION_TOUR_COMPLETED] = completed }
    }

    suspend fun setSyncTourCompleted(completed: Boolean) {
        dataStore.edit { it[SYNC_TOUR_COMPLETED] = completed }
    }
}

package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class LanguagePreferences(private val context: Context) {
    companion object {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val INSTALLED_LANGUAGES = stringSetPreferencesKey("installed_languages")
    }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { it[SETUP_COMPLETE] ?: false }
    val installedLanguages: Flow<Set<String>> = context.dataStore.data.map { it[INSTALLED_LANGUAGES] ?: emptySet() }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[SETUP_COMPLETE] = complete }
    }

    suspend fun addLanguage(languageCode: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[INSTALLED_LANGUAGES] ?: emptySet()
            prefs[INSTALLED_LANGUAGES] = current + languageCode
        }
    }

    suspend fun removeLanguage(languageCode: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[INSTALLED_LANGUAGES] ?: emptySet()
            prefs[INSTALLED_LANGUAGES] = current - languageCode
        }
    }
}

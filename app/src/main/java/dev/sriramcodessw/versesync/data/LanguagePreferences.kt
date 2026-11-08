package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import kotlinx.coroutines.flow.map

/**
 * What to do when the settings file cannot be parsed: replace it with empty settings.
 *
 * Without this, a corrupt file made every read and every write throw `CorruptionException`, and the
 * app crashed on launch each time. The cost is that settings, including the app lock, go back to
 * their defaults. The lock setting lives in the same file, so there is no way to keep it.
 */
internal val settingsCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = settingsCorruptionHandler
)

/**
 * Reads that also survive an I/O error, such as a full disk, by reporting empty settings. A
 * failure in the upstream flow would otherwise end the `stateIn` collection of the view models and
 * crash the app.
 */
internal val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

class LanguagePreferences(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val INSTALLED_LANGUAGES = stringSetPreferencesKey("installed_languages")
    }

    val isSetupComplete: Flow<Boolean> = dataStore.safeData.map { it[SETUP_COMPLETE] ?: false }
    val installedLanguages: Flow<Set<String>> = dataStore.safeData.map { it[INSTALLED_LANGUAGES] ?: emptySet() }

    suspend fun setSetupComplete(complete: Boolean) {
        dataStore.edit { it[SETUP_COMPLETE] = complete }
    }

    suspend fun addLanguage(languageCode: String) {
        dataStore.edit { prefs ->
            val current = prefs[INSTALLED_LANGUAGES] ?: emptySet()
            prefs[INSTALLED_LANGUAGES] = current + languageCode
        }
    }

    suspend fun removeLanguage(languageCode: String) {
        dataStore.edit { prefs ->
            val current = prefs[INSTALLED_LANGUAGES] ?: emptySet()
            prefs[INSTALLED_LANGUAGES] = current - languageCode
        }
    }
}

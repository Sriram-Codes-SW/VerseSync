package dev.sriramcodessw.versesync.concert

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import dev.sriramcodessw.versesync.data.dataStore
import dev.sriramcodessw.versesync.data.safeData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where the person was in a setlist, kept on the device so a killed app or a phone call does not lose the place. */
data class SavedPlace(
    val setlistId: Int,
    val entryId: Int,
    val index: Int,
    val scroll: Int,
)

/** How the concert screen looks. It is separate from the rest of the app (ADR-20): dark by default, large type. */
data class ConcertDisplay(
    val lightTheme: Boolean = false,
    val textScale: Float = 1f,
) {
    companion object {
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 2.0f

        fun clampScale(value: Float): Float = value.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
    }
}

/**
 * Concert settings in DataStore, which Auto Backup and device transfer exclude (ADR-12), so a place
 * or a look does not follow a backup to another install.
 */
class ConcertPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        private val SETLIST_ID = intPreferencesKey("concert_setlist_id")
        private val ENTRY_ID = intPreferencesKey("concert_entry_id")
        private val INDEX = intPreferencesKey("concert_index")
        private val SCROLL = intPreferencesKey("concert_scroll")
        private val LIGHT_THEME = booleanPreferencesKey("concert_light_theme")
        private val TEXT_SCALE = floatPreferencesKey("concert_text_scale")
    }

    val savedPlace: Flow<SavedPlace?> =
        dataStore.safeData.map { prefs ->
            val setlistId = prefs[SETLIST_ID] ?: return@map null
            SavedPlace(setlistId, prefs[ENTRY_ID] ?: -1, prefs[INDEX] ?: 0, prefs[SCROLL] ?: 0)
        }

    val display: Flow<ConcertDisplay> =
        dataStore.safeData.map { prefs ->
            ConcertDisplay(
                lightTheme = prefs[LIGHT_THEME] ?: false,
                textScale = ConcertDisplay.clampScale(prefs[TEXT_SCALE] ?: 1f),
            )
        }

    suspend fun savePlace(place: SavedPlace) {
        dataStore.edit {
            it[SETLIST_ID] = place.setlistId
            it[ENTRY_ID] = place.entryId
            it[INDEX] = place.index
            it[SCROLL] = place.scroll
        }
    }

    suspend fun saveScroll(scroll: Int) {
        dataStore.edit { if (it[SETLIST_ID] != null) it[SCROLL] = scroll }
    }

    suspend fun clearPlace() {
        dataStore.edit {
            it.remove(SETLIST_ID)
            it.remove(ENTRY_ID)
            it.remove(INDEX)
            it.remove(SCROLL)
        }
    }

    suspend fun setLightTheme(light: Boolean) {
        dataStore.edit { it[LIGHT_THEME] = light }
    }

    suspend fun setTextScale(scale: Float) {
        dataStore.edit { it[TEXT_SCALE] = ConcertDisplay.clampScale(scale) }
    }
}

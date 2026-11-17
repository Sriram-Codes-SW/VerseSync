package dev.sriramcodessw.versesync.concert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.data.SetlistDao
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the concert screen draws. */
data class ConcertUiState(
    val controller: ConcertController = ConcertController(),
    /** The setlist's songs by song id. */
    val songs: Map<Int, Song> = emptyMap(),
    val loaded: Boolean = false,
    /** The setlist was deleted while it was open. */
    val setlistGone: Boolean = false,
    /** Where to start scrolling the lyrics of the shown song. Reset to 0 on every move. */
    val initialScroll: Int = 0,
)

/**
 * Runs one concert (ADR-20). It reads the setlist and the library and keeps the place; it has no
 * audio, no recognition and no network. The place is saved on every change of song and the scroll
 * position on request, and restored when a concert is opened again.
 */
class ConcertViewModel(
    application: Application,
    private val setlistId: Int,
    private val setlists: SetlistDao,
    songDao: SongDao,
    private val prefs: ConcertPreferences = ConcertPreferences(application),
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ConcertUiState())
    val state: StateFlow<ConcertUiState> = _state.asStateFlow()

    val display: StateFlow<ConcertDisplay> =
        prefs.display.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConcertDisplay())

    /** Every library song, for showing one that is not in the setlist. */
    val library: StateFlow<List<Song>> =
        songDao
            .getAllSongs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val saved = prefs.savedPlace.first()?.takeIf { it.setlistId == setlistId }
            var restored = false
            var clearedWhenGone = false
            combine(setlists.getEntriesWithSongs(setlistId), setlists.getSetlists()) { rows, all ->
                rows to all.any { it.id == setlistId }
            }.collect { (rows, exists) ->
                if (!exists) {
                    // Only this setlist's own saved place is cleared, never another setlist's, and only once.
                    if (!clearedWhenGone) {
                        clearedWhenGone = true
                        if (prefs.savedPlace.first()?.setlistId == setlistId) prefs.clearPlace()
                    }
                    _state.update { it.copy(setlistGone = true, loaded = true) }
                    return@collect
                }
                val entries = rows.map { ConcertEntry(it.entry.id, it.entry.songId) }
                val songs = rows.associate { it.song.id to it.song }
                // The saved place is used once there is something to put it on, not on an empty first emission.
                val restoreNow = !restored && entries.isNotEmpty()
                var toSave: SavedPlace? = null
                _state.update { current ->
                    toSave = null
                    val before = current.controller.anchorEntryId
                    var controller = current.controller.withEntries(entries)
                    var scroll = current.initialScroll
                    if (restoreNow) {
                        controller = controller.restore(saved?.entryId, saved?.index ?: 0)
                        // The scroll only belongs to the song it was saved for.
                        scroll = if (saved != null && controller.anchorEntryId == saved.entryId) saved.scroll else 0
                        // Saved at once, so a scroll before the first move is not lost and the setlist can be resumed.
                        controller.anchorEntryId?.let { toSave = SavedPlace(setlistId, it, controller.index, scroll) }
                    } else if (controller.anchorEntryId != before) {
                        // An edit moved the place (a song was deleted, say): the old scroll belongs to another song.
                        scroll = 0
                        controller.anchorEntryId?.let { toSave = SavedPlace(setlistId, it, controller.index, 0) }
                    }
                    current.copy(controller = controller, songs = songs, loaded = true, initialScroll = scroll)
                }
                if (restoreNow) restored = true
                toSave?.let { prefs.savePlace(it) }
            }
        }
    }

    fun next() = move { it.next() }

    fun previous() = move { it.previous() }

    fun jumpTo(position: Int) = move { it.jumpTo(position) }

    fun toggleBlank() = move { it.toggleBlank() }

    fun showOffList(songId: Int) = move { it.showOffList(songId) }

    fun backToSetlist() = move { it.backToSetlist() }

    /** Saves how far the lyrics are scrolled. The screen calls it after the person stops scrolling. */
    fun saveScroll(scroll: Int) {
        viewModelScope.launch { prefs.saveScroll(scroll) }
    }

    fun setLightTheme(light: Boolean) {
        viewModelScope.launch { prefs.setLightTheme(light) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { prefs.setTextScale(scale) }
    }

    private fun move(change: (ConcertController) -> ConcertController) {
        var toSave: SavedPlace? = null
        // One update, so the screen never sees a new song with the old scroll.
        _state.update { current ->
            toSave = null
            val controller = change(current.controller)
            val moved = controller.anchorEntryId != current.controller.anchorEntryId
            if (moved) controller.anchorEntryId?.let { toSave = SavedPlace(setlistId, it, controller.index, scroll = 0) }
            current.copy(controller = controller, initialScroll = if (moved) 0 else current.initialScroll)
        }
        toSave?.let { place -> viewModelScope.launch { prefs.savePlace(place) } }
    }
}

class ConcertViewModelFactory(
    private val application: Application,
    private val setlistId: Int,
    private val setlists: SetlistDao,
    private val songDao: SongDao,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConcertViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConcertViewModel(application, setlistId, setlists, songDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

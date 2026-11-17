package dev.sriramcodessw.versesync.concert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.data.Setlist
import dev.sriramcodessw.versesync.data.SetlistDao
import dev.sriramcodessw.versesync.data.SetlistEntryWithSong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The "Resume" card: the setlist the person was in and the song they were at. */
data class ResumeInfo(
    val setlistId: Int,
    val setlistName: String,
    val songNumber: Int,
)

/** What an undo needs to put a removed song back where it was. */
data class RemovedEntry(
    val setlistId: Int,
    val songId: Int,
    val index: Int,
    val title: String,
)

/**
 * Lists, makes, renames and deletes setlists, and edits one: songs are added at the end, moved up
 * or down, and removed with an undo (ADR-20). Concert mode is free, so this never looks at Pro
 * status.
 */
class SetlistsViewModel(
    application: Application,
    private val dao: SetlistDao,
    private val prefs: ConcertPreferences = ConcertPreferences(application),
) : AndroidViewModel(application) {
    val setlists: StateFlow<List<Setlist>> =
        dao.getSetlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val resume: StateFlow<ResumeInfo?> =
        combine(dao.getSetlists(), prefs.savedPlace) { all, place ->
            val list = place?.let { saved -> all.firstOrNull { it.id == saved.setlistId } }
            if (place == null || list == null) null else ResumeInfo(list.id, list.name, place.index + 1)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // One-shot events, as channels, so each is handled once and a rotation does not repeat or lose it.
    private val createdChannel = Channel<Int>(Channel.BUFFERED)
    private val removedChannel = Channel<RemovedEntry>(Channel.BUFFERED)

    /** The id of a setlist just made, so the screen can open it. */
    val created: Flow<Int> = createdChannel.receiveAsFlow()

    /** A song just removed from a setlist, so the screen can offer an undo. */
    val removed: Flow<RemovedEntry> = removedChannel.receiveAsFlow()

    fun entries(setlistId: Int): Flow<List<SetlistEntryWithSong>> = dao.getEntriesWithSongs(setlistId)

    /** Makes a setlist. A blank name becomes "Setlist". The new id arrives on [created]. */
    fun create(name: String) {
        viewModelScope.launch {
            val id = dao.insertSetlist(Setlist(name = cleanName(name))).toInt()
            createdChannel.send(id)
        }
    }

    fun rename(
        setlistId: Int,
        name: String,
    ) {
        viewModelScope.launch { dao.updateSetlist(Setlist(id = setlistId, name = cleanName(name))) }
    }

    fun delete(setlistId: Int) {
        viewModelScope.launch {
            dao.deleteSetlist(setlistId)
            if (prefs.savedPlace.first()?.setlistId == setlistId) prefs.clearPlace()
        }
    }

    fun addSong(
        setlistId: Int,
        songId: Int,
    ) {
        viewModelScope.launch { dao.appendSong(setlistId, songId) }
    }

    /** Moves an entry one place earlier ([by] -1) or later (1). It stops at the ends. */
    fun move(
        setlistId: Int,
        entryId: Int,
        by: Int,
    ) {
        viewModelScope.launch { dao.moveEntry(setlistId, entryId, by) }
    }

    /** Removes an entry and offers an undo on [removed]. [index] is where it was in the list. */
    fun remove(
        row: SetlistEntryWithSong,
        index: Int,
    ) {
        viewModelScope.launch {
            dao.removeEntry(row.entry.id)
            removedChannel.send(RemovedEntry(row.entry.setlistId, row.entry.songId, index, row.song.title))
        }
    }

    /** Puts a removed song back where it was. It gets a new entry id. */
    fun undoRemove(removed: RemovedEntry) {
        viewModelScope.launch { dao.insertAt(removed.setlistId, removed.songId, removed.index) }
    }

    private fun cleanName(name: String) = name.trim().ifEmpty { "Setlist" }
}

class SetlistsViewModelFactory(
    private val application: Application,
    private val dao: SetlistDao,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SetlistsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SetlistsViewModel(application, dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

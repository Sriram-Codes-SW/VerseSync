package dev.sriramcodessw.versesync.concert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.data.Setlist
import dev.sriramcodessw.versesync.data.SetlistDao
import dev.sriramcodessw.versesync.data.SetlistEntryWithSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The "Resume" card: the setlist the person was in and the song they were at. */
data class ResumeInfo(
    val setlistId: Int,
    val setlistName: String,
    val songNumber: Int,
)

/**
 * Lists, makes and deletes setlists and adds songs to one (ADR-20, first version: songs are added
 * at the end, and the order is the order they were added). Concert mode is free, so this never
 * looks at Pro status.
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

    fun entries(setlistId: Int): Flow<List<SetlistEntryWithSong>> = dao.getEntriesWithSongs(setlistId)

    /** Makes a setlist and passes on its id. A blank name becomes "Setlist". */
    fun create(
        name: String,
        onCreated: (Int) -> Unit = {},
    ) {
        viewModelScope.launch {
            val id = dao.insertSetlist(Setlist(name = name.trim().ifEmpty { "Setlist" })).toInt()
            onCreated(id)
        }
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

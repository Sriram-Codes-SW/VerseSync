package dev.sriramcodessw.versesync.ui.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.data.Song
import kotlinx.coroutines.launch

class ShareViewModel(private val verseViewModel: VerseViewModel) : ViewModel() {

    val songsToShare = mutableStateListOf<Song>()
    val incomingSongs = mutableStateListOf<Song>()
    val selectedSongs = mutableStateListOf<Song>()

    fun onSongSelected(song: Song, isSelected: Boolean) {
        if (isSelected) {
            selectedSongs.add(song)
        } else {
            selectedSongs.remove(song)
        }
    }

    fun acceptSong(song: Song) {
        viewModelScope.launch {
            verseViewModel.addSongWithConflictResolution(song)
            incomingSongs.remove(song)
        }
    }

    fun rejectSong(song: Song) {
        incomingSongs.remove(song)
    }

    fun acceptSelectedSongs() {
        viewModelScope.launch {
            selectedSongs.forEach { verseViewModel.addSongWithConflictResolution(it) }
            incomingSongs.removeAll(selectedSongs)
            selectedSongs.clear()
        }
    }

    fun rejectAllSongs() {
        incomingSongs.clear()
        selectedSongs.clear()
    }
}

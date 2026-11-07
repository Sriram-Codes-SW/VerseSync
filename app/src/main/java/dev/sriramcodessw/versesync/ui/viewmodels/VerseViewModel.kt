package dev.sriramcodessw.versesync.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.data.DataPortabilityManager
import dev.sriramcodessw.versesync.data.Folder
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    TITLE, ARTIST, RECENT
}

class VerseViewModel(application: Application, private val songDao: SongDao) : AndroidViewModel(application) {

    private val portabilityManager = DataPortabilityManager(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<Song>> = combine(_searchQuery, _sortOrder) { query, order ->
        query to order
    }.flatMapLatest { (query, order) ->
        val baseFlow = if (query.isEmpty()) songDao.getAllSongs() else songDao.searchSongs(query)
        baseFlow.map { list ->
            when (order) {
                SortOrder.TITLE -> list.sortedBy { it.title }
                SortOrder.ARTIST -> list.sortedBy { it.artist }
                SortOrder.RECENT -> list.sortedByDescending { it.dateAdded }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val folders: StateFlow<List<Folder>> = songDao.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun getSong(id: Int): Flow<Song> {
        return songDao.getSongById(id)
    }

    fun addSong(title: String, artist: String, lyrics: String, language: String = "en-us", folderId: Int? = null) {
        viewModelScope.launch {
            songDao.insert(Song(title = title, artist = artist, lyrics = lyrics, language = language, folderId = folderId))
        }
    }

    /**
     * Adds a song that came from outside this database (Bluetooth or an import).
     * The incoming id belongs to another database, so it is discarded and a new one is
     * generated. Keeping it would overwrite a local song that happens to share the id.
     * [folderId] must already be a folder that exists locally, or null.
     */
    suspend fun addSongWithConflictResolution(song: Song, folderId: Int? = null) {
        val existingSongs = songDao.getAllSongs().first()
        var newTitle = song.title
        var count = 2
        while (existingSongs.any { it.title == newTitle }) {
            newTitle = "${song.title} ($count)"
            count++
        }
        songDao.insert(song.copy(id = 0, title = newTitle, folderId = folderId))
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch { songDao.delete(song) }
    }

    fun deleteSongs(songs: List<Song>) {
        viewModelScope.launch {
            songs.forEach { songDao.delete(it) }
        }
    }

    fun moveSongsToFolder(songs: List<Song>, folderId: Int?) {
        viewModelScope.launch {
            songs.forEach { songDao.update(it.copy(folderId = folderId)) }
        }
    }

    fun addFolder(name: String) {
        viewModelScope.launch { songDao.insertFolder(Folder(name = name)) }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            songDao.removeSongsFromFolder(folder.id)
            songDao.deleteFolder(folder)
        }
    }

    fun updateSyncedData(song: Song, lrcData: String, fingerprint: List<Float>) {
        viewModelScope.launch {
            songDao.update(song.copy(lrcData = lrcData, fingerprint = fingerprint))
        }
    }

    fun exportLibrary(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val currentSongs = songs.value
            val currentFolders = folders.value
            val encryptedData = portabilityManager.exportLibrary(currentSongs, currentFolders)
            onResult(encryptedData)
        }
    }

    fun importLibrary(encodedData: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = portabilityManager.importLibrary(encodedData)
            if (result != null) {
                val (songs, folders) = result
                // Folders get new ids too, so remap each song to its folder's new id.
                val folderIdMap = folders.associate { it.id to songDao.insertFolder(it.copy(id = 0)).toInt() }
                songs.forEach { addSongWithConflictResolution(it, folderIdMap[it.folderId]) }
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }
}

class VerseViewModelFactory(private val application: Application, private val songDao: SongDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VerseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VerseViewModel(application, songDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

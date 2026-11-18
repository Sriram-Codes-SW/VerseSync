package dev.sriramcodessw.versesync.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<Song>>

    /** How many songs are in no setlist, over the whole library (the search box does not change it). See [FreeLimit]. */
    @Query("SELECT COUNT(*) FROM songs WHERE id NOT IN (SELECT songId FROM setlist_entries)")
    fun countLooseSongs(): Flow<Int>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongById(id: Int): Flow<Song>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<Song>>

    /**
     * Adds a song. Pass id 0, which makes a new row. With `REPLACE`, a song with an id that already
     * exists is deleted first and then written, and the foreign key then deletes that song's setlist
     * entries (ADR-20). To change a song use [update], never this.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song)

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Delete
    suspend fun deleteSongs(songs: List<Song>)

    // Folder Operations
    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM songs WHERE folderId = :folderId")
    fun getSongsInFolder(folderId: Int): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("UPDATE songs SET folderId = NULL WHERE folderId = :folderId")
    suspend fun removeSongsFromFolder(folderId: Int)
}

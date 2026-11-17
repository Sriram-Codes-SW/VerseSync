package dev.sriramcodessw.versesync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Setlists (ADR-20). They are not part of the library export. Use [appendSong] and [reorder] and do
 * not write positions directly: they keep the numbering and refuse a half applied reorder.
 */
@Dao
abstract class SetlistDao {
    @Query("SELECT * FROM setlists ORDER BY name COLLATE NOCASE, id")
    abstract fun getSetlists(): Flow<List<Setlist>>

    /** The entries of a setlist with their songs, in the order they are played. */
    @Transaction
    @Query("SELECT * FROM setlist_entries WHERE setlistId = :setlistId ORDER BY position, id")
    abstract fun getEntriesWithSongs(setlistId: Int): Flow<List<SetlistEntryWithSong>>

    @Query("SELECT * FROM setlist_entries WHERE setlistId = :setlistId ORDER BY position, id")
    abstract suspend fun getEntries(setlistId: Int): List<SetlistEntry>

    @Insert
    abstract suspend fun insertSetlist(setlist: Setlist): Long

    @Update
    abstract suspend fun updateSetlist(setlist: Setlist)

    /** Deleting a setlist deletes its entries and leaves the songs alone. */
    @Query("DELETE FROM setlists WHERE id = :setlistId")
    abstract suspend fun deleteSetlist(setlistId: Int)

    @Insert
    protected abstract suspend fun insertEntry(entry: SetlistEntry): Long

    @Query("SELECT MAX(position) FROM setlist_entries WHERE setlistId = :setlistId")
    protected abstract suspend fun maxPosition(setlistId: Int): Int?

    /** Adds a song at the end. The same song may be added again. Returns the new entry's id. */
    @Transaction
    open suspend fun appendSong(
        setlistId: Int,
        songId: Int,
    ): Int {
        val next = (maxPosition(setlistId) ?: -1) + 1
        return insertEntry(SetlistEntry(setlistId = setlistId, songId = songId, position = next)).toInt()
    }

    @Query("DELETE FROM setlist_entries WHERE id = :entryId")
    abstract suspend fun removeEntry(entryId: Int)

    @Query("UPDATE setlist_entries SET position = :position WHERE id = :entryId AND setlistId = :setlistId")
    protected abstract suspend fun setPosition(
        setlistId: Int,
        entryId: Int,
        position: Int,
    )

    /**
     * Puts the entries in the given order, numbering them 0, 1, 2 and so on in one transaction.
     * [orderedEntryIds] must be exactly the entries of the setlist, each once, or nothing changes
     * and [IllegalArgumentException] is thrown: a half applied reorder would scramble the setlist.
     */
    @Transaction
    open suspend fun reorder(
        setlistId: Int,
        orderedEntryIds: List<Int>,
    ) {
        val current = getEntries(setlistId).map { it.id }
        require(orderedEntryIds.size == current.size && orderedEntryIds.toSet() == current.toSet()) {
            "A reorder must list every entry of the setlist exactly once"
        }
        orderedEntryIds.forEachIndexed { index, entryId -> setPosition(setlistId, entryId, index) }
    }

    /**
     * Adds a song at [index] (0 is first, an index past the end means last) in one transaction, so
     * the setlist never shows the song at the end first. It is how an undo puts a removed song back.
     * Returns the new entry's id, which is not the old one.
     */
    @Transaction
    open suspend fun insertAt(
        setlistId: Int,
        songId: Int,
        index: Int,
    ): Int {
        val newId = appendSong(setlistId, songId)
        val ids = getEntries(setlistId).map { it.id }
        reorder(setlistId, SetlistOrder.move(ids, from = ids.lastIndex, to = index))
        return newId
    }

    /**
     * Moves an entry [by] places (-1 is one earlier, 1 is one later), stopping at the ends. An entry
     * that is not in the setlist changes nothing. Returns true when something moved.
     */
    @Transaction
    open suspend fun moveEntry(
        setlistId: Int,
        entryId: Int,
        by: Int,
    ): Boolean {
        val ids = getEntries(setlistId).map { it.id }
        val from = ids.indexOf(entryId)
        val moved = SetlistOrder.move(ids, from, from + by)
        if (moved == ids) return false
        reorder(setlistId, moved)
        return true
    }
}

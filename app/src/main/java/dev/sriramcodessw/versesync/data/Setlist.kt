package dev.sriramcodessw.versesync.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A named, ordered list of library songs for a concert (ADR-20). Kept on the device, not in the library export. */
@Entity(tableName = "setlists")
data class Setlist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
)

/**
 * One place in a setlist. It has its own id because a song may appear more than once.
 *
 * Entries are read in order of `position, id`. `position` is deliberately not unique, because a
 * reorder would break the constraint part way, and gaps are allowed (deleting a song removes its
 * entries and leaves a gap).
 *
 * Deleting a setlist or a song deletes its entries. Because of that, a song must never be written
 * with `OnConflictStrategy.REPLACE` on an existing id, which deletes the row first and would remove
 * that song's entries: edits use `@Update`, and inserts use id 0.
 */
@Entity(
    tableName = "setlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = Setlist::class,
            parentColumns = ["id"],
            childColumns = ["setlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["setlistId", "position"]), Index(value = ["songId"])],
)
data class SetlistEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val setlistId: Int,
    val songId: Int,
    val position: Int,
)

/** An entry together with its song, for showing a setlist. */
data class SetlistEntryWithSong(
    @Embedded val entry: SetlistEntry,
    @Relation(parentColumn = "songId", entityColumn = "id")
    val song: Song,
)

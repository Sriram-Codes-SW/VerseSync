package dev.sriramcodessw.versesync.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val artist: String,
    val lyrics: String,
    val language: String = "en-us",
    val folderId: Int? = null,
    val dateAdded: Long = System.currentTimeMillis(), // New field for sorting
    val lrcData: String? = null,
    val fingerprint: List<Float>? = null
)

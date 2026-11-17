package dev.sriramcodessw.versesync.ui.viewmodels

import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.ui.screens.songCountText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** TC-U-56: how many songs a folder holds, and how that is written on a folder row. */
class FolderCountsTest {
    private fun song(
        id: Int,
        folderId: Int?,
    ) = Song(id = id, title = "T$id", artist = "A", lyrics = "...", folderId = folderId)

    @Test
    fun countsSongsPerFolderAndIgnoresSongsInNoFolder() {
        val songs = listOf(song(1, 10), song(2, 10), song(3, 20), song(4, null))

        assertEquals(mapOf(10 to 2, 20 to 1), countByFolder(songs))
    }

    @Test
    fun anEmptyLibraryHasNoCounts() {
        assertEquals(emptyMap<Int, Int>(), countByFolder(emptyList()))
    }

    @Test
    fun aFolderWithNoSongsIsAbsentFromTheMap() {
        assertEquals(null, countByFolder(listOf(song(1, 10)))[99])
    }

    @Test
    fun theCountIsWrittenForOneNoneAndMany() {
        assertEquals("No songs", songCountText(0))
        assertEquals("1 song", songCountText(1))
        assertEquals("2 songs", songCountText(2))
        assertEquals("12 songs", songCountText(12))
    }
}

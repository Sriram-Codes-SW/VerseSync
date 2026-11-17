package dev.sriramcodessw.versesync.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.ui.screens.FolderScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TC-M-31: a folder shows the songs in it, and an empty folder says how to fill it. */
@RunWith(AndroidJUnit4::class)
class FolderScreenInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(
        id: Int,
        title: String,
    ) = Song(id = id, title = title, artist = "Artist $id", lyrics = "words")

    @Test
    fun showsTheFolderNameAndItsSongsAndOpensOneOnTap() {
        var opened = -1
        compose.setContent {
            FolderScreen(
                name = "Telugu hits",
                songs = listOf(song(1, "Alpha"), song(2, "Beta")),
                onSongClick = { opened = it },
                onBack = {},
            )
        }

        compose.onNodeWithText("Telugu hits").assertExists()
        compose.onNodeWithText("Alpha").assertExists()
        compose.onNodeWithText("Artist 2").assertExists()

        compose.onNodeWithText("Beta").performClick()
        assertEquals(2, opened)
    }

    @Test
    fun anEmptyFolderSaysHowToAddSongs() {
        compose.setContent { FolderScreen(name = "Empty", songs = emptyList(), onSongClick = {}, onBack = {}) }

        compose.onNodeWithText("No songs in this folder yet. Long press a song on the Songs tab, then choose Move.").assertExists()
    }

    @Test
    fun theBackButtonLeaves() {
        var left = 0
        compose.setContent { FolderScreen(name = "F", songs = emptyList(), onSongClick = {}, onBack = { left++ }) }

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, left)
    }
}

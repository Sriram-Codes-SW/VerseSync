package dev.sriramcodessw.versesync.concert

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sriramcodessw.versesync.data.Setlist
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.VerseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * TC-M-29 (ADR-20 Phase 1): the setlist editor on a real database. Songs move up and down and stop
 * at the ends, a removed song comes back where it was with Undo, the setlist can be renamed, and a
 * new setlist's id arrives once as an event.
 */
@RunWith(AndroidJUnit4::class)
class SetlistEditorInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var application: Application
    private lateinit var database: VerseDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private lateinit var viewModel: SetlistsViewModel
    private var listId = 0
    private var newSongRequests = 0

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(application, VerseDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File(application.cacheDir, "editor-test-${UUID.randomUUID()}.preferences_pb")
        val prefs = ConcertPreferences(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        runBlocking {
            val songs = database.songDao()
            listOf("First", "Second", "Third").forEach { songs.insert(Song(title = it, artist = "A", lyrics = "words of $it")) }
            val byTitle = songs.getAllSongs().first().associateBy { it.title }
            listId = database.setlistDao().insertSetlist(Setlist(name = "Show")).toInt()
            listOf("First", "Second", "Third").forEach { database.setlistDao().appendSong(listId, byTitle.getValue(it).id) }
        }
        viewModel = SetlistsViewModel(application, database.setlistDao(), prefs)
        compose.setContent {
            val entries by viewModel.entries(listId).collectAsState(initial = emptyList())
            val setlists by viewModel.setlists.collectAsState()
            SetlistEditorScreen(
                name = setlists.firstOrNull { it.id == listId }?.name.orEmpty(),
                entries = entries,
                library = emptyList(),
                removed = viewModel.removed,
                onAddSong = {},
                onNewSong = { newSongRequests++ },
                onMove = { entryId, by -> viewModel.move(listId, entryId, by) },
                onRemove = { row, index -> viewModel.remove(row, index) },
                onUndo = { viewModel.undoRemove(it) },
                onRename = { viewModel.rename(listId, it) },
                onStart = {},
                onBack = {},
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
        prefsFile.delete()
    }

    private fun titles(): List<String> =
        runBlocking {
            database
                .setlistDao()
                .getEntriesWithSongs(listId)
                .first()
                .map { it.song.title }
        }

    private fun waitForOrder(vararg expected: String) {
        compose.waitUntil(5_000) { titles() == expected.toList() }
        assertEquals(expected.toList(), titles())
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theFirstCannotMoveUpAndTheLastCannotMoveDown() {
        waitForText("1. First")

        compose.onNodeWithContentDescription("Move First up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move First down").assertIsEnabled()
        compose.onNodeWithContentDescription("Move Third down").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Third up").assertIsEnabled()
    }

    @Test
    fun moveUpAndMoveDownChangeTheOrder() {
        waitForText("1. First")

        compose.onNodeWithContentDescription("Move Third up").performClick()
        waitForOrder("First", "Third", "Second")

        compose.onNodeWithContentDescription("Move First down").performClick()
        waitForOrder("Third", "First", "Second")
        waitForText("1. Third")
    }

    @Test
    fun removeOffersAnUndoThatPutsTheSongBackWhereItWas() {
        waitForText("2. Second")

        compose.onNodeWithContentDescription("Remove Second").performClick()
        waitForOrder("First", "Third")
        waitForText("Removed Second")

        compose.onNodeWithText("Undo").performClick()
        waitForOrder("First", "Second", "Third")
    }

    @Test
    fun undoingTheRemovalOfTheFirstAndLastPutsThemBackAtTheEnds() {
        waitForText("1. First")

        compose.onNodeWithContentDescription("Remove First").performClick()
        waitForOrder("Second", "Third")
        compose.onNodeWithText("Undo").performClick()
        waitForOrder("First", "Second", "Third")
    }

    @Test
    fun renamingChangesTheTitleAndKeepsTheSongs() {
        waitForText("1. First")

        compose.onNodeWithContentDescription("Rename setlist").performClick()
        compose.onNodeWithText("Name").performTextClearance()
        compose.onNodeWithText("Name").performTextInput("Friday Gig")
        compose.onNodeWithText("Save").performClick()

        waitForText("Friday Gig")
        assertEquals(listOf("First", "Second", "Third"), titles())
    }

    @Test
    fun aBlankNameBecomesSetlistAndTheNewIdArrivesOnce() =
        runBlocking {
            viewModel.create("   ")

            val id = viewModel.created.first()

            val made =
                database
                    .setlistDao()
                    .getSetlists()
                    .first()
                    .first { it.id == id }
            assertEquals("Setlist", made.name)
        }

    @Test
    fun theSongPickerOffersANewSong_whichLeavesThePickerAndAsksForTheNewSongScreen() {
        waitForText("1. First")

        compose.onNodeWithContentDescription("Add a song to the setlist").performClick()
        waitForText("Add songs")
        compose.onNodeWithText("New song").performClick()

        compose.waitUntil(5_000) { newSongRequests == 1 }
        compose.onNodeWithText("Add songs").assertDoesNotExist()
        assertEquals(1, newSongRequests)
    }
}

package dev.sriramcodessw.versesync.concert

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * TC-M-27 (ADR-20 Phase 1): the concert screen on a real database and a real DataStore. It moves
 * between songs, jumps, blanks, shows a song that is not in the setlist, shows a placeholder for
 * missing lyrics, asks before leaving, and a new view model gets the saved place back.
 */
@RunWith(AndroidJUnit4::class)
class ConcertScreenInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var application: Application
    private lateinit var database: VerseDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private var showId = 0
    private var quietId = 0

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(application, VerseDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File(application.cacheDir, "concert-test-${UUID.randomUUID()}.preferences_pb")
        runBlocking {
            val songs = database.songDao()
            listOf(
                Song(title = "First", artist = "A", lyrics = "alpha one"),
                Song(title = "Second", artist = "A", lyrics = "beta two"),
                Song(title = "Third", artist = "A", lyrics = "gamma three"),
                Song(title = "Quiet", artist = "A", lyrics = ""),
                Song(title = "Spare", artist = "A", lyrics = "delta spare"),
            ).forEach { songs.insert(it) }
            val byTitle = songs.getAllSongs().first().associateBy { it.title }
            val setlists = database.setlistDao()
            showId = setlists.insertSetlist(Setlist(name = "Show")).toInt()
            listOf("First", "Second", "Third").forEach { setlists.appendSong(showId, byTitle.getValue(it).id) }
            quietId = setlists.insertSetlist(Setlist(name = "Quiet")).toInt()
            setlists.appendSong(quietId, byTitle.getValue("Quiet").id)
        }
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
        prefsFile.delete()
    }

    // One DataStore per file, as in the app. Every view model of a test shares it, like a restart would the saved file.
    private val sharedPrefs by lazy { ConcertPreferences(PreferenceDataStoreFactory.create(scope = scope) { prefsFile }) }

    private fun newPrefs() = sharedPrefs

    private fun newViewModel(setlistId: Int) = ConcertViewModel(application, setlistId, database.setlistDao(), database.songDao(), newPrefs())

    private val left = AtomicInteger()

    private fun show(setlistId: Int): ConcertViewModel {
        val viewModel = newViewModel(setlistId)
        compose.setContent { ConcertScreen(viewModel = viewModel, onLeave = { left.incrementAndGet() }) }
        return viewModel
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun gone(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()

    @Test
    fun startsOnTheFirstSongAndMovesWithNextAndPreviousStoppingAtTheEnds() {
        show(showId)
        waitFor("Song 1 of 3")
        waitFor("alpha one")
        compose.onNodeWithText("Previous").assertIsNotEnabled()

        compose.onNodeWithText("Next").performClick()
        waitFor("Song 2 of 3")
        waitFor("beta two")
        compose.onNodeWithText("Previous").assertIsEnabled()

        compose.onNodeWithText("Next").performClick()
        waitFor("Song 3 of 3")
        compose.onNodeWithText("Next").assertIsNotEnabled()

        compose.onNodeWithText("Previous").performClick()
        waitFor("Song 2 of 3")
    }

    @Test
    fun jumpsToAnySongFromTheList() {
        show(showId)
        waitFor("Song 1 of 3")

        compose.onNodeWithText("Songs").performClick()
        compose.onNodeWithText("3. Third").performClick()

        waitFor("Song 3 of 3")
        waitFor("gamma three")
    }

    @Test
    fun blankHidesTheLyricsAndShowBringsThemBack() {
        show(showId)
        waitFor("alpha one")

        compose.onNodeWithText("Blank").performClick()
        compose.waitUntil(5_000) { gone("alpha one") }

        compose.onNodeWithText("Show").performClick()
        waitFor("alpha one")
    }

    @Test
    fun showsALibrarySongThatIsNotInTheSetlistAndComesBack() {
        show(showId)
        waitFor("Song 1 of 3")

        compose.onNodeWithText("Songs").performClick()
        compose.onNodeWithText("A song not in the setlist").performClick()
        compose.onNodeWithText("Spare - A").performClick()

        waitFor("Not in the setlist")
        waitFor("delta spare")

        compose.onNodeWithText("Songs").performClick()
        compose.onNodeWithText("Back to the setlist").performClick()
        waitFor("Song 1 of 3")
        waitFor("alpha one")
    }

    @Test
    fun aSongWithNoLyricsShowsAPlaceholderInsteadOfABlankScreen() {
        show(quietId)

        waitFor("No lyrics")
    }

    @Test
    fun backAsksBeforeLeavingAndStayKeepsTheConcertOpen() {
        show(showId)
        waitFor("Song 1 of 3")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("Leave the concert?")
        compose.onNodeWithText("Stay").performClick()
        compose.waitUntil(5_000) { gone("Leave the concert?") }
        assertEquals(0, left.get())

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("Leave the concert?")
        compose.onNodeWithText("Leave").performClick()
        compose.waitUntil(5_000) { left.get() == 1 }
    }

    @Test
    fun keepsTheScreenOnWhileShown() {
        show(showId)
        waitFor("Song 1 of 3")

        fun anyKeepsScreenOn(view: View): Boolean = view.keepScreenOn || (view is ViewGroup && (0 until view.childCount).any { anyKeepsScreenOn(view.getChildAt(it)) })

        var on = false
        compose.runOnUiThread { on = anyKeepsScreenOn(compose.activity.window.decorView) }
        assertTrue(on)
    }

    @Test
    fun aNewViewModelGetsTheSavedPlaceBack() =
        runBlocking {
            val first = newViewModel(showId)
            first.state.first { it.loaded }
            first.next()
            first.next()
            // Give the save a moment: it runs on the view model's scope.
            val saved = newPrefs().savedPlace.first { it?.index == 2 }
            assertEquals(showId, saved?.setlistId)

            val second = newViewModel(showId)
            val restored = second.state.first { it.loaded }

            assertEquals(2, restored.controller.index)
            assertEquals(saved?.entryId, restored.controller.anchorEntryId)
        }

    @Test
    fun aSavedPlaceForAnotherSetlistIsNotUsed() =
        runBlocking {
            val first = newViewModel(showId)
            first.state.first { it.loaded }
            first.next()
            newPrefs().savedPlace.first { it?.index == 1 }

            val other = newViewModel(quietId).state.first { it.loaded }

            assertEquals(0, other.controller.index)
            assertFalse(other.setlistGone)
        }

    @Test
    fun openingAConcertSavesThePlaceAtOnceSoItCanBeResumed() =
        runBlocking {
            newViewModel(showId).state.first { it.loaded }

            val saved = newPrefs().savedPlace.first { it != null }

            assertEquals(showId, saved?.setlistId)
            assertEquals(0, saved?.index)
        }

    @Test
    fun deletingTheSongBeingShownMovesThePlaceSavesItAndStartsTheNextSongAtTheTop() =
        runBlocking {
            val viewModel = newViewModel(showId)
            viewModel.state.first { it.loaded }
            viewModel.next()
            viewModel.saveScroll(400)
            val second = database.setlistDao().getEntries(showId)[1]

            val secondSong =
                database
                    .songDao()
                    .getAllSongs()
                    .first()
                    .first { it.id == second.songId }
            database.songDao().delete(secondSong)

            val moved = viewModel.state.first { it.controller.entries.size == 2 }
            assertEquals(0, moved.initialScroll)
            val saved = newPrefs().savedPlace.first { it?.scroll == 0 && it.entryId == moved.controller.anchorEntryId }
            assertEquals(showId, saved?.setlistId)
        }

    @Test
    fun aDeletedSetlistDoesNotClearAnotherSetlistsSavedPlace() =
        runBlocking {
            // A place is saved for the "Show" setlist. Opening a setlist that does not exist must leave it alone.
            val show = newViewModel(showId)
            show.state.first { it.loaded }
            newPrefs().savedPlace.first { it?.setlistId == showId }

            val gone = newViewModel(9999).state.first { it.setlistGone }

            assertTrue(gone.setlistGone)
            assertEquals(showId, newPrefs().savedPlace.first()?.setlistId)
        }
}

package dev.sriramcodessw.versesync.ui.viewmodels

import dev.sriramcodessw.versesync.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ShareViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var verseViewModelMock: VerseViewModel
    private lateinit var viewModel: ShareViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        verseViewModelMock = mock()
        viewModel = ShareViewModel(verseViewModelMock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onSongSelected_shouldAddOrRemoveSongFromSelectedList() {
        val song = Song(1, "t", "a", "l")

        // Add to list
        viewModel.onSongSelected(song, true)
        assertTrue(viewModel.selectedSongs.contains(song), "Song should be added to selected list")

        // Remove from list
        viewModel.onSongSelected(song, false)
        assertFalse(viewModel.selectedSongs.contains(song), "Song should be removed from selected list")
    }

    @Test
    fun acceptSong_shouldAddSongAndRemoveFromIncoming() = runTest {
        val song = Song(2, "t2", "a2", "l2")
        viewModel.incomingSongs.add(song)

        viewModel.acceptSong(song)

        verify(verseViewModelMock).addSongWithConflictResolution(song)
        assertFalse(viewModel.incomingSongs.contains(song), "Accepted song should be removed from incoming list")
    }

    @Test
    fun rejectSong_shouldRemoveFromIncoming() {
        val song = Song(3, "t3", "a3", "l3")
        viewModel.incomingSongs.add(song)

        viewModel.rejectSong(song)

        assertFalse(viewModel.incomingSongs.contains(song), "Rejected song should be removed from incoming list")
    }

    @Test
    fun acceptSelectedSongs_shouldAddAllSelectedAndClearLists() = runTest {
        val song1 = Song(4, "t4", "a4", "l4")
        val song2 = Song(5, "t5", "a5", "l5")
        viewModel.incomingSongs.addAll(listOf(song1, song2))
        viewModel.selectedSongs.addAll(listOf(song1, song2))

        viewModel.acceptSelectedSongs()

        verify(verseViewModelMock).addSongWithConflictResolution(song1)
        verify(verseViewModelMock).addSongWithConflictResolution(song2)
        assertTrue(viewModel.incomingSongs.isEmpty(), "Incoming songs list should be empty after accepting all")
        assertTrue(viewModel.selectedSongs.isEmpty(), "Selected songs list should be empty after accepting all")
    }

    @Test
    fun rejectAllSongs_shouldClearIncomingAndSelectedLists() {
        val song1 = Song(6, "t6", "a6", "l6")
        val song2 = Song(7, "t7", "a7", "l7")
        viewModel.incomingSongs.addAll(listOf(song1, song2))
        viewModel.selectedSongs.addAll(listOf(song1, song2))

        viewModel.rejectAllSongs()

        assertTrue(viewModel.incomingSongs.isEmpty(), "Incoming songs list should be empty after rejecting all")
        assertTrue(viewModel.selectedSongs.isEmpty(), "Selected songs list should be empty after rejecting all")
    }
}

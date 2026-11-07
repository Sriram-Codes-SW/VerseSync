package dev.sriramcodessw.versesync.ui.viewmodels

import android.app.Application
import app.cash.turbine.test
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class VerseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var songDaoMock: SongDao
    private lateinit var applicationMock: Application
    private lateinit var viewModel: VerseViewModel
    private val allSongsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Song>>(emptyList())

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        songDaoMock = mock()
        applicationMock = mock()
        allSongsFlow.value = emptyList()
        whenever(songDaoMock.getAllSongs()).thenReturn(allSongsFlow)
        whenever(songDaoMock.getAllFolders()).thenReturn(flowOf(emptyList()))
        viewModel = VerseViewModel(applicationMock, songDaoMock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateSortOrder_shouldTriggerReSorting() = runTest {
        val song1 = Song(id = 1, title = "B", artist = "Z", lyrics = "...")
        val song2 = Song(id = 2, title = "A", artist = "Y", lyrics = "...")
        allSongsFlow.value = listOf(song1, song2)

        // When sorting by Title
        viewModel.updateSortOrder(SortOrder.TITLE)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.songs.test {
            var sorted = awaitItem()
            if (sorted.isEmpty()) {
                sorted = awaitItem()
            }
            assertEquals("A", sorted[0].title)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun deleteSongs_shouldCallDeleteForEachSongInList() = runTest {
        val songsToDelete = listOf(
            Song(id = 1, title = "S1", artist = "A1", lyrics = "..."),
            Song(id = 2, title = "S2", artist = "A2", lyrics = "...")
        )
        
        viewModel.deleteSongs(songsToDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(songDaoMock).delete(songsToDelete[0])
        verify(songDaoMock).delete(songsToDelete[1])
    }

    @Test
    fun moveSongsToFolder_shouldUpdateEachSongWithNewFolderId() = runTest {
        val songsToMove = listOf(
            Song(id = 1, title = "S1", artist = "A1", lyrics = "..."),
            Song(id = 2, title = "S2", artist = "A2", lyrics = "...")
        )
        val targetFolderId = 100
        
        viewModel.moveSongsToFolder(songsToMove, targetFolderId)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(songDaoMock).update(songsToMove[0].copy(folderId = targetFolderId))
        verify(songDaoMock).update(songsToMove[1].copy(folderId = targetFolderId))
    }
}

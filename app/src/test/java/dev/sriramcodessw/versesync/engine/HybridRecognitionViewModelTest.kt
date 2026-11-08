package dev.sriramcodessw.versesync.engine

import dev.sriramcodessw.versesync.VerseSyncApp
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.data.VerseDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

class HybridRecognitionViewModelTest {
    private lateinit var appMock: VerseSyncApp
    private lateinit var databaseMock: VerseDatabase
    private lateinit var songDaoMock: SongDao
    private lateinit var engineMock: HybridRecognitionEngine
    private lateinit var viewModel: HybridRecognitionViewModel

    @BeforeEach
    fun setup() {
        appMock = mock()
        databaseMock = mock()
        songDaoMock = mock()
        engineMock = mock()

        whenever(appMock.database).thenReturn(databaseMock)
        whenever(databaseMock.songDao()).thenReturn(songDaoMock)

        viewModel = HybridRecognitionViewModel(appMock)

        val engineField: Field = viewModel.javaClass.getDeclaredField("hybridRecognitionEngine")
        engineField.isAccessible = true
        engineField.set(viewModel, engineMock)
    }

    @Test
    fun startRecognition_shouldDelegateCallToEngine() {
        val isPro = true
        viewModel.startRecognition(isPro)
        verify(engineMock).startRecognition(isPro)
    }

    @Test
    fun stopRecognition_shouldDelegateCallToEngine() {
        viewModel.stopRecognition()
        verify(engineMock).stopRecognition()
    }
}

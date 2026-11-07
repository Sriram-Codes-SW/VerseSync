package dev.sriramcodessw.versesync.data

import android.app.Application
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

@ExperimentalCoroutinesApi
class LanguageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var applicationMock: Application
    private lateinit var viewModel: LanguageViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        applicationMock = mock()
        val downloadManagerMock: android.app.DownloadManager = mock()
        whenever(applicationMock.getSystemService(android.content.Context.DOWNLOAD_SERVICE)).thenReturn(downloadManagerMock)
        whenever(applicationMock.applicationContext).thenReturn(applicationMock)
        whenever(applicationMock.filesDir).thenReturn(File("build/tmp/testFilesDir").apply { mkdirs() })
        viewModel = LanguageViewModel(applicationMock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun isSetupComplete_shouldInitiallyBeFalse() = runTest {
        viewModel.isSetupComplete.test {
            assertEquals(false, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun markSetupComplete_shouldUpdateState() = runTest {
        viewModel.markSetupComplete()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.isSetupComplete.test {
            var item = awaitItem()
            if (!item) {
                item = awaitItem()
            }
            assertTrue(item)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun availableLanguages_shouldContainEnglishAndHindi() {
        val languages = viewModel.availableLanguages
        assertTrue(languages.any { it.code == "en-us" })
        assertTrue(languages.any { it.code == "hi" })
    }
}

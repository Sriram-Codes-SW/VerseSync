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
        // Mocking filesDir for removeLanguage test
        whenever(applicationMock.filesDir).thenReturn(File("temp"))
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
        // Allow for DataStore to persist
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.isSetupComplete.test {
            assertTrue(awaitItem())
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

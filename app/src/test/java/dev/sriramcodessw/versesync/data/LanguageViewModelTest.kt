package dev.sriramcodessw.versesync.data

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Executors

@ExperimentalCoroutinesApi
class LanguageViewModelTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope
    // A real single thread stands in for Android's main thread.
    private val mainExecutor = Executors.newSingleThreadExecutor()
    private lateinit var prefs: LanguagePreferences
    private lateinit var viewModel: LanguageViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val applicationMock: Application = mock()
        val downloadManagerMock: android.app.DownloadManager = mock()
        whenever(applicationMock.getSystemService(android.content.Context.DOWNLOAD_SERVICE)).thenReturn(downloadManagerMock)
        whenever(applicationMock.applicationContext).thenReturn(applicationMock)
        whenever(applicationMock.filesDir).thenReturn(File(tempDir, "files").apply { mkdirs() })
        prefs = LanguagePreferences(createTestDataStore(tempDir, dataStoreScope))
        viewModel = LanguageViewModel(applicationMock, prefs)
    }

    @AfterEach
    fun tearDown() {
        // Stop the view model collecting before the DataStore scope is cancelled, and reset Main
        // last, or cancellation surfaces as an uncaught exception in the next test.
        viewModel.viewModelScope.cancel()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
        mainExecutor.shutdownNow()
    }

    @Test
    fun isSetupComplete_shouldInitiallyBeFalse() = runBlocking {
        assertEquals(false, awaitReal { viewModel.isSetupComplete.first() })
    }

    @Test
    fun markSetupComplete_persistsCompletion() = runBlocking {
        viewModel.markSetupComplete()

        assertTrue(awaitReal { prefs.isSetupComplete.first { it } })
    }

    @Test
    fun availableLanguages_shouldContainEnglishAndHindi() {
        val languages = viewModel.availableLanguages
        assertTrue(languages.any { it.code == "en-us" })
        assertTrue(languages.any { it.code == "hi" })
    }
}

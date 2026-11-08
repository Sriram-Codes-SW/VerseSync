package dev.sriramcodessw.versesync.data

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.sriramcodessw.versesync.engine.languageSamples
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        // last (see resetTestMain), or cancellation surfaces as an uncaught exception.
        resetTestMain(mainExecutor, viewModel.viewModelScope, dataStoreScope)
    }

    @Test
    fun isSetupComplete_shouldInitiallyBeFalse() =
        runBlocking {
            assertEquals(false, awaitReal { viewModel.isSetupComplete.first() })
        }

    @Test
    fun markSetupComplete_persistsCompletion() =
        runBlocking {
            viewModel.markSetupComplete()

            assertTrue(awaitReal { prefs.isSetupComplete.first { it } })
        }

    @Test
    fun availableLanguages_areAllCoveredByMatchingSamples() {
        // Adding a language without a sample phrase in LanguageSamples.kt should fail here.
        assertEquals(languageSamples.keys, viewModel.availableLanguages.map { it.code }.toSet())
    }

    @Test
    fun availableLanguages_haveUniqueCodesAndHttpsModelUrls() {
        val languages = viewModel.availableLanguages
        assertEquals(languages.size, languages.map { it.code }.toSet().size)
        assertTrue(languages.all { it.url == null || it.url.startsWith("https://") })
    }

    @Test
    fun languagesWithoutAPublishedSpeechModel_areMarkedAndDoNotNeedOne() {
        // Vosk publishes no usable model for these. A download link would only ever fail.
        val withoutModel =
            viewModel.availableLanguages
                .filter { !it.hasSpeechModel }
                .map { it.code }
                .toSet()

        assertEquals(setOf("ta", "mr", "bn", "kn", "ml", "pa", "el"), withoutModel)
        withoutModel.forEach { assertFalse(viewModel.needsSpeechModel(it)) }
        assertTrue(viewModel.needsSpeechModel("en-us"))
        assertTrue(viewModel.needsSpeechModel("not-a-language"))
    }

    @Test
    fun downloadLanguage_doesNothingWhenNoSpeechModelExists() {
        val tamil = viewModel.availableLanguages.first { it.code == "ta" }

        viewModel.downloadLanguage(tamil)

        assertEquals(emptyMap<String, Int>(), viewModel.downloadStatus.value)
    }

    @Test
    fun availableLanguages_shouldContainEnglishAndHindi() {
        val languages = viewModel.availableLanguages
        assertTrue(languages.any { it.code == "en-us" })
        assertTrue(languages.any { it.code == "hi" })
    }
}

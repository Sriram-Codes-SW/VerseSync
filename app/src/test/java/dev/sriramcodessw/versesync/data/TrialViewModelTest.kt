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
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Executors

@ExperimentalCoroutinesApi
class TrialViewModelTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope

    // A real single thread stands in for Android's main thread.
    private val mainExecutor = Executors.newSingleThreadExecutor()
    private lateinit var applicationMock: Application
    private lateinit var prefs: TrialPreferences
    private val viewModels = mutableListOf<TrialViewModel>()

    private fun newViewModel() = TrialViewModel(applicationMock, prefs).also { viewModels += it }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        applicationMock = mock()
        whenever(applicationMock.applicationContext).thenReturn(applicationMock)
        prefs = TrialPreferences(createTestDataStore(tempDir, dataStoreScope))
    }

    @AfterEach
    fun tearDown() {
        // Stop view models collecting before the DataStore scope is cancelled, and reset Main
        // last (see resetTestMain), or cancellation surfaces as an uncaught exception.
        resetTestMain(mainExecutor, *viewModels.map { it.viewModelScope }.toTypedArray(), dataStoreScope)
    }

    @Test
    fun remainingTrials_shouldInitiallyBeMax() =
        runBlocking {
            assertEquals(TrialPreferences.MAX_TRIALS, awaitReal { newViewModel().remainingTrials.first() })
        }

    @Test
    fun remainingTrials_reflectsTrialsUsedBeforeStartup() =
        runBlocking {
            prefs.useTrial()

            val remaining = awaitReal { newViewModel().remainingTrials.first { it != TrialPreferences.MAX_TRIALS } }

            assertEquals(TrialPreferences.MAX_TRIALS - 1, remaining)
        }

    @Test
    fun useTrial_shouldDecrementCount() =
        runBlocking {
            newViewModel().useTrial()

            assertEquals(
                TrialPreferences.MAX_TRIALS - 1,
                awaitReal { prefs.remainingTrials.first { it == TrialPreferences.MAX_TRIALS - 1 } },
            )
        }

    @Test
    fun useTrial_neverGoesBelowZero() =
        runBlocking {
            val viewModel = newViewModel()
            repeat(TrialPreferences.MAX_TRIALS + 2) { viewModel.useTrial() }

            assertEquals(0, awaitReal { prefs.remainingTrials.first { it == 0 } })
        }
}

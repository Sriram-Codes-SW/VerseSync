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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class TrialViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var applicationMock: Application
    private lateinit var viewModel: TrialViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        applicationMock = mock()
        whenever(applicationMock.applicationContext).thenReturn(applicationMock)
        viewModel = TrialViewModel(applicationMock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun remainingTrials_shouldInitiallyBeMax() = runTest {
        viewModel.remainingTrials.test {
            assertEquals(3, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun useTrial_shouldDecrementCount() = runTest {
        viewModel.useTrial()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.remainingTrials.test {
            var item = awaitItem()
            if (item == 3) {
                item = awaitItem()
            }
            assertEquals(2, item)
            cancelAndConsumeRemainingEvents()
        }
    }
}

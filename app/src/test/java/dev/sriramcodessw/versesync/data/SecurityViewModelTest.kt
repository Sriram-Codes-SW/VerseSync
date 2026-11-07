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

@ExperimentalCoroutinesApi
class SecurityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var applicationMock: Application
    private lateinit var viewModel: SecurityViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        applicationMock = mock()
        viewModel = SecurityViewModel(applicationMock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setAppLockEnabled_shouldUpdateState() = runTest {
        viewModel.setAppLockEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.isAppLockEnabled.test {
            assertEquals(true, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun setLockType_shouldUpdateState() = runTest {
        viewModel.setLockType(LockType.VOCAL)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.lockType.test {
            assertEquals(LockType.VOCAL, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}

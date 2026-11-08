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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Executors
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class SecurityViewModelTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope

    // A real single thread stands in for Android's main thread.
    private val mainExecutor = Executors.newSingleThreadExecutor()
    private lateinit var applicationMock: Application
    private lateinit var prefs: SecurityPreferences
    private val viewModels = mutableListOf<SecurityViewModel>()

    private fun newViewModel() = SecurityViewModel(applicationMock, prefs).also { viewModels += it }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        applicationMock = mock()
        whenever(applicationMock.applicationContext).thenReturn(applicationMock)
        prefs = SecurityPreferences(createTestDataStore(tempDir, dataStoreScope))
    }

    @AfterEach
    fun tearDown() {
        // Stop view models collecting before the DataStore scope is cancelled, and reset Main
        // last, or cancellation surfaces as an uncaught exception in the next test.
        viewModels.forEach { it.viewModelScope.cancel() }
        dataStoreScope.cancel()
        Dispatchers.resetMain()
        mainExecutor.shutdownNow()
    }

    @Test
    fun lockConfig_isNullUntilSavedSettingsAreLoaded() {
        // A non-null default here would let the app unlock before the real setting is known.
        assertNull(newViewModel().lockConfig.value)
    }

    @Test
    fun lockConfig_loadsDefaultsFromEmptyStorage() =
        runBlocking {
            val config = awaitReal { newViewModel().lockConfig.first { it != null } }

            assertEquals(LockConfig(enabled = false, type = LockType.DEVICE), config)
        }

    @Test
    fun lockConfig_reflectsSettingsSavedBeforeStartup() =
        runBlocking {
            // Saved first, then a fresh view model, as on a cold start.
            prefs.setAppLockEnabled(true)
            prefs.setLockType(LockType.VOCAL)

            val config = awaitReal { newViewModel().lockConfig.first { it != null } }

            assertEquals(LockConfig(enabled = true, type = LockType.VOCAL), config)
        }

    @Test
    fun setAppLockEnabled_persistsTheSetting() =
        runBlocking {
            newViewModel().setAppLockEnabled(true)

            assertEquals(true, awaitReal { prefs.isAppLockEnabled.first { it } })
        }

    @Test
    fun setLockType_persistsTheSetting() =
        runBlocking {
            newViewModel().setLockType(LockType.VOCAL)

            assertEquals(LockType.VOCAL, awaitReal { prefs.lockType.first { it == LockType.VOCAL } })
        }
}

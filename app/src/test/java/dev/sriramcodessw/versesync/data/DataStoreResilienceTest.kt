package dev.sriramcodessw.versesync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A corrupt or unreadable settings file used to crash the app on every launch. These tests use the
 * same corruption handler and safe read the app uses.
 */
class DataStoreResilienceTest {
    @TempDir
    lateinit var tempDir: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    /** A file that ends in the middle of a field, which the preferences parser rejects. */
    private fun corruptFile(): File =
        File(tempDir, "settings.preferences_pb").also {
            it.writeBytes(byteArrayOf(0x0d, 0x0a, 0x64, 0x61, 0x62, 0x63))
        }

    @Test
    fun corruptSettingsFile_isReplacedAndSettingsFallBackToDefaults() =
        runBlocking {
            val file = corruptFile()
            val store =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = settingsCorruptionHandler,
                    scope = scope,
                    produceFile = { file },
                )
            val prefs = SecurityPreferences(store)

            assertEquals(false, prefs.isAppLockEnabled.first())
            assertEquals(LockType.DEVICE, prefs.lockType.first())
        }

    @Test
    fun afterCorruption_settingsCanBeWrittenAgain() =
        runBlocking {
            val file = corruptFile()
            val store =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = settingsCorruptionHandler,
                    scope = scope,
                    produceFile = { file },
                )
            val prefs = SecurityPreferences(store)

            // The write path also threw on a corrupt file before the handler was added.
            prefs.setAppLockEnabled(true)

            assertEquals(true, prefs.isAppLockEnabled.first())
        }

    @Test
    fun withoutTheHandler_aCorruptFileStillFails() {
        // Documents why the handler exists. Remove it and the crash returns.
        val file = corruptFile()
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

        assertFailsWith<Exception> { runBlocking { store.data.first() } }
    }

    @Test
    fun ioErrorWhileReading_fallsBackToDefaults() =
        runBlocking {
            val prefs = TrialPreferences(failingStore(IOException("disk full")))

            assertEquals(TrialPreferences.MAX_TRIALS, prefs.remainingTrials.first())
        }

    @Test
    fun otherErrorsWhileReading_areNotHidden() {
        val prefs = TrialPreferences(failingStore(IllegalStateException("a real bug")))

        assertFailsWith<IllegalStateException> { runBlocking { prefs.remainingTrials.first() } }
    }

    private fun failingStore(error: Throwable): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw error }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
        }
}

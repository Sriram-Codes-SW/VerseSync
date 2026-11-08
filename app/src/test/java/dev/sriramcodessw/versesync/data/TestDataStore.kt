package dev.sriramcodessw.versesync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * A DataStore backed by its own file in [directory], so each test starts from empty state.
 *
 * The production `Context.dataStore` is a process-wide singleton. Using it from tests shared one
 * on-disk file between every test class, which made results depend on execution order.
 * Cancel [scope] in the test's teardown, after cancelling the view model's own scope.
 */
internal fun createTestDataStore(
    directory: File,
    scope: CoroutineScope,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "test.preferences_pb") }

/**
 * Waits for [block] using wall-clock time. DataStore does real file I/O on another thread, so
 * these tests run under `runBlocking` instead of `runTest`, whose virtual-time scheduler does not
 * cooperate with work that completes off-thread.
 */
internal suspend fun <T> awaitReal(
    timeoutMillis: Long = 10_000,
    block: suspend () -> T,
): T = withTimeout(timeoutMillis) { block() }

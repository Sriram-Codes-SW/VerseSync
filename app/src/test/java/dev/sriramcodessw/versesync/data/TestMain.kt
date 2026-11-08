package dev.sriramcodessw.versesync.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Ends a test that used a single-thread [executor] as `Dispatchers.Main`.
 *
 * Cancelling a view model or the DataStore scope resumes coroutines through `Dispatchers.Main`,
 * from the fake main thread and from IO threads. If `Main` is reset while that happens, the reset
 * throws `IllegalStateException` ("Dispatchers.Main is used concurrently with setting it").
 * So each of [scopes] is cancelled and awaited, in order, and then the executor's queue is
 * drained before `Main` is reset. List the view model scopes before the DataStore scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun resetTestMain(
    executor: ExecutorService,
    vararg scopes: CoroutineScope,
) {
    runBlocking {
        withTimeoutOrNull(5_000) { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
    }
    // Drain twice: the first batch of tasks can queue more.
    repeat(2) { runCatching { executor.submit {}.get(5, TimeUnit.SECONDS) } }
    Dispatchers.resetMain()
    executor.shutdownNow()
}

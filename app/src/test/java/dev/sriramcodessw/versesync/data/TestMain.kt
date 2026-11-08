package dev.sriramcodessw.versesync.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Ends a test that used a single-thread [executor] as `Dispatchers.Main`.
 *
 * Cancelling a view model or the DataStore scope resumes coroutines through `Dispatchers.Main`,
 * from the fake main thread and from IO threads. If `Main` is reset while that happens, the reset
 * throws `IllegalStateException` ("Dispatchers.Main is used concurrently with setting it").
 * So each of [scopes] is cancelled and awaited, in order, and then the executor is shut down and
 * awaited so that everything already queued has run. Only then is `Main` reset. List the view model
 * scopes before the DataStore scope.
 *
 * A scope or executor that does not finish within five seconds fails the test, because carrying on
 * would bring back the race this exists to prevent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun resetTestMain(
    executor: ExecutorService,
    vararg scopes: CoroutineScope,
) {
    runBlocking {
        withTimeout(5_000) { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
    }
    executor.shutdown()
    check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "The fake main thread did not finish its queued work" }
    Dispatchers.resetMain()
}

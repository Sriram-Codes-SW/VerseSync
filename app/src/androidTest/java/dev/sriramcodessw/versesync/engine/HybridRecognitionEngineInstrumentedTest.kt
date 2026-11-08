package dev.sriramcodessw.versesync.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sriramcodessw.versesync.data.SongDao
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HybridRecognitionEngineInstrumentedTest {
    @Test
    fun startRecognition_doesNotCrashOnARealDevice() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val songDaoMock: SongDao = mock()

        val engine = HybridRecognitionEngine(appContext, songDaoMock)
        val latch = CountDownLatch(1)

        try {
            engine.startRecognition(isPro = true)
            // Wait for 1 second without blocking the main thread in a problematic way.
            latch.await(1, TimeUnit.SECONDS)
            engine.stopRecognition()
        } catch (e: Exception) {
            // If any exception is thrown, the test fails.
            assert(false) { "HybridRecognitionEngine crashed during execution: ${e.message}" }
        }
    }
}

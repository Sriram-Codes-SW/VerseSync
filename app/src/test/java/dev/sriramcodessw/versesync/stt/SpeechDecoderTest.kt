package dev.sriramcodessw.versesync.stt

import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-U-36: the speech decoder that replaces the speech library's own microphone recorder. */
class SpeechDecoderTest {
    private class FakeRecognizer(
        private val finishesUtterance: (callNumber: Int) -> Boolean = { false },
        private val blockUntil: CountDownLatch? = null,
    ) : WaveformRecognizer {
        val received = CopyOnWriteArrayList<ShortArray>()
        val closedWhileInUse =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        override fun acceptWaveForm(
            samples: ShortArray,
            length: Int,
        ): Boolean {
            blockUntil?.await(5, TimeUnit.SECONDS)
            received += samples.copyOf(length)
            return finishesUtterance(received.size)
        }

        override fun result() = """{"text":"final words"}"""

        override fun partialResult() = """{"partial":"partial words"}"""
    }

    private fun waitFor(
        timeoutMillis: Long = 3000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    @Test
    fun aFrameIsConvertedToSixteenBitSamplesAndGivenToTheRecognizer() {
        val recognizer = FakeRecognizer()
        val decoder = SpeechDecoder(recognizer, onResult = {})

        decoder.offer(floatArrayOf(0f, 0.5f, -0.5f, 1.0f, -1.0f, 2.0f))
        waitFor { recognizer.received.isNotEmpty() }
        decoder.stop()

        assertEquals(listOf<Short>(0, 16384, -16384, 32767, -32768, 32767), recognizer.received.first().toList())
    }

    @Test
    fun aSixteenBitSampleSurvivesTheRoundTripThroughFloat() {
        // The microphone gives 16-bit samples and the app turns them into floats for the pitch
        // detector. Turning them back for the recognizer must give exactly the original audio.
        val recognizer = FakeRecognizer()
        val decoder = SpeechDecoder(recognizer, onResult = {})
        val original = ShortArray(2000) { (it * 31 - 30000).toShort() }

        decoder.offer(FloatArray(original.size) { original[it] / 32768f })
        waitFor { recognizer.received.isNotEmpty() }
        decoder.stop()

        assertEquals(original.toList(), recognizer.received.first().toList())
    }

    @Test
    fun anUnfinishedUtteranceGivesAPartialResultAndAFinishedOneGivesTheResult() {
        val recognizer = FakeRecognizer(finishesUtterance = { it == 2 })
        val results = CopyOnWriteArrayList<String>()
        val decoder = SpeechDecoder(recognizer, onResult = { results += it })

        decoder.offer(FloatArray(8))
        decoder.offer(FloatArray(8))
        waitFor { results.size >= 2 }
        decoder.stop()

        assertEquals(listOf("""{"partial":"partial words"}""", """{"text":"final words"}"""), results.toList())
    }

    @Test
    fun aSlowRecognizerNeverBlocksTheAudioThreadAndTheOldestFramesAreDropped() {
        val gate = CountDownLatch(1)
        val recognizer = FakeRecognizer(blockUntil = gate)
        val decoder = SpeechDecoder(recognizer, onResult = {}, capacity = 4)

        val started = System.nanoTime()
        repeat(100) { decoder.offer(FloatArray(8) { _ -> it / 100f }) }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        gate.countDown()
        decoder.stop()

        assertTrue(elapsedMillis < 1000, "Offering 100 frames took $elapsedMillis ms, so the audio thread was blocked")
        assertTrue(decoder.droppedFrames > 0, "Frames should have been dropped")
    }

    @Test
    fun stopWaitsForTheRecognizerToBeFreeAndLaterFramesAreIgnored() {
        val recognizer = FakeRecognizer()
        val decoder = SpeechDecoder(recognizer, onResult = {})
        decoder.offer(FloatArray(8))
        waitFor { recognizer.received.isNotEmpty() }

        assertTrue(decoder.stop(), "The decoder should have finished")
        val countAtStop = recognizer.received.size
        decoder.offer(FloatArray(8))
        Thread.sleep(150)

        assertEquals(countAtStop, recognizer.received.size, "The recognizer was used after stop returned")
    }

    @Test
    fun stoppingTwiceIsHarmless() {
        val decoder = SpeechDecoder(FakeRecognizer(), onResult = {})

        assertTrue(decoder.stop())
        assertTrue(decoder.stop())
    }

    @Test
    fun aRecognizerFailureOnOneFrameDoesNotEndRecognition() {
        var calls = 0
        val survivor = CopyOnWriteArrayList<String>()
        val recognizer =
            object : WaveformRecognizer {
                override fun acceptWaveForm(
                    samples: ShortArray,
                    length: Int,
                ): Boolean {
                    if (++calls == 1) throw IllegalStateException("bad frame")
                    return false
                }

                override fun result() = ""

                override fun partialResult() = """{"partial":"still working"}"""
            }
        val decoder = SpeechDecoder(recognizer, onResult = { survivor += it })

        decoder.offer(FloatArray(8))
        decoder.offer(FloatArray(8))
        waitFor { survivor.isNotEmpty() }
        decoder.stop()

        assertFalse(survivor.isEmpty(), "Recognition should continue after a failed frame")
    }
}

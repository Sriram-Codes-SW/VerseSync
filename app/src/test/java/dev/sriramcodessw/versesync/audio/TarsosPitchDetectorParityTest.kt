package dev.sriramcodessw.versesync.audio

import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import dev.sriramcodessw.versesync.audio.tarsos.TarsosPitchDetector
import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals
import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals.frames
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

/**
 * TC-U-34: the detector behind the new interface gives exactly what the library's own
 * `PitchProcessor` gave the app before the interface existed, frame for frame.
 */
class TarsosPitchDetectorParityTest {
    private data class Reading(
        val hz: Float,
        val confidence: Float,
    )

    /** What the app used before: the pitch processor, fed through its handler. */
    private fun oldWay(signal: FloatArray): List<Reading> {
        val readings = mutableListOf<Reading>()
        val processor =
            PitchProcessor(
                PitchEstimationAlgorithm.YIN,
                AudioFormat.SAMPLE_RATE.toFloat(),
                AudioFormat.FRAME_SAMPLES,
            ) { result, _ -> readings += Reading(if (result.pitch > 0f) result.pitch else 0f, result.probability) }
        var start = 0
        while (start + AudioFormat.FRAME_SAMPLES <= signal.size) {
            val event = AudioEvent(TarsosDSPAudioFormat(AudioFormat.SAMPLE_RATE.toFloat(), 16, 1, true, false))
            event.floatBuffer = signal.copyOfRange(start, start + AudioFormat.FRAME_SAMPLES)
            processor.process(event)
            start += AudioFormat.FRAME_SAMPLES
        }
        return readings
    }

    private fun newWay(signal: FloatArray): List<Reading> {
        val detector = TarsosPitchDetector()
        val readings = mutableListOf<Reading>()
        var start = 0
        var index = 0L
        while (start + AudioFormat.FRAME_SAMPLES <= signal.size) {
            val frame = AudioFrame(signal.copyOfRange(start, start + AudioFormat.FRAME_SAMPLES), AudioFormat.SAMPLE_RATE, index++)
            val pitch = detector.detect(frame)
            readings += Reading(pitch.hz, pitch.confidence)
            start += AudioFormat.FRAME_SAMPLES
        }
        return readings
    }

    private val n = frames(12)

    private val signals: Map<String, FloatArray> =
        mapOf(
            "tone 110 Hz" to SyntheticSignals.tone(110.0, n),
            "tone 440 Hz" to SyntheticSignals.tone(440.0, n),
            "quiet tone" to SyntheticSignals.tone(220.0, n, amplitude = 0.01),
            "silence" to SyntheticSignals.silence(n),
            "white noise" to SyntheticSignals.noise(n),
            "glide" to SyntheticSignals.glide(200.0, 300.0, n),
            "vibrato" to SyntheticSignals.vibrato(220.0, 6.0, 5.0, n),
            "voice-like" to SyntheticSignals.voiceLike(150.0, n),
            "tone in noise" to SyntheticSignals.mix(SyntheticSignals.tone(220.0, n), SyntheticSignals.noise(n), 0.3),
        )

    @TestFactory
    fun everySignal_givesTheSameReadingsFrameForFrame() =
        signals.map { (name, signal) ->
            DynamicTest.dynamicTest(name) {
                assertEquals(oldWay(signal), newWay(signal))
            }
        }
}

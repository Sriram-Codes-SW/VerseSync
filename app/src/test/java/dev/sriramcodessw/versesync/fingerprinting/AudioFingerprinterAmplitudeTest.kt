package dev.sriramcodessw.versesync.fingerprinting

import dev.sriramcodessw.versesync.audio.AudioFrame
import dev.sriramcodessw.versesync.audio.tarsos.TarsosPitchDetector
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TC-U-30: the level meter follows loudness, through the real pitch detector. */
class AudioFingerprinterAmplitudeTest {
    private val sampleRate = 16000.0

    private fun tone(
        hz: Double,
        amplitude: Double,
    ) = FloatArray(1024) { (amplitude * sin(2 * PI * hz * it / sampleRate)).toFloat() }

    private fun frame(samples: FloatArray) = AudioFrame(samples, sampleRate.toInt(), 0)

    @Test
    fun amplitude_isLouderForALouderTone() {
        val fingerprinter = AudioFingerprinter(TarsosPitchDetector())

        fingerprinter.onFrame(frame(tone(220.0, 0.02)))
        val quiet = fingerprinter.amplitude.value
        fingerprinter.onFrame(frame(tone(220.0, 0.5)))
        val loud = fingerprinter.amplitude.value

        assertTrue(quiet < loud, "quiet=$quiet loud=$loud")
        assertEquals(AudioLevel.fromSamples(tone(220.0, 0.5)), loud, 1e-6f)
    }

    @Test
    fun amplitude_isNotTheDetectorsConfidence() {
        val fingerprinter = AudioFingerprinter(TarsosPitchDetector())

        // A clean tone is detected with high confidence however quiet it is. The meter must
        // still show it as quiet.
        fingerprinter.onFrame(frame(tone(220.0, 0.005)))

        assertTrue(fingerprinter.amplitude.value < 0.3f, "meter was ${fingerprinter.amplitude.value}")
    }

    @Test
    fun amplitude_isZeroForSilence() {
        val fingerprinter = AudioFingerprinter(TarsosPitchDetector())

        fingerprinter.onFrame(frame(FloatArray(1024)))

        assertEquals(0f, fingerprinter.amplitude.value)
    }
}

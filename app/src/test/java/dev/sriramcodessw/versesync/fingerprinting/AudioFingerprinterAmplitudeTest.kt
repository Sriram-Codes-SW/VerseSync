package dev.sriramcodessw.versesync.fingerprinting

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TC-U-29: the level meter follows loudness, through the real pitch processor. */
class AudioFingerprinterAmplitudeTest {
    private val sampleRate = 16000f

    private fun tone(
        hz: Double,
        amplitude: Double,
    ) = FloatArray(1024) { (amplitude * sin(2 * PI * hz * it / sampleRate)).toFloat() }

    private fun fingerprinterAndProcessor(): Pair<AudioFingerprinter, AudioProcessor> {
        val dispatcher: AudioDispatcher = mock()
        val fingerprinter = AudioFingerprinter(dispatcher)
        fingerprinter.attachPitchProcessor(sampleRate, 1024)
        val captor = argumentCaptor<AudioProcessor>()
        verify(dispatcher).addAudioProcessor(captor.capture())
        return fingerprinter to captor.firstValue
    }

    private fun AudioProcessor.feed(samples: FloatArray) {
        val event = AudioEvent(TarsosDSPAudioFormat(sampleRate, 16, 1, true, false))
        event.floatBuffer = samples
        process(event)
    }

    @Test
    fun amplitude_isLouderForALouderTone() {
        val (fingerprinter, processor) = fingerprinterAndProcessor()

        processor.feed(tone(220.0, 0.02))
        val quiet = fingerprinter.amplitude.value
        processor.feed(tone(220.0, 0.5))
        val loud = fingerprinter.amplitude.value

        assertTrue(quiet < loud, "quiet=$quiet loud=$loud")
        assertEquals(AudioLevel.fromSamples(tone(220.0, 0.5)), loud, 1e-6f)
    }

    @Test
    fun amplitude_isNotTheDetectorsConfidence() {
        val (fingerprinter, processor) = fingerprinterAndProcessor()

        // A clean tone is detected with high confidence however quiet it is. The meter must
        // still show it as quiet.
        processor.feed(tone(220.0, 0.005))

        assertTrue(fingerprinter.amplitude.value < 0.3f, "meter was ${fingerprinter.amplitude.value}")
    }

    @Test
    fun amplitude_isZeroForSilence() {
        val (fingerprinter, processor) = fingerprinterAndProcessor()

        processor.feed(FloatArray(1024))

        assertEquals(0f, fingerprinter.amplitude.value)
    }
}

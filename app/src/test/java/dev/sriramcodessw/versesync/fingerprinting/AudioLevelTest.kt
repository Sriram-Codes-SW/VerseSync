package dev.sriramcodessw.versesync.fingerprinting

import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioLevelTest {
    private fun sine(
        amplitude: Float,
        n: Int = 1024,
    ) = FloatArray(n) { (amplitude * sin(2 * PI * 440.0 * it / 16000.0)).toFloat() }

    @Test
    fun silenceAndEmptyInput_areZero() {
        assertEquals(0f, AudioLevel.fromSamples(FloatArray(1024)))
        assertEquals(0f, AudioLevel.fromSamples(FloatArray(0)))
    }

    @Test
    fun fullScaleSquareWave_isOne() {
        assertEquals(1f, AudioLevel.fromSamples(FloatArray(1024) { if (it % 2 == 0) 1f else -1f }), 1e-4f)
    }

    @Test
    fun rms_ofASineIsAmplitudeOverRootTwo() {
        assertEquals(0.5f / 1.4142135f, AudioLevel.rms(sine(0.5f)), 1e-3f)
    }

    @Test
    fun louderInput_movesTheMeterUp() {
        val quiet = AudioLevel.fromSamples(sine(0.01f))
        val normal = AudioLevel.fromSamples(sine(0.1f))
        val loud = AudioLevel.fromSamples(sine(0.8f))
        assertTrue(quiet < normal && normal < loud, "quiet=$quiet normal=$normal loud=$loud")
    }

    @Test
    fun ordinarySpeechLevel_isClearlyVisible() {
        // Speech has an RMS of roughly 0.03 to 0.2. A linear meter would show 3 to 20 percent.
        val speech = AudioLevel.fromSamples(sine(0.1f))
        assertTrue(speech in 0.4f..0.9f, "speech level was $speech")
    }

    @Test
    fun veryQuietNoise_staysAtTheFloor() {
        assertEquals(0f, AudioLevel.fromSamples(FloatArray(1024) { 0.0005f }))
    }

    @Test
    fun levelDoesNotDependOnPitchConfidence() {
        // The same loudness at two different pitches reads the same.
        val low = AudioLevel.fromSamples(FloatArray(1024) { (0.2 * sin(2 * PI * 110.0 * it / 16000.0)).toFloat() })
        val high = AudioLevel.fromSamples(FloatArray(1024) { (0.2 * sin(2 * PI * 880.0 * it / 16000.0)).toFloat() })
        assertEquals(low, high, 0.02f)
    }

    @Test
    fun nanInput_doesNotBreakTheMeter() {
        assertEquals(0f, AudioLevel.fromSamples(floatArrayOf(Float.NaN, 0.5f)))
    }
}

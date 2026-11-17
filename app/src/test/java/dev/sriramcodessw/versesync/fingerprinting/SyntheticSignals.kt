package dev.sriramcodessw.versesync.fingerprinting

import dev.sriramcodessw.versesync.audio.AudioFrame
import dev.sriramcodessw.versesync.audio.PitchDetector
import dev.sriramcodessw.versesync.audio.tarsos.TarsosPitchDetector
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Audio with a known pitch, for testing pitch detection without a microphone.
 * Sample rate and frame size match the app: 16 kHz and 1,024 samples per frame.
 */
object SyntheticSignals {
    const val SAMPLE_RATE = 16000
    const val FRAME = 1024

    fun frames(count: Int) = count * FRAME

    fun silence(samples: Int) = FloatArray(samples)

    fun tone(
        hz: Double,
        samples: Int,
        amplitude: Double = 0.5,
    ) = FloatArray(samples) { (amplitude * sin(2 * PI * hz * it / SAMPLE_RATE)).toFloat() }

    /** A pitch that moves linearly from [fromHz] to [toHz]. */
    fun glide(
        fromHz: Double,
        toHz: Double,
        samples: Int,
        amplitude: Double = 0.5,
    ): FloatArray {
        var phase = 0.0
        return FloatArray(samples) {
            val hz = fromHz + (toHz - fromHz) * it / samples
            phase += 2 * PI * hz / SAMPLE_RATE
            (amplitude * sin(phase)).toFloat()
        }
    }

    /** A pitch that wavers by +/- [depthHz] at [rateHz] times a second. */
    fun vibrato(
        centerHz: Double,
        depthHz: Double,
        rateHz: Double,
        samples: Int,
        amplitude: Double = 0.5,
    ): FloatArray {
        var phase = 0.0
        return FloatArray(samples) {
            val hz = centerHz + depthHz * sin(2 * PI * rateHz * it / SAMPLE_RATE)
            phase += 2 * PI * hz / SAMPLE_RATE
            (amplitude * sin(phase)).toFloat()
        }
    }

    /** A fundamental with [harmonics] overtones that fall off like 1/n, a little like a voice. */
    fun voiceLike(
        fundamentalHz: Double,
        samples: Int,
        harmonics: Int = 6,
    ) = FloatArray(samples) { k ->
        (1..harmonics).sumOf { h -> (0.3 / h) * sin(2 * PI * fundamentalHz * h * k / SAMPLE_RATE) }.toFloat()
    }

    fun noise(
        samples: Int,
        amplitude: Double = 0.5,
        seed: Int = 1,
    ): FloatArray {
        val random = Random(seed)
        return FloatArray(samples) { ((random.nextDouble() - 0.5) * 2 * amplitude).toFloat() }
    }

    fun mix(
        signal: FloatArray,
        noise: FloatArray,
        noiseGain: Double,
    ) = FloatArray(signal.size) { signal[it] + (noiseGain * noise[it]).toFloat() }

    fun join(vararg parts: FloatArray): FloatArray = parts.fold(FloatArray(0)) { acc, part -> acc + part }
}

/**
 * The pitch reported for every 1,024-sample frame of a signal, in Hz, with 0 for unvoiced.
 * This is what the app stores as a song's fingerprint. By default it runs the current detector,
 * TarsosDSP YIN, through [AudioFingerprinter], and any other [PitchDetector] can be passed in to
 * compare it against that.
 */
object PitchTrack {
    fun of(
        signal: FloatArray,
        detector: PitchDetector = TarsosPitchDetector(),
    ): List<Float> {
        val fingerprinter = AudioFingerprinter(detector)
        val track = mutableListOf<Float>()
        var start = 0
        var index = 0L
        while (start + SyntheticSignals.FRAME <= signal.size) {
            val samples = signal.copyOfRange(start, start + SyntheticSignals.FRAME)
            fingerprinter.onFrame(AudioFrame(samples, SyntheticSignals.SAMPLE_RATE, index++))
            track += fingerprinter.instantPitch.value
            start += SyntheticSignals.FRAME
        }
        return track
    }
}

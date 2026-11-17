package dev.sriramcodessw.versesync.fingerprinting

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * How loud an audio frame is, for the level meter.
 *
 * This is loudness, not pitch confidence. It only reads the samples, so it works with any audio
 * source or pitch detector.
 */
object AudioLevel {
    /** Anything at or below this level (in dB relative to full scale) shows as an empty meter. */
    const val FLOOR_DB = -60f

    /**
     * A frame quieter than this (RMS, in dB relative to full scale) has no pitch worth recording.
     * A faint steady hum, such as the emulator's microphone at about -73 dB, would otherwise be
     * reported as a voiced tone and recorded into fingerprints (issue #33). Ordinary quiet speech
     * is around -40 dB or louder, so there is a wide margin on both sides.
     */
    const val SILENCE_DB = -55f

    /** Root mean square of [samples], which are expected to lie in -1.0 to 1.0. */
    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size).toFloat()
    }

    /** RMS level of [samples] in dB relative to full scale, or negative infinity for no signal. */
    fun db(samples: FloatArray): Float {
        val rms = rms(samples)
        return if (rms.isFinite() && rms > 0f) 20f * log10(rms) else Float.NEGATIVE_INFINITY
    }

    /** True when [samples] are quieter than [SILENCE_DB], or are not a valid signal. */
    fun isSilent(samples: FloatArray): Boolean = !(db(samples) >= SILENCE_DB)

    /**
     * Meter position from 0.0 (silence, or [FLOOR_DB] and below) to 1.0 (full scale).
     * Decibels are used because loudness is heard on a logarithmic scale: a plain RMS value
     * of ordinary speech would barely move the meter.
     */
    fun fromSamples(samples: FloatArray): Float {
        val rms = rms(samples)
        if (!rms.isFinite() || rms <= 0f) return 0f
        val db = 20f * log10(rms)
        return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
    }
}

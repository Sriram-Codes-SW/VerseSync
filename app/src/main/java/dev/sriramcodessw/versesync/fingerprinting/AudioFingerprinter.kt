package dev.sriramcodessw.versesync.fingerprinting

import dev.sriramcodessw.versesync.audio.AudioFrame
import dev.sriramcodessw.versesync.audio.PitchDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log2

/**
 * Turns audio frames into a pitch contour: one value per frame in Hz, 0 for a frame with no pitch.
 * The pitch comes from [detector], so any detector can be used.
 */
class AudioFingerprinter(
    private val detector: PitchDetector,
) {
    private val rawFingerprintList = mutableListOf<Float>()

    private val _fingerprint = MutableStateFlow<List<Float>>(emptyList())
    val fingerprint = _fingerprint.asStateFlow()

    private val _instantPitch = MutableStateFlow(0f)
    val instantPitch = _instantPitch.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    /** Processes one frame. Called on the audio source's thread. */
    fun onFrame(frame: AudioFrame) {
        val processedPitch = detector.detect(frame).hz

        _instantPitch.value = processedPitch

        synchronized(rawFingerprintList) {
            rawFingerprintList.add(processedPitch)
            // Periodically update the state flow snapshot without hammering garbage collection
            if (rawFingerprintList.size % 4 == 0) {
                _fingerprint.value = rawFingerprintList.toList()
            }
        }

        // Loudness of this frame. This used to be the detector's confidence, which is not loudness.
        _amplitude.value = AudioLevel.fromSamples(frame.samples)
    }

    companion object {
        /**
         * Converts frequency in Hz to musical semitones (MIDI pitch scale).
         * Standard concert pitch A4 (440 Hz) maps to 69.0.
         * Returns 0f for unvoiced audio or silence.
         */
        fun hzToSemitone(hz: Float): Float {
            if (hz <= 20f || hz > 5000f) return 0f
            return (69.0 + 12.0 * log2(hz.toDouble() / 440.0)).toFloat()
        }

        /**
         * Converts a sequence of raw Hz pitch values to normalized semitones,
         * centering voiced frames around their mean to achieve key-independent and
         * transposition-invariant contour matching.
         *
         * Unvoiced frames (silence, 0 Hz) become [Float.NaN] so they cannot be confused with
         * a voiced frame that happens to sit exactly on the mean. Callers must skip NaN.
         */
        fun toRelativeSemitoneContour(pitches: List<Float>): List<Float> {
            val semitones = pitches.map { hzToSemitone(it) }
            val voiced = semitones.filter { it > 0f }
            if (voiced.isEmpty()) return emptyList()
            val mean = voiced.average().toFloat()
            return semitones.map { if (it > 0f) it - mean else Float.NaN }
        }
    }
}

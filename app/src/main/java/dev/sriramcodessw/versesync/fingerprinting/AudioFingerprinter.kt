package dev.sriramcodessw.versesync.fingerprinting

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log2

class AudioFingerprinter(
    private val audioDispatcher: AudioDispatcher,
) {
    private val rawFingerprintList = mutableListOf<Float>()

    private val _fingerprint = MutableStateFlow<List<Float>>(emptyList())
    val fingerprint = _fingerprint.asStateFlow()

    private val _instantPitch = MutableStateFlow(0f)
    val instantPitch = _instantPitch.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    private var isProcessorAttached = false

    fun attachPitchProcessor(
        sampleRate: Float = 16000f,
        bufferSize: Int = 1024,
    ) {
        if (isProcessorAttached) return
        isProcessorAttached = true

        val pitchDetectionHandler =
            PitchDetectionHandler { res, _ ->
                val pitch = res.pitch
                val processedPitch = if (pitch > 0f) pitch else 0f

                _instantPitch.value = processedPitch

                synchronized(rawFingerprintList) {
                    rawFingerprintList.add(processedPitch)
                    // Periodically update the state flow snapshot without hammering garbage collection
                    if (rawFingerprintList.size % 4 == 0) {
                        _fingerprint.value = rawFingerprintList.toList()
                    }
                }

                _amplitude.value = res.probability
            }

        val pitchProcessor =
            PitchProcessor(
                PitchEstimationAlgorithm.YIN,
                sampleRate,
                bufferSize,
                pitchDetectionHandler,
            )

        audioDispatcher.addAudioProcessor(pitchProcessor)
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

package dev.sriramcodessw.versesync.fingerprinting

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioFingerprinter(private val audioDispatcher: AudioDispatcher) {

    private val _fingerprint = MutableStateFlow<List<Float>>(emptyList())
    val fingerprint = _fingerprint.asStateFlow()

    private val _instantPitch = MutableStateFlow(0f)
    val instantPitch = _instantPitch.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    fun attachPitchProcessor() {
        val pitchDetectionHandler = PitchDetectionHandler { res, _ ->
            val pitch = res.pitch
            val processedPitch = if (pitch != -1f) pitch else 0f
            
            _instantPitch.value = processedPitch
            // CRITICAL FIX: Always add a value to the fingerprint list to maintain the timeline.
            // Even if no pitch is detected, we record 0f so the duration remains accurate.
            _fingerprint.value = _fingerprint.value + processedPitch
            
            _amplitude.value = res.probability
        }

        val pitchProcessor = PitchProcessor(
            PitchEstimationAlgorithm.YIN,
            22050f,
            1024,
            pitchDetectionHandler
        )

        audioDispatcher.addAudioProcessor(pitchProcessor)
    }
}

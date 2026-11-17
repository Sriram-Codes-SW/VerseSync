package dev.sriramcodessw.versesync.audio.tarsos

import be.tarsos.dsp.pitch.Yin
import dev.sriramcodessw.versesync.audio.AudioFormat
import dev.sriramcodessw.versesync.audio.AudioFrame
import dev.sriramcodessw.versesync.audio.PitchDetector
import dev.sriramcodessw.versesync.audio.PitchFrame

/*
 * The only code that uses TarsosDSP (GPL-3.0). Everything else works with the types in AudioPorts.kt.
 * Replacing the library means replacing this file (docs/07-pitch-detection-design.md).
 */

/**
 * The YIN pitch detector from TarsosDSP, called on one frame at a time. It is the same algorithm
 * and the same calls that `PitchProcessor` made for the app before, so the results are identical.
 */
class TarsosPitchDetector(
    sampleRate: Float = AudioFormat.SAMPLE_RATE.toFloat(),
    frameSamples: Int = AudioFormat.FRAME_SAMPLES,
) : PitchDetector {
    private val yin = Yin(sampleRate, frameSamples)

    override fun detect(frame: AudioFrame): PitchFrame {
        val result = yin.getPitch(frame.samples)
        return PitchFrame(hz = if (result.pitch > 0f) result.pitch else 0f, confidence = result.probability)
    }
}

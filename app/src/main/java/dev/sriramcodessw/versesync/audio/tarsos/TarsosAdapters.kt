package dev.sriramcodessw.versesync.audio.tarsos

import android.annotation.SuppressLint
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.Yin
import dev.sriramcodessw.versesync.audio.AudioFormat
import dev.sriramcodessw.versesync.audio.AudioFrame
import dev.sriramcodessw.versesync.audio.AudioFrameListener
import dev.sriramcodessw.versesync.audio.AudioFrameSource
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

/**
 * The default microphone, read by a TarsosDSP audio dispatcher on a thread of its own. Creating it
 * opens the microphone, so a failure to do so surfaces here, as it did before.
 */
@SuppressLint("MissingPermission")
class TarsosMicrophoneSource(
    private val sampleRate: Int = AudioFormat.SAMPLE_RATE,
    private val frameSamples: Int = AudioFormat.FRAME_SAMPLES,
) : AudioFrameSource {
    // ADR-01: 16,000 Hz to align with Vosk speech recognition and the hardware HAL.
    private val dispatcher: AudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, frameSamples, 0)
    private var thread: Thread? = null

    override fun start(listener: AudioFrameListener) {
        var index = 0L
        dispatcher.addAudioProcessor(
            object : AudioProcessor {
                override fun process(audioEvent: AudioEvent): Boolean {
                    listener.onFrame(AudioFrame(audioEvent.floatBuffer, sampleRate, index++))
                    return true
                }

                override fun processingFinished() = Unit
            },
        )
        thread = Thread(dispatcher, "Audio Dispatcher").also { it.start() }
    }

    override fun stop() {
        dispatcher.stop()
        thread?.interrupt()
        thread = null
    }
}

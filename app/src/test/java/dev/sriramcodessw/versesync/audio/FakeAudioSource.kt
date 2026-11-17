package dev.sriramcodessw.versesync.audio

/**
 * An [AudioFrameSource] for tests. Nothing is delivered until the test calls [emit], so a test
 * decides exactly when frames arrive, with no microphone, no thread and no waiting.
 */
class FakeAudioSource : AudioFrameSource {
    var startCount = 0
        private set
    var stopCount = 0
        private set
    private var listener: AudioFrameListener? = null
    private var nextIndex = 0L

    val isRunning get() = listener != null

    override fun start(listener: AudioFrameListener) {
        startCount++
        this.listener = listener
    }

    override fun stop() {
        stopCount++
        listener = null
    }

    /** Delivers one frame to whoever started the source. Does nothing if it is not running. */
    fun emit(samples: FloatArray) {
        listener?.onFrame(AudioFrame(samples, AudioFormat.SAMPLE_RATE, nextIndex++))
    }
}

/** A [PitchDetector] for tests that reports the same pitch for every frame. */
class FixedPitchDetector(
    private val hz: Float,
) : PitchDetector {
    var frames = 0
        private set

    override fun detect(frame: AudioFrame): PitchFrame {
        frames++
        return PitchFrame(hz, 1f)
    }
}

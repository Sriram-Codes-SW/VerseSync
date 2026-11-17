package dev.sriramcodessw.versesync.audio

// The audio the app works with, and the two things it needs from the outside: a source of frames
// and a pitch detector. Nothing here refers to Android or to a signal processing library, so the
// matching and fingerprinting logic does not depend on which one is used underneath
// (docs/07-pitch-detection-design.md).

/** One block of mono audio, with samples from -1.0 to 1.0. [index] counts blocks from 0. */
class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
    val index: Long,
)

/** The pitch of one [AudioFrame]. [hz] is 0 for a frame with no pitch. */
data class PitchFrame(
    val hz: Float,
    val confidence: Float,
)

/** Finds the pitch of one frame. */
interface PitchDetector {
    fun detect(frame: AudioFrame): PitchFrame
}

fun interface AudioFrameListener {
    /**
     * Called for each frame, on the source's own thread. The frame and its samples are only valid
     * during the call, because a source may reuse the buffer for the next frame.
     */
    fun onFrame(frame: AudioFrame)
}

/** A live stream of audio frames, such as the microphone. */
interface AudioFrameSource {
    /** Starts delivering frames to [listener] on a thread of the source's own. */
    fun start(listener: AudioFrameListener)

    /** Stops delivering frames. Safe to call more than once, and before [start]. */
    fun stop()
}

/** The frame size and sample rate the app's fingerprints are built on: 64 ms at 16 kHz. */
object AudioFormat {
    const val SAMPLE_RATE = 16000
    const val FRAME_SAMPLES = 1024
}

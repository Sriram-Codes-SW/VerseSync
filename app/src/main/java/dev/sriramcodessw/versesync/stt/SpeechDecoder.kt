package dev.sriramcodessw.versesync.stt

import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

private const val TAG = "SpeechDecoder"

/** The part of a Vosk recognizer that [SpeechDecoder] needs, so it can be tested without native code. */
interface WaveformRecognizer {
    /** Feeds [length] samples. Returns true when the recognizer has finished an utterance. */
    fun acceptWaveForm(
        samples: ShortArray,
        length: Int,
    ): Boolean

    /** The finished utterance as JSON, with its text under "text". */
    fun result(): String

    /** The utterance so far as JSON, with its text under "partial". */
    fun partialResult(): String
}

/**
 * Runs speech recognition on a thread of its own, on audio that another thread delivers.
 *
 * The audio thread must never wait for the recognizer, or the microphone would overrun and lose
 * audio. So [offer] only copies a frame into a small queue and returns. When the recognizer falls
 * behind by more than [capacity] frames, the oldest frames are dropped.
 *
 * This replaces the speech library's own recorder, which opened a second microphone capture.
 */
class SpeechDecoder(
    private val recognizer: WaveformRecognizer,
    private val onResult: (json: String) -> Unit,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val queue = ArrayBlockingQueue<ShortArray>(capacity)

    @Volatile private var running = true

    @Volatile var droppedFrames = 0L
        private set

    private val thread = Thread(::decodeLoop, "Speech Decoder").also { it.start() }

    /** Queues a copy of [samples], from -1.0 to 1.0. Never blocks. */
    fun offer(samples: FloatArray) {
        if (!running) return
        val pcm = ShortArray(samples.size) { (samples[it] * 32768f).toInt().coerceIn(-32768, 32767).toShort() }
        while (!queue.offer(pcm)) {
            // Full: make room by dropping the oldest frame.
            if (queue.poll() != null) droppedFrames++
        }
    }

    private fun decodeLoop() {
        while (running) {
            val pcm =
                try {
                    queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    null
                } ?: continue
            try {
                val json = if (recognizer.acceptWaveForm(pcm, pcm.size)) recognizer.result() else recognizer.partialResult()
                onResult(json)
            } catch (e: Exception) {
                Log.e(TAG, "Speech recognition failed on a frame", e)
            }
        }
    }

    /**
     * Stops the decoder and waits until it no longer uses the recognizer, so that the caller can
     * close the recognizer without the decoder touching freed memory.
     *
     * @return false if the thread did not finish in time. The recognizer must then not be closed.
     */
    fun stop(): Boolean {
        running = false
        queue.clear()
        thread.interrupt()
        if (thread !== Thread.currentThread()) thread.join(JOIN_MILLIS)
        Log.i(TAG, "Stopped. Frames dropped because the recognizer fell behind: $droppedFrames")
        return !thread.isAlive
    }

    companion object {
        /** About four seconds of audio at 1,024 samples per frame and 16,000 Hz. */
        const val DEFAULT_CAPACITY = 64
        private const val POLL_MILLIS = 100L
        private const val JOIN_MILLIS = 5000L
    }
}

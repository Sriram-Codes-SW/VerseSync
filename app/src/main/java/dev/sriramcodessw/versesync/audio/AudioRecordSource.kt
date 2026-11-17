package dev.sriramcodessw.versesync.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.media.AudioFormat as AndroidAudioFormat

private const val TAG = "AudioRecordSource"

/**
 * The microphone, read with a single [AudioRecord] at 16,000 Hz, mono, 16-bit, in blocks of
 * [frameSamples] samples. This is the only microphone capture while recognizing: the pitch
 * detector, the level meter and the speech recognizer all receive these same frames, where before
 * two recorders ran at once (ADR-01).
 *
 * Creating it opens the microphone, so a failure to do so surfaces here, and the caller can show
 * its "microphone could not be opened" message.
 */
@SuppressLint("MissingPermission")
class AudioRecordSource(
    private val sampleRate: Int = AudioFormat.SAMPLE_RATE,
    private val frameSamples: Int = AudioFormat.FRAME_SAMPLES,
) : AudioFrameSource {
    private val record: AudioRecord

    init {
        val minBytes =
            AudioRecord.getMinBufferSize(sampleRate, AndroidAudioFormat.CHANNEL_IN_MONO, AndroidAudioFormat.ENCODING_PCM_16BIT)
        check(minBytes > 0) { "Audio capture is not supported at $sampleRate Hz" }
        // Room for several frames, so a slow listener does not make the recorder overrun and lose audio.
        val bufferBytes = maxOf(minBytes, frameSamples * BYTES_PER_SAMPLE * BUFFER_FRAMES)
        record =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AndroidAudioFormat.CHANNEL_IN_MONO,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("The microphone could not be opened")
        }
    }

    @Volatile private var running = false
    private var released = false
    private var thread: Thread? = null

    @Synchronized
    override fun start(listener: AudioFrameListener) {
        check(!released) { "This source has been stopped" }
        check(thread == null) { "This source is already running" }
        record.startRecording()
        running = true
        thread = Thread({ readLoop(listener) }, "Audio Capture").also { it.start() }
    }

    private fun readLoop(listener: AudioFrameListener) {
        val pcm = ShortArray(frameSamples)
        val samples = FloatArray(frameSamples)
        var index = 0L
        while (running) {
            var read = 0
            while (read < frameSamples && running) {
                val n = record.read(pcm, read, frameSamples - read)
                if (n < 0) {
                    Log.e(TAG, "Reading the microphone failed with code $n")
                    running = false
                    return
                }
                read += n
            }
            if (read < frameSamples) return
            for (i in 0 until frameSamples) samples[i] = pcm[i] / 32768f
            try {
                listener.onFrame(AudioFrame(samples, sampleRate, index++))
            } catch (e: Exception) {
                // One bad frame must not end the recording.
                Log.e(TAG, "A frame listener failed", e)
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (released) return
        released = true
        running = false
        try {
            record.stop() // also unblocks a read in progress
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The recorder was not recording", e)
        }
        val reader = thread
        thread = null
        // Release the recorder only once the reading thread is done with it.
        if (reader != null && reader !== Thread.currentThread()) reader.join(JOIN_MILLIS)
        record.release()
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_FRAMES = 8
        const val JOIN_MILLIS = 2000L
    }
}

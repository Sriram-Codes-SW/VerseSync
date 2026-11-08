package dev.sriramcodessw.versesync.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

class PitchPlayer {
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 22050

    // Buffer size from engine: 1024. Sample rate: 22050.
    // Duration per block = (1024 / 22050) * 1000 ~= 46.4ms
    private val durationPerPitch = 46L

    private val bufferSize =
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

    suspend fun playFingerprint(
        pitches: List<Float>,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (pitches.isEmpty()) return@withContext

        audioTrack =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        audioTrack?.play()

        for (i in pitches.indices) {
            val freq = pitches[i]
            if (freq > 0) {
                val samples = generateSineWave(freq, durationPerPitch)
                audioTrack?.write(samples, 0, samples.size)
            } else {
                // Silence - maintain the time gap
                val silence = ShortArray((sampleRate * durationPerPitch / 1000).toInt())
                audioTrack?.write(silence, 0, silence.size)
            }

            if (i % 5 == 0) {
                onProgress(i)
            }
        }

        stop()
    }

    private fun generateSineWave(
        frequency: Float,
        durationMs: Long,
    ): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            samples[i] = (sin(2.0 * PI * i * frequency / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Log if needed
        }
        audioTrack = null
    }
}

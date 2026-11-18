package dev.sriramcodessw.versesync.eval

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "EVALDEC"
private const val OUT_RATE = 16_000

/**
 * Decodes recordings (Ogg Vorbis, Opus, WAV and the like) with the device's own decoders into 16 kHz mono
 * 16-bit WAV files, for an evaluation corpus (docs/09, section 15). It exists because the development PC has no
 * audio converter and Android already has the decoders. It reads and writes files only: no microphone, no network.
 *
 * It is skipped unless started with `-e corpus <name>`. Recordings are read from
 * `<app external files>/songs-in/` and written to `<app files>/eval/<name>/recordings/<file name>.wav`. For each,
 * the length, the original sample rate and channels, and the level are logged under the tag EVALDEC.
 */
@RunWith(AndroidJUnit4::class)
class DecodeRecordingsInstrumentedTest {
    @Test
    fun decodeRecordings() {
        val corpus = InstrumentationRegistry.getArguments().getString("corpus")
        assumeTrue("Pass -e corpus <name> to run this test", corpus != null)
        corpus!!

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.getExternalFilesDir(null), "songs-in")
        val output = File(context.filesDir, "eval/$corpus/recordings").also { it.mkdirs() }
        val files = input.listFiles { f -> f.isFile && !f.name.startsWith(".") }?.sortedBy { it.name }.orEmpty()
        assumeTrue("No recordings in $input", files.isNotEmpty())

        for (file in files) {
            try {
                val decoded = decode(file)
                val mono = toMono16k(decoded)
                File(output, file.nameWithoutExtension + ".wav").writeBytes(wav(mono))
                var sum = 0.0
                var peak = 0.0
                for (s in mono) {
                    val v = s / 32768.0
                    sum += v * v
                    peak = maxOf(peak, abs(v))
                }
                Log.i(
                    TAG,
                    "ok ${file.name} seconds=${"%.1f".format(mono.size / OUT_RATE.toDouble())} from=${decoded.rate}Hz/${decoded.channels}ch " +
                        "rms=${"%.4f".format(sqrt(sum / mono.size.coerceAtLeast(1)))} peak=${"%.3f".format(peak)}",
                )
            } catch (e: Exception) {
                Log.e(TAG, "FAILED ${file.name}: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    private class Decoded(
        val samples: ShortArray,
        val rate: Int,
        val channels: Int,
    )

    private fun decode(file: File): Decoded {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i
                format = f
                break
            }
        }
        require(trackIndex >= 0 && format != null) { "no audio track" }
        extractor.selectTrack(trackIndex)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = ByteArrayOutputStream()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, 10_000)
            when {
                out >= 0 -> {
                    val buffer = codec.getOutputBuffer(out)!!
                    val chunk = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(chunk)
                    pcm.write(chunk)
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }

                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val bytes = pcm.toByteArray()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        return Decoded(shorts, rate, channels)
    }

    /** Averages the channels, then averages each group of input samples that falls into one output sample (area resampling). */
    private fun toMono16k(d: Decoded): ShortArray {
        val frames = d.samples.size / d.channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0f
            for (c in 0 until d.channels) s += d.samples[i * d.channels + c]
            mono[i] = s / d.channels
        }
        val outLen = (frames.toLong() * OUT_RATE / d.rate).toInt()
        val out = ShortArray(outLen)
        val ratio = d.rate.toDouble() / OUT_RATE
        for (i in 0 until outLen) {
            val start = (i * ratio).toInt()
            val end = minOf(frames, maxOf(start + 1, ((i + 1) * ratio).toInt()))
            var s = 0f
            for (j in start until end) s += mono[j]
            out[i] = (s / (end - start)).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun wav(samples: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        header
            .put("fmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(OUT_RATE)
            .putInt(OUT_RATE * 2)
            .putShort(2)
            .putShort(16)
        header.put("data".toByteArray()).putInt(samples.size * 2)
        return header.array() + data.array()
    }
}

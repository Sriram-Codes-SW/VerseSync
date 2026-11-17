package dev.sriramcodessw.versesync.eval

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads WAV files for the evaluation harness and turns them into 16 kHz mono samples, as the app records. */
object Wav {
    const val TARGET_RATE = 16000

    class Audio(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    /** Reads 16-bit PCM or 32-bit float WAV, averaging channels to mono. Samples are -1.0 to 1.0. */
    fun read(file: File): Audio {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.limit() >= 12 && String(bytes.array(), 0, 4) == "RIFF") { "${file.name} is not a WAV file" }
        var pos = 12
        var format = 1
        var channels = 1
        var rate = TARGET_RATE
        var bits = 16
        var dataStart = -1
        var dataLength = 0
        while (pos + 8 <= bytes.limit()) {
            val id = String(bytes.array(), pos, 4)
            val length = bytes.getInt(pos + 4)
            if (id == "fmt ") {
                format = bytes.getShort(pos + 8).toInt() and 0xFFFF
                channels = bytes.getShort(pos + 10).toInt()
                rate = bytes.getInt(pos + 12)
                bits = bytes.getShort(pos + 22).toInt()
            } else if (id == "data") {
                dataStart = pos + 8
                dataLength = minOf(length.toLong() and 0xFFFFFFFFL, (bytes.limit() - dataStart).toLong()).toInt()
                break
            }
            pos += 8 + length + (length and 1)
        }
        require(dataStart >= 0) { "${file.name} has no audio data" }
        require((format == 1 && bits == 16) || (format == 3 && bits == 32)) {
            "${file.name}: only 16-bit PCM and 32-bit float WAV are supported (format $format, $bits bits)"
        }
        val bytesPerSample = bits / 8
        val frames = dataLength / (bytesPerSample * channels)
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val at = dataStart + (i * channels + c) * bytesPerSample
                sum += if (format == 3) bytes.getFloat(at) else bytes.getShort(at) / 32768f
            }
            mono[i] = sum / channels
        }
        return Audio(mono, rate)
    }

    /** Linear resampling to [TARGET_RATE]. Good enough for pitch and speech at these rates. */
    fun to16k(audio: Audio): FloatArray {
        if (audio.sampleRate == TARGET_RATE) return audio.samples
        val ratio = audio.sampleRate.toDouble() / TARGET_RATE
        val out = FloatArray((audio.samples.size / ratio).toInt())
        for (i in out.indices) {
            val src = i * ratio
            val i0 = src.toInt()
            val i1 = minOf(i0 + 1, audio.samples.size - 1)
            out[i] = (audio.samples[i0] + (audio.samples[i1] - audio.samples[i0]) * (src - i0)).toFloat()
        }
        return out
    }

    fun read16k(file: File): FloatArray = to16k(read(file))

    /** Writes 16-bit PCM mono. Used to make synthetic corpora for tests. */
    fun write(
        file: File,
        samples: FloatArray,
        sampleRate: Int = TARGET_RATE,
    ) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort((it * 32767f).toInt().coerceIn(-32768, 32767).toShort()) }
        val out = ByteBuffer.allocate(44 + data.capacity()).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVEfmt ".toByteArray())
        out
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(sampleRate)
            .putInt(sampleRate * 2)
            .putShort(2)
            .putShort(16)
        out.put("data".toByteArray()).putInt(data.capacity()).put(data.array())
        file.parentFile?.mkdirs()
        file.writeBytes(out.array())
    }
}

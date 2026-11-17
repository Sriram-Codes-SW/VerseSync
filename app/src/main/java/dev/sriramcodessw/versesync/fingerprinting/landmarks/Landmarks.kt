package dev.sriramcodessw.versesync.fingerprinting.landmarks

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * A landmark fingerprint of a recording, written from the published method (Wang, "An Industrial-Strength Audio Search
 * Algorithm", 2003): the strongest spectral peaks are paired, each pair becomes a hash of where the two peaks are in
 * frequency and how far apart in time, and a recording is recognized when many hashes of a short snippet land at one
 * consistent offset in a stored recording. It recognizes the SAME recording playing again. It does not recognize a cover,
 * a live version, an edit, or a person singing, and it uses no speech and no pitch. See ADR-19.
 *
 * Pure Kotlin, no Android and no library. Only hashes are kept: the audio cannot be rebuilt from a print.
 */

/** The fixed parameters, from docs/09 section 13. They were set before any corpus was run and are not tuned on one. */
object LandmarkParams {
    const val SAMPLE_RATE = 16000
    const val FFT_SIZE = 1024
    const val HOP = 256

    /** Peaks are taken from 250 to 4,000 Hz, the band a phone speaker can reproduce. Bin width is 15.625 Hz. */
    const val MIN_BIN = 16
    const val MAX_BIN = 256
    const val BAND = MAX_BIN - MIN_BIN + 1

    /** A peak is the largest value within this many bins and frames. */
    const val PEAK_BIN_RADIUS = 10
    const val PEAK_FRAME_RADIUS = 8

    /** A peak must also stand this far (in natural log units) above the average of its frame's band. */
    const val PEAK_FLOOR_MARGIN = 0.5f

    /** At most this many peaks are kept per block of [FRAMES_PER_BLOCK] frames, about a second. */
    const val MAX_PEAKS_PER_BLOCK = 30
    const val FRAMES_PER_BLOCK = 62

    /** Each anchor peak pairs with its strongest peaks in a window ahead of it. */
    const val FAN_OUT = 5
    const val MIN_FRAME_GAP = 2
    const val MAX_FRAME_GAP = 63
    const val MAX_BIN_GAP = 63

    /** A hash is 9 + 7 + 6 bits, stored with its anchor frame in 3 + 3 bytes. */
    const val BYTES_PER_HASH = 6

    const val FRAMES_PER_SECOND = SAMPLE_RATE / HOP.toDouble()
}

/** The hashes of a recording and the frame each starts at, in order of frame. */
class LandmarkPrint(
    val hashes: IntArray,
    val frames: IntArray,
) {
    init {
        require(hashes.size == frames.size) { "one frame for each hash" }
    }

    val size: Int get() = hashes.size

    /** Packed as 3 bytes of hash and 3 bytes of frame each, the form a database would hold. */
    fun toBytes(): ByteArray {
        val out = ByteArray(size * LandmarkParams.BYTES_PER_HASH)
        for (i in 0 until size) {
            val at = i * LandmarkParams.BYTES_PER_HASH
            put3(out, at, hashes[i])
            put3(out, at + 3, frames[i])
        }
        return out
    }

    companion object {
        fun fromBytes(bytes: ByteArray): LandmarkPrint {
            val n = bytes.size / LandmarkParams.BYTES_PER_HASH
            val hashes = IntArray(n)
            val frames = IntArray(n)
            for (i in 0 until n) {
                val at = i * LandmarkParams.BYTES_PER_HASH
                hashes[i] = get3(bytes, at)
                frames[i] = get3(bytes, at + 3)
            }
            return LandmarkPrint(hashes, frames)
        }

        private fun put3(
            out: ByteArray,
            at: Int,
            value: Int,
        ) {
            out[at] = (value shr 16).toByte()
            out[at + 1] = (value shr 8).toByte()
            out[at + 2] = value.toByte()
        }

        private fun get3(
            bytes: ByteArray,
            at: Int,
        ) = ((bytes[at].toInt() and 0xFF) shl 16) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or (bytes[at + 2].toInt() and 0xFF)
    }
}

internal class Peak(
    val frame: Int,
    val bin: Int,
    val value: Float,
)

/** A radix-2 fast Fourier transform, in place, for one fixed size. */
internal class Fft(
    private val size: Int,
) {
    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { -sin(2.0 * PI * it / size) }
    private val reversed = IntArray(size)

    init {
        require(size > 1 && size and (size - 1) == 0) { "size must be a power of two" }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                start += length
            }
            length *= 2
        }
    }
}

object LandmarkFingerprinter {
    private val window = DoubleArray(LandmarkParams.FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (LandmarkParams.FFT_SIZE - 1)) }

    /** Natural log of one plus the magnitude, per frame, for the bins from [LandmarkParams.MIN_BIN] to [LandmarkParams.MAX_BIN]. */
    internal fun logSpectrogram(samples: FloatArray): Array<FloatArray> {
        val size = LandmarkParams.FFT_SIZE
        if (samples.size < size) return emptyArray()
        val frames = (samples.size - size) / LandmarkParams.HOP + 1
        val fft = Fft(size)
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        return Array(frames) { frame ->
            val from = frame * LandmarkParams.HOP
            for (i in 0 until size) {
                re[i] = samples[from + i] * window[i]
                im[i] = 0.0
            }
            fft.transform(re, im)
            FloatArray(LandmarkParams.BAND) { b ->
                val bin = LandmarkParams.MIN_BIN + b
                ln(1.0 + sqrt(re[bin] * re[bin] + im[bin] * im[bin])).toFloat()
            }
        }
    }

    /** The peaks of a spectrogram: local maxima that stand above their frame, the strongest few per second. */
    internal fun peaks(spectrogram: Array<FloatArray>): List<Peak> {
        val frames = spectrogram.size
        if (frames == 0) return emptyList()
        val band = LandmarkParams.BAND
        // The largest value within the bin radius, then within the frame radius: a separable maximum filter.
        val acrossBins =
            Array(frames) { t ->
                val row = spectrogram[t]
                FloatArray(band) { b ->
                    var best = row[b]
                    for (k in maxOf(0, b - LandmarkParams.PEAK_BIN_RADIUS)..minOf(band - 1, b + LandmarkParams.PEAK_BIN_RADIUS)) {
                        if (row[k] > best) best = row[k]
                    }
                    best
                }
            }
        val found = mutableListOf<Peak>()
        for (t in 0 until frames) {
            val row = spectrogram[t]
            val floor = row.average().toFloat() + LandmarkParams.PEAK_FLOOR_MARGIN
            for (b in 0 until band) {
                val value = row[b]
                if (value <= floor) continue
                var largest = true
                for (u in maxOf(0, t - LandmarkParams.PEAK_FRAME_RADIUS)..minOf(frames - 1, t + LandmarkParams.PEAK_FRAME_RADIUS)) {
                    if (acrossBins[u][b] > value) {
                        largest = false
                        break
                    }
                }
                if (largest) found += Peak(t, LandmarkParams.MIN_BIN + b, value)
            }
        }
        // At most so many per block, the strongest, so a loud passage does not fill the print.
        return found
            .groupBy { it.frame / LandmarkParams.FRAMES_PER_BLOCK }
            .values
            .flatMap { block -> block.sortedByDescending { it.value }.take(LandmarkParams.MAX_PEAKS_PER_BLOCK) }
            .sortedWith(compareBy({ it.frame }, { it.bin }))
    }

    /** The print of [samples], 16 kHz mono with values from -1 to 1. Silence has no print. */
    fun fingerprint(samples: FloatArray): LandmarkPrint {
        val peaks = peaks(logSpectrogram(samples))
        val hashes = ArrayList<Int>()
        val frames = ArrayList<Int>()
        for (i in peaks.indices) {
            val anchor = peaks[i]
            val ahead = ArrayList<Peak>()
            var j = i + 1
            while (j < peaks.size && peaks[j].frame - anchor.frame <= LandmarkParams.MAX_FRAME_GAP) {
                val other = peaks[j]
                val gap = other.frame - anchor.frame
                if (gap >= LandmarkParams.MIN_FRAME_GAP && kotlin.math.abs(other.bin - anchor.bin) <= LandmarkParams.MAX_BIN_GAP) ahead += other
                j++
            }
            ahead.sortedByDescending { it.value }.take(LandmarkParams.FAN_OUT).forEach { other ->
                val binPart = anchor.bin - LandmarkParams.MIN_BIN // 0..240, 9 bits
                val diffPart = other.bin - anchor.bin + LandmarkParams.MAX_BIN_GAP // 0..126, 7 bits
                val gapPart = other.frame - anchor.frame - LandmarkParams.MIN_FRAME_GAP // 0..61, 6 bits
                hashes += (binPart shl 13) or (diffPart shl 6) or gapPart
                frames += anchor.frame
            }
        }
        return LandmarkPrint(hashes.toIntArray(), frames.toIntArray())
    }
}

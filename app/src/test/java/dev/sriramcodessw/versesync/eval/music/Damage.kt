package dev.sriramcodessw.versesync.eval.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** What a recording goes through between a speaker and a microphone, made up (docs/09 section 13). All of it is seeded. */
internal object Damage {
    enum class Noise { WHITE, PINK, SPEECH }

    private const val RATE = SyntheticMusic.RATE

    fun rms(x: FloatArray): Double {
        if (x.isEmpty()) return 0.0
        var sum = 0.0
        for (v in x) sum += v.toDouble() * v
        return sqrt(sum / x.size)
    }

    /** [signal] plus noise of the given kind, [snrDb] below the signal's own level. */
    fun addNoise(
        signal: FloatArray,
        kind: Noise,
        snrDb: Double,
        random: Random,
    ): FloatArray {
        val noise = noise(signal.size, kind, random)
        val noiseRms = rms(noise)
        val signalRms = rms(signal)
        if (noiseRms == 0.0 || signalRms == 0.0) return signal.copyOf()
        val scale = (signalRms / noiseRms) * 10.0.pow(-snrDb / 20.0)
        return FloatArray(signal.size) { (signal[it] + scale * noise[it]).toFloat() }
    }

    private fun noise(
        n: Int,
        kind: Noise,
        random: Random,
    ): FloatArray {
        val white = FloatArray(n) { (random.nextDouble() * 2 - 1).toFloat() }
        return when (kind) {
            Noise.WHITE -> {
                white
            }

            Noise.PINK -> {
                // Paul Kellet's filter.
                var b0 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                FloatArray(n) {
                    val w = white[it].toDouble()
                    b0 = 0.99765 * b0 + w * 0.0990460
                    b1 = 0.96300 * b1 + w * 0.2965164
                    b2 = 0.57000 * b2 + w * 1.0526913
                    (b0 + b1 + b2 + w * 0.1848).toFloat()
                }
            }

            Noise.SPEECH -> {
                // Noise in the speech band, rising and falling about four times a second, a little like babble.
                val band = lowPass(highPass(white, 300.0), 3400.0)
                FloatArray(n) { (band[it] * (1.0 + 0.8 * sin(2 * PI * 4.0 * it / RATE))).toFloat() }
            }
        }
    }

    /** [db] more or less level, then clipped at full scale as an overdriven microphone would. */
    fun gain(
        signal: FloatArray,
        db: Double,
    ): FloatArray {
        val g = 10.0.pow(db / 20.0)
        return FloatArray(signal.size) { (signal[it] * g).coerceIn(-1.0, 1.0).toFloat() }
    }

    /** What a small speaker reproduces: roughly 250 Hz to 4 kHz. */
    fun speakerBand(signal: FloatArray): FloatArray = lowPass(highPass(signal, 250.0), 4000.0)

    /** A made-up room: 24 echoes over 0.4 s that fade with a decay (RT60) of about [rt60] seconds. */
    fun echo(
        signal: FloatArray,
        random: Random,
        rt60: Double = 0.45,
    ): FloatArray {
        val out = signal.copyOf()
        repeat(24) {
            val delay = ((0.02 + random.nextDouble() * 0.38) * RATE).toInt()
            val gain = 0.6 * 10.0.pow(-3.0 * (delay.toDouble() / RATE) / rt60) * (if (random.nextBoolean()) 1 else -1)
            for (i in delay until signal.size) out[i] += (gain * signal[i - delay]).toFloat()
        }
        return out
    }

    /** Played [factor] times as fast, by linear interpolation. */
    fun speed(
        signal: FloatArray,
        factor: Double,
    ): FloatArray {
        val n = (signal.size / factor).toInt() - 1
        return FloatArray(maxOf(0, n)) { i ->
            val pos = i * factor
            val k = pos.toInt()
            val frac = (pos - k).toFloat()
            signal[k] * (1 - frac) + signal[minOf(k + 1, signal.size - 1)] * frac
        }
    }

    private fun highPass(
        x: FloatArray,
        hz: Double,
    ): FloatArray {
        val w = 2 * PI * hz / RATE
        val alpha = sin(w) / (2 * 0.7071)
        val c = cos(w)
        return biquad(x, (1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    private fun lowPass(
        x: FloatArray,
        hz: Double,
    ): FloatArray {
        val w = 2 * PI * hz / RATE
        val alpha = sin(w) / (2 * 0.7071)
        val c = cos(w)
        return biquad(x, (1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    private fun biquad(
        x: FloatArray,
        b0: Double,
        b1: Double,
        b2: Double,
        a0: Double,
        a1: Double,
        a2: Double,
    ): FloatArray {
        val out = FloatArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val v = x[i].toDouble()
            val y = (b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            out[i] = y.toFloat()
            x2 = x1
            x1 = v
            y2 = y1
            y1 = y
        }
        return out
    }

    fun db(
        a: Double,
        b: Double,
    ) = 20 * log10(a / b)
}

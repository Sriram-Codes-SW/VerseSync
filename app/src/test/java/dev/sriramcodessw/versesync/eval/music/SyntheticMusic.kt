package dev.sriramcodessw.versesync.eval.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Music made from a seed, for testing the landmark matcher without any copyrighted audio (docs/09 section 13).
 * Never written to disk or committed: a song is rebuilt from (seed, index) every time it is needed.
 *
 * A song is harmonic notes in a key, a chord progression, a melody, a bass line and noise percussion, in sections that
 * repeat (a chorus comes back unchanged). Songs share tempo, key and progression families, so a wrong song looks like
 * the right one in structure, and only its melody, timbre and percussion differ. Real music repeats less and shares
 * more than this, so a result here overstates accuracy.
 */
internal object SyntheticMusic {
    const val RATE = 16000

    private val tempos = listOf(90, 110, 128)
    private val keyOffsets = listOf(0, 3, 5) // semitones above A2 (110 Hz)
    private val progressions = listOf(listOf(0, 7, 9, 5), listOf(9, 5, 0, 7), listOf(0, 9, 5, 7)) // chord roots, in semitones
    private val major = listOf(0, 2, 4, 5, 7, 9, 11)
    private val sections = listOf("A", "B", "A", "B", "C", "B")

    /** A random source for the given parts, mixed so that no two different parts give related streams. */
    private fun rng(vararg parts: Long): Random {
        var h = 1469598103934665603L
        for (p in parts) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 29)
            h *= -4658895280553007687L
            h = h xor (h ushr 32)
        }
        return Random(h)
    }

    private fun hz(semitonesAboveA2: Int) = 110.0 * 2.0.pow(semitonesAboveA2 / 12.0)

    /** One song of [seconds], the same samples for the same ([seed], [index]). */
    fun song(
        seed: Long,
        index: Int,
        seconds: Int,
        performance: Performance = Performance(),
        /** Songs of a family share their tempo and tuning exactly. Off by default: real songs do not (docs/09 section 13). */
        exactFamilies: Boolean = false,
    ): FloatArray {
        val random = rng(seed, index.toLong(), 1)
        val tempo = tempos[random.nextInt(tempos.size)]
        val key = keyOffsets[random.nextInt(keyOffsets.size)]
        val progression = progressions[random.nextInt(progressions.size)]
        val originalHarmonics = random.nextInt(3, 8)
        val originalRolloff = 0.8 + random.nextDouble() * 0.8
        val originalDecay = 0.25 + random.nextDouble() * 0.5 // seconds
        // Within a family a song differs a little in tempo and tuning, as real recordings do.
        val tempoSpread = if (exactFamilies) 1.0 else 1.0 + (random.nextDouble() * 2 - 1) * 0.04
        val tune = if (exactFamilies) 1.0 else 2.0.pow((random.nextDouble() * 2 - 1) * 25 / 1200.0)

        fun tuned(semitones: Int) = hz(semitones) * tune
        // The score (tempo, key, progression, melody) is the song; the sounds are how a performance plays it.
        val timbre = performance.timbreSeed?.let { rng(it, index.toLong(), 4) }
        val harmonics = timbre?.nextInt(3, 8) ?: originalHarmonics
        val rolloff = timbre?.let { 0.8 + it.nextDouble() * 0.8 } ?: originalRolloff
        val decay = timbre?.let { 0.25 + it.nextDouble() * 0.5 } ?: originalDecay
        val jitterRandom = rng(seed, index.toLong(), 5)
        val out = FloatArray(seconds * RATE)

        val sectionMelodies = HashMap<String, IntArray>()
        val sectionRandoms = HashMap<String, Random>()
        var barStart = 0.0
        var barNumber = 0
        while (barStart < seconds) {
            // A performance can be faster, and drift: the tempo moves a little from bar to bar.
            val localFactor = performance.tempoFactor * (1 + performance.driftFraction * sin(0.5 * barNumber))
            val beat = 60.0 / (tempo * tempoSpread * localFactor)
            val bar = 4 * beat
            val section = sections[(barNumber / 4) % sections.size]
            val melody = sectionMelodies.getOrPut(section) { IntArray(32) { rng(seed, index.toLong(), section.hashCode().toLong(), it.toLong(), 2).nextInt(0, 14) } }
            val percussion = sectionRandoms.getOrPut(section) { rng(performance.percussionSeed ?: seed, index.toLong(), section.hashCode().toLong(), 3) }
            val root = key + progression[barNumber % 4]

            // Chord and bass, held for the bar.
            for (interval in listOf(0, 4, 7)) {
                note(out, barStart, bar, tuned(root + 12 + interval), 0.10, harmonics, rolloff, bar * 0.9)
            }
            note(out, barStart, beat * 2, tuned(root), 0.18, harmonics, rolloff, decay)
            note(out, barStart + 2 * beat, beat * 2, tuned(root), 0.18, harmonics, rolloff, decay)

            // Melody, eight notes a bar, from the section's own pattern.
            val eighth = beat / 2
            for (n in 0 until 8) {
                val degree = melody[(barNumber % 4) * 8 + n]
                val semitone = key + 24 + major[degree % 7] + 12 * (degree / 7)
                val late = if (performance.jitterSeconds > 0) (jitterRandom.nextDouble() * 2 - 1) * performance.jitterSeconds else 0.0
                note(out, maxOf(0.0, barStart + n * eighth + late), eighth * 1.6, tuned(semitone), 0.14, harmonics, rolloff, decay)
            }

            // Percussion: a kick on the beats 1 and 3, a snare on 2 and 4, a hat on each eighth, all noise or a low tone.
            for (b in 0 until 4) {
                val at = barStart + b * beat
                if (b % 2 == 0) kick(out, at) else burst(out, at, 0.12, 0.35, percussion)
            }
            for (n in 0 until 8) burst(out, barStart + n * eighth, 0.03, 0.12, percussion)

            barStart += bar
            barNumber++
        }
        // Keep the peak under full scale, as a mastered recording would be.
        var peak = 0f
        for (v in out) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        if (peak > 0f) {
            val scale = 0.8f / peak
            for (i in out.indices) out[i] *= scale
        }
        return out
    }

    /** A note with [harmonics] overtones falling off as `1 / h^rolloff`, decaying with time constant [decay] seconds. */
    private fun note(
        out: FloatArray,
        startSeconds: Double,
        lengthSeconds: Double,
        frequency: Double,
        level: Double,
        harmonics: Int,
        rolloff: Double,
        decay: Double,
    ) {
        val start = (startSeconds * RATE).toInt()
        val length = minOf((lengthSeconds * RATE).toInt(), out.size - start)
        if (start < 0 || length <= 0) return
        val decayPerSample = exp(-1.0 / (decay * RATE))
        for (h in 1..harmonics) {
            val f = frequency * h
            if (f >= RATE / 2.0) break
            val amplitude = level / h.toDouble().pow(rolloff)
            // A rotating phasor: one complex multiply per sample instead of a sine.
            val c = cos(2 * PI * f / RATE)
            val s = sin(2 * PI * f / RATE)
            var x = 1.0
            var y = 0.0
            var envelope = 1.0
            for (i in 0 until length) {
                val attack = if (i < 80) i / 80.0 else 1.0
                out[start + i] += (amplitude * envelope * attack * y).toFloat()
                val nx = x * c - y * s
                y = x * s + y * c
                x = nx
                envelope *= decayPerSample
            }
        }
    }

    private fun kick(
        out: FloatArray,
        startSeconds: Double,
    ) {
        val start = (startSeconds * RATE).toInt()
        val length = minOf((0.15 * RATE).toInt(), out.size - start)
        for (i in 0 until length) {
            val t = i.toDouble() / RATE
            out[start + i] += (0.5 * exp(-t * 25) * sin(2 * PI * (90.0 - 400 * t) * t)).toFloat()
        }
    }

    private fun burst(
        out: FloatArray,
        startSeconds: Double,
        lengthSeconds: Double,
        level: Double,
        random: Random,
    ) {
        val start = (startSeconds * RATE).toInt()
        val length = minOf((lengthSeconds * RATE).toInt(), out.size - start)
        var previous = 0.0
        for (i in 0 until length) {
            val white = random.nextDouble() * 2 - 1
            val bright = white - previous // a first difference makes the noise brighter
            previous = white
            out[start + i] += (level * 0.5 * bright * exp(-4.0 * i / length)).toFloat()
        }
    }
}

/**
 * How a performance of a song differs from the original recording of it: other sounds and percussion, a faster tempo,
 * a tempo that drifts, and notes that start a little early or late. The default is the original, sample for sample.
 */
internal class Performance(
    val timbreSeed: Long? = null,
    val tempoFactor: Double = 1.0,
    val driftFraction: Double = 0.0,
    val jitterSeconds: Double = 0.0,
    val percussionSeed: Long? = null,
)

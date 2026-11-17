package dev.sriramcodessw.versesync.fingerprinting.landmarks

import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-U-49: the landmark matcher on small made-up signals (ADR-19). The corpus run is in docs/09 section 13. */
class LandmarkMatcherTest {
    private val rate = LandmarkParams.SAMPLE_RATE

    /** A made-up piece: a new chord of three tones with a few overtones every quarter second. */
    private fun piece(
        seed: Int,
        seconds: Int,
    ): FloatArray {
        val random = Random(seed)
        val out = FloatArray(seconds * rate)
        val noteLength = rate / 4
        var start = 0
        while (start < out.size) {
            repeat(3) {
                val hz = 220.0 * 2.0.pow(random.nextInt(0, 36) / 12.0)
                for (h in 1..4) {
                    val amplitude = 0.25f / h
                    for (i in 0 until minOf(noteLength, out.size - start)) {
                        out[start + i] += amplitude * sin(2 * PI * hz * h * i / rate).toFloat()
                    }
                }
            }
            start += noteLength
        }
        return out
    }

    @Test
    fun theFft_agreesWithAPlainFourierTransform() {
        val n = 64
        val random = Random(3)
        val re = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val im = DoubleArray(n)
        val expectedRe = DoubleArray(n)
        val expectedIm = DoubleArray(n)
        for (k in 0 until n) {
            for (t in 0 until n) {
                expectedRe[k] += re[t] * cos(2 * PI * k * t / n)
                expectedIm[k] -= re[t] * sin(2 * PI * k * t / n)
            }
        }
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertEquals(expectedRe[k], re[k], 1e-9)
            assertEquals(expectedIm[k], im[k], 1e-9)
        }
    }

    @Test
    fun aPrint_isTheSameEveryTime() {
        val signal = piece(1, 8)
        val a = LandmarkFingerprinter.fingerprint(signal)
        val b = LandmarkFingerprinter.fingerprint(signal)
        assertContentEquals(a.hashes, b.hashes)
        assertContentEquals(a.frames, b.frames)
    }

    @Test
    fun silenceAndShortInput_haveNoPrint() {
        assertEquals(0, LandmarkFingerprinter.fingerprint(FloatArray(5 * rate)).size)
        assertEquals(0, LandmarkFingerprinter.fingerprint(FloatArray(500)).size)
    }

    @Test
    fun aToneBelowTheSpeakerBand_isNotUsed() {
        val low = FloatArray(4 * rate) { (0.5 * sin(2 * PI * 100.0 * it / rate)).toFloat() }
        assertEquals(0, LandmarkFingerprinter.fingerprint(low).size)
    }

    @Test
    fun aPrint_staysWithinTheDensityBudget() {
        val seconds = 20
        val print = LandmarkFingerprinter.fingerprint(piece(2, seconds))
        val perSecond = print.size / seconds.toDouble()
        // At most 30 peaks per block of about a second, each pairing with at most 5 others.
        assertTrue(perSecond in 10.0..160.0, "$perSecond hashes per second")
        // 6 bytes each: about 220 KB for a four minute song at the top of the budget.
        assertEquals(print.size * 6, print.toBytes().size)
    }

    @Test
    fun aPrint_survivesBeingPackedAndUnpacked() {
        val print = LandmarkFingerprinter.fingerprint(piece(4, 6))
        val again = LandmarkPrint.fromBytes(print.toBytes())
        assertContentEquals(print.hashes, again.hashes)
        assertContentEquals(print.frames, again.frames)
    }

    private val library = (1..4).associateWith { piece(100 + it, 30) }

    private val index = LandmarkIndex(library.mapValues { LandmarkFingerprinter.fingerprint(it.value) })

    private fun snippet(
        song: Int,
        startSeconds: Double,
        seconds: Double,
    ): FloatArray = library.getValue(song).copyOfRange((startSeconds * rate).toInt(), ((startSeconds + seconds) * rate).toInt())

    @Test
    fun aSnippetOfALibrarySong_isRecognized_andItsOffsetIsRecovered() {
        // 6.4 s is frame 400 (256 samples per frame).
        val scores = index.scores(LandmarkFingerprinter.fingerprint(snippet(3, 6.4, 4.0)))

        assertEquals(3, scores.song)
        assertTrue(abs(scores.offsetFrames - 400) <= 1, "offset ${scores.offsetFrames}")
        assertTrue(scores.votes >= 15, "${scores.votes} votes")
        assertTrue(scores.rivalVotes * 3 <= scores.votes, "rival ${scores.rivalVotes}")
        assertEquals(3, index.match(LandmarkFingerprinter.fingerprint(snippet(3, 6.4, 4.0)), minVotes = 15, ratio = 3f))
    }

    @Test
    fun aSongThatIsNotInTheLibrary_isNotAccepted() {
        val stranger = LandmarkFingerprinter.fingerprint(piece(999, 10))
        assertNull(index.match(stranger, minVotes = 15, ratio = 3f))
    }

    @Test
    fun theLevelOfTheSnippet_doesNotMatter() {
        for (gain in listOf(0.05f, 0.3f, 1.0f)) {
            val quiet = snippet(2, 10.0, 5.0).map { (it * gain).coerceIn(-1f, 1f) }.toFloatArray()
            assertEquals(2, index.match(LandmarkFingerprinter.fingerprint(quiet), 15, 3f), "gain $gain")
        }
    }

    @Test
    fun aQueryWithNothingHeard_matchesNothing() {
        assertNull(index.match(LandmarkFingerprinter.fingerprint(FloatArray(5 * rate)), 1, 1f))
        val scores = index.scores(LandmarkFingerprinter.fingerprint(FloatArray(5 * rate)))
        assertNull(scores.song)
        assertNotNull(scores)
    }

    @Test
    fun theRatioRule_needsTheWinnerToBeClearlyAhead() {
        val scores = LandmarkScores(song = 1, votes = 20, rivalVotes = 10, offsetFrames = 0)
        assertEquals(1, scores.accepted(minVotes = 15, ratio = 2f))
        assertNull(scores.accepted(minVotes = 15, ratio = 2.1f))
        assertNull(scores.accepted(minVotes = 21, ratio = 1f))
    }
}

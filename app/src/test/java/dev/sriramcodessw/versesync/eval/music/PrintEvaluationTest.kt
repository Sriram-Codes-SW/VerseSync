package dev.sriramcodessw.versesync.eval.music

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** TC-U-50: the generated music, the damage, and the evaluation, on a small setup (docs/09 section 13). */
class PrintEvaluationTest {
    @Test
    fun aSong_isTheSameEveryTime_andDifferentForAnotherIndexOrSeed() {
        val a = SyntheticMusic.song(1, 0, 6)
        assertContentEquals(a, SyntheticMusic.song(1, 0, 6))
        assertFalse(a.contentEquals(SyntheticMusic.song(1, 1, 6)))
        assertFalse(a.contentEquals(SyntheticMusic.song(2, 0, 6)))
        assertTrue(a.max() <= 0.81f && a.min() >= -0.81f)
        assertTrue(Damage.rms(a) > 0.01)
    }

    @Test
    fun aPerformance_keepsTheScoreButChangesTheSoundTheTempoAndTheTiming() {
        val original = SyntheticMusic.song(1, 0, 8)
        val sameScoreOtherSound = SyntheticMusic.song(1, 0, 8, Performance(timbreSeed = 9, percussionSeed = 10))
        val faster = SyntheticMusic.song(1, 0, 8, Performance(tempoFactor = 1.05))
        val loose = SyntheticMusic.song(1, 0, 8, Performance(jitterSeconds = 0.03))

        assertFalse(original.contentEquals(sameScoreOtherSound))
        assertFalse(original.contentEquals(faster))
        assertFalse(original.contentEquals(loose))
        // The default is the original, sample for sample.
        assertContentEquals(original, SyntheticMusic.song(1, 0, 8, Performance()))
    }

    @Test
    fun theNoise_isAddedAtTheRequestedLevel() {
        val signal = SyntheticMusic.song(3, 0, 5)
        for (db in listOf(20.0, 5.0, -5.0)) {
            val noisy = Damage.addNoise(signal, Damage.Noise.PINK, db, Random(5))
            val noiseOnly = FloatArray(signal.size) { noisy[it] - signal[it] }
            val measured = 20 * log10(Damage.rms(signal) / Damage.rms(noiseOnly))
            assertEquals(db, measured, 0.1, "SNR $db")
        }
    }

    @Test
    fun theSpeakerBand_cutsALowToneAndPassesAMidOne() {
        fun tone(hz: Double) = FloatArray(16000) { (0.5 * sin(2 * Math.PI * hz * it / 16000)).toFloat() }
        val low = Damage.speakerBand(tone(60.0))
        val mid = Damage.speakerBand(tone(1000.0))
        // Ignore the first 2000 samples while the filters settle.
        assertTrue(Damage.rms(low.copyOfRange(2000, low.size)) < 0.05)
        assertTrue(Damage.rms(mid.copyOfRange(2000, mid.size)) > 0.3)
    }

    @Test
    fun theLevel_isClipped_theSpeedChangesTheLength_andEchoAddsLaterEnergy() {
        val loud = Damage.gain(FloatArray(100) { 0.5f }, 20.0)
        assertTrue(loud.all { abs(it) <= 1.0f })
        assertEquals(1.0f, loud.max())
        assertTrue(abs(Damage.speed(FloatArray(1000), 1.01).size - 990) <= 3)
        val impulse = FloatArray(16000).also { it[0] = 1f }
        val echoed = Damage.echo(impulse, Random(1))
        assertTrue(echoed.drop(1).any { it != 0f })
    }

    @Test
    fun aSmallEvaluation_runsEndToEnd_cleanQueriesAreFound_andSongsNotInTheLibraryAreNot() {
        val run = PrintEvaluation.Run(PrintEvaluation.Setup(seed = 5, librarySongs = 6, outsideSongs = 4, songSeconds = 30, knownPerSong = 2, unknownQueries = 20))
        val clean = run.outcomes(PrintEvaluation.conditions().first { it.name == "clean" })

        val tally = PrintEvaluation.tally(clean, PrintEvaluation.Rule(25, 3f))
        assertEquals(12, tally.knownRight.total)
        assertEquals(20, tally.unknownAccepted.total)
        assertTrue(tally.knownRight.value >= 0.75, "right ${tally.knownRight}")
        assertEquals(0, tally.unknownAccepted.hits, "the strictest rule of the grid accepted a song not in the library")
        // Songs not in the library only reach chance levels of votes, far below the votes of a song that is.
        val unknownVotes = clean.filter { !it.query.known }.maxOf { it.scores.votes }
        val knownVotes = clean.filter { it.query.known }.minOf { it.scores.votes }
        assertTrue(unknownVotes * 3 < knownVotes, "unknown at most $unknownVotes votes, known at least $knownVotes")
    }

    @Test
    fun theHeadlineCondition_alwaysUsesFiveSecondQueries_andRePerformanceConditionsAreMarked() {
        val conditions = PrintEvaluation.conditions()
        val headline = conditions.single { it.name == PrintEvaluation.headlineName }
        assertEquals(5, headline.fixedSeconds)
        val re = conditions.filter { it.rePerformance != null }
        assertEquals(5, re.size)
        assertTrue(conditions.filter { it.rePerformance == null }.all { it.fixedSeconds == null || it === headline })
    }

    @Test
    fun theGridIsTheEighteenRulesOfTheDocument_andNoneIsChosenWhenNoneIsEligible() {
        assertEquals(18, PrintEvaluation.grid.size)
        assertNotEquals(null, PrintEvaluation.grid.firstOrNull())
        // With no outcomes at all every rule has 0 of 0 false accepts, whose upper end is not a number: none is eligible.
        assertEquals(null, PrintEvaluation.choose(emptyList()))
    }
}

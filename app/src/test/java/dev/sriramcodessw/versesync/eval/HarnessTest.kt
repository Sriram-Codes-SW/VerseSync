package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechRules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-U-40: the evaluation harness itself, on a corpus whose answers are known. */
class HarnessTest {
    @TempDir
    lateinit var dir: File

    private fun results(): Pair<Corpus, List<QueryResult>> {
        val corpus = SyntheticCorpus.build(dir)
        return corpus to Replay(corpus).run()
    }

    private fun List<QueryResult>.of(id: String) = first { it.query.id == id }

    @Test
    fun interval_matchesKnownWilsonValues() {
        val r = Rate(6, 10)
        assertEquals(0.31, r.low, 0.01)
        assertEquals(0.83, r.high, 0.01)
        assertEquals("6/10 = 60% (31 to 83)", r.toString())
        // Nothing seen out of 100 is still not proof of zero.
        assertEquals(0.037, Rate(0, 100).high, 0.002)
        assertEquals("none", Rate(0, 0).toString())
        assertEquals(1522, Rate.trialsNeeded(0.01, 0.005))
    }

    @Test
    fun wav_roundTripsAndResamples() {
        val file = File(dir, "a.wav")
        val samples = FloatArray(1600) { if (it % 2 == 0) 0.5f else -0.5f }
        Wav.write(file, samples, sampleRate = 8000)
        val audio = Wav.read(file)
        assertEquals(8000, audio.sampleRate)
        assertEquals(1600, audio.samples.size)
        assertEquals(0.5f, audio.samples[0], 0.001f)
        assertEquals(3200, Wav.to16k(audio).size)
    }

    @Test
    fun speech_threeRightWordsFindTheSong_andOpenTheLock() {
        val (_, all) = results()
        val speech = all.of("q1").byPath.getValue(Path.SPEECH)
        assertContentEquals(listOf("ocean"), speech.ranked)
        assertEquals(3, speech.evidence.getValue("ocean").speechWords)
        // Heard at frame 30, 64 ms each.
        assertEquals(1920L, speech.firstSeenMs["ocean"])
    }

    @Test
    fun speech_oneRightWordIsNotEnough() {
        val (_, all) = results()
        assertTrue(
            all
                .of("q2")
                .byPath
                .getValue(Path.SPEECH)
                .ranked
                .isEmpty(),
        )
    }

    @Test
    fun pitch_findsASongSungInAnotherKey_atTheFiveSecondTick() {
        val (_, all) = results()
        val pitch = all.of("q1").byPath.getValue(Path.PITCH)
        assertEquals("ocean", pitch.ranked.first())
        assertEquals(5000L, pitch.firstSeenMs["ocean"])
        assertTrue(
            all
                .of("q2")
                .byPath
                .getValue(Path.PITCH)
                .ranked
                .contains("garden"),
        )
    }

    @Test
    fun pitch_isNotLookedAtBeforeFiveSeconds() {
        val (_, all) = results()
        assertTrue(
            all
                .of("q4")
                .byPath
                .getValue(Path.PITCH)
                .ranked
                .isEmpty(),
        )
        // Speech still finds it.
        assertEquals(
            listOf("forest"),
            all
                .of("q4")
                .byPath
                .getValue(Path.SPEECH)
                .ranked,
        )
    }

    @Test
    fun unknownMelodyAndWords_findNothing_butTwoLuckyWordsAreAFalseAccept() {
        val (corpus, all) = results()
        assertTrue(
            all
                .of("q3")
                .byPath
                .getValue(Path.COMBINED)
                .ranked
                .isEmpty(),
        )
        assertEquals(
            listOf("ocean"),
            all
                .of("q5")
                .byPath
                .getValue(Path.SPEECH)
                .ranked,
        )

        val speech = Metrics.slice("all", all, Path.SPEECH, corpus.library)
        assertEquals(Rate(1, 2).toString(), speech.falseAccept.toString())
    }

    @Test
    fun metrics_countFoundTop1AndLatency() {
        val (corpus, all) = results()
        val combined = Metrics.slice("all", all, Path.COMBINED, corpus.library)
        assertEquals(3, combined.inLibrary)
        assertEquals(2, combined.outOfLibrary)
        // q1, q2 (pitch) and q4 (speech) are all found.
        assertEquals(3, combined.found.hits)
        assertEquals(3, combined.top1.hits)
        // Latency is the median of q1 1.9 s, q2 5.0 s and q4 1.3 s.
        assertEquals(1920L, combined.medianLatencyMs)
        // The lock opens for the right song on q1 (three words, and the pitch) and q2 (the pitch alone); q4 has only two words.
        assertEquals(2, combined.lockOpens.hits)
        // Of the wrong-song trials, none opens it.
        assertEquals(0, combined.lockFalse.hits)
        assertEquals(3 * 2 + 2 * 3, combined.lockFalse.total)
    }

    @Test
    fun metrics_sliceByCondition() {
        val (corpus, all) = results()
        val slices = Metrics.slices(all, Path.COMBINED, corpus.library).getValue("condition").associateBy { it.name }
        assertEquals(2, slices.getValue("condition: quiet").inLibrary)
        assertEquals(1, slices.getValue("condition: street").inLibrary)
    }

    @Test
    fun aMissingTranscriptIsReportedNotHidden() {
        val (corpus, _) = results()
        File(dir, "transcripts/q1.json").delete()
        val rerun = Replay(corpus).run()
        assertTrue(rerun.of("q1").notes.any { "no transcript" in it })
        assertTrue(
            rerun
                .of("q1")
                .byPath
                .getValue(Path.SPEECH)
                .ranked
                .isEmpty(),
        )
    }

    @Test
    fun aQueryNamingAnUnknownSong_isRejected() {
        SyntheticCorpus.build(dir)
        File(dir, "queries.json").writeText("""[{"id":"x","song":"nope"}]""")
        val error = runCatching { Corpus.load(dir) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertNull(runCatching { Corpus.load(dir) }.getOrNull())
    }

    @Test
    fun sweep_coversEverySetting_andItsDefaultRowIsTheNormalRun() {
        val (corpus, all) = results()
        val rows = Sweep.run(corpus)

        assertEquals(Sweep.minWords.size * Sweep.shareThresholds.size * Sweep.fuzzy.size * Sweep.leadMargins.size, rows.size)
        val normal = Metrics.slice("all", all, Path.SPEECH, corpus.library)
        val default = rows.single { it.rules == SpeechRules() }
        assertEquals(normal.found.toString(), default.slice.found.toString())
        assertEquals(normal.falseAccept.toString(), default.slice.falseAccept.toString())
        // Asking for more words can only find fewer songs.
        val oneWord = rows.first { it.rules.minWords == 1 && !it.rules.fuzzy && it.rules.shareThreshold == 0.35f }
        val threeWords = rows.first { it.rules.minWords == 3 && !it.rules.fuzzy && it.rules.shareThreshold == 0.35f }
        assertTrue(oneWord.slice.found.hits >= threeWords.slice.found.hits)
        assertTrue("(default)" in Sweep.markdown(corpus, rows, "synthetic"))
    }

    @Test
    fun theReport_isTheSameEveryTime_andHoldsNoDate() {
        val (corpus, first) = results()
        val second = Replay(corpus).run()
        val a = Report.markdown(corpus, first, "synthetic")
        val b = Report.markdown(corpus, second, "synthetic")
        assertEquals(a, b)
        assertEquals(Report.json(corpus, first, "synthetic"), Report.json(corpus, second, "synthetic"))
        assertFalse(Regex("20\\d\\d-\\d\\d-\\d\\d").containsMatchIn(a))

        val written = Report.write(corpus, first, "synthetic", File(dir, "out"))
        assertTrue(written.markdown.isFile && written.json.isFile)
        assertTrue("| all | 3 |" in a)
    }
}

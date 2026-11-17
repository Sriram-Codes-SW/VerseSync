package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechDetail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-U-43: decision rules, evaluated with the rival taken from every song, acting on the first firing (issue #44). */
class DecisionRuleTest {
    @TempDir
    lateinit var dir: File

    private fun detail(
        words: Int,
        weight: Float,
        share: Float = 0.8f,
        lyricWords: Int = 40,
    ) = SpeechDetail(words, weight, share, lyricWords)

    @Test
    fun aTie_neverDecides() {
        val rule = DecisionRule(minWords = 2, minShare = 0.35f, absoluteGap = 0f)
        assertNull(rule.decide(mapOf("a" to detail(3, 3f), "b" to detail(3, 3f))))
        assertNotNull(rule.decide(mapOf("a" to detail(3, 3.1f), "b" to detail(3, 3f))))
    }

    @Test
    fun theGap_mustBeExceeded_notJustReached() {
        val state = mapOf("a" to detail(4, 3f), "b" to detail(2, 1f))
        assertEquals("a", DecisionRule(2, 0.35f, 1.5f).decide(state)?.song)
        assertNull(DecisionRule(2, 0.35f, 2f).decide(state), "a gap of exactly 2 does not exceed 2")
    }

    @Test
    fun aSongThatIsNotACandidate_stillCountsAsARival() {
        // b would not pass the rule's own thresholds, so it is not a candidate, but it has nearly as much weight as a.
        val state = mapOf("a" to detail(4, 4f, share = 0.9f), "b" to detail(1, 3.5f, share = 0.2f))
        val strict = DecisionRule(minWords = 3, minShare = 0.5f, absoluteGap = 0f, relativeMargin = 0.25f)

        // With the rival left out (what the first sweep did), a would decide. With b counted, it does not.
        assertNull(strict.decide(state))
        assertEquals("a", strict.decide(mapOf("a" to state.getValue("a")))?.song)
    }

    @Test
    fun minimumWords_isCapped_byTheWordsInTheLyrics() {
        val rule = DecisionRule(minWords = 4, minShare = 0.35f, absoluteGap = 0f)
        assertEquals("a", rule.decide(mapOf("a" to detail(2, 2f, lyricWords = 2)))?.song)
        assertNull(rule.decide(mapOf("a" to detail(2, 2f, lyricWords = 30))))
    }

    @Test
    fun nothingHeard_decidesNothing() {
        assertNull(DecisionRule(1, 0f, 0f).decide(emptyMap()))
    }

    private fun result(
        id: String,
        song: String?,
        vararg steps: Map<String, SpeechDetail>,
    ) = QueryResult(
        Query(id, null, song, "speak", "en-us", "read speech", "fleurs", null, "female"),
        emptyMap(),
        emptyList(),
        steps.mapIndexed { n, state -> SpeechStep(10 * (n + 1), state) },
    )

    @Test
    fun actingTheFirstTimeItFires_isStricterThanLookingAtTheEnd() {
        val rule = DecisionRule(minWords = 2, minShare = 0.35f, absoluteGap = 0f)
        val wrongEarly = mapOf("y" to detail(2, 2f))
        val rightLater = mapOf("x" to detail(5, 5f), "y" to detail(2, 2f))
        val results =
            listOf(
                result("known-1", "x", wrongEarly, rightLater),
                // A song not in the library that only ever fires early, then is outweighed by another and ties.
                result("unknown-1", null, mapOf("y" to detail(2, 2f)), mapOf("y" to detail(2, 2f), "z" to detail(2, 2f))),
            )

        val slice = RuleMetrics.evaluate("rule", results, rule)

        // It names y first, which is the wrong song, though by the end it names x.
        assertEquals(0, slice.firstRight.hits)
        assertEquals(1, slice.firstWrongSong.hits)
        assertEquals(1, slice.finalRight.hits)
        // The unknown recording fires at the first update, and not at the end (a tie).
        assertEquals(1, slice.firstFalse.hits)
        assertEquals(0, slice.finalFalse.hits)
        assertEquals(1, slice.known)
        assertEquals(1, slice.unknown)
    }

    @Test
    fun aRecordingWithoutATranscript_neverDecides_andStillCounts() {
        val slice = RuleMetrics.evaluate("rule", listOf(result("q", "x")), DecisionRule(1, 0f, 0f))
        assertEquals(1, slice.known)
        assertEquals(0, slice.firstRight.hits)
    }

    private fun scored(
        rule: DecisionRule,
        right: Int,
        wrongSong: Int,
        falseDecisions: Int,
    ) = Selection.Scored(
        rule,
        RuleSlice("r", 100, 200, Rate(right, 100), Rate(wrongSong, 100), Rate(falseDecisions, 200), Rate(0, 100), Rate(0, 100), Rate(0, 200), 0),
    )

    @Test
    fun theCriterion_choosesTheBestEligibleRule_andTiesGoToTheSimplest() {
        val loose = scored(DecisionRule(1, 0.35f, 0f), right = 95, wrongSong = 0, falseDecisions = 60)
        val strictButWrong = scored(DecisionRule(3, 0.5f, 0f), right = 90, wrongSong = 5, falseDecisions = 1)
        val good = scored(DecisionRule(4, 0.65f, 1f), right = 80, wrongSong = 0, falseDecisions = 2)
        val sameButComplex = scored(DecisionRule(6, 0.8f, 2f), right = 80, wrongSong = 0, falseDecisions = 2)

        assertTrue(!loose.eligible && !strictButWrong.eligible && good.eligible)
        assertEquals(good.rule, Selection.choose(listOf(loose, strictButWrong, sameButComplex, good)))
        assertNull(Selection.choose(listOf(loose, strictButWrong)))
    }

    @Test
    fun theUpperBound_mustBeLow_notJustThePointEstimate() {
        // 0 of 20 is a point estimate of 0, but the upper end of the interval is far above 5 percent.
        val small = Selection.Scored(DecisionRule(1, 0f, 0f), RuleSlice("r", 100, 20, Rate(90, 100), Rate(0, 100), Rate(0, 20), Rate(0, 100), Rate(0, 100), Rate(0, 20), 0))
        assertTrue(!small.eligible)
    }

    @Test
    fun replay_recordsTheEvidenceAfterEachPieceOfText_forEverySong() {
        val corpus = SyntheticCorpus.build(dir)
        val q1 = Replay(corpus, EvalConfig(pitch = false)).run().first { it.query.id == "q1" }

        assertEquals(1, q1.speechSteps.size)
        val state = q1.speechSteps.single().state
        assertEquals(3, state.getValue("ocean").words)
        assertTrue("garden" !in state, "a song with no heard word is not in the evidence")
    }
}

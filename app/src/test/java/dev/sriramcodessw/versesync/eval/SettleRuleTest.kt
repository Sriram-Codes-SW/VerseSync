package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechDetail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-U-44: settling before acting, evidence that can be taken back, and the end-of-file result (issue #44). */
class SettleRuleTest {
    @TempDir
    lateinit var dir: File

    private val rule = DecisionRule(minWords = 2, minShare = 0.35f, absoluteGap = 0f)
    private val strong = mapOf("x" to SpeechDetail(4, 4f, 0.9f, 40))
    private val nothing = emptyMap<String, SpeechDetail>()
    private val tied = mapOf("x" to SpeechDetail(4, 4f, 0.9f, 40), "y" to SpeechDetail(4, 4f, 0.9f, 40))

    private fun steps(vararg pairs: Pair<Int, Map<String, SpeechDetail>>) = pairs.map { (frame, state) -> SpeechStep(frame, state) }

    @Test
    fun withNoHold_theRuleActsAtTheFirstUpdateThatDecides() {
        val firing = RuleMetrics.firing(steps(5 to nothing, 10 to strong, 30 to tied), rule)
        assertEquals("x", firing?.song)
        assertEquals(10, firing?.frame)
    }

    @Test
    fun aDecisionThatOnlyExistsAtTheLastReportedFrame_isTheEndOfFileResult_andDoesNotCount() {
        // A live recognizer gives a final result only when it is stopped, so an update at the last frame is not acted on.
        assertNull(RuleMetrics.firing(steps(10 to nothing, 20 to strong), rule))
    }

    @Test
    fun aHold_delaysTheAct_andActsOnlyIfTheDecisionLastsThatLong() {
        val lasting = steps(10 to strong, 30 to tied)
        assertEquals(25, RuleMetrics.firing(lasting, rule, Settle(holdFrames = 15))?.frame)
        // The decision lasts 20 frames before the tie at 30, so a hold of 25 never completes.
        assertNull(RuleMetrics.firing(lasting, rule, Settle(holdFrames = 25)))
    }

    @Test
    fun aDecisionThatIsBroken_startsTheHoldAgain() {
        val broken = steps(10 to strong, 20 to nothing, 40 to strong, 50 to tied)
        // The first run (10 to 20) is too short for a hold of 15, and the second (40 to 50) is too.
        assertNull(RuleMetrics.firing(broken, rule, Settle(holdFrames = 15)))
        // A second run of 20 frames is long enough, and its hold starts again from where it began.
        assertEquals(55, RuleMetrics.firing(steps(10 to strong, 20 to nothing, 40 to strong, 60 to tied), rule, Settle(holdFrames = 15))?.frame)
    }

    @Test
    fun aLongerHold_neverMakesMoreFalseDecisions_andNeverFewerMissedRecordings() {
        val results =
            listOf(
                QueryResult(Query("u", null, null, "speak", "en", "read speech", "fleurs", null, "f"), emptyMap(), emptyList(), steps(10 to strong, 15 to nothing, 90 to nothing)),
                QueryResult(Query("k", null, "x", "speak", "en", "read speech", "fleurs", null, "f"), emptyMap(), emptyList(), steps(10 to strong, 90 to strong)),
            )
        var previousFalse = Int.MAX_VALUE
        var previousRight = Int.MAX_VALUE
        listOf(0, 3, 10, 40, 100).forEach { hold ->
            val slice = RuleMetrics.evaluate("h$hold", results, rule, Settle(holdFrames = hold))
            assertTrue(slice.firstFalse.hits <= previousFalse)
            assertTrue(slice.firstRight.hits <= previousRight)
            previousFalse = slice.firstFalse.hits
            previousRight = slice.firstRight.hits
        }
        // The unknown recording's decision lasts 5 frames, so only an immediate act is false. The known one lasts 80.
        assertEquals(1, RuleMetrics.evaluate("h0", results, rule).firstFalse.hits)
        assertEquals(0, RuleMetrics.evaluate("h10", results, rule, Settle(holdFrames = 10)).firstFalse.hits)
        assertEquals(1, RuleMetrics.evaluate("h40", results, rule, Settle(holdFrames = 40)).firstRight.hits)
    }

    @Test
    fun timeToTheRightAnswer_isReported() {
        val results = listOf(QueryResult(Query("k", null, "x", "speak", "en", "read speech", "fleurs", null, "f"), emptyMap(), emptyList(), steps(50 to strong, 200 to strong)))
        val slice = RuleMetrics.evaluate("r", results, rule, Settle(holdFrames = 10))
        // Acts at frame 60, 64 ms each.
        assertEquals(60 * 0.064, slice.medianSecondsToRight!!, 0.0001)
    }

    private fun corpusWithTranscript(entries: String): QueryResult {
        val corpus = SyntheticCorpus.build(dir)
        File(dir, "transcripts/q1.json").writeText(entries)
        return Replay(corpus, EvalConfig(pitch = false)).run().first { it.query.id == "q1" }
    }

    @Test
    fun retractableEvidence_letsALaterPartialReplaceAnEarlierOne_butKeepsFinishedUtterances() {
        val result =
            corpusWithTranscript(
                """[{"frame":10,"text":"ocean river mountain"},
                   {"frame":20,"text":"banana kiwi mango"},
                   {"frame":30,"text":"ocean river mountain","final":true},
                   {"frame":40,"text":"banana kiwi mango"}]""",
            )

        // Evidence that only grows keeps the first partial for good.
        assertEquals(listOf(true, true, true, true), result.speechSteps.map { "ocean" in it.state.keys })
        // Evidence that can be taken back drops it when the next partial replaces it, and keeps what was finished.
        assertEquals(listOf(true, false, true, true), result.retractableSteps.map { "ocean" in it.state.keys })
    }

    @Test
    fun anOlderTranscript_withNoFinalMarks_isReplayedWithEachUpdateOnItsOwn() {
        val result = corpusWithTranscript("""[{"frame":10,"text":"ocean river mountain"},{"frame":20,"text":"banana kiwi mango"}]""")

        assertNotNull(result.retractableSteps.firstOrNull())
        assertEquals(listOf(true, false), result.retractableSteps.map { "ocean" in it.state.keys })
    }
}

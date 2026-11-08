package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.data.SongDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.kotlin.mock
import java.text.Normalizer
import kotlin.test.assertTrue

/** Matching threshold used by the recognition engine for speech candidates. */
private const val MATCH_THRESHOLD = 0.35f

class LyricMatchingLanguagesTest {

    private lateinit var engine: HybridRecognitionEngine

    @BeforeEach
    fun setup() {
        engine = HybridRecognitionEngine(mock<Context>(), mock<SongDao>())
    }

    @TestFactory
    fun spokenPhrase_matchesItsSavedLyrics_inEveryLanguage(): List<DynamicTest> =
        languageSamples.map { (code, sample) ->
            DynamicTest.dynamicTest("${sample.name} ($code) matches") {
                val score = engine.matchLyricsScore(sample.spoken, sample.lyrics)
                assertTrue(
                    score >= MATCH_THRESHOLD,
                    "${sample.name}: expected a match at or above $MATCH_THRESHOLD but got $score"
                )
            }
        }

    @TestFactory
    fun unrelatedPhrase_doesNotMatch_inEveryLanguage(): List<DynamicTest> {
        // Every phrase is scored against the lyrics of the next language in the table.
        val samples = languageSamples.values.toList()
        return samples.mapIndexed { i, sample ->
            val other = samples[(i + 1) % samples.size]
            DynamicTest.dynamicTest("${sample.name} lyrics reject ${other.name} speech") {
                val score = engine.matchLyricsScore(other.spoken, sample.lyrics)
                assertTrue(score < MATCH_THRESHOLD, "False match between ${other.name} speech and ${sample.name} lyrics: $score")
            }
        }
    }

    @TestFactory
    fun decomposedAndComposedText_matchEachOther_inEveryLanguage(): List<DynamicTest> =
        languageSamples.map { (code, sample) ->
            DynamicTest.dynamicTest("${sample.name} ($code) ignores Unicode composition form") {
                val decomposed = Normalizer.normalize(sample.spoken, Normalizer.Form.NFD)
                val score = engine.matchLyricsScore(decomposed, sample.lyrics)
                assertTrue(score >= MATCH_THRESHOLD, "${sample.name}: NFD speech scored $score against NFC lyrics")
            }
        }

    @Test
    fun partialPhrase_stillMatches() {
        // A listener catches only part of the line, which is the normal case for live recognition.
        val sample = languageSamples.getValue("hi")
        val score = engine.matchLyricsScore("सारे जहाँ से अच्छा", sample.lyrics)

        assertTrue(score >= MATCH_THRESHOLD, "Partial Hindi phrase scored $score")
    }
}

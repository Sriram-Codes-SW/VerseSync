package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-U-58: the conversion of Whisper output into a transcript, and the counts of what was heard (docs/09 section 16). */
class WhisperTranscriptTest {
    private fun file(vararg segments: Pair<Long, String>): String {
        val body = segments.joinToString(",") { (to, text) -> """{"timestamps":{"from":"x","to":"y"},"offsets":{"from":0,"to":$to},"text":"$text"}""" }
        return """{"systeminfo":"x","result":{"language":"en"},"transcription":[$body]}"""
    }

    @Test
    fun segmentsBecomeEntriesAtTheirEndFrame() {
        val heard = WhisperTranscript.toHeard(file(6400L to " hello world", 12830L to " second line"))
        assertEquals(listOf(100, 200), heard.map { it.frame }) // 6400/64 = 100; 12830/64 = 200.47 rounds to 200
        assertEquals(listOf("hello world", "second line"), heard.map { it.text })
        assertTrue(heard.all { it.final })
    }

    @Test
    fun emptySegmentsAreDropped_andNothingIsMergedOrSplit() {
        val heard = WhisperTranscript.toHeard(file(1000L to "   ", 2000L to "one", 3000L to "", 4000L to "two"))
        assertEquals(listOf("one", "two"), heard.map { it.text })
    }

    @Test
    fun noTranscriptionFieldGivesNothing() {
        assertEquals(emptyList(), WhisperTranscript.toHeard("""{"systeminfo":"x"}"""))
    }

    @Test
    fun counts_useTheAppsTokens_andIgnoreRepeatsForDistinct() {
        val heard = WhisperTranscript.toHeard(file(1000L to "the quick brown fox", 2000L to "the quick fox again"))
        val counts = WhisperTranscript.count(heard, setOf("quick", "fox", "lazy"))
        assertEquals(2, counts.entries)
        assertEquals(setOf("quick", "brown", "fox", "again", "the").size, counts.distinctHeard)
        assertEquals(2, counts.distinctInLyrics)
        assertFalse(counts.loopSuspected)
    }

    @Test
    fun songNotInTheLibraryHasNoLyricCount() {
        val counts = WhisperTranscript.count(WhisperTranscript.toHeard(file(1000L to "some words here")), null)
        assertNull(counts.distinctInLyrics)
    }

    @Test
    fun aThreeWordSequenceThreeTimesIsFlaggedAsALoop() {
        val loop = WhisperTranscript.toHeard(file(1000L to "love you tender", 2000L to "love you tender", 3000L to "love you tender"))
        assertTrue(WhisperTranscript.count(loop, null).loopSuspected)
        val twice = WhisperTranscript.toHeard(file(1000L to "love you tender", 2000L to "love you tender"))
        assertFalse(WhisperTranscript.count(twice, null).loopSuspected)
    }
}

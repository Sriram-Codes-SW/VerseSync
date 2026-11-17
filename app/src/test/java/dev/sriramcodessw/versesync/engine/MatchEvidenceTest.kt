package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import dev.sriramcodessw.versesync.ui.screens.VocalLockPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TC-U-38 and TC-U-39: how much evidence a song needs before it is a candidate, and before the
 * vocal lock opens (issue #41). One heard word that happens to be in the lyrics used to be enough.
 */
class MatchEvidenceTest {
    private val songDao: SongDao = mock()

    private val lyrics = "Amazing grace how sweet the sound that saved a wretch like me"

    private fun engineHearing(
        spoken: MutableStateFlow<String>,
        vararg songs: Song,
    ): HybridRecognitionEngine {
        whenever(songDao.getAllSongs()).thenReturn(
            flow {
                delay(30)
                emit(songs.toList())
            },
        )
        val vosk: VoskVoiceRecognition = mock()
        whenever(vosk.isModelReady).thenReturn(MutableStateFlow(true))
        whenever(vosk.recognitionResult).thenReturn(spoken)
        return HybridRecognitionEngine(mock<Context>(), songDao, vosk, pitchIntervalMs = 100)
    }

    /** What the engine reports a moment after [spoken] is heard, or null if the song never appears. */
    private fun resultAfterHearing(
        spokenText: String,
        song: Song,
    ): RecognitionResult? {
        val spoken = MutableStateFlow("")
        val engine = engineHearing(spoken, song)
        return runBlocking {
            spoken.value = spokenText
            withTimeoutOrNull(1_500) { engine.recognitionResult.first { r -> r.candidates.any { it.id == song.id } } }
        }
    }

    private val song = Song(id = 3, title = "Grace", artist = "x", lyrics = lyrics)

    @Test
    fun oneHeardWord_thatIsInTheLyrics_isNotACandidate() {
        assertNull(resultAfterHearing("grace", song), "A single heard word was enough to match a song")
    }

    @Test
    fun twoHeardWords_inTheLyrics_makeTheSongACandidate() {
        val result = resultAfterHearing("amazing grace", song)

        assertNotNull(result)
        assertEquals(2, result.evidence[song.id]?.speechWords)
    }

    @Test
    fun theSameWordHeardTwice_countsOnce() {
        assertNull(resultAfterHearing("grace grace grace", song))
    }

    @Test
    fun oneWordIsEnoughWhenTheLyricsHaveOnlyOne() {
        val short = Song(id = 4, title = "Amen", artist = "x", lyrics = "Amen")

        assertNotNull(resultAfterHearing("amen", short))
    }

    @Test
    fun theShareOfHeardWordsStillApplies() {
        // Two of five heard words are in the lyrics (0.4), which passes the share, and that is enough.
        assertNotNull(resultAfterHearing("amazing grace banana kiwi mango", song))
        // One of three (0.33) fails the share, whatever the count.
        assertNull(resultAfterHearing("grace banana kiwi", song))
    }

    @Test
    fun chineseNeedsMoreThanTwoCharacters() {
        val chinese = Song(id = 5, title = "C", artist = "x", lyrics = "感染的元素属于金属", language = "cn")

        // Characters are matched in overlapping pairs, so two characters are one token.
        assertNull(resultAfterHearing("感染", chinese))
        assertNotNull(resultAfterHearing("感染的", chinese))
    }

    @Test
    fun theLockNeedsMoreWordsThanRecognitionDoes() {
        val two = resultAfterHearing("amazing grace", song)
        val three = resultAfterHearing("amazing grace how", song)

        assertFalse(VocalLockPolicy.opens(two?.evidence?.get(song.id)))
        assertTrue(VocalLockPolicy.opens(three?.evidence?.get(song.id)))
    }

    // The lock policy on its own.

    @Test
    fun lockPolicy_opensOnThreeWordsOrOnAStrongLongPitchMatch() {
        assertFalse(VocalLockPolicy.opens(null))
        assertFalse(VocalLockPolicy.opens(MatchEvidence()))
        assertFalse(VocalLockPolicy.opens(MatchEvidence(speechWords = 2)))
        assertTrue(VocalLockPolicy.opens(MatchEvidence(speechWords = 3)))

        assertFalse(VocalLockPolicy.opens(MatchEvidence(pitchScore = 2.4f, pitchOverlap = 60)), "not close enough")
        assertFalse(VocalLockPolicy.opens(MatchEvidence(pitchScore = 0.5f, pitchOverlap = 29)), "too short")
        assertTrue(VocalLockPolicy.opens(MatchEvidence(pitchScore = 2.0f, pitchOverlap = 30)))
    }

    // Pitch, with two real recorded fingerprints of different spoken sentences (hum removed).

    private val clips: List<List<Float>> =
        javaClass
            .getResourceAsStream("/real-fingerprints.csv")!!
            .bufferedReader()
            .readLines()
            .map { line -> line.split(',').map { it.toFloat() } }

    private val engine = HybridRecognitionEngine(mock<Context>(), songDao)

    @Test
    fun aDifferentClip_neverGivesTheLockEnoughPitchEvidence_atAnyWindow() {
        val (a, b) = clips
        for (n in 40..216 step 8) {
            for ((reference, live) in listOf(a to b.take(n), b to a.take(n))) {
                val match = engine.calculatePitchMatch(reference, live)
                val evidence = MatchEvidence(pitchScore = match.score, pitchOverlap = match.overlap)
                assertFalse(VocalLockPolicy.opens(evidence), "A different clip opened the lock at window $n: $match")
            }
        }
    }

    @Test
    fun theCaseThatSlippedThroughBefore_isNowRejected() {
        val (a, b) = clips

        // The first 160 frames of the second clip scored 2.82 against the first, under the old 3.0.
        val match = engine.calculatePitchMatch(a, b.take(160))

        assertTrue(match.score >= 2.5f, "scored ${match.score}, which the recognition threshold would accept")
    }

    @Test
    fun theSameClipHeardInFull_opensTheLock() {
        val (a, _) = clips

        val match = engine.calculatePitchMatch(a, a)

        assertTrue(VocalLockPolicy.opens(MatchEvidence(pitchScore = match.score, pitchOverlap = match.overlap)), "$match")
    }

    @Test
    fun theSameClipHeardInFull_reportsHowManyFramesItWasMeasuredOver() {
        val (a, _) = clips

        assertEquals(a.count { it > 0f }, engine.calculatePitchMatch(a, a).overlap)
    }
}

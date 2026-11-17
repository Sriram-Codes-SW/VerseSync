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
 * TC-U-41: scoring every song at once, weighting words by how rare they are in the library, requiring
 * a lead over the strongest other song, and counting a heard word that is a letter off (issue #44).
 */
class SpeechScoringTest {
    private fun library(
        vararg lyrics: String,
        rules: SpeechRules = SpeechRules(),
    ) = SpeechLibrary(lyrics.indices.associateWith { lyricTokenSet(lyrics[it]) }, rules)

    private fun heard(text: String) = spokenMatchTokens(text)

    @Test
    fun scoreEvery_keepsSongsThatAreNotCandidates_andScoreAllIsItsFilter() {
        val songs = library("ocean river mountain valley", "ocean forest meadow whisper", "desert canyon sunrise shadow")
        val every = songs.scoreEvery(heard("ocean river mountain banana kiwi"))

        // Song 1 has one heard word of five: not a candidate, but it is evidence, and a rival.
        assertEquals(setOf(0, 1), every.keys)
        assertEquals(1, every.getValue(1).words)
        assertEquals(setOf(0), songs.scoreAll(heard("ocean river mountain banana kiwi")).keys)
        assertEquals(0.6f, every.getValue(0).share, 0.0001f)
    }

    @Test
    fun editDistance_countsOneLetterOffAsClose() {
        assertTrue(editDistanceAtMost("garden", "gardin", 1))
        assertTrue(editDistanceAtMost("garden", "gardens", 1))
        assertTrue(editDistanceAtMost("garden", "garrden", 1))
        assertTrue(editDistanceAtMost("garden", "harden", 1))
        assertFalse(editDistanceAtMost("garden", "hardin", 1))
        assertTrue(editDistanceAtMost("garden", "hardin", 2))
        assertFalse(editDistanceAtMost("garden", "gardener", 1))
    }

    @Test
    fun aRareWordWeighsMoreThanAWordInEverySong() {
        val songs = library("ocean river mountain", "ocean desert canyon", "ocean forest meadow")
        // "ocean" is in all three songs, "river" and "desert" in one each.
        val scores = songs.scoreAll(heard("ocean river desert"))

        assertEquals(setOf(0, 1), scores.keys)
        assertEquals(2, scores.getValue(0).words)
        // Equal evidence for two songs: the same weight.
        assertEquals(scores.getValue(0).weight, scores.getValue(1).weight, 0.0001f)
        // A word in every song weighs less than a word in one.
        val onlyCommon = library("ocean river alpha", "ocean river beta", "ocean river gamma")
        val common = onlyCommon.scoreAll(heard("ocean river"))
        assertTrue(common.getValue(0).weight < scores.getValue(0).weight)
    }

    @Test
    fun everySongIsScored_andOnlyTheCandidatesAreKept() {
        val songs = library("ocean river mountain valley", "ocean river canyon desert", "forest meadow whisper sunrise")
        val scores = songs.scoreAll(heard("ocean river mountain"))

        assertEquals(setOf(0, 1), scores.keys)
        assertEquals(3, scores.getValue(0).words)
        assertEquals(2, scores.getValue(1).words)
        assertTrue(scores.getValue(0).weight > scores.getValue(1).weight)
    }

    @Test
    fun theSameWordsAsBefore_whenAlone() {
        val lyrics = setOf("amazing", "grace", "sweet", "sound")
        assertNull(SpeechMatcher.matchedWords(heard("grace"), lyrics))
        assertEquals(2, SpeechMatcher.matchedWords(heard("amazing grace"), lyrics))
        assertNull(SpeechMatcher.matchedWords(heard("grace banana kiwi"), lyrics))
    }

    @Test
    fun rivalWeights_isTheStrongestOtherSong() {
        assertEquals(mapOf("a" to 3f, "b" to 3f, "c" to 3f), SpeechMatcher.rivalWeights(mapOf("a" to 3f, "b" to 3f, "c" to 1f)))
        assertEquals(mapOf("a" to 2f, "b" to 3f), SpeechMatcher.rivalWeights(mapOf("a" to 3f, "b" to 2f)))
        assertEquals(mapOf("a" to 0f), SpeechMatcher.rivalWeights(mapOf("a" to 3f)))
        assertEquals(emptyMap(), SpeechMatcher.rivalWeights(emptyMap<String, Float>()))
    }

    @Test
    fun leads_needsAMarginOverTheRival() {
        assertTrue(SpeechMatcher.leads(3f, 0f))
        assertFalse(SpeechMatcher.leads(3f, 3f), "a tie is not a lead")
        assertFalse(SpeechMatcher.leads(2.4f, 2f))
        assertTrue(SpeechMatcher.leads(2.5f, 2f))
        assertFalse(SpeechMatcher.leads(1f, 3f), "behind")
    }

    @Test
    fun aNearMiss_countsOnlyWhenFuzzyIsOn_andOnlyForLongerWords() {
        val lyrics = "amazing grace sweet sound lovely"
        val exact = library(lyrics).scoreAll(heard("amazin graze"))
        val fuzzy = library(lyrics, rules = SpeechRules(fuzzy = true)).scoreAll(heard("amazin graze"))

        assertTrue(exact.isEmpty(), "A garbled word does not match unless fuzzy matching is on")
        assertEquals(2, fuzzy[0]?.words)

        // A short word is too easy to match by chance: "sweat" is within one letter of "sweet" (5 letters, so allowed),
        // but "lone" is within one letter of "love" and has only four.
        val shortWords = library("love song heart", rules = SpeechRules(fuzzy = true)).scoreAll(heard("lone sing heat"))
        assertTrue(shortWords.isEmpty())
    }

    @Test
    fun theLockOnTheWordsPath_needsALead() {
        // Three words, tied with another song: the words cannot tell the two apart.
        assertFalse(VocalLockPolicy.opens(MatchEvidence(speechWords = 3, speechWeight = 3f, rivalWeight = 3f)))
        assertFalse(VocalLockPolicy.opens(MatchEvidence(speechWords = 3, speechWeight = 3f, rivalWeight = 2.9f)))
        // Three words, clearly ahead.
        assertTrue(VocalLockPolicy.opens(MatchEvidence(speechWords = 3, speechWeight = 3f, rivalWeight = 1f)))
        // The pitch path does not depend on the words.
        assertTrue(VocalLockPolicy.opens(MatchEvidence(pitchScore = 1.0f, pitchOverlap = 40, rivalWeight = 9f)))
    }

    private val songDao: SongDao = mock()

    private fun resultHearing(
        text: String,
        vararg songs: Song,
    ): RecognitionResult? {
        whenever(songDao.getAllSongs()).thenReturn(
            flow {
                delay(30)
                emit(songs.toList())
            },
        )
        val spoken = MutableStateFlow("")
        val vosk: VoskVoiceRecognition = mock()
        whenever(vosk.isModelReady).thenReturn(MutableStateFlow(true))
        whenever(vosk.recognitionResult).thenReturn(spoken)
        val engine = HybridRecognitionEngine(mock<Context>(), songDao, vosk, pitchIntervalMs = 100)
        return runBlocking {
            spoken.value = text
            withTimeoutOrNull(1_500) { engine.recognitionResult.first { r -> songs.all { s -> r.candidates.any { it.id == s.id } } } }
        }
    }

    @Test
    fun twoSongsTiedOnTheHeardWords_keepTheLockClosedForBoth() {
        val one = Song(id = 1, title = "One", artist = "x", lyrics = "ocean river mountain valley")
        val two = Song(id = 2, title = "Two", artist = "x", lyrics = "ocean river mountain desert")
        val result = resultHearing("ocean river mountain", one, two)

        assertNotNull(result)
        assertEquals(3, result.evidence[1]?.speechWords)
        assertEquals(3, result.evidence[2]?.speechWords)
        assertFalse(VocalLockPolicy.opens(result.evidence[1]), "tied songs must not open the lock")
        assertFalse(VocalLockPolicy.opens(result.evidence[2]))
    }

    @Test
    fun aSongClearlyAhead_opensTheLock_whileItsRivalDoesNot() {
        val one = Song(id = 1, title = "One", artist = "x", lyrics = "ocean river mountain valley")
        val two = Song(id = 2, title = "Two", artist = "x", lyrics = "ocean river canyon desert")
        val result = resultHearing("ocean river mountain", one, two)

        assertNotNull(result)
        assertTrue(VocalLockPolicy.opens(result.evidence[1]))
        assertFalse(VocalLockPolicy.opens(result.evidence[2]), "two words are not enough")
    }
}

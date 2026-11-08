package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridRecognitionEngineMatchingTest {

    private lateinit var engine: HybridRecognitionEngine
    private lateinit var songDao: SongDao

    private val melody = listOf(
        261.63f, 293.66f, 329.63f, 349.23f, 392.00f, 440.00f,
        493.88f, 523.25f, 493.88f, 440.00f, 392.00f, 349.23f
    )

    @BeforeEach
    fun setup() {
        songDao = mock()
        engine = HybridRecognitionEngine(mock<Context>(), songDao)
    }

    @Test
    fun relativeContour_marksUnvoicedFramesAsNaN() {
        val contour = AudioFingerprinter.toRelativeSemitoneContour(listOf(440f, 0f, 880f))

        assertTrue(contour[1].isNaN(), "Silence must not look like a voiced frame at the mean pitch")
        assertEquals(-6f, contour[0], 0.01f)
        assertEquals(6f, contour[2], 0.01f)
    }

    @Test
    fun calculateMatchScore_skipsFramesSilentInEitherContour() {
        // TC-U-05: the live take has two silent frames where the reference is voiced.
        val live = melody.toMutableList().also { it[2] = 0f; it[9] = 0f }

        val score = engine.calculateMatchScore(melody, live)

        assertTrue(score < 0.5f, "Silence in one contour must be skipped, not compared with 0; was $score")
    }

    @Test
    fun calculateMatchScore_withTooLittleSharedVoicedMaterial_isNoMatch() {
        // Only 3 of 12 live frames are voiced, so the overlap is too small to trust.
        val live = melody.mapIndexed { i, hz -> if (i < 3) hz else 0f }

        assertEquals(Float.MAX_VALUE, engine.calculateMatchScore(melody, live))
    }

    @Test
    fun calculateMatchScore_withAllSilentLive_isNoMatch() {
        assertEquals(Float.MAX_VALUE, engine.calculateMatchScore(melody, List(12) { 0f }))
    }

    @Test
    fun recognitionResult_findsSpeechThatIsOnlyBrieflyPresent() {
        // Real speech models replace the text as soon as the next utterance starts. The match
        // must happen when the text changes, not on a later fixed tick that may find it gone.
        val song = Song(id = 9, title = "Brief", artist = "x", lyrics = "Amazing Grace! How sweet the sound")
        whenever(songDao.getAllSongs()).thenReturn(flow { delay(50); emit(listOf(song)) })
        val spokenText = MutableStateFlow("")
        val vosk: VoskVoiceRecognition = mock()
        whenever(vosk.isModelReady).thenReturn(MutableStateFlow(true))
        whenever(vosk.recognitionResult).thenReturn(spokenText)
        engine.javaClass.getDeclaredField("vosk").also { it.isAccessible = true }.set(engine, vosk)

        val found = runBlocking {
            val search = async {
                withTimeoutOrNull(3_000) {
                    engine.recognitionResult.first { result -> result.candidates.any { it.id == 9 } }
                }
            }
            delay(300)
            spokenText.value = "amazing grace how sweet the sound"
            delay(400)
            spokenText.value = "" // gone well before the next 5 second pitch tick
            search.await()
        }

        assertTrue(found != null, "A phrase heard for under a second was never matched")
    }

    @Test
    fun recognitionResult_retainsSttCandidatesAfterSpeechTextClears() {
        // TC-U-08 (ADR-05): a candidate found from speech must stay once the text is empty again.
        val song = Song(id = 7, title = "Amazing Grace", artist = "x", lyrics = "Amazing Grace! How sweet the sound")
        // Delay mimics Room hopping to its query executor.
        whenever(songDao.getAllSongs()).thenReturn(flow { delay(50); emit(listOf(song)) })
        val spokenText = MutableStateFlow("amazing grace how sweet")
        val vosk: VoskVoiceRecognition = mock()
        whenever(vosk.isModelReady).thenReturn(MutableStateFlow(true))
        whenever(vosk.recognitionResult).thenReturn(spokenText)
        val voskField = engine.javaClass.getDeclaredField("vosk").also { it.isAccessible = true }
        voskField.set(engine, vosk)

        var matched = false
        var clearedAt = 0L
        var retained: Boolean? = null
        runBlocking {
            withTimeout(25_000) {
                engine.recognitionResult
                    .takeWhile { retained == null }
                    .collect { result ->
                        val hasSong = result.candidates.any { it.id == 7 }
                        if (!matched && hasSong) {
                            matched = true
                            spokenText.value = ""
                            clearedAt = System.nanoTime()
                        } else if (matched && System.nanoTime() - clearedAt > 4_500_000_000L) {
                            // A full heartbeat later, so this result was computed with empty text.
                            retained = hasSong
                        }
                    }
            }
        }

        assertTrue(matched, "The spoken phrase should have matched the song")
        assertEquals(true, retained, "Candidate must be retained after the speech text clears")
    }
}

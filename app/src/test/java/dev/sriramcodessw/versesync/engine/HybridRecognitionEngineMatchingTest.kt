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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
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

    /**
     * An engine hearing [spokenText] with [songs] in its library. The recognizer is a fake and the
     * pitch interval is short, so nothing here waits for the real 5 second tick.
     */
    private fun engineHearing(spokenText: MutableStateFlow<String>, vararg songs: Song): HybridRecognitionEngine {
        // The delay mimics Room hopping to its query executor.
        whenever(songDao.getAllSongs()).thenReturn(flow { delay(50); emit(songs.toList()) })
        val vosk: VoskVoiceRecognition = mock()
        whenever(vosk.isModelReady).thenReturn(MutableStateFlow(true))
        whenever(vosk.recognitionResult).thenReturn(spokenText)
        return HybridRecognitionEngine(mock<Context>(), songDao, vosk, pitchIntervalMs = 100)
    }

    @Test
    fun recognitionResult_findsSpeechThatIsOnlyBrieflyPresent() {
        // Real speech models replace the text as soon as the next utterance starts. The match
        // must happen when the text changes, not on a later tick that may find it gone.
        val song = Song(id = 9, title = "Brief", artist = "x", lyrics = "Amazing Grace! How sweet the sound")
        val spokenText = MutableStateFlow("")
        val engine = engineHearing(spokenText, song)

        val found = runBlocking {
            val search = async {
                withTimeoutOrNull(5_000) {
                    engine.recognitionResult.first { result -> result.candidates.any { it.id == 9 } }
                }
            }
            delay(200)
            spokenText.value = "amazing grace how sweet the sound"
            delay(120)
            spokenText.value = "" // gone again after a fraction of a second
            search.await()
        }

        assertTrue(found != null, "A phrase heard for a fraction of a second was never matched")
    }

    @Test
    fun recognitionResult_doesNotReportASongForUnrelatedSpeech() {
        val song = Song(id = 5, title = "Other", artist = "x", lyrics = "Amazing Grace! How sweet the sound")
        val spokenText = MutableStateFlow("completely different words about something else entirely")
        val engine = engineHearing(spokenText, song)

        val emissions = runBlocking {
            withTimeoutOrNull(1_500) { engine.recognitionResult.take(5).toList() } ?: emptyList()
        }

        assertTrue(emissions.isNotEmpty(), "The engine should still report, with no candidates")
        assertTrue(emissions.none { r -> r.candidates.any { it.id == 5 } }, "Unrelated speech matched a song")
    }

    @Test
    fun recognitionResult_followsLibraryChanges() {
        // A song added while listening must be searchable without restarting recognition.
        val spokenText = MutableStateFlow("amazing grace how sweet the sound")
        val library = MutableStateFlow(emptyList<Song>())
        whenever(songDao.getAllSongs()).thenReturn(library)
        val vosk: VoskVoiceRecognition = mock()
        whenever(vosk.isModelReady).thenReturn(MutableStateFlow(true))
        whenever(vosk.recognitionResult).thenReturn(spokenText)
        val engine = HybridRecognitionEngine(mock<Context>(), songDao, vosk, pitchIntervalMs = 100)

        val found = runBlocking {
            val search = async {
                withTimeoutOrNull(5_000) {
                    engine.recognitionResult.first { r -> r.candidates.any { it.id == 3 } }
                }
            }
            delay(300)
            library.value = listOf(Song(id = 3, title = "Late", artist = "x", lyrics = "Amazing Grace! How sweet the sound"))
            search.await()
        }

        assertTrue(found != null, "A song added during recognition was not searched")
    }

    @Test
    fun recognitionResult_retainsSttCandidatesAfterSpeechTextClears() {
        // TC-U-08 (ADR-05): a candidate found from speech must stay once the text is empty again.
        val song = Song(id = 7, title = "Amazing Grace", artist = "x", lyrics = "Amazing Grace! How sweet the sound")
        val spokenText = MutableStateFlow("amazing grace how sweet")
        val engine = engineHearing(spokenText, song)

        var matched = false
        val afterClear = mutableListOf<Boolean>()
        runBlocking {
            withTimeout(10_000) {
                // Several pitch ticks pass after the text is cleared, each computed with empty text.
                engine.recognitionResult
                    .takeWhile { afterClear.size < 5 }
                    .collect { result ->
                        val hasSong = result.candidates.any { it.id == 7 }
                        when {
                            !matched && hasSong -> {
                                matched = true
                                spokenText.value = ""
                            }
                            matched -> afterClear += hasSong
                        }
                    }
            }
        }

        assertTrue(matched, "The spoken phrase should have matched the song")
        assertTrue(afterClear.all { it }, "Candidate must be retained after the speech text clears: $afterClear")
    }
}

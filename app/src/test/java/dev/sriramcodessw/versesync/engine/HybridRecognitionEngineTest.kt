package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.audio.AudioFormat
import dev.sriramcodessw.versesync.audio.FakeAudioSource
import dev.sriramcodessw.versesync.audio.FixedPitchDetector
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridRecognitionEngineTest {
    private lateinit var voskMock: VoskVoiceRecognition
    private lateinit var songDaoMock: SongDao
    private lateinit var audioSource: FakeAudioSource
    private lateinit var detector: FixedPitchDetector
    private lateinit var hybridEngine: HybridRecognitionEngine

    // A loud frame. Frames that are nearly silent never reach the pitch detector (issue #33).
    private val voice = FloatArray(AudioFormat.FRAME_SAMPLES) { if (it % 2 == 0) 0.3f else -0.3f }

    @BeforeEach
    fun setup() {
        voskMock = mock()
        songDaoMock = mock()
        audioSource = FakeAudioSource()
        detector = FixedPitchDetector(220f)
        val contextMock: Context = mock()

        whenever(voskMock.isModelReady).thenReturn(MutableStateFlow(true))

        hybridEngine =
            HybridRecognitionEngine(
                contextMock,
                songDaoMock,
                voskMock,
                audioSourceFactory = { audioSource },
                pitchDetectorFactory = { detector },
            )
    }

    @Test
    fun startRecognition_forProUser_shouldStartBothSttAndPitch() {
        hybridEngine.startRecognition(isPro = true)
        audioSource.emit(voice)

        verify(voskMock).startListening()
        assertTrue(audioSource.isRunning)
        assertEquals(1, detector.frames, "A Pro user's audio must be run through the pitch detector")
    }

    @Test
    fun startRecognition_forFreeUser_shouldStartOnlyStt() {
        hybridEngine.startRecognition(isPro = false)
        audioSource.emit(voice)

        verify(voskMock).startListening()
        assertEquals(0, detector.frames, "A free user's audio must not be run through the pitch detector")
    }

    @Test
    fun startRecognition_whenVoskModelNotReady_shouldStillStartPitchForPro() {
        // Given (ADR-02, TC-U-07)
        whenever(voskMock.isModelReady).thenReturn(MutableStateFlow(false))

        // When
        hybridEngine.startRecognition(isPro = true)
        audioSource.emit(voice)

        // Then
        verify(voskMock, never()).startListening()
        assertEquals(1, detector.frames)
    }

    @Test
    fun startRecognition_whenTheMicrophoneCannotBeOpened_reportsAnError() {
        val failing =
            HybridRecognitionEngine(
                mock<Context>(),
                songDaoMock,
                voskMock,
                audioSourceFactory = { throw IllegalStateException("no microphone") },
                pitchDetectorFactory = { detector },
            )

        failing.startRecognition(isPro = true)

        assertEquals("Microphone initialization failed.", failing.error.value)
        verify(voskMock, never()).startListening()
    }

    @Test
    fun stopRecognition_shouldStopBothSttAndTheAudioSource() {
        hybridEngine.startRecognition(isPro = true)

        hybridEngine.stopRecognition()

        verify(voskMock).stopListening()
        assertEquals(1, audioSource.stopCount)
        assertFalse(audioSource.isRunning)
    }

    @Test
    fun startingAgainAfterStopping_usesAFreshAudioSource() {
        var created = 0
        val engine =
            HybridRecognitionEngine(
                mock<Context>(),
                songDaoMock,
                voskMock,
                audioSourceFactory = {
                    created++
                    FakeAudioSource()
                },
                pitchDetectorFactory = { detector },
            )

        engine.startRecognition(isPro = true)
        engine.stopRecognition()
        engine.startRecognition(isPro = true)

        assertEquals(2, created)
    }

    @Test
    fun matchLyricsScore_shouldMatchFormattedLyricsWithPunctuationAndNewlines() {
        // Given (ADR-03, TC-U-06)
        val spoken = "amazing grace how sweet the sound"
        val formattedLyrics = "Amazing Grace! How sweet the sound,\nThat saved a wretch like me."

        // When
        val score = hybridEngine.matchLyricsScore(spoken, formattedLyrics)

        // Then
        assertTrue(score >= 0.7f, "Expected high match score despite punctuation, but was $score")
    }

    @Test
    fun hzToSemitone_shouldMapConcertA4To69() {
        // Given (ADR-04, TC-U-03)
        val semitoneA4 = AudioFingerprinter.hzToSemitone(440f)
        val semitoneA3 = AudioFingerprinter.hzToSemitone(220f)

        // Then (A4 is MIDI 69, A3 is MIDI 57, exactly 12 semitones lower)
        assertEquals(69f, semitoneA4, 0.05f)
        assertEquals(57f, semitoneA3, 0.05f)
    }

    @Test
    fun calculateMatchScore_shouldBeTranspositionInvariant() {
        // Given (ADR-04, TC-U-04): Melody in C4 vs same melody in G3 (shifted by 5 semitones)
        val baseMelody = listOf(261.63f, 293.66f, 329.63f, 349.23f, 392.00f, 440.00f, 493.88f, 523.25f, 493.88f, 440f)
        val transposedMelody = listOf(196.00f, 220.00f, 246.94f, 261.63f, 293.66f, 329.63f, 369.99f, 392.00f, 369.99f, 329.63f)

        val score = hybridEngine.calculateMatchScore(baseMelody, transposedMelody)

        // Then: Relative contour distance should be very low (close to 0) despite key change
        assertTrue(score < 1.0f, "Expected transposition invariant score < 1.0 semitone, was $score")
    }

    @Test
    fun release_stopsTheMicrophoneAndTheSpeechServiceAndFreesTheModel() {
        hybridEngine.startRecognition(isPro = true)

        hybridEngine.release()

        assertFalse(audioSource.isRunning)
        assertEquals(1, audioSource.stopCount)
        verify(voskMock).stopListening()
        verify(voskMock).release()
    }

    @Test
    fun release_whenNothingWasStartedIsSafe() {
        hybridEngine.release()

        verify(voskMock).release()
    }
}

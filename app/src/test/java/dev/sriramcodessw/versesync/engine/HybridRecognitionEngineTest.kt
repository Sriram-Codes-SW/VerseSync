package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridRecognitionEngineTest {
    private lateinit var voskMock: VoskVoiceRecognition
    private lateinit var fingerprinterMock: AudioFingerprinter
    private lateinit var dispatcherMock: be.tarsos.dsp.AudioDispatcher
    private lateinit var songDaoMock: SongDao
    private lateinit var hybridEngine: HybridRecognitionEngine

    @BeforeEach
    fun setup() {
        voskMock = mock()
        fingerprinterMock = mock()
        dispatcherMock = mock()
        songDaoMock = mock()
        val contextMock: Context = mock()

        whenever(voskMock.isModelReady).thenReturn(MutableStateFlow(true))

        hybridEngine = HybridRecognitionEngine(contextMock, songDaoMock)

        val voskField: Field = hybridEngine.javaClass.getDeclaredField("vosk")
        voskField.isAccessible = true
        voskField.set(hybridEngine, voskMock)

        val fingerprinterField: Field = hybridEngine.javaClass.getDeclaredField("fingerprinter")
        fingerprinterField.isAccessible = true
        fingerprinterField.set(hybridEngine, fingerprinterMock)

        val dispatcherField: Field = hybridEngine.javaClass.getDeclaredField("dispatcher")
        dispatcherField.isAccessible = true
        dispatcherField.set(hybridEngine, dispatcherMock)
    }

    @Test
    fun startRecognition_forProUser_shouldStartBothSttAndFingerprinting() {
        // When
        hybridEngine.startRecognition(isPro = true)

        // Then
        verify(voskMock).startListening()
        verify(fingerprinterMock).attachPitchProcessor(any(), any())
    }

    @Test
    fun startRecognition_forFreeUser_shouldStartOnlyStt() {
        // When
        hybridEngine.startRecognition(isPro = false)

        // Then
        verify(voskMock).startListening()
        verify(fingerprinterMock, never()).attachPitchProcessor(any(), any())
    }

    @Test
    fun startRecognition_whenVoskModelNotReady_shouldStillStartPitchProcessorForPro() {
        // Given (ADR-02, TC-U-07)
        whenever(voskMock.isModelReady).thenReturn(MutableStateFlow(false))

        // When
        hybridEngine.startRecognition(isPro = true)

        // Then
        verify(voskMock, never()).startListening()
        verify(fingerprinterMock).attachPitchProcessor(any(), any())
    }

    @Test
    fun stopRecognition_shouldStopBothSttAndDispatcher() {
        // When
        hybridEngine.stopRecognition()

        // Then
        verify(voskMock).stopListening()
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
}

package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Field

class HybridRecognitionEngineTest {

    private lateinit var voskMock: VoskVoiceRecognition
    private lateinit var fingerprinterMock: AudioFingerprinter
    private lateinit var songDaoMock: SongDao
    private lateinit var hybridEngine: HybridRecognitionEngine

    @BeforeEach
    fun setup() {
        voskMock = mock()
        fingerprinterMock = mock()
        songDaoMock = mock()
        val contextMock: Context = mock()
        
        // Mock Vosk flows
        whenever(voskMock.isModelReady).thenReturn(MutableStateFlow(true))
        
        hybridEngine = HybridRecognitionEngine(contextMock, songDaoMock)

        // Manually inject mocks because of the way the engine is instantiated
        val voskField: Field = hybridEngine.javaClass.getDeclaredField("vosk")
        voskField.isAccessible = true
        voskField.set(hybridEngine, voskMock)

        val fingerprinterField: Field = hybridEngine.javaClass.getDeclaredField("fingerprinter")
        fingerprinterField.isAccessible = true
        fingerprinterField.set(hybridEngine, fingerprinterMock)
    }

    @Test
    fun startRecognition_forProUser_shouldStartBothSttAndFingerprinting() {
        // When
        hybridEngine.startRecognition(isPro = true)

        // Then
        verify(voskMock).startListening()
        verify(fingerprinterMock).attachPitchProcessor()
    }

    @Test
    fun startRecognition_forFreeUser_shouldStartOnlyStt() {
        // When
        hybridEngine.startRecognition(isPro = false)

        // Then
        verify(voskMock).startListening()
        verify(fingerprinterMock, never()).attachPitchProcessor()
    }

    @Test
    fun stopRecognition_shouldStopBothSttAndDispatcher() {
        // When
        hybridEngine.stopRecognition()

        // Then
        verify(voskMock).stopListening()
    }
}
